package com.gigwork.core.gigs.web.dto;

import jakarta.validation.constraints.Size;

/** @param reason optional, at most 500 characters; kept with the gig */
public record CancelRequest(@Size(max = 500) String reason) {
}
