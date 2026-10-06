package com.devtraining.tickets.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

	/**
	 * A seat is taken while it belongs to a paid booking or an unexpired hold. Holds past their
	 * expiry count as free even before the sweeper has released them.
	 */
	String TAKEN = """
			bs.active = true and (bs.booking.status = com.devtraining.tickets.booking.BookingStatus.CONFIRMED
			or (bs.booking.status = com.devtraining.tickets.booking.BookingStatus.HELD and bs.booking.holdExpiresAt > :now))""";

	@Query("select bs.seat.id from BookingSeat bs where bs.showtime.id = :showtimeId and " + TAKEN)
	Set<Long> findTakenSeatIds(Long showtimeId, Instant now);

	@Query("select bs.showtime.id, count(bs) from BookingSeat bs where bs.showtime.id in :showtimeIds and " + TAKEN
			+ " group by bs.showtime.id")
	List<Object[]> countTakenByShowtimeIds(Collection<Long> showtimeIds, Instant now);

	@Modifying
	@Query("""
			update BookingSeat bs set bs.active = false
			where bs.active = true and bs.booking.id in (select b.id from Booking b where b.showtime.id = :showtimeId
			and b.status = com.devtraining.tickets.booking.BookingStatus.HELD and b.holdExpiresAt <= :now)""")
	int releaseExpiredHolds(Long showtimeId, Instant now);

	@Modifying
	@Query("update BookingSeat bs set bs.active = false where bs.showtime.id = :showtimeId and bs.active = true")
	int releaseAllForShowtime(Long showtimeId);

}
