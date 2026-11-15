package com.gigwork.core.hiring.application;

import java.time.Instant;
import java.util.UUID;

/** The result of applying: shown to the Talent only, so the busy warning flag is included. */
public record Applied(UUID id, UUID gigId, Instant appliedAt, boolean busyWarningShown, boolean reapply) {
}
