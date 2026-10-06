package com.devtraining.tickets.movie.dto;

import com.devtraining.tickets.movie.AgeRating;
import com.devtraining.tickets.movie.Movie;

public record MovieResponse(Long id, String title, String description, int durationMinutes, AgeRating ageRating) {

	public static MovieResponse from(Movie movie) {
		return new MovieResponse(movie.getId(), movie.getTitle(), movie.getDescription(), movie.getDurationMinutes(),
				movie.getAgeRating());
	}

}
