package com.gigwork.core.identity;

import java.util.UUID;

/**
 * The contact details a user has chosen to share, for another module to reveal after its own consent check (FR-26).
 * {@code mobile} is set only if WhatsApp or call is chosen; {@code emailAddress} only if email is (not possible until
 * email verification exists). Never stored by the caller and never logged: {@link #toString()} masks everything
 * (NFR-46).
 */
public record ContactCard(UUID accountId, boolean whatsapp, boolean call, boolean email, String mobile,
		String emailAddress) {

	@Override
	public String toString() {
		return "ContactCard[accountId=" + accountId + ", whatsapp=" + whatsapp + ", call=" + call + ", email=" + email
				+ ", details=masked]";
	}

}
