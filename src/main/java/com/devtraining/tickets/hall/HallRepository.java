package com.devtraining.tickets.hall;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HallRepository extends JpaRepository<Hall, Long> {

	List<Hall> findAllByOrderByNameAsc();

	@EntityGraph(attributePaths = "seats")
	Optional<Hall> findWithSeatsById(Long id);

	boolean existsByNameIgnoreCase(String name);

}
