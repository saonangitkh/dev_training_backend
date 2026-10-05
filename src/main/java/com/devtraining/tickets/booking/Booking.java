package com.devtraining.tickets.booking;

import java.math.BigDecimal;
import java.time.Instant;

import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.event.Event;
import com.devtraining.tickets.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "bookings")
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal unitPrice;

	@Column(name = "total_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookingStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	protected Booking() {
		// for JPA
	}

	/**
	 * Prices always come from the event, never from the client.
	 */
	public Booking(User user, Event event, int quantity) {
		this.user = user;
		this.event = event;
		this.quantity = quantity;
		this.unitPrice = event.getPrice();
		this.totalPrice = event.totalPriceFor(quantity);
		this.status = BookingStatus.CONFIRMED;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public boolean isOwnedBy(Long userId) {
		return user.getId().equals(userId);
	}

	public void cancel() {
		if (status == BookingStatus.CANCELLED) {
			throw new ConflictException("Booking %d is already cancelled".formatted(id));
		}
		this.status = BookingStatus.CANCELLED;
		this.cancelledAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public Event getEvent() {
		return event;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getTotalPrice() {
		return totalPrice;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

}
