package com.devtraining.tickets.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.hall.Seat;
import com.devtraining.tickets.showtime.Showtime;
import com.devtraining.tickets.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
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
	@JoinColumn(name = "showtime_id", nullable = false)
	private Showtime showtime;

	@OneToMany(mappedBy = "booking", cascade = CascadeType.PERSIST)
	private List<BookingSeat> seats = new ArrayList<>();

	@Column(nullable = false)
	private int quantity;

	@Column(name = "total_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookingStatus status;

	@Column(name = "hold_expires_at", nullable = false)
	private Instant holdExpiresAt;

	@Column(name = "idempotency_key", length = 100)
	private String idempotencyKey;

	@Column(name = "ticket_code", length = 20)
	private String ticketCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "checked_in_at")
	private Instant checkedInAt;

	protected Booking() {
		// for JPA
	}

	/**
	 * Holds seats until {@code holdExpiresAt}. Prices always come from the showtime and seat type,
	 * never from the client.
	 */
	public Booking(User user, Showtime showtime, List<Seat> seats, Instant holdExpiresAt, String idempotencyKey) {
		this.user = user;
		this.showtime = showtime;
		this.quantity = seats.size();
		this.totalPrice = BigDecimal.ZERO;
		for (Seat seat : seats) {
			BigDecimal price = showtime.priceFor(seat.getSeatType());
			this.seats.add(new BookingSeat(this, showtime, seat, price));
			this.totalPrice = this.totalPrice.add(price);
		}
		this.status = BookingStatus.HELD;
		this.holdExpiresAt = holdExpiresAt;
		this.idempotencyKey = idempotencyKey;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public boolean isOwnedBy(Long userId) {
		return user.getId().equals(userId);
	}

	public boolean isHoldExpiredAt(Instant now) {
		return status == BookingStatus.HELD && !now.isBefore(holdExpiresAt);
	}

	/**
	 * The status a client should see: a hold past its expiry is EXPIRED even before the sweeper
	 * has updated the row.
	 */
	public BookingStatus statusAt(Instant now) {
		return isHoldExpiredAt(now) ? BookingStatus.EXPIRED : status;
	}

	public void pay(Instant now, String ticketCode) {
		checkNotExpired(now);
		if (status != BookingStatus.HELD) {
			throw new ConflictException("Booking %d is %s, only HELD bookings can be paid".formatted(id, status));
		}
		this.status = BookingStatus.CONFIRMED;
		this.confirmedAt = now;
		this.ticketCode = ticketCode;
	}

	public void cancel(Instant now) {
		checkNotExpired(now);
		if (status == BookingStatus.CANCELLED || status == BookingStatus.EXPIRED) {
			throw new ConflictException("Booking %d is already %s".formatted(id, status.name().toLowerCase()));
		}
		if (checkedInAt != null) {
			throw new ConflictException("Booking %d is already checked in".formatted(id));
		}
		this.status = BookingStatus.CANCELLED;
		this.cancelledAt = now;
		seats.forEach(BookingSeat::release);
	}

	public void checkIn(Instant now) {
		if (status != BookingStatus.CONFIRMED) {
			throw new ConflictException("Ticket %s is not valid: booking is %s".formatted(ticketCode, status));
		}
		if (checkedInAt != null) {
			throw new ConflictException("Ticket %s was already checked in at %s".formatted(ticketCode, checkedInAt));
		}
		this.checkedInAt = now;
	}

	private void checkNotExpired(Instant now) {
		if (isHoldExpiredAt(now)) {
			throw new ConflictException("Booking %d hold expired at %s, its seats were released".formatted(id,
					holdExpiresAt));
		}
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public Showtime getShowtime() {
		return showtime;
	}

	public List<BookingSeat> getSeats() {
		return Collections.unmodifiableList(seats);
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getTotalPrice() {
		return totalPrice;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public Instant getHoldExpiresAt() {
		return holdExpiresAt;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getTicketCode() {
		return ticketCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public Instant getCheckedInAt() {
		return checkedInAt;
	}

}
