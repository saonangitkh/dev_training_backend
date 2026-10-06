package com.devtraining.tickets.hall;

import java.util.List;

import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.common.exception.NotFoundException;
import com.devtraining.tickets.hall.dto.HallRequest;
import com.devtraining.tickets.hall.dto.HallResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HallService {

	private final HallRepository hallRepository;

	public HallService(HallRepository hallRepository) {
		this.hallRepository = hallRepository;
	}

	@Transactional(readOnly = true)
	public List<HallResponse> findAll() {
		return hallRepository.findAllByOrderByNameAsc().stream().map(HallResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public HallResponse findById(Long id) {
		return HallResponse.from(
				hallRepository.findWithSeatsById(id).orElseThrow(() -> new NotFoundException("Hall", id)));
	}

	@Transactional
	public HallResponse create(HallRequest request) {
		String name = request.name().trim();
		if (hallRepository.existsByNameIgnoreCase(name)) {
			throw new ConflictException("Hall '%s' already exists".formatted(name));
		}
		Hall hall = new Hall(name);
		request.rows().forEach((row) -> hall.addRow(row.row(), row.seats(), row.type()));
		try {
			return HallResponse.from(hallRepository.saveAndFlush(hall));
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("Hall '%s' already exists".formatted(name));
		}
	}

	public Hall getHall(Long id) {
		return hallRepository.findById(id).orElseThrow(() -> new NotFoundException("Hall", id));
	}

}
