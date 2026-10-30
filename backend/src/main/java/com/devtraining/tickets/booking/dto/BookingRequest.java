package com.devtraining.tickets.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Deliberately has no price field: any price sent by the client is ignored and the total is
 * computed on the server from the event price.
 */
public record BookingRequest(
		@NotNull Long eventId,
		@NotNull @Min(value = BookingRequest.MIN_TICKETS, message = "must be between 1 and 4")
		@Max(value = BookingRequest.MAX_TICKETS, message = "must be between 1 and 4") Integer quantity) {

	public static final int MIN_TICKETS = 1;

	public static final int MAX_TICKETS = 4;

}
