package com.devtraining.tickets.booking;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.devtraining.tickets.auth.CurrentUser;
import com.devtraining.tickets.booking.dto.BookingRequest;
import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.showtime.ShowtimeService;
import com.devtraining.tickets.support.MutableClock;
import com.devtraining.tickets.support.TestClockConfig;
import com.devtraining.tickets.user.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests against a real PostgreSQL database (see application-test.yml). Time-based rules
 * use {@link MutableClock}, so holds can expire and showtimes can start without waiting.
 * Run with {@code ./mvnw verify}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class BookingApiIT {

	private static final String ADULT_DOB = "1995-05-20";

	/** Test halls skip row I, like real cinemas, because it looks like 1. */
	private static final List<String> ROWS = List.of("A", "B", "C", "D", "E", "F", "G", "H", "J", "K");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private ShowtimeService showtimeService;

	@Autowired
	private HoldExpiryJob holdExpiryJob;

	@Autowired
	private MutableClock clock;

	private String adminToken;

	private int userCounter;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		for (String table : List.of("booking_seats", "bookings", "showtimes", "seats", "halls", "movies")) {
			jdbcTemplate.execute("DELETE FROM " + table);
		}
		jdbcTemplate.execute("DELETE FROM users WHERE role <> 'ADMIN'");
		adminToken = login("admin@tickets.local", "Admin@12345");
	}

	@Test
	void fullBookingFlowFollowsBusinessRules() throws Exception {
		long showtimeId = createShowtime(hall(8), movie(120, "G"), inDays(30), "5.00");
		String dara = customer();
		String sokha = customer();

		// 03: 1-4 seats per booking
		book(dara, showtimeId, "A1", "A2", "A3", "A4", "A5").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.seats").value("must be between 1 and 4 seats"));
		book(dara, showtimeId).andExpect(status().isBadRequest());

		// unknown or duplicate seats
		book(dara, showtimeId, "Z9").andExpect(status().isBadRequest());
		book(dara, showtimeId, "A1", "A1").andExpect(status().isBadRequest());

		// 04: price from the server (VIP row B = 1.5 x), client price ignored
		String body = mockMvc
			.perform(post("/api/bookings").header(HttpHeaders.AUTHORIZATION, bearer(dara))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"showtimeId": %d, "seats": ["A1", "A2", "B1"], "totalPrice": 0.01}
						""".formatted(showtimeId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("HELD"))
			.andExpect(jsonPath("$.totalPrice").value(17.50))
			.andExpect(jsonPath("$.seats[2].price").value(7.50))
			.andExpect(jsonPath("$.ticketCode").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long bookingId = ((Number) JsonPath.read(body, "$.id")).longValue();

		// 02: held seats are taken
		availableSeats(showtimeId, 77);
		seatAvailable(showtimeId, "A1", false);

		// 01: a seat is sold once
		book(sokha, showtimeId, "A2", "A3").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Seat(s) already taken: A2"));

		// 06: only your own bookings
		mockMvc.perform(get("/api/bookings/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(sokha)))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/bookings/{id}/pay", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(sokha)))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(sokha)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/bookings").header(HttpHeaders.AUTHORIZATION, bearer(sokha)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));

		// pay: confirmed, ticket code issued, can't pay twice
		pay(dara, bookingId).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.ticketCode").isNotEmpty());
		pay(dara, bookingId).andExpect(status().isConflict());

		// 02: cancelling gives the seats back, exactly once
		cancel(dara, bookingId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
		availableSeats(showtimeId, 80);
		cancel(dara, bookingId).andExpect(status().isConflict());
		book(sokha, showtimeId, "A1", "A2").andExpect(status().isCreated());

		// 05: no booking a cancelled showtime
		mockMvc.perform(post("/api/showtimes/{id}/cancel", showtimeId).header(HttpHeaders.AUTHORIZATION,
				bearer(adminToken)))
			.andExpect(status().isOk());
		book(sokha, showtimeId, "C1").andExpect(status().isConflict());
	}

	@Test
	void unpaidHoldsExpireAndReleaseTheirSeats() throws Exception {
		long showtimeId = createShowtime(hall(8), movie(120, "G"), inDays(30), "5.00");
		String dara = customer();
		String sokha = customer();
		long bookingId = bookingId(book(dara, showtimeId, "A1", "A2").andExpect(status().isCreated()));

		clock.advance(Duration.ofMinutes(9));
		seatAvailable(showtimeId, "A1", false);

		clock.advance(Duration.ofMinutes(1));
		seatAvailable(showtimeId, "A1", true);
		mockMvc.perform(get("/api/bookings/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(dara)))
			.andExpect(jsonPath("$.status").value("EXPIRED"));
		pay(dara, bookingId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("hold expired")));

		// someone else can now take the seats; booking first expires the old hold in the database
		book(sokha, showtimeId, "A1", "A2").andExpect(status().isCreated());
		assertThat(bookingStatusInDb(bookingId)).isEqualTo("EXPIRED");
	}

	@Test
	void expiryJobReleasesExpiredHolds() throws Exception {
		long showtimeId = createShowtime(hall(8), movie(120, "G"), inDays(30), "5.00");
		long bookingId = bookingId(book(customer(), showtimeId, "A1").andExpect(status().isCreated()));

		clock.advance(Duration.ofMinutes(11));
		holdExpiryJob.expireHolds();

		assertThat(bookingStatusInDb(bookingId)).isEqualTo("EXPIRED");
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM booking_seats WHERE active", Integer.class))
			.isZero();
	}

	@Test
	void repeatedRequestWithSameIdempotencyKeyReturnsTheSameBooking() throws Exception {
		long showtimeId = createShowtime(hall(8), movie(120, "G"), inDays(30), "5.00");
		String dara = customer();

		long first = bookingId(bookWithKey(dara, showtimeId, "key-123", "A1", "A2").andExpect(status().isCreated()));
		long second = bookingId(bookWithKey(dara, showtimeId, "key-123", "A1", "A2").andExpect(status().isCreated()));

		assertThat(second).isEqualTo(first);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(1);
		// the same key with different seats is a mistake, not a retry
		bookWithKey(dara, showtimeId, "key-123", "C1").andExpect(status().isConflict());
		// a new key is a new booking
		bookWithKey(dara, showtimeId, "key-456", "C1").andExpect(status().isCreated());
	}

	@Test
	void customerCannotHoldMoreThanEightSeatsForOneShowtime() throws Exception {
		long showtimeId = createShowtime(hall(10), movie(120, "G"), inDays(30), "5.00");
		String dara = customer();

		book(dara, showtimeId, "A1", "A2", "A3", "A4").andExpect(status().isCreated());
		book(dara, showtimeId, "A5", "A6", "A7", "A8").andExpect(status().isCreated());
		book(dara, showtimeId, "C1").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail")
				.value("You can book at most 8 seats for showtime %d, you already have 8".formatted(showtimeId)));
	}

	@Test
	void choiceThatLeavesASingleEmptySeatIsRejected() throws Exception {
		long showtimeId = createShowtime(hall(8), movie(120, "G"), inDays(30), "5.00");
		String dara = customer();
		book(dara, showtimeId, "A3", "A4").andExpect(status().isCreated());

		book(customer(), showtimeId, "A6").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(startsWith(
					"This choice would leave a single empty seat at A5")));
		book(customer(), showtimeId, "A1").andExpect(status().isConflict());
		book(customer(), showtimeId, "A5", "A6").andExpect(status().isCreated());
		book(customer(), showtimeId, "A1", "A2").andExpect(status().isCreated());
	}

	@Test
	void ageRatingNeedsOldEnoughCustomer() throws Exception {
		long showtimeId = createShowtime(hall(8), movie(100, "R18"), inDays(30), "5.00");

		book(customer(null), showtimeId, "A1").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value(containsString("add your date of birth")));
		String teen = customer(Instant.now().plus(Duration.ofDays(30)).atZone(ZoneOffset.UTC)
			.toLocalDate().minusYears(17).toString());
		book(teen, showtimeId, "A1").andExpect(status().isForbidden());
		book(customer(), showtimeId, "A1").andExpect(status().isCreated());
	}

	@Test
	void salesCloseAtStartCancelDeadlineAndCheckIn() throws Exception {
		Instant startsAt = Instant.now().plus(Duration.ofHours(6));
		long showtimeId = createShowtime(hall(8), movie(120, "G"), startsAt, "5.00");
		String dara = customer();
		long bookingId = bookingId(book(dara, showtimeId, "A1").andExpect(status().isCreated()));
		String ticketCode = JsonPath.read(pay(dara, bookingId).andReturn().getResponse().getContentAsString(),
				"$.ticketCode");

		// check-in opens 1 hour before the start
		checkIn(ticketCode).andExpect(status().isConflict());

		// 09: customers can't cancel a paid booking within 2 hours of the start; admins can
		clock.setInstant(startsAt.minus(Duration.ofMinutes(90)));
		cancel(dara, bookingId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail")
				.value("Paid bookings can only be cancelled until 2 hour(s) before the showtime starts"));

		// one check-in per ticket
		clock.setInstant(startsAt.minus(Duration.ofMinutes(30)));
		checkIn(ticketCode.toLowerCase()).andExpect(status().isOk()).andExpect(jsonPath("$.checkedInAt").isNotEmpty());
		checkIn(ticketCode).andExpect(status().isConflict());
		cancel(adminToken, bookingId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Booking %d is already checked in".formatted(bookingId)));
		checkIn("NOPE-NOPE").andExpect(status().isNotFound());

		// 08: sales close when the showtime starts
		clock.setInstant(startsAt);
		book(customer(), showtimeId, "C1").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("already started")));
		seatAvailable(showtimeId, "C1", false);
		availableSeats(showtimeId, 0);
	}

	@Test
	void adminCanCancelPaidBookingWithinDeadline() throws Exception {
		Instant startsAt = Instant.now().plus(Duration.ofHours(6));
		long showtimeId = createShowtime(hall(8), movie(120, "G"), startsAt, "5.00");
		String dara = customer();
		long bookingId = bookingId(book(dara, showtimeId, "A1").andExpect(status().isCreated()));
		pay(dara, bookingId).andExpect(status().isOk());

		clock.setInstant(startsAt.minus(Duration.ofMinutes(10)));
		cancel(adminToken, bookingId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void showtimesCantOverlapInAHallOrMoveAfterSales() throws Exception {
		long hallId = hall(8);
		long movieId = movie(120, "G");
		Instant startsAt = inDays(30);
		long showtimeId = createShowtime(hallId, movieId, startsAt, "5.00");

		// ends at start + 120 min + 15 min cleaning
		mockMvc.perform(get("/api/showtimes/{id}", showtimeId))
			.andExpect(jsonPath("$.endsAt").value(startsAt.plus(Duration.ofMinutes(135)).toString()));
		showtimeRequest(post("/api/showtimes"), hallId, movieId, startsAt.plus(Duration.ofMinutes(134)), "5.00")
			.andExpect(status().isConflict());
		showtimeRequest(post("/api/showtimes"), hallId, movieId, startsAt.plus(Duration.ofMinutes(135)), "5.00")
			.andExpect(status().isCreated());

		book(customer(), showtimeId, "A1").andExpect(status().isCreated());
		showtimeRequest(put("/api/showtimes/" + showtimeId), hallId, movieId, startsAt.minus(Duration.ofHours(3)),
				"5.00")
			.andExpect(status().isConflict());
		// the price can still change; existing bookings keep their price
		showtimeRequest(put("/api/showtimes/" + showtimeId), hallId, movieId, startsAt, "6.00")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.basePrice").value(6.00));

		showtimeRequest(post("/api/showtimes"), hallId, movieId, Instant.now().minus(Duration.ofDays(1)), "5.00")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.startsAt").exists());
	}

	@Test
	void cancellingAShowtimeCancelsItsBookings() throws Exception {
		long showtimeId = createShowtime(hall(8), movie(120, "G"), inDays(30), "5.00");
		String dara = customer();
		String sokha = customer();
		long paid = bookingId(book(dara, showtimeId, "A1", "A2").andExpect(status().isCreated()));
		pay(dara, paid).andExpect(status().isOk());
		long held = bookingId(book(sokha, showtimeId, "C1").andExpect(status().isCreated()));

		mockMvc.perform(post("/api/showtimes/{id}/cancel", showtimeId).header(HttpHeaders.AUTHORIZATION,
				bearer(adminToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));

		bookingCancelled(dara, paid);
		bookingCancelled(sokha, held);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM booking_seats WHERE active", Integer.class))
			.isZero();
	}

	@Test
	void securityRules() throws Exception {
		String dara = customer();

		mockMvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/showtimes")).andExpect(status().isOk());
		mockMvc.perform(get("/api/movies")).andExpect(status().isOk());
		mockMvc
			.perform(post("/api/movies").header(HttpHeaders.AUTHORIZATION, bearer(dara))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "Nope", "durationMinutes": 90, "ageRating": "G"}
						"""))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/tickets/ABCD-EFGH/check-in").header(HttpHeaders.AUTHORIZATION, bearer(dara)))
			.andExpect(status().isForbidden());
		mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "user1@example.com", "password": "wrong-password"}
						"""))
			.andExpect(status().isUnauthorized());
		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("user1@example.com", null)))
			.andExpect(status().isConflict());
	}

	@Test
	void concurrentBookingsSellEachSeatOnce() throws Exception {
		long showtimeId = createShowtime(hall(8), movie(120, "G"), inDays(30), "5.00");
		List<CurrentUser> customers = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			customers.add(currentUser(customer()));
		}

		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(customers.size())) {
			for (CurrentUser customer : customers) {
				results.add(executor.submit(() -> {
					start.await();
					try {
						bookingService.create(customer, new BookingRequest(showtimeId, List.of("A1", "A2")), null);
						return true;
					}
					catch (ConflictException ex) {
						return false;
					}
				}));
			}
			start.countDown();
		}

		long succeeded = 0;
		for (Future<Boolean> result : results) {
			if (result.get()) {
				succeeded++;
			}
		}
		assertThat(succeeded).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM booking_seats WHERE active", Integer.class))
			.isEqualTo(2);
	}

	@Test
	void cancellingShowtimeWhileCustomersCancelNeverDeadlocks() throws Exception {
		for (int round = 0; round < 5; round++) {
			long showtimeId = createShowtime(hall(10), movie(120, "G"), inDays(30 + round), "5.00");
			List<CurrentUser> customers = new ArrayList<>();
			List<Long> bookingIds = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				CurrentUser customer = currentUser(customer());
				customers.add(customer);
				String row = ROWS.get(i);
				long id = bookingService
					.create(customer, new BookingRequest(showtimeId, List.of(row + "1", row + "2")), null)
					.id();
				bookingService.pay(customer, id);
				bookingIds.add(id);
			}

			CountDownLatch start = new CountDownLatch(1);
			List<Future<?>> results = new ArrayList<>();
			try (ExecutorService executor = Executors.newFixedThreadPool(customers.size() + 1)) {
				results.add(executor.submit(() -> {
					start.await();
					return showtimeService.cancel(showtimeId);
				}));
				for (int i = 0; i < customers.size(); i++) {
					CurrentUser customer = customers.get(i);
					Long bookingId = bookingIds.get(i);
					results.add(executor.submit(() -> {
						start.await();
						try {
							return bookingService.cancel(customer, bookingId);
						}
						catch (ConflictException alreadyCancelledByShowtime) {
							return null;
						}
					}));
				}
				start.countDown();
			}

			for (Future<?> result : results) {
				result.get(); // rethrows a deadlock or any other unexpected error
			}
			assertThat(jdbcTemplate.queryForObject(
					"SELECT count(*) FROM booking_seats WHERE showtime_id = ? AND active", Integer.class, showtimeId))
				.isZero();
			assertThat(jdbcTemplate.queryForObject(
					"SELECT count(*) FROM bookings WHERE showtime_id = ? AND status <> 'CANCELLED'", Integer.class,
					showtimeId))
				.isZero();
		}
	}

	// --- helpers ---

	private static Instant inDays(int days) {
		return Instant.now().plus(Duration.ofDays(days));
	}

	/**
	 * Rows A, C, D... are STANDARD; row B is VIP. Each row has {@code seatsPerRow} seats.
	 */
	private long hall(int seatsPerRow) throws Exception {
		String rows = String.join(",",
				ROWS.stream()
					.map((row) -> """
							{"row": "%s", "seats": %d, "type": "%s"}""".formatted(row, seatsPerRow,
							row.equals("B") ? "VIP" : "STANDARD"))
					.toList());
		return id(mockMvc
			.perform(post("/api/halls").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Hall %d", "rows": [%s]}
						""".formatted(++userCounter, rows)))
			.andExpect(status().isCreated()));
	}

	private long movie(int minutes, String rating) throws Exception {
		return id(mockMvc
			.perform(post("/api/movies").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "Movie %d", "durationMinutes": %d, "ageRating": "%s"}
						""".formatted(++userCounter, minutes, rating)))
			.andExpect(status().isCreated()));
	}

	private long createShowtime(long hallId, long movieId, Instant startsAt, String price) throws Exception {
		return id(showtimeRequest(post("/api/showtimes"), hallId, movieId, startsAt, price)
			.andExpect(status().isCreated()));
	}

	private ResultActions showtimeRequest(MockHttpServletRequestBuilder request, long hallId, long movieId,
			Instant startsAt, String price) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"movieId": %d, "hallId": %d, "startsAt": "%s", "basePrice": %s}
					""".formatted(movieId, hallId, startsAt, price)));
	}

	private ResultActions book(String token, long showtimeId, String... seats) throws Exception {
		return bookWithKey(token, showtimeId, null, seats);
	}

	private ResultActions bookWithKey(String token, long showtimeId, String idempotencyKey, String... seats)
			throws Exception {
		String labels = String.join(",", List.of(seats).stream().map((seat) -> "\"" + seat + "\"").toList());
		MockHttpServletRequestBuilder request = post("/api/bookings").header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"showtimeId": %d, "seats": [%s]}
					""".formatted(showtimeId, labels));
		if (idempotencyKey != null) {
			request.header(BookingController.IDEMPOTENCY_KEY, idempotencyKey);
		}
		return mockMvc.perform(request);
	}

	private ResultActions pay(String token, long bookingId) throws Exception {
		return mockMvc
			.perform(post("/api/bookings/{id}/pay", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions cancel(String token, long bookingId) throws Exception {
		return mockMvc
			.perform(post("/api/bookings/{id}/cancel", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions checkIn(String code) throws Exception {
		return mockMvc
			.perform(post("/api/tickets/{code}/check-in", code).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)));
	}

	private void availableSeats(long showtimeId, int expected) throws Exception {
		mockMvc.perform(get("/api/showtimes/{id}", showtimeId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableSeats").value(expected));
	}

	private void seatAvailable(long showtimeId, String label, boolean expected) throws Exception {
		mockMvc.perform(get("/api/showtimes/{id}/seats", showtimeId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.seats[?(@.label == '%s')].available".formatted(label)).value(expected));
	}

	private void bookingCancelled(String token, long bookingId) throws Exception {
		mockMvc.perform(get("/api/bookings/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancelledAt").isNotEmpty());
	}

	private String bookingStatusInDb(long bookingId) {
		return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
	}

	private static long bookingId(ResultActions result) throws Exception {
		return id(result);
	}

	private static long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private String customer() throws Exception {
		return customer(ADULT_DOB);
	}

	private String customer(String dateOfBirth) throws Exception {
		String body = mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("user" + (++userCounter) + "@example.com", dateOfBirth)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private CurrentUser currentUser(String token) throws Exception {
		String body = mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new CurrentUser(((Number) JsonPath.read(body, "$.id")).longValue(), JsonPath.read(body, "$.email"),
				Role.CUSTOMER);
	}

	private String login(String email, String password) throws Exception {
		String body = mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}
						""".formatted(email, password)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private static String registerJson(String email, String dateOfBirth) {
		String dob = (dateOfBirth != null) ? ", \"dateOfBirth\": \"%s\"".formatted(dateOfBirth) : "";
		return """
				{"email": "%s", "password": "Password123", "fullName": "Test User"%s}
				""".formatted(email, dob);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

}
