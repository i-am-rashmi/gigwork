package com.gigwork.core.reference;

/** A language Talent and gigs may name, keyed by its ISO 639 code. */
public record LanguageInfo(String code, String name, int displayOrder) {
}
