package com.devtraining.tickets.event.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 5000) String description,
		@NotBlank @Size(max = 200) String venue,
		@NotNull Instant startsAt,
		@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
		@NotNull @Min(1) Integer totalSeats) {
}
