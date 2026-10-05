package com.devtraining.tickets.booking;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.devtraining.tickets.auth.CurrentUser;
import com.devtraining.tickets.booking.dto.BookingRequest;
import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.user.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests against a real PostgreSQL database (see application-test.yml).
 * Run with {@code ./mvnw verify}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingApiIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BookingService bookingService;

	private String adminToken;

	@BeforeEach
	void setUp() throws Exception {
		jdbcTemplate.execute("DELETE FROM bookings");
		jdbcTemplate.execute("DELETE FROM events");
		jdbcTemplate.execute("DELETE FROM users WHERE role <> 'ADMIN'");
		adminToken = login("admin@tickets.local", "Admin@12345");
	}

	@Test
	void fullBookingFlowFollowsBusinessRules() throws Exception {
		long eventId = createEvent(5, "20.00");
		String alice = register("alice@example.com");
		String bob = register("bob@example.com");

		// 03: quantity must be 1..4
		book(alice, eventId, 5).andExpect(status().isBadRequest());
		book(alice, eventId, 0).andExpect(status().isBadRequest());

		// 04: client price is ignored, total = price x quantity
		String body = mockMvc
			.perform(post("/api/bookings").header(HttpHeaders.AUTHORIZATION, bearer(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"eventId": %d, "quantity": 3, "totalPrice": 0.01, "unitPrice": 0.01}
						""".formatted(eventId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.totalPrice").value(60.00))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long bookingId = ((Number) JsonPath.read(body, "$.id")).longValue();

		// 02: booking reduces seats
		availableSeats(eventId, 2);

		// 01: no overbooking
		book(bob, eventId, 3).andExpect(status().isConflict());

		// 06: only your own bookings
		mockMvc.perform(get("/api/bookings/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/bookings").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));

		// 02: cancelling gives seats back
		mockMvc
			.perform(post("/api/bookings/{id}/cancel", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		availableSeats(eventId, 5);
		mockMvc
			.perform(post("/api/bookings/{id}/cancel", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
			.andExpect(status().isConflict());

		// 05: no booking a cancelled event
		mockMvc.perform(post("/api/events/{id}/cancel", eventId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
			.andExpect(status().isOk());
		book(bob, eventId, 1).andExpect(status().isConflict());
	}

	@Test
	void securityRules() throws Exception {
		String alice = register("alice@example.com");

		mockMvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/events")).andExpect(status().isOk());
		mockMvc
			.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, bearer(alice))
				.contentType(MediaType.APPLICATION_JSON)
				.content(eventJson(10, "5.00")))
			.andExpect(status().isForbidden());
		mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "alice@example.com", "password": "wrong-password"}
						"""))
			.andExpect(status().isUnauthorized());
		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("alice@example.com")))
			.andExpect(status().isConflict());
	}

	@Test
	void concurrentBookingsNeverOversell() throws Exception {
		long eventId = createEvent(10, "15.00");
		register("alice@example.com");
		Long aliceId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'alice@example.com'",
				Long.class);
		CurrentUser alice = new CurrentUser(aliceId, "alice@example.com", Role.CUSTOMER);

		int attempts = 20;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
			for (int i = 0; i < attempts; i++) {
				results.add(executor.submit(() -> {
					start.await();
					try {
						bookingService.create(alice, new BookingRequest(eventId, 1));
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
		assertThat(succeeded).isEqualTo(10);
		assertThat(jdbcTemplate.queryForObject("SELECT available_seats FROM events WHERE id = ?", Integer.class,
				eventId))
			.isZero();
	}

	private ResultActions book(String token, long eventId, int quantity)
			throws Exception {
		return mockMvc.perform(post("/api/bookings").header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"eventId": %d, "quantity": %d}
					""".formatted(eventId, quantity)));
	}

	private void availableSeats(long eventId, int expected) throws Exception {
		mockMvc.perform(get("/api/events/{id}", eventId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableSeats").value(expected));
	}

	private long createEvent(int seats, String price) throws Exception {
		String body = mockMvc
			.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(eventJson(seats, price)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private String register(String email) throws Exception {
		String body = mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson(email)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
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

	private static String eventJson(int seats, String price) {
		return """
				{"title": "Concert", "venue": "Main Hall", "startsAt": "2030-01-01T19:00:00Z",
				 "price": %s, "totalSeats": %d}
				""".formatted(price, seats);
	}

	private static String registerJson(String email) {
		return """
				{"email": "%s", "password": "Password123", "fullName": "Test User"}
				""".formatted(email);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

}
