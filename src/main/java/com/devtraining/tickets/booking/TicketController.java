package com.devtraining.tickets.booking;

import com.devtraining.tickets.booking.dto.BookingResponse;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Used by cinema staff at the door. Requires ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

	private final BookingService bookingService;

	public TicketController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@PostMapping("/{code}/check-in")
	public BookingResponse checkIn(@PathVariable String code) {
		return bookingService.checkIn(code);
	}

}
