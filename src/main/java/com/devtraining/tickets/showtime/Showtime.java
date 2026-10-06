package com.devtraining.tickets.showtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.hall.Hall;
import com.devtraining.tickets.hall.SeatType;
import com.devtraining.tickets.movie.Movie;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * One screening of a movie in a hall. Seats are not counted here: a seat is taken when an active
 * booking seat exists for it (see {@code booking_seats}).
 */
@Entity
@Table(name = "showtimes")
public class Showtime {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "movie_id", nullable = false)
	private Movie movie;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "hall_id", nullable = false)
	private Hall hall;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Column(name = "base_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal basePrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ShowtimeStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Showtime() {
		// for JPA
	}

	public Showtime(Movie movie, Hall hall, Instant startsAt, Instant endsAt, BigDecimal basePrice) {
		this.movie = movie;
		this.hall = hall;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.basePrice = basePrice;
		this.status = ShowtimeStatus.SCHEDULED;
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
		return status == ShowtimeStatus.CANCELLED;
	}

	public boolean hasStartedAt(Instant now) {
		return !now.isBefore(startsAt);
	}

	public boolean hasEndedAt(Instant now) {
		return !now.isBefore(endsAt);
	}

	/**
	 * Sales are open while the showtime is scheduled and hasn't started.
	 */
	public void checkOpenForSale(Instant now) {
		if (isCancelled()) {
			throw new ConflictException("Showtime %d is cancelled".formatted(id));
		}
		if (hasStartedAt(now)) {
			throw new ConflictException("Showtime %d has already started, sales are closed".formatted(id));
		}
	}

	public BigDecimal priceFor(SeatType seatType) {
		return seatType.priceFor(basePrice);
	}

	/**
	 * Once seats are sold or held, the movie, hall and start time are fixed: customers paid for
	 * them. The price can still change, because bookings keep the price they were sold at.
	 */
	public void update(Movie movie, Hall hall, Instant startsAt, Instant endsAt, BigDecimal basePrice,
			boolean hasSales) {
		if (isCancelled()) {
			throw new ConflictException("Showtime %d is cancelled".formatted(id));
		}
		boolean moved = !Objects.equals(this.movie.getId(), movie.getId())
				|| !Objects.equals(this.hall.getId(), hall.getId()) || !this.startsAt.equals(startsAt);
		if (moved && hasSales) {
			throw new ConflictException(("Showtime %d already has bookings: its movie, hall and start time can't "
					+ "change. Cancel it and create a new showtime instead").formatted(id));
		}
		this.movie = movie;
		this.hall = hall;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.basePrice = basePrice;
	}

	public void cancel() {
		if (isCancelled()) {
			throw new ConflictException("Showtime %d is already cancelled".formatted(id));
		}
		this.status = ShowtimeStatus.CANCELLED;
	}

	public Long getId() {
		return id;
	}

	public Movie getMovie() {
		return movie;
	}

	public Hall getHall() {
		return hall;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public BigDecimal getBasePrice() {
		return basePrice;
	}

	public ShowtimeStatus getStatus() {
		return status;
	}

}
