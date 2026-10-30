package com.devtraining.tickets.auth;

import com.devtraining.tickets.user.Role;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller, read from the verified JWT. Controllers pass this to services so
 * business code never depends on Spring Security types.
 */
public record CurrentUser(Long id, String email, Role role) {

	public static CurrentUser from(Jwt jwt) {
		return new CurrentUser(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(JwtService.EMAIL_CLAIM),
				Role.valueOf(jwt.getClaimAsString(JwtService.ROLE_CLAIM)));
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

}
