package com.devtraining.tickets.user;

import java.time.Instant;
import java.time.LocalDate;

public record UserResponse(Long id, String email, String fullName, Role role, LocalDate dateOfBirth,
		Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
				user.getDateOfBirth(), user.getCreatedAt());
	}

}
