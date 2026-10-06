package com.devtraining.tickets.hall;

import java.net.URI;
import java.util.List;

import com.devtraining.tickets.hall.dto.HallRequest;
import com.devtraining.tickets.hall.dto.HallResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading halls is public. Creating requires ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/halls")
public class HallController {

	private final HallService hallService;

	public HallController(HallService hallService) {
		this.hallService = hallService;
	}

	@GetMapping
	public List<HallResponse> findAll() {
		return hallService.findAll();
	}

	@GetMapping("/{id}")
	public HallResponse findById(@PathVariable Long id) {
		return hallService.findById(id);
	}

	@PostMapping
	public ResponseEntity<HallResponse> create(@Valid @RequestBody HallRequest request) {
		HallResponse created = hallService.create(request);
		return ResponseEntity.created(URI.create("/api/halls/" + created.id())).body(created);
	}

}
