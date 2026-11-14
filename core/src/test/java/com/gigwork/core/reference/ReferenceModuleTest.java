package com.gigwork.core.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import com.gigwork.core.TestcontainersConfiguration;

/** The Reference module on its own: it starts, loads its seed data and exposes only its API. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ReferenceModuleTest {

	@Autowired
	ReferenceData reference;

	@Test
	void theModuleStartsAndServesTheSeededData() {
		assertThat(reference.liveCategories()).isNotEmpty();
		assertThat(reference.languages()).isNotEmpty();
		assertThat(reference.liveCities()).hasSize(1);
		assertThat(reference.searchLocalities("indiranagar", 5)).isNotEmpty();
		assertThat(reference.resolveArea(12.9756, 77.6097).insideZone()).isTrue();
	}

}
