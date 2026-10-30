package com.devtraining.tickets.auth;

import java.util.Locale;

import com.devtraining.tickets.auth.dto.AuthResponse;
import com.devtraining.tickets.auth.dto.LoginRequest;
import com.devtraining.tickets.auth.dto.RegisterRequest;
import com.devtraining.tickets.common.exception.ConflictException;
import com.devtraining.tickets.common.exception.NotFoundException;
import com.devtraining.tickets.user.Role;
import com.devtraining.tickets.user.User;
import com.devtraining.tickets.user.UserRepository;
import com.devtraining.tickets.user.UserResponse;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	/**
	 * Self-registration always creates a CUSTOMER. Admins are bootstrapped from configuration.
	 */
	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new ConflictException("Email is already registered");
		}
		User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()),
				request.fullName().trim(), Role.CUSTOMER));
		return toAuthResponse(user);
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
			.filter((candidate) -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
			.orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
		return toAuthResponse(user);
	}

	@Transactional(readOnly = true)
	public UserResponse me(CurrentUser currentUser) {
		return userRepository.findById(currentUser.id())
			.map(UserResponse::from)
			.orElseThrow(() -> new NotFoundException("User", currentUser.id()));
	}

	private AuthResponse toAuthResponse(User user) {
		JwtService.IssuedToken token = jwtService.issueToken(user);
		return AuthResponse.bearer(token.value(), token.expiresAt(), UserResponse.from(user));
	}

	static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
