package com.gigwork.core.identity.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Which channels you are willing to share. All three must be given; all false means you share none.
 *
 * @param whatsapp share your verified mobile number for WhatsApp
 * @param call share your verified mobile number for phone calls
 * @param email not available until email verification exists
 */
public record ContactMethodsRequest(@NotNull Boolean whatsapp, @NotNull Boolean call, @NotNull Boolean email) {
}
