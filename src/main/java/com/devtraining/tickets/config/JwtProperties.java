package com.devtraining.tickets.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		@NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
		@NotNull Duration expiration,
		@NotBlank String issuer) {
}
