package com.devtraining.tickets.movie;

/**
 * Who may watch a movie. {@code minAge} 0 means anyone.
 */
public enum AgeRating {

	G(0),

	PG13(13),

	R18(18);

	private final int minAge;

	AgeRating(int minAge) {
		this.minAge = minAge;
	}

	public int minAge() {
		return minAge;
	}

	public boolean isRestricted() {
		return minAge > 0;
	}

}
