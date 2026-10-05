package com.devtraining.tickets.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.devtraining.tickets.auth.CurrentUser;
import com.devtraining.tickets.booking.dto.BookingRequest;
import com.devtraining.tickets.booking.dto.BookingResponse;
import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.common.exception.ForbiddenException;
import com.devtraining.tickets.common.exception.NotFoundException;
import com.devtraining.tickets.event.Event;
import com.devtraining.tickets.event.EventRepository;
import com.devtraining.tickets.user.Role;
import com.devtraining.tickets.user.User;
import com.devtraining.tickets.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the booking business rules. No database or Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

	private static final CurrentUser ALICE = new CurrentUser(1L, "alice@example.com", Role.CUSTOMER);

	private static final CurrentUser BOB = new CurrentUser(2L, "bob@example.com", Role.CUSTOMER);

	private static final CurrentUser ADMIN = new CurrentUser(99L, "admin@example.com", Role.ADMIN);

	@Mock
	private BookingRepository bookingRepository;

	@Mock
	private EventRepository eventRepository;

	@Mock
	private UserRepository userRepository;

	private BookingService bookingService;

	private Event event;

	@BeforeEach
	void setUp() {
		bookingService = new BookingService(bookingRepository, eventRepository, userRepository);
		event = event(10L, new BigDecimal("25.50"), 5);
	}

	@Test
	void createReducesSeatsAndComputesPriceOnServer() {
		givenEvent(event);
		given(userRepository.getReferenceById(ALICE.id())).willReturn(user(ALICE));
		given(bookingRepository.save(any(Booking.class))).willAnswer((invocation) -> invocation.getArgument(0));

		BookingResponse response = bookingService.create(ALICE, new BookingRequest(event.getId(), 3));

		assertThat(event.getAvailableSeats()).isEqualTo(2);
		assertThat(response.unitPrice()).isEqualByComparingTo("25.50");
		assertThat(response.totalPrice()).isEqualByComparingTo("76.50");
		assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
	}

	@Test
	void createRejectsOverbookingWithConflict() {
		givenEvent(event);
		given(userRepository.getReferenceById(ALICE.id())).willReturn(user(ALICE));
		given(bookingRepository.save(any(Booking.class))).willAnswer((invocation) -> invocation.getArgument(0));
		bookingService.create(ALICE, new BookingRequest(event.getId(), 4));

		assertThatThrownBy(() -> bookingService.create(ALICE, new BookingRequest(event.getId(), 2)))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("Only 1 seat(s) left");
		assertThat(event.getAvailableSeats()).isEqualTo(1);
	}

	@Test
	void createRejectsCancelledEventWithConflict() {
		event.cancel();
		givenEvent(event);

		assertThatThrownBy(() -> bookingService.create(ALICE, new BookingRequest(event.getId(), 1)))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("cancelled");
		verify(bookingRepository, never()).save(any());
	}

	@Test
	void createRejectsUnknownEvent() {
		given(eventRepository.findByIdForUpdate(404L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> bookingService.create(ALICE, new BookingRequest(404L, 1)))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void cancelGivesSeatsBack() {
		event.reserveSeats(2);
		Booking booking = booking(100L, ALICE, event, 2);
		given(bookingRepository.findByIdForUpdate(100L)).willReturn(Optional.of(booking));
		givenEvent(event);

		BookingResponse response = bookingService.cancel(ALICE, 100L);

		assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(event.getAvailableSeats()).isEqualTo(5);
	}

	@Test
	void cancelTwiceIsConflictAndDoesNotReleaseSeatsAgain() {
		event.reserveSeats(2);
		Booking booking = booking(100L, ALICE, event, 2);
		given(bookingRepository.findByIdForUpdate(100L)).willReturn(Optional.of(booking));
		givenEvent(event);
		bookingService.cancel(ALICE, 100L);

		assertThatThrownBy(() -> bookingService.cancel(ALICE, 100L)).isInstanceOf(ConflictException.class);
		assertThat(event.getAvailableSeats()).isEqualTo(5);
	}

	@Test
	void customerCannotViewOrCancelSomeoneElsesBooking() {
		Booking booking = booking(100L, ALICE, event, 1);
		given(bookingRepository.findWithEventById(100L)).willReturn(Optional.of(booking));
		given(bookingRepository.findByIdForUpdate(100L)).willReturn(Optional.of(booking));

		assertThatThrownBy(() -> bookingService.findById(BOB, 100L)).isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> bookingService.cancel(BOB, 100L)).isInstanceOf(ForbiddenException.class);
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
	}

	@Test
	void adminCanViewAnyBooking() {
		Booking booking = booking(100L, ALICE, event, 1);
		given(bookingRepository.findWithEventById(100L)).willReturn(Optional.of(booking));

		assertThat(bookingService.findById(ADMIN, 100L).userId()).isEqualTo(ALICE.id());
	}

	private void givenEvent(Event event) {
		given(eventRepository.findByIdForUpdate(event.getId())).willReturn(Optional.of(event));
	}

	private static Event event(Long id, BigDecimal price, int seats) {
		Event event = new Event("Concert", "Live music", "Main Hall", Instant.parse("2030-01-01T19:00:00Z"), price,
				seats);
		ReflectionTestUtils.setField(event, "id", id);
		return event;
	}

	private static User user(CurrentUser currentUser) {
		User user = new User(currentUser.email(), "{noop}secret", "Test User", currentUser.role());
		ReflectionTestUtils.setField(user, "id", currentUser.id());
		return user;
	}

	private static Booking booking(Long id, CurrentUser owner, Event event, int quantity) {
		Booking booking = new Booking(user(owner), event, quantity);
		ReflectionTestUtils.setField(booking, "id", id);
		return booking;
	}

}
