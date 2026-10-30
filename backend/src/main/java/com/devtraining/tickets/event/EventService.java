package com.devtraining.tickets.event;

import java.time.Clock;
import java.util.List;

import com.devtraining.tickets.booking.Booking;
import com.devtraining.tickets.booking.BookingRepository;
import com.devtraining.tickets.booking.BookingStatus;
import com.devtraining.tickets.common.exception.NotFoundException;
import com.devtraining.tickets.event.dto.EventRequest;
import com.devtraining.tickets.event.dto.EventResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

	private final EventRepository eventRepository;

	private final BookingRepository bookingRepository;

	private final Clock clock;

	public EventService(EventRepository eventRepository, BookingRepository bookingRepository, Clock clock) {
		this.eventRepository = eventRepository;
		this.bookingRepository = bookingRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<EventResponse> findAll() {
		return eventRepository.findAllByOrderByStartsAtAsc().stream().map(EventResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public EventResponse findById(Long id) {
		return EventResponse.from(getEvent(id));
	}

	@Transactional
	public EventResponse create(EventRequest request) {
		Event event = new Event(request.title(), request.description(), request.venue(), request.startsAt(),
				request.price(), request.totalSeats());
		return EventResponse.from(eventRepository.save(event));
	}

	@Transactional
	public EventResponse update(Long id, EventRequest request) {
		Event event = getEventForUpdate(id);
		event.update(request.title(), request.description(), request.venue(), request.startsAt(), request.price(),
				request.totalSeats(), clock.instant());
		return EventResponse.from(event);
	}

	@Transactional
	public EventResponse cancel(Long id) {
		Event event = getEventForUpdate(id);
		event.cancel(clock.instant());
		// The event's bookings are cancelled with it and their seats given back. Locks are taken
		// event first, then bookings, the same order as BookingService.cancel, so they can't deadlock.
		for (Booking booking : bookingRepository.findAllByEventIdAndStatusForUpdate(id, BookingStatus.CONFIRMED)) {
			booking.cancel(clock.instant());
			event.releaseSeats(booking.getQuantity());
		}
		return EventResponse.from(event);
	}

	private Event getEvent(Long id) {
		return eventRepository.findById(id).orElseThrow(() -> new NotFoundException("Event", id));
	}

	private Event getEventForUpdate(Long id) {
		return eventRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Event", id));
	}

}
