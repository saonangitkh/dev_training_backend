package com.devtraining.tickets.event;

import java.util.List;

import com.devtraining.tickets.common.exception.NotFoundException;
import com.devtraining.tickets.event.dto.EventRequest;
import com.devtraining.tickets.event.dto.EventResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

	private final EventRepository eventRepository;

	public EventService(EventRepository eventRepository) {
		this.eventRepository = eventRepository;
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
				request.totalSeats());
		return EventResponse.from(event);
	}

	@Transactional
	public EventResponse cancel(Long id) {
		Event event = getEventForUpdate(id);
		event.cancel();
		return EventResponse.from(event);
	}

	private Event getEvent(Long id) {
		return eventRepository.findById(id).orElseThrow(() -> new NotFoundException("Event", id));
	}

	private Event getEventForUpdate(Long id) {
		return eventRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Event", id));
	}

}
