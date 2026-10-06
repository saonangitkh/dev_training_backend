package com.devtraining.tickets.movie;

import java.util.List;

import com.devtraining.tickets.common.exception.NotFoundException;
import com.devtraining.tickets.movie.dto.MovieRequest;
import com.devtraining.tickets.movie.dto.MovieResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MovieService {

	private final MovieRepository movieRepository;

	public MovieService(MovieRepository movieRepository) {
		this.movieRepository = movieRepository;
	}

	@Transactional(readOnly = true)
	public List<MovieResponse> findAll() {
		return movieRepository.findAllByOrderByTitleAsc().stream().map(MovieResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public MovieResponse findById(Long id) {
		return MovieResponse.from(getMovie(id));
	}

	@Transactional
	public MovieResponse create(MovieRequest request) {
		Movie movie = new Movie(request.title(), request.description(), request.durationMinutes(),
				request.ageRating());
		return MovieResponse.from(movieRepository.save(movie));
	}

	@Transactional
	public MovieResponse update(Long id, MovieRequest request) {
		Movie movie = getMovie(id);
		movie.update(request.title(), request.description(), request.durationMinutes(), request.ageRating());
		return MovieResponse.from(movie);
	}

	public Movie getMovie(Long id) {
		return movieRepository.findById(id).orElseThrow(() -> new NotFoundException("Movie", id));
	}

}
