package com.devtraining.tickets.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.devtraining.tickets.booking.Booking;
import com.devtraining.tickets.booking.BookingSeat;
import com.devtraining.tickets.booking.BookingStatus;
import com.devtraining.tickets.hall.SeatType;

public record BookingResponse(Long id, Long showtimeId, String movieTitle, String hallName, Instant startsAt,
		Long userId, List<SeatLine> seats, int quantity, BigDecimal totalPrice, BookingStatus status,
		Instant holdExpiresAt, String ticketCode, Instant createdAt, Instant confirmedAt, Instant cancelledAt,
		Instant checkedInAt) {

	public static BookingResponse from(Booking booking, Instant now) {
		List<SeatLine> seats = booking.getSeats()
			.stream()
			.sorted(Comparator.comparing((BookingSeat seat) -> seat.getSeat().getRowLabel())
				.thenComparing((seat) -> seat.getSeat().getSeatNumber()))
			.map((seat) -> new SeatLine(seat.getSeat().getLabel(), seat.getSeat().getSeatType(), seat.getPrice()))
			.toList();
		var showtime = booking.getShowtime();
		return new BookingResponse(booking.getId(), showtime.getId(), showtime.getMovie().getTitle(),
				showtime.getHall().getName(), showtime.getStartsAt(), booking.getUser().getId(), seats,
				booking.getQuantity(), booking.getTotalPrice(), booking.statusAt(now), booking.getHoldExpiresAt(),
				booking.getTicketCode(), booking.getCreatedAt(), booking.getConfirmedAt(), booking.getCancelledAt(),
				booking.getCheckedInAt());
	}

	public record SeatLine(String label, SeatType type, BigDecimal price) {
	}

}
