package com.devtraining.tickets.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.devtraining.tickets.booking.Booking;
import com.devtraining.tickets.booking.BookingStatus;

public record BookingResponse(Long id, Long eventId, String eventTitle, Long userId, int quantity,
		BigDecimal unitPrice, BigDecimal totalPrice, BookingStatus status, Instant createdAt, Instant cancelledAt) {

	public static BookingResponse from(Booking booking) {
		return new BookingResponse(booking.getId(), booking.getEvent().getId(), booking.getEvent().getTitle(),
				booking.getUser().getId(), booking.getQuantity(), booking.getUnitPrice(), booking.getTotalPrice(),
				booking.getStatus(), booking.getCreatedAt(), booking.getCancelledAt());
	}

}
