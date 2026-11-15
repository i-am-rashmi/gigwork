package com.gigwork.core.hiring.web.dto;

import jakarta.validation.constraints.NotNull;

/** @param share whether to share your own contact details with this shortlisted Talent */
public record PosterShareRequest(@NotNull Boolean share) {
}
