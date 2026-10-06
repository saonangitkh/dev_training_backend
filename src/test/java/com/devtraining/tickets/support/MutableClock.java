package com.devtraining.tickets.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock tests can move forward, to expire holds or reach a showtime without waiting.
 */
public class MutableClock extends Clock {

	private volatile Instant now = Instant.now();

	public void reset() {
		now = Instant.now();
	}

	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	public void setInstant(Instant instant) {
		now = instant;
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}
