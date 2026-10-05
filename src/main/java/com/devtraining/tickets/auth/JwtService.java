package com.devtraining.tickets.auth;

import java.time.Instant;

import com.devtraining.tickets.config.JwtProperties;
import com.devtraining.tickets.user.User;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	public static final String EMAIL_CLAIM = "email";

	public static final String ROLE_CLAIM = "role";

	private final JwtEncoder jwtEncoder;

	private final JwtProperties properties;

	public JwtService(JwtEncoder jwtEncoder, JwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	public IssuedToken issueToken(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(properties.expiration());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.subject(String.valueOf(user.getId()))
			.claim(EMAIL_CLAIM, user.getEmail())
			.claim(ROLE_CLAIM, user.getRole().name())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	public record IssuedToken(String value, Instant expiresAt) {
	}

}
