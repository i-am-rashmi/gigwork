package com.gigwork.core.hiring.application;

import java.util.List;

/**
 * Contact details revealed to one person for one gig, valid only for the moment they were fetched. Never stored,
 * never logged: {@link #toString()} is masked (NFR-46).
 *
 * @param methods the channels the other person chose: WHATSAPP, CALL, EMAIL
 * @param mobile the verified mobile number, set only if WHATSAPP or CALL is among the methods
 * @param email the verified email, set only if EMAIL is among the methods
 */
public record ContactView(List<String> methods, String mobile, String email) {

	@Override
	public String toString() {
		return "ContactView[methods=" + methods + ", details=masked]";
	}

}
