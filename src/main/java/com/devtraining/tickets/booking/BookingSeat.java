package com.devtraining.tickets.booking;

import java.math.BigDecimal;

import com.devtraining.tickets.hall.Seat;
import com.devtraining.tickets.showtime.Showtime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One seat in a booking, with the price it was sold at. While {@code active}, the unique index
 * {@code ux_booking_seats_active (showtime_id, seat_id) WHERE active} stops anyone else taking it.
 */
@Entity
@Table(name = "booking_seats")
public class BookingSeat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "showtime_id", nullable = false)
	private Showtime showtime;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seat_id", nullable = false)
	private Seat seat;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Column(nullable = false)
	private boolean active;

	protected BookingSeat() {
		// for JPA
	}

	BookingSeat(Booking booking, Showtime showtime, Seat seat, BigDecimal price) {
		this.booking = booking;
		this.showtime = showtime;
		this.seat = seat;
		this.price = price;
		this.active = true;
	}

	void release() {
		this.active = false;
	}

	public Long getId() {
		return id;
	}

	public Seat getSeat() {
		return seat;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public boolean isActive() {
		return active;
	}

}
