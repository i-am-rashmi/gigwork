package com.gigwork.core.hiring.web.dto;

import jakarta.validation.constraints.NotNull;

/** @param consent true to reveal your own contact details to the Poster, false to withdraw */
public record ConsentRequest(@NotNull Boolean consent) {
}
