package com.devtraining.tickets.booking;

import java.net.URI;
import java.util.List;

import com.devtraining.tickets.auth.CurrentUser;
import com.devtraining.tickets.booking.dto.BookingRequest;
import com.devtraining.tickets.booking.dto.BookingResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

	private final BookingService bookingService;

	public BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@PostMapping
	public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody BookingRequest request) {
		BookingResponse created = bookingService.create(CurrentUser.from(jwt), request);
		return ResponseEntity.created(URI.create("/api/bookings/" + created.id())).body(created);
	}

	/**
	 * Customers see their own bookings; admins see all bookings.
	 */
	@GetMapping
	public List<BookingResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
		return bookingService.findAll(CurrentUser.from(jwt));
	}

	@GetMapping("/{id}")
	public BookingResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return bookingService.findById(CurrentUser.from(jwt), id);
	}

	@PostMapping("/{id}/cancel")
	public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return bookingService.cancel(CurrentUser.from(jwt), id);
	}

}
