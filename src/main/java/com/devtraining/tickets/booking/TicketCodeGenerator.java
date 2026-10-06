package com.devtraining.tickets.booking;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Random ticket codes shown at the door, for example {@code K7QW-M2XP}. No 0/O or 1/I, so they're
 * easy to read out loud. 32^8 combinations, and a unique index catches the unlikely collision.
 */
@Component
public class TicketCodeGenerator {

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private final SecureRandom random = new SecureRandom();

	public String next() {
		StringBuilder code = new StringBuilder(9);
		for (int i = 0; i < 8; i++) {
			if (i == 4) {
				code.append('-');
			}
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

}
