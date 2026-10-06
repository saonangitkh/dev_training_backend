package com.devtraining.tickets.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.devtraining.tickets.auth.CurrentUser;
import com.devtraining.tickets.booking.dto.BookingRequest;
import com.devtraining.tickets.booking.dto.BookingResponse;
import com.devtraining.tickets.common.exception.BadRequestException;
import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.common.exception.ForbiddenException;
import com.devtraining.tickets.common.exception.NotFoundException;
import com.devtraining.tickets.config.BookingProperties;
import com.devtraining.tickets.config.CinemaProperties;
import com.devtraining.tickets.hall.Seat;
import com.devtraining.tickets.hall.SeatRepository;
import com.devtraining.tickets.movie.AgeRating;
import com.devtraining.tickets.showtime.Showtime;
import com.devtraining.tickets.showtime.ShowtimeRepository;
import com.devtraining.tickets.user.User;
import com.devtraining.tickets.user.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lock order: every write locks the showtime row first, then booking rows. Keeping one order
 * everywhere (here and in {@code ShowtimeService.cancel}) means concurrent writes can't deadlock.
 */
@Service
public class BookingService {

	private static final List<BookingStatus> ACTIVE = List.of(BookingStatus.HELD, BookingStatus.CONFIRMED);

	private final BookingRepository bookingRepository;

	private final BookingSeatRepository bookingSeatRepository;

	private final ShowtimeRepository showtimeRepository;

	private final SeatRepository seatRepository;

	private final UserRepository userRepository;

	private final TicketCodeGenerator ticketCodes;

	private final BookingProperties properties;

	private final CinemaProperties cinema;

	private final Clock clock;

	public BookingService(BookingRepository bookingRepository, BookingSeatRepository bookingSeatRepository,
			ShowtimeRepository showtimeRepository, SeatRepository seatRepository, UserRepository userRepository,
			TicketCodeGenerator ticketCodes, BookingProperties properties, CinemaProperties cinema, Clock clock) {
		this.bookingRepository = bookingRepository;
		this.bookingSeatRepository = bookingSeatRepository;
		this.showtimeRepository = showtimeRepository;
		this.seatRepository = seatRepository;
		this.userRepository = userRepository;
		this.ticketCodes = ticketCodes;
		this.properties = properties;
		this.cinema = cinema;
		this.clock = clock;
	}

	/**
	 * Holds the chosen seats for {@code holdDuration}. The showtime row is locked for the whole
	 * transaction, so all checks and the insert happen atomically even under concurrent requests.
	 * The unique index on active booking seats is the last line of defence against double-selling.
	 * @param idempotencyKey optional; repeating a request with the same key returns the first booking
	 */
	@Transactional
	public BookingResponse create(CurrentUser currentUser, BookingRequest request, String idempotencyKey) {
		Instant now = clock.instant();
		Showtime showtime = lockShowtime(request.showtimeId());

		if (idempotencyKey != null) {
			var previous = bookingRepository.findByUserIdAndIdempotencyKey(currentUser.id(), idempotencyKey);
			if (previous.isPresent()) {
				return replay(previous.get(), request, now);
			}
		}

		expireHolds(showtime.getId(), now);
		showtime.checkOpenForSale(now);
		User user = userRepository.findById(currentUser.id())
			.orElseThrow(() -> new NotFoundException("User", currentUser.id()));
		checkAge(user, showtime);

		List<Seat> hallSeats = seatRepository.findAllByHallId(showtime.getHall().getId());
		List<Seat> chosen = resolveSeats(hallSeats, request.seats());
		checkCustomerLimit(currentUser, showtime, chosen.size());
		Set<Long> taken = bookingSeatRepository.findTakenSeatIds(showtime.getId(), now);
		checkSeatsFree(chosen, taken);
		if (properties.preventSingleSeatGaps()) {
			checkNoSingleSeatGaps(hallSeats, taken, chosen);
		}

		Booking booking = new Booking(user, showtime, chosen, now.plus(properties.holdDuration()), idempotencyKey);
		try {
			bookingRepository.saveAndFlush(booking);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("Seats or idempotency key already in use, please try again");
		}
		return BookingResponse.from(booking, now);
	}

	/**
	 * Confirms a held booking (payment is simulated) and issues the ticket code.
	 */
	@Transactional
	public BookingResponse pay(CurrentUser currentUser, Long id) {
		Instant now = clock.instant();
		Booking booking = lockBooking(id);
		checkAccess(currentUser, booking);
		if (booking.getStatus() == BookingStatus.HELD) {
			booking.getShowtime().checkOpenForSale(now);
		}
		booking.pay(now, ticketCodes.next());
		return BookingResponse.from(booking, now);
	}

	@Transactional
	public BookingResponse cancel(CurrentUser currentUser, Long id) {
		Instant now = clock.instant();
		Booking booking = lockBooking(id);
		checkAccess(currentUser, booking);
		if (!currentUser.isAdmin() && booking.getStatus() == BookingStatus.CONFIRMED) {
			checkCancelDeadline(booking.getShowtime(), now);
		}
		booking.cancel(now);
		return BookingResponse.from(booking, now);
	}

	/**
	 * Staff scan the ticket at the door. Each ticket can be used once, from
	 * {@code checkInOpensBefore} before the start until the showtime ends.
	 */
	@Transactional
	public BookingResponse checkIn(String ticketCode) {
		Instant now = clock.instant();
		String code = ticketCode.trim().toUpperCase(Locale.ROOT);
		Long showtimeId = bookingRepository.findShowtimeIdByTicketCode(code)
			.orElseThrow(() -> new NotFoundException("Ticket", code));
		Showtime showtime = lockShowtime(showtimeId);
		Booking booking = bookingRepository.findByTicketCodeForUpdate(code)
			.orElseThrow(() -> new NotFoundException("Ticket", code));
		Instant opensAt = showtime.getStartsAt().minus(cinema.checkInOpensBefore());
		if (now.isBefore(opensAt)) {
			throw new ConflictException("Check-in for ticket %s opens at %s".formatted(code, opensAt));
		}
		if (showtime.hasEndedAt(now)) {
			throw new ConflictException("Showtime %d has ended".formatted(showtime.getId()));
		}
		booking.checkIn(now);
		return BookingResponse.from(booking, now);
	}

	@Transactional(readOnly = true)
	public List<BookingResponse> findAll(CurrentUser currentUser) {
		Instant now = clock.instant();
		List<Booking> bookings = currentUser.isAdmin() ? bookingRepository.findAllByOrderByCreatedAtDesc()
				: bookingRepository.findAllByUserIdOrderByCreatedAtDesc(currentUser.id());
		return bookings.stream().map((booking) -> BookingResponse.from(booking, now)).toList();
	}

	@Transactional(readOnly = true)
	public BookingResponse findById(CurrentUser currentUser, Long id) {
		Booking booking = bookingRepository.findWithDetailsById(id)
			.orElseThrow(() -> new NotFoundException("Booking", id));
		checkAccess(currentUser, booking);
		return BookingResponse.from(booking, clock.instant());
	}

	/**
	 * Releases the seats of unpaid holds that have expired. Called by {@link HoldExpiryJob}.
	 */
	@Transactional
	public int expireHolds(Long showtimeId) {
		lockShowtime(showtimeId);
		return expireHolds(showtimeId, clock.instant());
	}

	/**
	 * Cancels every held or paid booking for a showtime. Holds that already ran out are marked
	 * EXPIRED, not CANCELLED. The caller holds the showtime lock.
	 */
	@Transactional
	public int cancelAllForShowtime(Long showtimeId, Instant now) {
		expireHolds(showtimeId, now);
		bookingSeatRepository.releaseAllForShowtime(showtimeId);
		return bookingRepository.cancelAllForShowtime(showtimeId, now);
	}

	/**
	 * Whether any seat is held or sold. The caller holds the showtime lock.
	 */
	@Transactional(readOnly = true)
	public boolean hasActiveSeats(Long showtimeId, Instant now) {
		return !bookingSeatRepository.findTakenSeatIds(showtimeId, now).isEmpty();
	}

	private int expireHolds(Long showtimeId, Instant now) {
		bookingSeatRepository.releaseExpiredHolds(showtimeId, now);
		return bookingRepository.expireHolds(showtimeId, now);
	}

	private BookingResponse replay(Booking previous, BookingRequest request, Instant now) {
		Set<String> previousSeats = new TreeSet<>();
		previous.getSeats().forEach((seat) -> previousSeats.add(seat.getSeat().getLabel()));
		Set<String> requestedSeats = new TreeSet<>();
		request.seats().forEach((label) -> requestedSeats.add(label.trim().toUpperCase(Locale.ROOT)));
		if (!previous.getShowtime().getId().equals(request.showtimeId()) || !previousSeats.equals(requestedSeats)) {
			throw new ConflictException("Idempotency key was already used for a different booking request");
		}
		return BookingResponse.from(previous, now);
	}

	private Showtime lockShowtime(Long showtimeId) {
		return showtimeRepository.findByIdForUpdate(showtimeId)
			.orElseThrow(() -> new NotFoundException("Showtime", showtimeId));
	}

	/**
	 * Locks the showtime, then the booking: the same order as everywhere else.
	 */
	private Booking lockBooking(Long id) {
		Long showtimeId = bookingRepository.findShowtimeIdById(id)
			.orElseThrow(() -> new NotFoundException("Booking", id));
		lockShowtime(showtimeId);
		return bookingRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Booking", id));
	}

	private void checkAge(User user, Showtime showtime) {
		AgeRating rating = showtime.getMovie().getAgeRating();
		if (!rating.isRestricted()) {
			return;
		}
		if (user.getDateOfBirth() == null) {
			throw new ForbiddenException("'%s' is rated %s: add your date of birth to book it"
				.formatted(showtime.getMovie().getTitle(), rating));
		}
		LocalDate showDate = showtime.getStartsAt().atZone(cinema.timeZone()).toLocalDate();
		int age = Period.between(user.getDateOfBirth(), showDate).getYears();
		if (age < rating.minAge()) {
			throw new ForbiddenException("'%s' is rated %s: you must be at least %d on the day of the showtime"
				.formatted(showtime.getMovie().getTitle(), rating, rating.minAge()));
		}
	}

	private static List<Seat> resolveSeats(List<Seat> hallSeats, List<String> labels) {
		Map<String, Seat> byLabel = new HashMap<>();
		hallSeats.forEach((seat) -> byLabel.put(seat.getLabel(), seat));
		Set<String> requested = new LinkedHashSet<>();
		for (String label : labels) {
			String normalized = label.trim().toUpperCase(Locale.ROOT);
			if (!requested.add(normalized)) {
				throw new BadRequestException("Seat %s is listed twice".formatted(normalized));
			}
		}
		List<String> unknown = requested.stream().filter((label) -> !byLabel.containsKey(label)).toList();
		if (!unknown.isEmpty()) {
			throw new BadRequestException("Unknown seat(s) in this hall: " + String.join(", ", unknown));
		}
		return requested.stream().map(byLabel::get).toList();
	}

	/**
	 * Caps the seats one customer holds or owns for one showtime, so nobody can buy up a show
	 * through many small bookings. Safe without extra locking because the showtime row is locked.
	 */
	private void checkCustomerLimit(CurrentUser currentUser, Showtime showtime, int quantity) {
		int max = properties.maxTicketsPerCustomer();
		int alreadyBooked = bookingRepository.sumQuantity(currentUser.id(), showtime.getId(), ACTIVE);
		if (alreadyBooked + quantity > max) {
			throw new ConflictException("You can book at most %d seats for showtime %d, you already have %d"
				.formatted(max, showtime.getId(), alreadyBooked));
		}
	}

	private static void checkSeatsFree(List<Seat> chosen, Set<Long> taken) {
		List<String> unavailable = chosen.stream()
			.filter((seat) -> taken.contains(seat.getId()))
			.map(Seat::getLabel)
			.toList();
		if (!unavailable.isEmpty()) {
			throw new ConflictException("Seat(s) already taken: " + String.join(", ", unavailable));
		}
	}

	/**
	 * Rejects a choice that leaves exactly one empty seat between two taken seats in a row, since
	 * nobody books a lone seat squeezed between strangers. Row ends don't count as taken.
	 */
	private static void checkNoSingleSeatGaps(List<Seat> hallSeats, Set<Long> taken, List<Seat> chosen) {
		Set<Long> occupied = new HashSet<>(taken);
		chosen.forEach((seat) -> occupied.add(seat.getId()));
		Map<String, Map<Integer, Seat>> rows = new HashMap<>();
		hallSeats.forEach((seat) -> rows.computeIfAbsent(seat.getRowLabel(), (row) -> new HashMap<>())
			.put(seat.getSeatNumber(), seat));

		Set<String> gaps = new TreeSet<>();
		for (Seat seat : chosen) {
			Map<Integer, Seat> row = rows.get(seat.getRowLabel());
			for (int step : new int[] { -1, 1 }) {
				Seat neighbour = row.get(seat.getSeatNumber() + step);
				Seat beyond = row.get(seat.getSeatNumber() + 2 * step);
				if (neighbour != null && beyond != null && !occupied.contains(neighbour.getId())
						&& occupied.contains(beyond.getId())) {
					gaps.add(neighbour.getLabel());
				}
			}
		}
		if (!gaps.isEmpty()) {
			throw new ConflictException("This choice would leave a single empty seat at " + String.join(", ", gaps)
					+ ". Choose seats next to each other, or leave at least two seats free");
		}
	}

	private void checkCancelDeadline(Showtime showtime, Instant now) {
		Instant deadline = showtime.getStartsAt().minus(properties.cancelCutoff());
		if (now.isAfter(deadline)) {
			throw new ConflictException("Paid bookings can only be cancelled until %s before the showtime starts"
				.formatted(describe(properties.cancelCutoff())));
		}
	}

	private static String describe(Duration duration) {
		long minutes = duration.toMinutes();
		return (minutes % 60 == 0) ? (minutes / 60) + " hour(s)" : minutes + " minute(s)";
	}

	private static void checkAccess(CurrentUser currentUser, Booking booking) {
		if (!currentUser.isAdmin() && !booking.isOwnedBy(currentUser.id())) {
			throw new ForbiddenException("You can only access your own bookings");
		}
	}

}
