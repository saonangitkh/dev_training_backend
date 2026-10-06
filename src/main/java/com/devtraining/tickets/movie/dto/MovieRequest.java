package com.devtraining.tickets.movie.dto;

import com.devtraining.tickets.movie.AgeRating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MovieRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 5000) String description,
		@NotNull @Min(1) @Max(600) Integer durationMinutes,
		@NotNull AgeRating ageRating) {
}
