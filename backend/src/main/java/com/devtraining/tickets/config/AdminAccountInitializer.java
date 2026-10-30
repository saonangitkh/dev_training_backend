package com.devtraining.tickets.config;

import java.util.Locale;

import com.devtraining.tickets.user.Role;
import com.devtraining.tickets.user.User;
import com.devtraining.tickets.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the configured admin account on startup so events can be managed.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final AdminProperties adminProperties;

	public AdminAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AdminProperties adminProperties) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminProperties = adminProperties;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		String email = adminProperties.email().trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmailIgnoreCase(email)) {
			return;
		}
		userRepository.save(new User(email, passwordEncoder.encode(adminProperties.password()),
				adminProperties.fullName(), Role.ADMIN));
		log.info("Created admin account {}", email);
	}

}
