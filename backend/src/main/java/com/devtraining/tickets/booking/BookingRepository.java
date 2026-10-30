package com.devtraining.tickets.booking;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	@EntityGraph(attributePaths = { "event", "user" })
	List<Booking> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	@EntityGraph(attributePaths = { "event", "user" })
	List<Booking> findAllByOrderByCreatedAtDesc();

	@EntityGraph(attributePaths = { "event", "user" })
	Optional<Booking> findWithEventById(Long id);

	/**
	 * Locks the booking row so the same booking cannot be cancelled twice concurrently.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.id = :id")
	Optional<Booking> findByIdForUpdate(Long id);

	/**
	 * Locks the event's bookings with that status, used when the whole event is cancelled.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.event.id = :eventId and b.status = :status")
	List<Booking> findAllByEventIdAndStatusForUpdate(Long eventId, BookingStatus status);

}
