package com.gigwork.core.identity.domain;

import java.text.Normalizer;
import java.util.regex.Pattern;

import com.gigwork.core.identity.domain.InvalidFirstNameException.Reason;
import com.gigwork.core.platform.ContactDetails;

/**
 * A user's first name: 1-50 characters after trimming, made of Unicode letters (with their combining marks, as
 * Indic scripts need) plus spaces, apostrophes, hyphens and dots, starting with a letter, and without contact
 * details (FR-6a). Runs of spaces collapse to one; the text is normalised to NFC.
 */
public record FirstName(String value) {

	public static final int MAX_LENGTH = 50;

	private static final Pattern ALLOWED = Pattern.compile("\\p{L}[\\p{L}\\p{M} '’.\\-]*");

	public static FirstName parse(String input) {
		if (input == null) {
			throw new InvalidFirstNameException(Reason.FORMAT);
		}
		String text = Normalizer.normalize(input, Normalizer.Form.NFC).strip().replaceAll(" {2,}", " ");
		if (text.isEmpty() || text.codePointCount(0, text.length()) > MAX_LENGTH || !ALLOWED.matcher(text).matches()) {
			throw new InvalidFirstNameException(Reason.FORMAT);
		}
		if (ContactDetails.appearsIn(text)) {
			throw new InvalidFirstNameException(Reason.CONTACT_DETAILS);
		}
		return new FirstName(text);
	}

}
