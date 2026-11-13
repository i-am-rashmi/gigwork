package com.gigwork.core.identity.domain;

import java.util.regex.Pattern;

/**
 * An Indian mobile number in its canonical form {@code +91XXXXXXXXXX}. Everything that is hashed, compared or
 * stored goes through this type, so the same number always produces the same hash (NFR-44).
 *
 * <p>Accepted input: ten digits starting with 6 to 9, optionally prefixed with {@code +91}, {@code 91} or
 * {@code 0}, with spaces and hyphens ignored. {@link #toString()} is masked so an accidental log line cannot
 * leak the number (NFR-46); use {@link #value()} deliberately.
 */
public record MobileNumber(String value) {

	private static final Pattern NATIONAL = Pattern.compile("[6-9][0-9]{9}");
	private static final Pattern CANONICAL = Pattern.compile("\\+91[6-9][0-9]{9}");
	private static final int MAX_INPUT_LENGTH = 32;

	public MobileNumber {
		if (value == null || !CANONICAL.matcher(value).matches()) {
			throw new InvalidMobileNumberException();
		}
	}

	/** Normalises user input to the canonical form, or throws {@link InvalidMobileNumberException}. */
	public static MobileNumber parse(String input) {
		if (input == null || input.length() > MAX_INPUT_LENGTH) {
			throw new InvalidMobileNumberException();
		}
		String digits = input.replaceAll("[ \\-]", "");
		String national;
		if (digits.startsWith("+91")) {
			national = digits.substring(3);
		}
		else if (digits.startsWith("91") && digits.length() == 12) {
			national = digits.substring(2);
		}
		else if (digits.startsWith("0")) {
			national = digits.substring(1);
		}
		else {
			national = digits;
		}
		if (!NATIONAL.matcher(national).matches()) {
			throw new InvalidMobileNumberException();
		}
		return new MobileNumber("+91" + national);
	}

	/** For example {@code +91******3210}: country code and last four digits only. */
	public String masked() {
		return "+91******" + value.substring(value.length() - 4);
	}

	@Override
	public String toString() {
		return masked();
	}

}
