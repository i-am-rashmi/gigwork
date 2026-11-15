package com.gigwork.core.identity.web.dto;

/** The channels you share. Never the number itself. */
public record ContactMethodsResponse(boolean whatsapp, boolean call, boolean email) {
}
