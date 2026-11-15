package com.gigwork.core.hiring.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gigwork.core.hiring.application.ContactView;

/**
 * The other side's contact details, for this gig only (FR-26). Only here: no list ever carries them. Never
 * cached, never logged: the masked {@link #toString()} keeps an accidental log line clean (NFR-46).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContactResponse(List<String> methods, String mobile, String email) {

	public static ContactResponse of(ContactView view) {
		return new ContactResponse(view.methods(), view.mobile(), view.email());
	}

	@Override
	public String toString() {
		return "ContactResponse[methods=" + methods + ", details=masked]";
	}

}
