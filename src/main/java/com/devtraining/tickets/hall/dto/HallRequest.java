package com.devtraining.tickets.hall.dto;

import java.util.List;

import com.devtraining.tickets.hall.SeatType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A hall and its layout, front row first, for example rows A–H of STANDARD seats and row J of
 * COUPLE seats.
 */
public record HallRequest(
		@NotBlank @Size(max = 100) String name,
		@NotEmpty @Size(max = 26) List<@Valid @NotNull Row> rows) {

	public record Row(
			@NotNull @Pattern(regexp = "[A-Z]{1,3}", message = "must be 1-3 capital letters") String row,
			@NotNull @Min(1) @Max(50) Integer seats,
			@NotNull SeatType type) {
	}

}
