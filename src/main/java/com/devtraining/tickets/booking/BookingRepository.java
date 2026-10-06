package com.devtraining.tickets.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	@EntityGraph(attributePaths = { "showtime.movie", "showtime.hall", "seats.seat" })
	List<Booking> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	@EntityGraph(attributePaths = { "showtime.movie", "showtime.hall", "seats.seat" })
	List<Booking> findAllByOrderByCreatedAtDesc();

	@EntityGraph(attributePaths = { "showtime.movie", "showtime.hall", "seats.seat" })
	Optional<Booking> findWithDetailsById(Long id);

	Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

	/**
	 * Locks the booking row. Callers lock the showtime row first.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.id = :id")
	Optional<Booking> findByIdForUpdate(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.ticketCode = :ticketCode")
	Optional<Booking> findByTicketCodeForUpdate(String ticketCode);

	/**
	 * Reads only the showtime id, without loading or locking the booking, so callers can lock the
	 * showtime row before the booking row.
	 */
	@Query("select b.showtime.id from Booking b where b.id = :id")
	Optional<Long> findShowtimeIdById(Long id);

	@Query("select b.showtime.id from Booking b where b.ticketCode = :ticketCode")
	Optional<Long> findShowtimeIdByTicketCode(String ticketCode);

	@Query("""
			select coalesce(sum(b.quantity), 0) from Booking b
			where b.user.id = :userId and b.showtime.id = :showtimeId and b.status in :statuses""")
	int sumQuantity(Long userId, Long showtimeId, Collection<BookingStatus> statuses);

	@Query("""
			select distinct b.showtime.id from Booking b
			where b.status = com.devtraining.tickets.booking.BookingStatus.HELD and b.holdExpiresAt <= :now""")
	List<Long> findShowtimeIdsWithExpiredHolds(Instant now);

	@Modifying
	@Query("""
			update Booking b set b.status = com.devtraining.tickets.booking.BookingStatus.EXPIRED
			where b.showtime.id = :showtimeId and b.status = com.devtraining.tickets.booking.BookingStatus.HELD
			and b.holdExpiresAt <= :now""")
	int expireHolds(Long showtimeId, Instant now);

	@Modifying
	@Query("""
			update Booking b set b.status = com.devtraining.tickets.booking.BookingStatus.CANCELLED, b.cancelledAt = :now
			where b.showtime.id = :showtimeId and b.status in (com.devtraining.tickets.booking.BookingStatus.HELD,
			com.devtraining.tickets.booking.BookingStatus.CONFIRMED)""")
	int cancelAllForShowtime(Long showtimeId, Instant now);

}
