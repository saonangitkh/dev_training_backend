package com.devtraining.tickets.showtime;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

	/**
	 * Loads the showtime with {@code SELECT ... FOR UPDATE}. Every write that touches the
	 * showtime's seats or bookings takes this lock first, so they run one after another.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Showtime s where s.id = :id")
	Optional<Showtime> findByIdForUpdate(Long id);

	@EntityGraph(attributePaths = { "movie", "hall" })
	Optional<Showtime> findWithMovieAndHallById(Long id);

	/**
	 * Showtimes that haven't ended yet, optionally for one movie.
	 */
	@EntityGraph(attributePaths = { "movie", "hall" })
	@Query("""
			select s from Showtime s
			where s.endsAt > :now and (:movieId is null or s.movie.id = :movieId)
			order by s.startsAt""")
	List<Showtime> findUpcoming(Instant now, Long movieId);

	@Query("""
			select count(s) > 0 from Showtime s
			where s.hall.id = :hallId and s.status = com.devtraining.tickets.showtime.ShowtimeStatus.SCHEDULED
			and s.startsAt < :endsAt and s.endsAt > :startsAt and s.id <> :excludeId""")
	boolean existsOverlap(Long hallId, Instant startsAt, Instant endsAt, Long excludeId);

}
