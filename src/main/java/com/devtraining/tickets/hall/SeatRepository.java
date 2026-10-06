package com.devtraining.tickets.hall;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeatRepository extends JpaRepository<Seat, Long> {

	@Query("select s from Seat s where s.hall.id = :hallId order by s.rowLabel, s.seatNumber")
	List<Seat> findAllByHallId(Long hallId);

	@Query("select s.hall.id, count(s) from Seat s where s.hall.id in :hallIds group by s.hall.id")
	List<Object[]> countByHallIds(List<Long> hallIds);

}
