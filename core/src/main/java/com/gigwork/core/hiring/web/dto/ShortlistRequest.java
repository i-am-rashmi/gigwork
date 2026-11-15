package com.gigwork.core.hiring.web.dto;

import jakarta.validation.constraints.NotNull;

/** @param shareMyContact whether to share your own contact details with this Talent; needs a contact method */
public record ShortlistRequest(@NotNull Boolean shareMyContact) {
}
