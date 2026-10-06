package com.devtraining.tickets.showtime.dto;

import java.math.BigDecimal;
import java.util.List;

import com.devtraining.tickets.hall.SeatType;

/**
 * Every seat in the hall for one showtime, with its price and whether it can still be booked.
 */
public record SeatMapResponse(ShowtimeResponse showtime, List<SeatStatus> seats) {

	public record SeatStatus(String label, String row, int number, SeatType type, BigDecimal price,
			boolean available) {
	}

}
