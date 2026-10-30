package com.devtraining.tickets.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.devtraining.tickets.booking.Booking;
import com.devtraining.tickets.booking.BookingStatus;

/**
 * The customer's name and email are included so an admin listing all bookings can tell whose each one is.
 */
public record BookingResponse(Long id, Long eventId, String eventTitle, Instant eventStartsAt, Long userId,
		String userEmail, String userFullName, int quantity, BigDecimal unitPrice, BigDecimal totalPrice,
		BookingStatus status, Instant createdAt, Instant cancelledAt) {

	public static BookingResponse from(Booking booking) {
		return new BookingResponse(booking.getId(), booking.getEvent().getId(), booking.getEvent().getTitle(),
				booking.getEvent().getStartsAt(), booking.getUser().getId(), booking.getUser().getEmail(),
				booking.getUser().getFullName(), booking.getQuantity(), booking.getUnitPrice(),
				booking.getTotalPrice(), booking.getStatus(), booking.getCreatedAt(), booking.getCancelledAt());
	}

}
