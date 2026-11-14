package com.gigwork.core.reference.web.dto;

import com.gigwork.core.reference.LanguageInfo;

/** @param code the ISO 639 code, e.g. {@code kn} */
public record LanguageResponse(String code, String name) {

	public static LanguageResponse of(LanguageInfo l) {
		return new LanguageResponse(l.code(), l.name());
	}

}
