package com.devtraining.tickets.event;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.devtraining.tickets.booking.Booking;
import com.devtraining.tickets.booking.BookingRepository;
import com.devtraining.tickets.booking.BookingStatus;
import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.event.dto.EventRequest;
import com.devtraining.tickets.event.dto.EventResponse;
import com.devtraining.tickets.user.Role;
import com.devtraining.tickets.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for the admin's event rules. No database or Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

	private static final Instant NOW = Instant.parse("2029-06-01T12:00:00Z");

	private static final Instant LATER = Instant.parse("2030-01-01T19:00:00Z");

	@Mock
	private EventRepository eventRepository;

	@Mock
	private BookingRepository bookingRepository;

	private EventService eventService;

	@BeforeEach
	void setUp() {
		eventService = new EventService(eventRepository, bookingRepository, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void cancelEventCancelsItsBookingsAndGivesSeatsBack() {
		Event event = event(10L, 5, LATER);
		Booking first = booking(100L, event, 2);
		Booking second = booking(101L, event, 1);
		given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));
		given(bookingRepository.findAllByEventIdAndStatusForUpdate(10L, BookingStatus.CONFIRMED))
			.willReturn(List.of(first, second));

		EventResponse response = eventService.cancel(10L);

		assertThat(response.status()).isEqualTo(EventStatus.CANCELLED);
		assertThat(response.availableSeats()).isEqualTo(5);
		assertThat(first.getStatus()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(second.getStatus()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(first.getCancelledAt()).isEqualTo(NOW);
	}

	@Test
	void cancelStartedEventIsConflict() {
		Event event = event(10L, 5, NOW.minusSeconds(60));
		given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));

		assertThatThrownBy(() -> eventService.cancel(10L)).isInstanceOf(ConflictException.class)
			.hasMessageContaining("already started");
		assertThat(event.getStatus()).isEqualTo(EventStatus.SCHEDULED);
	}

	@Test
	void cancelledEventCannotBeEdited() {
		Event event = event(10L, 5, LATER);
		event.cancel(NOW);
		given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));

		assertThatThrownBy(() -> eventService.update(10L, request("Renamed", LATER)))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("cancelled");
		assertThat(event.getTitle()).isEqualTo("Concert");
	}

	@Test
	void startedEventCannotBeEdited() {
		Event event = event(10L, 5, NOW.minusSeconds(60));
		given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));

		assertThatThrownBy(() -> eventService.update(10L, request("Renamed", LATER)))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("already started");
		assertThat(event.getTitle()).isEqualTo("Concert");
	}

	@Test
	void updateKeepsSeatsAlreadyBooked() {
		Event event = event(10L, 5, LATER);
		event.reserveSeats(3, NOW);
		given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));

		EventResponse response = eventService.update(10L, request("Renamed", LATER));

		assertThat(response.title()).isEqualTo("Renamed");
		assertThat(response.totalSeats()).isEqualTo(8);
		assertThat(response.availableSeats()).isEqualTo(5);
	}

	private static EventRequest request(String title, Instant startsAt) {
		return new EventRequest(title, null, "Main Hall", startsAt, new BigDecimal("10.00"), 8);
	}

	private static Event event(Long id, int seats, Instant startsAt) {
		Event event = new Event("Concert", "Live music", "Main Hall", startsAt, new BigDecimal("10.00"), seats);
		ReflectionTestUtils.setField(event, "id", id);
		return event;
	}

	private static Booking booking(Long id, Event event, int quantity) {
		User user = new User("alice@example.com", "{noop}secret", "Alice", Role.CUSTOMER);
		ReflectionTestUtils.setField(user, "id", 1L);
		event.reserveSeats(quantity, NOW);
		Booking booking = new Booking(user, event, quantity);
		ReflectionTestUtils.setField(booking, "id", id);
		return booking;
	}

}
