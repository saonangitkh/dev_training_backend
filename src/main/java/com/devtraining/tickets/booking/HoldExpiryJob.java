package com.devtraining.tickets.booking;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases the seats of unpaid holds. Bookings and seat maps already treat an expired hold as
 * free, and booking a showtime expires its holds first; this job just keeps the rows tidy.
 */
@Component
public class HoldExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

	private final BookingRepository bookingRepository;

	private final BookingService bookingService;

	private final Clock clock;

	public HoldExpiryJob(BookingRepository bookingRepository, BookingService bookingService, Clock clock) {
		this.bookingRepository = bookingRepository;
		this.bookingService = bookingService;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.booking.expiry-sweep-interval:PT1M}")
	public void expireHolds() {
		for (Long showtimeId : bookingRepository.findShowtimeIdsWithExpiredHolds(clock.instant())) {
			int expired = bookingService.expireHolds(showtimeId);
			if (expired > 0) {
				log.info("Expired {} unpaid hold(s) for showtime {}", expired, showtimeId);
			}
		}
	}

}
