package com.devtraining.tickets.hall;

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
import jakarta.persistence.Table;

/**
 * One physical seat, identified by row and number, for example "F7".
 */
@Entity
@Table(name = "seats")
public class Seat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "hall_id", nullable = false)
	private Hall hall;

	@Column(name = "row_label", nullable = false, length = 3)
	private String rowLabel;

	@Column(name = "seat_number", nullable = false)
	private int seatNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "seat_type", nullable = false, length = 20)
	private SeatType seatType;

	protected Seat() {
		// for JPA
	}

	Seat(Hall hall, String rowLabel, int seatNumber, SeatType seatType) {
		this.hall = hall;
		this.rowLabel = rowLabel;
		this.seatNumber = seatNumber;
		this.seatType = seatType;
	}

	public String getLabel() {
		return rowLabel + seatNumber;
	}

	public Long getId() {
		return id;
	}

	public Hall getHall() {
		return hall;
	}

	public String getRowLabel() {
		return rowLabel;
	}

	public int getSeatNumber() {
		return seatNumber;
	}

	public SeatType getSeatType() {
		return seatType;
	}

}
