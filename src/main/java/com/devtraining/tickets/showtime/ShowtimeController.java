package com.devtraining.tickets.showtime;

import java.net.URI;
import java.util.List;

import com.devtraining.tickets.showtime.dto.SeatMapResponse;
import com.devtraining.tickets.showtime.dto.ShowtimeRequest;
import com.devtraining.tickets.showtime.dto.ShowtimeResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading showtimes and seat maps is public. Creating, updating and cancelling require ADMIN (see
 * SecurityConfig).
 */
@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {

	private final ShowtimeService showtimeService;

	public ShowtimeController(ShowtimeService showtimeService) {
		this.showtimeService = showtimeService;
	}

	@GetMapping
	public List<ShowtimeResponse> findUpcoming(@RequestParam(required = false) Long movieId) {
		return showtimeService.findUpcoming(movieId);
	}

	@GetMapping("/{id}")
	public ShowtimeResponse findById(@PathVariable Long id) {
		return showtimeService.findById(id);
	}

	@GetMapping("/{id}/seats")
	public SeatMapResponse seatMap(@PathVariable Long id) {
		return showtimeService.seatMap(id);
	}

	@PostMapping
	public ResponseEntity<ShowtimeResponse> create(@Valid @RequestBody ShowtimeRequest request) {
		ShowtimeResponse created = showtimeService.create(request);
		return ResponseEntity.created(URI.create("/api/showtimes/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public ShowtimeResponse update(@PathVariable Long id, @Valid @RequestBody ShowtimeRequest request) {
		return showtimeService.update(id, request);
	}

	@PostMapping("/{id}/cancel")
	public ShowtimeResponse cancel(@PathVariable Long id) {
		return showtimeService.cancel(id);
	}

}
