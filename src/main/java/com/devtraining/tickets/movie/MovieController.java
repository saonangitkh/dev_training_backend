package com.devtraining.tickets.movie;

import java.net.URI;
import java.util.List;

import com.devtraining.tickets.movie.dto.MovieRequest;
import com.devtraining.tickets.movie.dto.MovieResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading movies is public. Creating and updating require ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/movies")
public class MovieController {

	private final MovieService movieService;

	public MovieController(MovieService movieService) {
		this.movieService = movieService;
	}

	@GetMapping
	public List<MovieResponse> findAll() {
		return movieService.findAll();
	}

	@GetMapping("/{id}")
	public MovieResponse findById(@PathVariable Long id) {
		return movieService.findById(id);
	}

	@PostMapping
	public ResponseEntity<MovieResponse> create(@Valid @RequestBody MovieRequest request) {
		MovieResponse created = movieService.create(request);
		return ResponseEntity.created(URI.create("/api/movies/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public MovieResponse update(@PathVariable Long id, @Valid @RequestBody MovieRequest request) {
		return movieService.update(id, request);
	}

}
