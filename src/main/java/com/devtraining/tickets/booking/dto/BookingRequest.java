package com.devtraining.tickets.booking.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Seats by label, for example {@code ["F7", "F8"]}. Deliberately has no price field: any price
 * sent by the client is ignored and the total is computed on the server.
 */
public record BookingRequest(
		@NotNull Long showtimeId,
		@NotNull @Size(min = BookingRequest.MIN_SEATS, max = BookingRequest.MAX_SEATS,
				message = "must be between 1 and 4 seats") List<@NotBlank String> seats) {

	public static final int MIN_SEATS = 1;

	public static final int MAX_SEATS = 4;

}
