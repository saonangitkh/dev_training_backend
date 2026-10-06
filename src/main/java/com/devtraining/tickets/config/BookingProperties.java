package com.devtraining.tickets.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Booking limits that are policy rather than hard rules, so they can be tuned per environment.
 */
@Validated
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
		@Min(1) int maxTicketsPerCustomer,
		@NotNull Duration cancelCutoff,
		@NotNull Duration holdDuration,
		boolean preventSingleSeatGaps) {
}
