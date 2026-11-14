package com.gigwork.core.profile.domain;

import com.gigwork.core.platform.ContactDetails;

/** Rules for the free text of a profile (FR-6a). */
public final class ProfileText {

	static final String CONTACT_MESSAGE = "Contact details can't be in your profile. Posters reach you through GigWork.";

	private ProfileText() {
	}

	/** Throws if the text holds a phone number, an email address or a contact link. Null is fine. */
	public static void requireNoContactDetails(String text) {
		if (ContactDetails.appearsIn(text)) {
			throw new ContactDetailsInProfileException(CONTACT_MESSAGE);
		}
	}

}
