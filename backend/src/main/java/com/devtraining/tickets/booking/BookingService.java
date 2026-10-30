package com.devtraining.tickets.booking;

import java.time.Clock;
import java.util.List;

import com.devtraining.tickets.auth.CurrentUser;
import com.devtraining.tickets.booking.dto.BookingRequest;
import com.devtraining.tickets.booking.dto.BookingResponse;
import com.devtraining.tickets.common.exception.ForbiddenException;
import com.devtraining.tickets.common.exception.NotFoundException;
import com.devtraining.tickets.event.Event;
import com.devtraining.tickets.event.EventRepository;
import com.devtraining.tickets.user.User;
import com.devtraining.tickets.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

	private final BookingRepository bookingRepository;

	private final EventRepository eventRepository;

	private final UserRepository userRepository;

	private final Clock clock;

	public BookingService(BookingRepository bookingRepository, EventRepository eventRepository,
			UserRepository userRepository, Clock clock) {
		this.bookingRepository = bookingRepository;
		this.eventRepository = eventRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/**
	 * Books tickets. The event row is locked for the whole transaction, so the seat check and
	 * the seat decrement happen atomically even under concurrent requests.
	 */
	@Transactional
	public BookingResponse create(CurrentUser currentUser, BookingRequest request) {
		// Admins run events; a booking under the admin's own account would belong to no customer.
		if (currentUser.isAdmin()) {
			throw new ForbiddenException("Admins can't book tickets. Log in as a customer to book.");
		}
		Event event = eventRepository.findByIdForUpdate(request.eventId())
			.orElseThrow(() -> new NotFoundException("Event", request.eventId()));
		event.reserveSeats(request.quantity(), clock.instant());
		User user = userRepository.getReferenceById(currentUser.id());
		Booking booking = bookingRepository.save(new Booking(user, event, request.quantity()));
		return BookingResponse.from(booking);
	}

	@Transactional(readOnly = true)
	public List<BookingResponse> findAll(CurrentUser currentUser) {
		List<Booking> bookings = currentUser.isAdmin() ? bookingRepository.findAllByOrderByCreatedAtDesc()
				: bookingRepository.findAllByUserIdOrderByCreatedAtDesc(currentUser.id());
		return bookings.stream().map(BookingResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public BookingResponse findById(CurrentUser currentUser, Long id) {
		Booking booking = bookingRepository.findWithEventById(id)
			.orElseThrow(() -> new NotFoundException("Booking", id));
		checkAccess(currentUser, booking);
		return BookingResponse.from(booking);
	}

	/**
	 * Locks the booking's event first, then the booking, the same order as EventService.cancel.
	 */
	@Transactional
	public BookingResponse cancel(CurrentUser currentUser, Long id) {
		Event event = eventRepository.findByBookingIdForUpdate(id)
			.orElseThrow(() -> new NotFoundException("Booking", id));
		Booking booking = bookingRepository.findByIdForUpdate(id)
			.orElseThrow(() -> new NotFoundException("Booking", id));
		checkAccess(currentUser, booking);
		event.requireNotStarted(clock.instant());
		booking.cancel(clock.instant());
		event.releaseSeats(booking.getQuantity());
		return BookingResponse.from(booking);
	}

	private static void checkAccess(CurrentUser currentUser, Booking booking) {
		if (!currentUser.isAdmin() && !booking.isOwnedBy(currentUser.id())) {
			throw new ForbiddenException("You can only access your own bookings");
		}
	}

}
