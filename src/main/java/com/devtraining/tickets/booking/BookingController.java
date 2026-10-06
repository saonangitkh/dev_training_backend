package com.devtraining.tickets.booking;

import java.net.URI;
import java.util.List;

import com.devtraining.tickets.auth.CurrentUser;
import com.devtraining.tickets.booking.dto.BookingRequest;
import com.devtraining.tickets.booking.dto.BookingResponse;
import com.devtraining.tickets.common.exception.BadRequestException;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

	static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final BookingService bookingService;

	public BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	/**
	 * Holds the seats. Pay within the hold time with {@code POST /api/bookings/{id}/pay}. Send an
	 * {@code Idempotency-Key} header so a retried request returns the same booking.
	 */
	@PostMapping
	public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
			@Valid @RequestBody BookingRequest request) {
		if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
			throw new BadRequestException(IDEMPOTENCY_KEY + " must be 1-100 characters");
		}
		BookingResponse created = bookingService.create(CurrentUser.from(jwt), request, idempotencyKey);
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

	/**
	 * Simulated payment: confirms the held seats and issues the ticket code.
	 */
	@PostMapping("/{id}/pay")
	public BookingResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return bookingService.pay(CurrentUser.from(jwt), id);
	}

	@PostMapping("/{id}/cancel")
	public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return bookingService.cancel(CurrentUser.from(jwt), id);
	}

}
