package com.devtraining.tickets.event;

import java.net.URI;
import java.util.List;

import com.devtraining.tickets.event.dto.EventRequest;
import com.devtraining.tickets.event.dto.EventResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading events is public. Creating, updating and cancelling require ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@GetMapping
	public List<EventResponse> findAll() {
		return eventService.findAll();
	}

	@GetMapping("/{id}")
	public EventResponse findById(@PathVariable Long id) {
		return eventService.findById(id);
	}

	@PostMapping
	public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
		EventResponse created = eventService.create(request);
		return ResponseEntity.created(URI.create("/api/events/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
		return eventService.update(id, request);
	}

	@PostMapping("/{id}/cancel")
	public EventResponse cancel(@PathVariable Long id) {
		return eventService.cancel(id);
	}

}
