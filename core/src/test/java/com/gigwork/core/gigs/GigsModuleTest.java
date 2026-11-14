package com.gigwork.core.gigs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.gigwork.core.TestcontainersConfiguration;
import com.gigwork.core.gigs.application.GigDraft;
import com.gigwork.core.gigs.application.GigService;
import com.gigwork.core.gigs.application.GigView;
import com.gigwork.core.gigs.domain.GigStatus;
import com.gigwork.core.gigs.domain.WorkMode;
import com.gigwork.core.identity.AccountNames;
import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.LocationResolver;
import com.gigwork.core.platform.JobRuns;
import com.gigwork.core.reference.ReferenceData;

/**
 * The Gigs module on its own, against real PostgreSQL: its own beans plus what it asks other modules for (reference
 * data, the location resolver, first names and the job recorder, stubbed here, and the platform clock).
 */
@ApplicationModuleTest
@Import({ TestcontainersConfiguration.class, GigsModuleTest.Platform.class })
class GigsModuleTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class Platform {

		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}

	}

	@Autowired
	GigService gigs;
	@MockitoBean
	ReferenceData reference;
	@MockitoBean
	LocationResolver locations;
	@MockitoBean
	AccountNames names;
	@MockitoBean
	JobRuns jobRuns;

	@Test
	void aRemoteGigIsPublishedAndCancelledUsingOnlyTheModulesOwnBeansAndStubbedNeighbours() {
		UUID poster = UUID.randomUUID();
		UUID category = UUID.randomUUID();
		when(names.firstName(poster)).thenReturn(Optional.of("Asha"));
		when(reference.category(category)).thenReturn(Optional.of(new CategoryInfo(category, "video-editor", "Video editor",
				"REMOTE", false, true, 1, List.of())));
		Instant start = Instant.now().plusSeconds(3 * 86_400);

		GigView published = gigs.publish(poster, new GigDraft(category, null, null, "Edit a wedding film", "Two hours of footage",
				start, start.plusSeconds(3600), WorkMode.REMOTE, null, null, null, null, null, null));

		assertThat(published.status()).isEqualTo(GigStatus.OPEN);
		assertThat(published.posterFirstName()).isEqualTo("Asha");
		assertThat(published.locationLabel()).isNull();

		GigView cancelled = gigs.cancel(poster, published.id(), null);
		assertThat(cancelled.status()).isEqualTo(GigStatus.CANCELLED);
	}

}
