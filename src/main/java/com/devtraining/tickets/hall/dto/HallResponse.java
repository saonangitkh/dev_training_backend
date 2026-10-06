package com.devtraining.tickets.hall.dto;

import java.util.List;

import com.devtraining.tickets.hall.Hall;
import com.devtraining.tickets.hall.Seat;
import com.devtraining.tickets.hall.SeatType;

public record HallResponse(Long id, String name, int totalSeats, List<SeatResponse> seats) {

	public static HallResponse from(Hall hall) {
		List<SeatResponse> seats = hall.getSeats().stream().map(SeatResponse::from).toList();
		return new HallResponse(hall.getId(), hall.getName(), seats.size(), seats);
	}

	public record SeatResponse(Long id, String label, String row, int number, SeatType type) {

		static SeatResponse from(Seat seat) {
			return new SeatResponse(seat.getId(), seat.getLabel(), seat.getRowLabel(), seat.getSeatNumber(),
					seat.getSeatType());
		}

	}

}
