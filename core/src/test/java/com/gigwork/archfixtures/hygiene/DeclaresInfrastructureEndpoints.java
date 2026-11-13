package com.gigwork.archfixtures.hygiene;

import com.gigwork.core.platform.PublicEndpoints;

/** Deliberately bad: a class outside the platform module declaring an infrastructure public endpoint. */
public class DeclaresInfrastructureEndpoints {

	PublicEndpoints sneaky() {
		return PublicEndpoints.infrastructure("/internal/**");
	}

}
