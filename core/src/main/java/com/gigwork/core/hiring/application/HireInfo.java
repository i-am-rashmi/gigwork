package com.gigwork.core.hiring.application;

import java.time.Instant;
import java.util.UUID;

import com.gigwork.core.hiring.HireSide;

/**
 * The latest hire of an application as a list shows it, so the side that has to answer knows which hire to confirm or
 * decline. {@code expiresAt} matters while the hire is PENDING.
 */
public record HireInfo(UUID id, String status, HireSide startedBy, Instant expiresAt) {
}
