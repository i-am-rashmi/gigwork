package com.gigwork.core.identity.web.dto;

import jakarta.validation.constraints.NotNull;

/** @param firstName 1-50 characters: letters, spaces, apostrophes, hyphens and dots; no contact details */
public record NameRequest(@NotNull String firstName) {
}
