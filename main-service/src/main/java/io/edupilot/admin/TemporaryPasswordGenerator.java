package io.edupilot.admin;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class TemporaryPasswordGenerator {

	private static final int PASSWORD_LENGTH = 16;
	private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final String LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
	private static final String DIGITS = "23456789";
	private static final String SPECIALS = "!@#$%^&*()_+-=";
	private static final String ALL_CHARACTERS = UPPERCASE
		+ LOWERCASE
		+ DIGITS
		+ SPECIALS;

	private final SecureRandom secureRandom;

	public TemporaryPasswordGenerator() {
		this(new SecureRandom());
	}

	TemporaryPasswordGenerator(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
	}

	public String generate() {
		char[] password = new char[PASSWORD_LENGTH];
		password[0] = randomCharacter(UPPERCASE);
		password[1] = randomCharacter(LOWERCASE);
		password[2] = randomCharacter(DIGITS);
		password[3] = randomCharacter(SPECIALS);
		for (int index = 4; index < password.length; index++) {
			password[index] = randomCharacter(ALL_CHARACTERS);
		}
		shuffle(password);
		return new String(password);
	}

	private char randomCharacter(String candidates) {
		return candidates.charAt(secureRandom.nextInt(candidates.length()));
	}

	private void shuffle(char[] value) {
		for (int index = value.length - 1; index > 0; index--) {
			int other = secureRandom.nextInt(index + 1);
			char current = value[index];
			value[index] = value[other];
			value[other] = current;
		}
	}
}
