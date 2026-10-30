package com.devtraining.tickets.event.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.devtraining.tickets.event.Event;
import com.devtraining.tickets.event.EventStatus;

public record EventResponse(Long id, String title, String description, String venue, Instant startsAt,
		BigDecimal price, int totalSeats, int availableSeats, EventStatus status) {

	public static EventResponse from(Event event) {
		return new EventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getVenue(),
				event.getStartsAt(), event.getPrice(), event.getTotalSeats(), event.getAvailableSeats(),
				event.getStatus());
	}

}
