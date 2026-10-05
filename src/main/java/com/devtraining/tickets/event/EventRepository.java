package com.devtraining.tickets.event;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long> {

	List<Event> findAllByOrderByStartsAtAsc();

	/**
	 * Loads the event with {@code SELECT ... FOR UPDATE}, so concurrent bookings for the same
	 * event run one after another and can never oversell seats.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Event e where e.id = :id")
	Optional<Event> findByIdForUpdate(Long id);

}
