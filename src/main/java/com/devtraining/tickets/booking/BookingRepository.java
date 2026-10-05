package com.devtraining.tickets.booking;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	@EntityGraph(attributePaths = "event")
	List<Booking> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	@EntityGraph(attributePaths = "event")
	List<Booking> findAllByOrderByCreatedAtDesc();

	@EntityGraph(attributePaths = "event")
	Optional<Booking> findWithEventById(Long id);

	/**
	 * Locks the booking row so the same booking cannot be cancelled twice concurrently.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.id = :id")
	Optional<Booking> findByIdForUpdate(Long id);

}
