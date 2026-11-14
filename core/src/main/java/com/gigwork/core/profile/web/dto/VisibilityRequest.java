package com.gigwork.core.profile.web.dto;

import jakarta.validation.constraints.NotNull;

/** @param hidden true to hide the profile from everyone else, false to show it again (if it is complete) */
public record VisibilityRequest(@NotNull Boolean hidden) {
}
