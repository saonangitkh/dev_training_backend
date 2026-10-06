package com.devtraining.tickets.showtime.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.devtraining.tickets.movie.AgeRating;
import com.devtraining.tickets.showtime.Showtime;
import com.devtraining.tickets.showtime.ShowtimeStatus;

public record ShowtimeResponse(Long id, Long movieId, String movieTitle, AgeRating ageRating, Long hallId,
		String hallName, Instant startsAt, Instant endsAt, BigDecimal basePrice, ShowtimeStatus status,
		long totalSeats, long availableSeats) {

	/**
	 * {@code availableSeats} is what can still be booked: 0 once the showtime is cancelled or has
	 * started.
	 */
	public static ShowtimeResponse from(Showtime showtime, long totalSeats, long takenSeats, Instant now) {
		boolean open = !showtime.isCancelled() && !showtime.hasStartedAt(now);
		long available = open ? totalSeats - takenSeats : 0;
		return new ShowtimeResponse(showtime.getId(), showtime.getMovie().getId(), showtime.getMovie().getTitle(),
				showtime.getMovie().getAgeRating(), showtime.getHall().getId(), showtime.getHall().getName(),
				showtime.getStartsAt(), showtime.getEndsAt(), showtime.getBasePrice(), showtime.getStatus(),
				totalSeats, available);
	}

}
