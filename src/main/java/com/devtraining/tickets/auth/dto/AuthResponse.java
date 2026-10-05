package com.devtraining.tickets.auth.dto;

import java.time.Instant;

import com.devtraining.tickets.user.UserResponse;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

	public static AuthResponse bearer(String accessToken, Instant expiresAt, UserResponse user) {
		return new AuthResponse(accessToken, "Bearer", expiresAt, user);
	}

}
