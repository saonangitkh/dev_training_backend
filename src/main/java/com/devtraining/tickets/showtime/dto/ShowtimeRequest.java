package com.devtraining.tickets.showtime.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * {@code basePrice} is the STANDARD seat price; VIP and COUPLE seats cost more (see SeatType).
 */
public record ShowtimeRequest(
		@NotNull Long movieId,
		@NotNull Long hallId,
		@NotNull @Future Instant startsAt,
		@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal basePrice) {
}
