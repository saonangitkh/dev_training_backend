package com.devtraining.tickets.booking;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.devtraining.tickets.auth.CurrentUser;
import com.devtraining.tickets.booking.dto.BookingRequest;
import com.devtraining.tickets.booking.dto.BookingResponse;
import com.devtraining.tickets.common.exception.BadRequestException;
import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.common.exception.ForbiddenException;
import com.devtraining.tickets.config.BookingProperties;
import com.devtraining.tickets.config.CinemaProperties;
import com.devtraining.tickets.hall.Hall;
import com.devtraining.tickets.hall.Seat;
import com.devtraining.tickets.hall.SeatRepository;
import com.devtraining.tickets.hall.SeatType;
import com.devtraining.tickets.movie.AgeRating;
import com.devtraining.tickets.movie.Movie;
import com.devtraining.tickets.showtime.Showtime;
import com.devtraining.tickets.showtime.ShowtimeRepository;
import com.devtraining.tickets.user.Role;
import com.devtraining.tickets.user.User;
import com.devtraining.tickets.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the booking rules. No database or Spring context: repositories are mocked and
 * the clock is fixed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

	private static final CurrentUser DARA = new CurrentUser(1L, "dara@example.com", Role.CUSTOMER);

	private static final CurrentUser SOKHA = new CurrentUser(2L, "sokha@example.com", Role.CUSTOMER);

	private static final CurrentUser ADMIN = new CurrentUser(99L, "admin@example.com", Role.ADMIN);

	private static final Instant STARTS_AT = Instant.parse("2030-04-14T12:00:00Z");

	/** Two weeks before the showtime: booking, paying and cancelling are all allowed. */
	private static final Instant NOW = Instant.parse("2030-04-01T10:00:00Z");

	private static final BookingProperties PROPERTIES = new BookingProperties(8, Duration.ofHours(2),
			Duration.ofMinutes(10), true);

	private static final CinemaProperties CINEMA = new CinemaProperties(ZoneId.of("Asia/Phnom_Penh"),
			Duration.ofMinutes(15), Duration.ofHours(1));

	@Mock
	private BookingRepository bookingRepository;

	@Mock
	private BookingSeatRepository bookingSeatRepository;

	@Mock
	private ShowtimeRepository showtimeRepository;

	@Mock
	private SeatRepository seatRepository;

	@Mock
	private UserRepository userRepository;

	private Showtime showtime;

	/** Row A: 6 STANDARD seats. Row B: 4 VIP seats. */
	private List<Seat> seats;

	@BeforeEach
	void setUp() {
		Hall hall = withId(new Hall("Hall 1"), 1L);
		hall.addRow("A", 6, SeatType.STANDARD);
		hall.addRow("B", 4, SeatType.VIP);
		seats = hall.getSeats();
		for (int i = 0; i < seats.size(); i++) {
			withId(seats.get(i), 100L + i);
		}
		showtime = withId(new Showtime(withId(new Movie("The Last Reel", null, 120, AgeRating.G), 1L), hall,
				STARTS_AT, STARTS_AT.plus(Duration.ofMinutes(135)), new BigDecimal("5.00")), 10L);

		given(showtimeRepository.findByIdForUpdate(10L)).willReturn(Optional.of(showtime));
		given(seatRepository.findAllByHallId(1L)).willReturn(seats);
		given(bookingSeatRepository.findTakenSeatIds(eq(10L), any())).willReturn(Set.of());
		given(userRepository.findById(DARA.id())).willReturn(Optional.of(user(DARA, LocalDate.of(1995, 5, 20))));
		given(userRepository.findById(SOKHA.id())).willReturn(Optional.of(user(SOKHA, null)));
		given(bookingRepository.saveAndFlush(any(Booking.class))).willAnswer((invocation) -> {
			Booking booking = invocation.getArgument(0);
			return withId(booking, 500L);
		});
	}

	@Test
	void createHoldsSeatsWithServerPrices() {
		BookingResponse response = service(NOW).create(DARA, request("A1", "A2", "B1"), null);

		assertThat(response.status()).isEqualTo(BookingStatus.HELD);
		assertThat(response.holdExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
		assertThat(response.totalPrice()).isEqualByComparingTo("17.50");
		assertThat(response.seats()).extracting(BookingResponse.SeatLine::label).containsExactly("A1", "A2", "B1");
		assertThat(response.ticketCode()).isNull();
	}

	@Test
	void createExpiresOldHoldsFirst() {
		service(NOW).create(DARA, request("A1"), null);

		verify(bookingSeatRepository).releaseExpiredHolds(10L, NOW);
		verify(bookingRepository).expireHolds(10L, NOW);
	}

	@Test
	void createRejectsTakenSeats() {
		given(bookingSeatRepository.findTakenSeatIds(eq(10L), any())).willReturn(Set.of(seat("A2").getId()));

		assertThatThrownBy(() -> service(NOW).create(DARA, request("A1", "A2"), null))
			.isInstanceOf(ConflictException.class)
			.hasMessage("Seat(s) already taken: A2");
		verify(bookingRepository, never()).saveAndFlush(any());
	}

	@Test
	void createRejectsUnknownAndDuplicateSeats() {
		assertThatThrownBy(() -> service(NOW).create(DARA, request("A1", "Z9"), null))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("Z9");
		assertThatThrownBy(() -> service(NOW).create(DARA, request("A1", "a1"), null))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("listed twice");
	}

	@Test
	void createRejectsSingleSeatGap() {
		given(bookingSeatRepository.findTakenSeatIds(eq(10L), any())).willReturn(Set.of(seat("A3").getId()));

		assertThatThrownBy(() -> service(NOW).create(DARA, request("A1"), null)).isInstanceOf(ConflictException.class)
			.hasMessageStartingWith("This choice would leave a single empty seat at A2");
		assertThatThrownBy(() -> service(NOW).create(DARA, request("A5"), null)).isInstanceOf(ConflictException.class)
			.hasMessageStartingWith("This choice would leave a single empty seat at A4");
		service(NOW).create(DARA, request("A1", "A2"), null);
		service(NOW).create(DARA, request("A6"), null);
	}

	@Test
	void singleSeatGapRuleCanBeTurnedOff() {
		given(bookingSeatRepository.findTakenSeatIds(eq(10L), any())).willReturn(Set.of(seat("A3").getId()));
		BookingService lenient = new BookingService(bookingRepository, bookingSeatRepository, showtimeRepository,
				seatRepository, userRepository, new TicketCodeGenerator(),
				new BookingProperties(8, Duration.ofHours(2), Duration.ofMinutes(10), false), CINEMA,
				Clock.fixed(NOW, ZoneOffset.UTC));

		lenient.create(DARA, request("A1"), null);
	}

	@Test
	void createRejectsCustomerOverTheLimit() {
		given(bookingRepository.sumQuantity(eq(DARA.id()), eq(10L), anyCollection())).willReturn(6);

		assertThatThrownBy(() -> service(NOW).create(DARA, request("A1", "A2", "A3"), null))
			.isInstanceOf(ConflictException.class)
			.hasMessage("You can book at most 8 seats for showtime 10, you already have 6");
		service(NOW).create(DARA, request("A1", "A2"), null);
	}

	@Test
	void createRejectsStartedOrCancelledShowtime() {
		assertThatThrownBy(() -> service(STARTS_AT).create(DARA, request("A1"), null))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("already started");
		showtime.cancel();
		assertThatThrownBy(() -> service(NOW).create(DARA, request("A1"), null)).isInstanceOf(ConflictException.class)
			.hasMessageContaining("cancelled");
	}

	@Test
	void restrictedMovieNeedsAgeOnShowDay() {
		ReflectionTestUtils.setField(showtime.getMovie(), "ageRating", AgeRating.R18);
		// turns 18 on the showtime date in Phnom Penh (12:00 UTC = 19:00 local, same day)
		given(userRepository.findById(DARA.id())).willReturn(Optional.of(user(DARA, LocalDate.of(2012, 4, 14))));
		service(NOW).create(DARA, request("A1"), null);

		given(userRepository.findById(DARA.id())).willReturn(Optional.of(user(DARA, LocalDate.of(2012, 4, 15))));
		assertThatThrownBy(() -> service(NOW).create(DARA, request("A3"), null))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("at least 18");

		assertThatThrownBy(() -> service(NOW).create(SOKHA, request("A3"), null))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("date of birth");
	}

	@Test
	void sameIdempotencyKeyReturnsTheFirstBooking() {
		Booking previous = held(DARA, "A1");
		given(bookingRepository.findByUserIdAndIdempotencyKey(DARA.id(), "key-1")).willReturn(Optional.of(previous));

		BookingResponse response = service(NOW).create(DARA, request("A1"), "key-1");

		assertThat(response.id()).isEqualTo(previous.getId());
		verify(bookingRepository, never()).saveAndFlush(any());
	}

	@Test
	void sameIdempotencyKeyWithDifferentSeatsIsRejected() {
		Booking previous = held(DARA, "A1");
		given(bookingRepository.findByUserIdAndIdempotencyKey(DARA.id(), "key-1")).willReturn(Optional.of(previous));

		assertThatThrownBy(() -> service(NOW).create(DARA, request("A2"), "key-1"))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("Idempotency key was already used");
		assertThat(service(NOW).create(DARA, request("a1"), "key-1").id()).isEqualTo(previous.getId());
	}

	@Test
	void payConfirmsAndIssuesTicketCode() {
		Booking booking = held(DARA, "A1");
		givenBooking(booking);

		BookingResponse response = service(NOW.plus(Duration.ofMinutes(5))).pay(DARA, booking.getId());

		assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(response.ticketCode()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
	}

	@Test
	void payAfterHoldExpiredIsRejected() {
		Booking booking = held(DARA, "A1");
		givenBooking(booking);

		assertThatThrownBy(() -> service(NOW.plus(Duration.ofMinutes(10))).pay(DARA, booking.getId()))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("hold expired");
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
	}

	@Test
	void customerCannotPayViewOrCancelSomeoneElsesBooking() {
		Booking booking = held(DARA, "A1");
		givenBooking(booking);
		given(bookingRepository.findWithDetailsById(booking.getId())).willReturn(Optional.of(booking));

		assertThatThrownBy(() -> service(NOW).pay(SOKHA, booking.getId())).isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> service(NOW).findById(SOKHA, booking.getId())).isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> service(NOW).cancel(SOKHA, booking.getId())).isInstanceOf(ForbiddenException.class);
		assertThat(service(NOW).findById(ADMIN, booking.getId()).userId()).isEqualTo(DARA.id());
	}

	@Test
	void cancelReleasesSeatsExactlyOnce() {
		Booking booking = held(DARA, "A1", "A2");
		givenBooking(booking);

		service(NOW).cancel(DARA, booking.getId());

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(booking.getSeats()).noneMatch(BookingSeat::isActive);
		assertThatThrownBy(() -> service(NOW).cancel(DARA, booking.getId())).isInstanceOf(ConflictException.class);
	}

	@Test
	void customerCannotCancelPaidBookingWithinTwoHours() {
		Booking booking = held(DARA, "A1");
		booking.pay(NOW, "ABCD-EFGH");
		givenBooking(booking);
		Instant ninetyMinutesBefore = STARTS_AT.minus(Duration.ofMinutes(90));

		assertThatThrownBy(() -> service(ninetyMinutesBefore).cancel(DARA, booking.getId()))
			.isInstanceOf(ConflictException.class)
			.hasMessage("Paid bookings can only be cancelled until 2 hour(s) before the showtime starts");
		service(STARTS_AT.minus(Duration.ofHours(2))).cancel(DARA, booking.getId());
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
	}

	@Test
	void adminCanCancelPaidBookingWithinTwoHours() {
		Booking booking = held(DARA, "A1");
		booking.pay(NOW, "ABCD-EFGH");
		givenBooking(booking);

		service(STARTS_AT.minus(Duration.ofMinutes(10))).cancel(ADMIN, booking.getId());

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
	}

	@Test
	void ticketChecksInOnceInsideTheWindow() {
		Booking booking = held(DARA, "A1");
		booking.pay(NOW, "ABCD-EFGH");
		given(bookingRepository.findShowtimeIdByTicketCode("ABCD-EFGH")).willReturn(Optional.of(10L));
		given(bookingRepository.findByTicketCodeForUpdate("ABCD-EFGH")).willReturn(Optional.of(booking));

		assertThatThrownBy(() -> service(STARTS_AT.minus(Duration.ofMinutes(61))).checkIn("abcd-efgh"))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("opens at");
		service(STARTS_AT.minus(Duration.ofMinutes(60))).checkIn("abcd-efgh");
		assertThatThrownBy(() -> service(STARTS_AT).checkIn("ABCD-EFGH")).isInstanceOf(ConflictException.class)
			.hasMessageContaining("already checked in");
	}

	private BookingService service(Instant now) {
		return new BookingService(bookingRepository, bookingSeatRepository, showtimeRepository, seatRepository,
				userRepository, new TicketCodeGenerator(), PROPERTIES, CINEMA, Clock.fixed(now, ZoneOffset.UTC));
	}

	private BookingRequest request(String... labels) {
		return new BookingRequest(10L, List.of(labels));
	}

	private Booking held(CurrentUser owner, String... labels) {
		List<Seat> chosen = List.of(labels).stream().map(this::seat).toList();
		Booking booking = new Booking(user(owner, null), showtime, chosen, NOW.plus(Duration.ofMinutes(10)), null);
		return withId(booking, 500L);
	}

	private void givenBooking(Booking booking) {
		given(bookingRepository.findShowtimeIdById(booking.getId())).willReturn(Optional.of(10L));
		given(bookingRepository.findByIdForUpdate(booking.getId())).willReturn(Optional.of(booking));
	}

	private Seat seat(String label) {
		return seats.stream().filter((seat) -> seat.getLabel().equals(label)).findFirst().orElseThrow();
	}

	private static User user(CurrentUser currentUser, LocalDate dateOfBirth) {
		return withId(new User(currentUser.email(), "{noop}secret", "Test User", currentUser.role(), dateOfBirth),
				currentUser.id());
	}

	private static <T> T withId(T entity, Long id) {
		ReflectionTestUtils.setField(entity, "id", id);
		return entity;
	}

}
