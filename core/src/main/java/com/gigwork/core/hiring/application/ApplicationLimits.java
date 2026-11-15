package com.gigwork.core.hiring.application;

import java.util.UUID;

/**
 * How many submissions a Talent may make in any rolling 24 hours. Today everyone gets the abuse limit from the
 * configuration (NFR-39); when plan limits (free, standard, pro) are designed they plug in here, with a Talent's
 * plan deciding the answer. Never lower than the abuse limit.
 */
public interface ApplicationLimits {

	int perRollingDay(UUID talentId);

}
