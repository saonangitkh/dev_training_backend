package com.devtraining.tickets.showtime;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.devtraining.tickets.booking.BookingSeatRepository;
import com.devtraining.tickets.booking.BookingService;
import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.common.exception.NotFoundException;
import com.devtraining.tickets.config.CinemaProperties;
import com.devtraining.tickets.hall.Hall;
import com.devtraining.tickets.hall.HallService;
import com.devtraining.tickets.hall.Seat;
import com.devtraining.tickets.hall.SeatRepository;
import com.devtraining.tickets.movie.Movie;
import com.devtraining.tickets.movie.MovieService;
import com.devtraining.tickets.showtime.dto.SeatMapResponse;
import com.devtraining.tickets.showtime.dto.ShowtimeRequest;
import com.devtraining.tickets.showtime.dto.ShowtimeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShowtimeService {

	private static final Logger log = LoggerFactory.getLogger(ShowtimeService.class);

	private final ShowtimeRepository showtimeRepository;

	private final SeatRepository seatRepository;

	private final BookingSeatRepository bookingSeatRepository;

	private final BookingService bookingService;

	private final MovieService movieService;

	private final HallService hallService;

	private final CinemaProperties cinema;

	private final Clock clock;

	public ShowtimeService(ShowtimeRepository showtimeRepository, SeatRepository seatRepository,
			BookingSeatRepository bookingSeatRepository, BookingService bookingService, MovieService movieService,
			HallService hallService, CinemaProperties cinema, Clock clock) {
		this.showtimeRepository = showtimeRepository;
		this.seatRepository = seatRepository;
		this.bookingSeatRepository = bookingSeatRepository;
		this.bookingService = bookingService;
		this.movieService = movieService;
		this.hallService = hallService;
		this.cinema = cinema;
		this.clock = clock;
	}

	/**
	 * Showtimes that haven't ended yet, soonest first.
	 */
	@Transactional(readOnly = true)
	public List<ShowtimeResponse> findUpcoming(Long movieId) {
		Instant now = clock.instant();
		List<Showtime> showtimes = showtimeRepository.findUpcoming(now, movieId);
		if (showtimes.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> totals = toMap(
				seatRepository.countByHallIds(showtimes.stream().map((s) -> s.getHall().getId()).distinct().toList()));
		Map<Long, Long> taken = toMap(
				bookingSeatRepository.countTakenByShowtimeIds(showtimes.stream().map(Showtime::getId).toList(), now));
		return showtimes.stream()
			.map((s) -> ShowtimeResponse.from(s, totals.getOrDefault(s.getHall().getId(), 0L),
					taken.getOrDefault(s.getId(), 0L), now))
			.toList();
	}

	@Transactional(readOnly = true)
	public ShowtimeResponse findById(Long id) {
		Showtime showtime = showtimeRepository.findWithMovieAndHallById(id)
			.orElseThrow(() -> new NotFoundException("Showtime", id));
		return toResponse(showtime, clock.instant());
	}

	/**
	 * Every seat with its price and whether it can be booked right now.
	 */
	@Transactional(readOnly = true)
	public SeatMapResponse seatMap(Long id) {
		Instant now = clock.instant();
		Showtime showtime = showtimeRepository.findWithMovieAndHallById(id)
			.orElseThrow(() -> new NotFoundException("Showtime", id));
		List<Seat> seats = seatRepository.findAllByHallId(showtime.getHall().getId());
		Set<Long> taken = bookingSeatRepository.findTakenSeatIds(id, now);
		boolean open = !showtime.isCancelled() && !showtime.hasStartedAt(now);
		List<SeatMapResponse.SeatStatus> statuses = seats.stream()
			.map((seat) -> new SeatMapResponse.SeatStatus(seat.getLabel(), seat.getRowLabel(), seat.getSeatNumber(),
					seat.getSeatType(), showtime.priceFor(seat.getSeatType()),
					open && !taken.contains(seat.getId())))
			.toList();
		return new SeatMapResponse(ShowtimeResponse.from(showtime, seats.size(), taken.size(), now), statuses);
	}

	@Transactional
	public ShowtimeResponse create(ShowtimeRequest request) {
		Movie movie = movieService.getMovie(request.movieId());
		Hall hall = hallService.getHall(request.hallId());
		Instant endsAt = endsAt(movie, request.startsAt());
		checkHallFree(hall, request.startsAt(), endsAt, -1L);
		Showtime showtime = new Showtime(movie, hall, request.startsAt(), endsAt, request.basePrice());
		saveAndFlush(showtime, hall);
		return toResponse(showtime, clock.instant());
	}

	@Transactional
	public ShowtimeResponse update(Long id, ShowtimeRequest request) {
		Instant now = clock.instant();
		Showtime showtime = lockShowtime(id);
		Movie movie = movieService.getMovie(request.movieId());
		Hall hall = hallService.getHall(request.hallId());
		boolean sameSlot = showtime.getMovie().getId().equals(movie.getId())
				&& showtime.getStartsAt().equals(request.startsAt());
		// a price-only change keeps the end time it was scheduled with, even if the movie was edited since
		Instant endsAt = sameSlot ? showtime.getEndsAt() : endsAt(movie, request.startsAt());
		showtime.update(movie, hall, request.startsAt(), endsAt, request.basePrice(),
				bookingService.hasActiveSeats(id, now));
		checkHallFree(hall, request.startsAt(), endsAt, id);
		saveAndFlush(showtime, hall);
		return toResponse(showtime, now);
	}

	/**
	 * Cancels the showtime and every held or paid booking for it. The showtime row is locked
	 * first, the same lock order as {@code BookingService}, so this can't deadlock with customers.
	 */
	@Transactional
	public ShowtimeResponse cancel(Long id) {
		Instant now = clock.instant();
		Showtime showtime = lockShowtime(id);
		showtime.cancel();
		int cancelled = bookingService.cancelAllForShowtime(id, now);
		log.info("Cancelled showtime {} and {} booking(s)", id, cancelled);
		return toResponse(showtime, now);
	}

	private Instant endsAt(Movie movie, Instant startsAt) {
		return startsAt.plus(movie.getDuration()).plus(cinema.cleaningTime());
	}

	private void checkHallFree(Hall hall, Instant startsAt, Instant endsAt, Long excludeId) {
		if (showtimeRepository.existsOverlap(hall.getId(), startsAt, endsAt, excludeId)) {
			throw hallBusy(hall);
		}
	}

	/**
	 * The exclusion constraint catches two admins scheduling the same hall at the same moment.
	 */
	private void saveAndFlush(Showtime showtime, Hall hall) {
		try {
			showtimeRepository.saveAndFlush(showtime);
		}
		catch (DataIntegrityViolationException ex) {
			throw hallBusy(hall);
		}
	}

	private static ConflictException hallBusy(Hall hall) {
		return new ConflictException(
				"%s already has a showtime in that time slot (movie length + cleaning time)".formatted(hall.getName()));
	}

	private Showtime lockShowtime(Long id) {
		return showtimeRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Showtime", id));
	}

	private ShowtimeResponse toResponse(Showtime showtime, Instant now) {
		long total = seatRepository.findAllByHallId(showtime.getHall().getId()).size();
		long taken = bookingSeatRepository.findTakenSeatIds(showtime.getId(), now).size();
		return ShowtimeResponse.from(showtime, total, taken, now);
	}

	private static Map<Long, Long> toMap(List<Object[]> rows) {
		Map<Long, Long> map = new HashMap<>();
		rows.forEach((row) -> map.put((Long) row[0], (Long) row[1]));
		return map;
	}

}
