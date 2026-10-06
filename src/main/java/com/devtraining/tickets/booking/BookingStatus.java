package com.devtraining.tickets.booking;

/**
 * HELD → CONFIRMED (paid) → CANCELLED, or HELD → EXPIRED (not paid in time) / CANCELLED.
 */
public enum BookingStatus {

	HELD,

	CONFIRMED,

	CANCELLED,

	EXPIRED

}
