package com.devtraining.tickets.hall;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Seat categories. A seat's price is the showtime's base price times the multiplier.
 */
public enum SeatType {

	STANDARD("1.00"),

	VIP("1.50"),

	/** A two-person sofa sold as one seat. */
	COUPLE("2.00");

	private final BigDecimal priceMultiplier;

	SeatType(String priceMultiplier) {
		this.priceMultiplier = new BigDecimal(priceMultiplier);
	}

	public BigDecimal priceFor(BigDecimal basePrice) {
		return basePrice.multiply(priceMultiplier).setScale(2, RoundingMode.HALF_UP);
	}

}
