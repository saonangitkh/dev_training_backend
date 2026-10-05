package com.devtraining.tickets.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.devtraining.tickets.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "events")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Column(nullable = false, length = 200)
	private String venue;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Column(name = "total_seats", nullable = false)
	private int totalSeats;

	@Column(name = "available_seats", nullable = false)
	private int availableSeats;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EventStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Event() {
		// for JPA
	}

	public Event(String title, String description, String venue, Instant startsAt, BigDecimal price,
			int totalSeats) {
		this.title = title;
		this.description = description;
		this.venue = venue;
		this.startsAt = startsAt;
		this.price = price;
		this.totalSeats = totalSeats;
		this.availableSeats = totalSeats;
		this.status = EventStatus.SCHEDULED;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public boolean isCancelled() {
		return status == EventStatus.CANCELLED;
	}

	/**
	 * Takes seats for a booking. Callers must hold a write lock on this row.
	 */
	public void reserveSeats(int quantity) {
		if (isCancelled()) {
			throw new ConflictException("Event %d is cancelled".formatted(id));
		}
		if (quantity > availableSeats) {
			throw new ConflictException(
					"Only %d seat(s) left for event %d, requested %d".formatted(availableSeats, id, quantity));
		}
		availableSeats -= quantity;
	}

	/**
	 * Gives seats back when a booking is cancelled. Callers must hold a write lock on this row.
	 */
	public void releaseSeats(int quantity) {
		availableSeats = Math.min(totalSeats, availableSeats + quantity);
	}

	public void update(String title, String description, String venue, Instant startsAt, BigDecimal price,
			int totalSeats) {
		int seatsAlreadyBooked = this.totalSeats - this.availableSeats;
		if (totalSeats < seatsAlreadyBooked) {
			throw new ConflictException(
					"Cannot reduce total seats to %d, %d seat(s) are already booked".formatted(totalSeats,
							seatsAlreadyBooked));
		}
		this.title = title;
		this.description = description;
		this.venue = venue;
		this.startsAt = startsAt;
		this.price = price;
		this.totalSeats = totalSeats;
		this.availableSeats = totalSeats - seatsAlreadyBooked;
	}

	public void cancel() {
		if (isCancelled()) {
			throw new ConflictException("Event %d is already cancelled".formatted(id));
		}
		this.status = EventStatus.CANCELLED;
	}

	public BigDecimal totalPriceFor(int quantity) {
		return price.multiply(BigDecimal.valueOf(quantity));
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getVenue() {
		return venue;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public int getTotalSeats() {
		return totalSeats;
	}

	public int getAvailableSeats() {
		return availableSeats;
	}

	public EventStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
