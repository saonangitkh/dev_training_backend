package com.devtraining.tickets.config;

import java.time.Duration;
import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cinema-wide settings.
 *
 * @param timeZone local time zone of the cinema, used for age checks
 * @param cleaningTime gap after each movie before the hall can be used again
 * @param checkInOpensBefore how early before the start tickets can be checked in
 */
@Validated
@ConfigurationProperties(prefix = "app.cinema")
public record CinemaProperties(
		@NotNull ZoneId timeZone,
		@NotNull Duration cleaningTime,
		@NotNull Duration checkInOpensBefore) {
}
