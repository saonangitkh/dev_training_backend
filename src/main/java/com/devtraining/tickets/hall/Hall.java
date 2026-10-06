package com.devtraining.tickets.hall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.devtraining.tickets.common.exception.BadRequestException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A screening room. Its seat layout is fixed once created, so seat ids stay valid for bookings.
 */
@Entity
@Table(name = "halls")
public class Hall {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@OneToMany(mappedBy = "hall", cascade = CascadeType.PERSIST)
	@OrderBy("rowLabel ASC, seatNumber ASC")
	private List<Seat> seats = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Hall() {
		// for JPA
	}

	public Hall(String name) {
		this.name = name;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	/**
	 * Adds a row of seats numbered 1..count.
	 */
	public void addRow(String rowLabel, int count, SeatType seatType) {
		boolean exists = seats.stream().anyMatch((seat) -> seat.getRowLabel().equals(rowLabel));
		if (exists) {
			throw new BadRequestException("Row %s is defined twice".formatted(rowLabel));
		}
		for (int number = 1; number <= count; number++) {
			seats.add(new Seat(this, rowLabel, number, seatType));
		}
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<Seat> getSeats() {
		return Collections.unmodifiableList(seats);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
