package com.devtraining.tickets.booking;

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

	public BookingService(BookingRepository bookingRepository, EventRepository eventRepository,
			UserRepository userRepository) {
		this.bookingRepository = bookingRepository;
		this.eventRepository = eventRepository;
		this.userRepository = userRepository;
	}

	/**
	 * Books tickets. The event row is locked for the whole transaction, so the seat check and
	 * the seat decrement happen atomically even under concurrent requests.
	 */
	@Transactional
	public BookingResponse create(CurrentUser currentUser, BookingRequest request) {
		Event event = eventRepository.findByIdForUpdate(request.eventId())
			.orElseThrow(() -> new NotFoundException("Event", request.eventId()));
		event.reserveSeats(request.quantity());
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

	@Transactional
	public BookingResponse cancel(CurrentUser currentUser, Long id) {
		Booking booking = bookingRepository.findByIdForUpdate(id)
			.orElseThrow(() -> new NotFoundException("Booking", id));
		checkAccess(currentUser, booking);
		booking.cancel();
		Event event = eventRepository.findByIdForUpdate(booking.getEvent().getId())
			.orElseThrow(() -> new NotFoundException("Event", booking.getEvent().getId()));
		event.releaseSeats(booking.getQuantity());
		return BookingResponse.from(booking);
	}

	private static void checkAccess(CurrentUser currentUser, Booking booking) {
		if (!currentUser.isAdmin() && !booking.isOwnedBy(currentUser.id())) {
			throw new ForbiddenException("You can only access your own bookings");
		}
	}

}
