package com.gigwork.core.identity.web.dto;

import java.util.UUID;

/**
 * @param challengeId      send this back with the code
 * @param expiresInSeconds how long the code is valid
 */
public record OtpRequestResponse(UUID challengeId, long expiresInSeconds) {
}
