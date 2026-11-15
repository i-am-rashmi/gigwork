package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.gigwork.core.TestcontainersConfiguration;
import com.gigwork.core.availability.Availability;
import com.gigwork.core.availability.Conflicts;
import com.gigwork.core.gigs.GigLookup;
import com.gigwork.core.gigs.GigState;
import com.gigwork.core.gigs.GigSummary;
import com.gigwork.core.hiring.application.ApplicationQueries;
import com.gigwork.core.hiring.application.ApplyService;
import com.gigwork.core.hiring.application.WithdrawService;
import com.gigwork.core.hiring.domain.AlreadyAppliedException;
import com.gigwork.core.hiring.domain.ProfileNotActiveException;
import com.gigwork.core.profile.TalentEligibility;
import com.gigwork.core.profile.TalentProfiles;

/**
 * The Hiring module on its own, against real PostgreSQL: its own beans plus what it asks the other modules for (gigs,
 * profiles and the calendar, stubbed here, and the platform clock).
 */
@ApplicationModuleTest
@Import({ TestcontainersConfiguration.class, HiringModuleTest.Platform.class })
class HiringModuleTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class Platform {

		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}

	}

	@Autowired
	ApplyService applying;
	@Autowired
	WithdrawService withdrawing;
	@Autowired
	ApplicationQueries queries;
	@MockitoBean
	GigLookup gigs;
	@MockitoBean
	TalentProfiles profiles;
	@MockitoBean
	Availability availability;

	@Test
	void aTalentAppliesWithdrawsAndSeesTheApplicationUsingOnlyTheModulesOwnBeansAndStubbedNeighbours() {
		UUID talent = UUID.randomUUID();
		UUID poster = UUID.randomUUID();
		UUID category = UUID.randomUUID();
		UUID gigId = UUID.randomUUID();
		Instant start = Instant.now().plusSeconds(3 * 86_400);
		GigSummary gig = new GigSummary(gigId, poster, category, "Photographer", "Wedding", start, start.plusSeconds(3600),
				"ON_SITE", "Indiranagar", GigState.OPEN);
		when(gigs.find(gigId)).thenReturn(Optional.of(gig));
		when(gigs.findAll(any())).thenReturn(Map.of(gigId, gig));
		when(profiles.eligibility(talent)).thenReturn(Optional.of(new TalentEligibility("ACTIVE", Set.of(category))));
		when(availability.conflicts(talent, gig.startAt(), gig.endAt())).thenReturn(Conflicts.of(List.of()));

		UUID id = applying.apply(talent, gigId, "Happy to help", false).id();

		assertThatThrownBy(() -> applying.apply(talent, gigId, null, false)).isInstanceOf(AlreadyAppliedException.class);
		var mine = queries.mine(talent, null, null);
		assertThat(mine.items()).singleElement().satisfies(a -> {
			assertThat(a.id()).isEqualTo(id);
			assertThat(a.status()).isEqualTo("ACTIVE");
			assertThat(a.gig().status()).isEqualTo(GigState.OPEN);
		});

		withdrawing.withdraw(talent, id);
		assertThat(queries.mine(talent, null, null).items()).singleElement().extracting(a -> a.status()).isEqualTo("WITHDRAWN");
		assertThat(queries.applicants(poster, gigId, null, null).items()).isEmpty();
	}

	@Test
	void aTalentWithoutAnActiveProfileCannotApply() {
		UUID talent = UUID.randomUUID();
		when(profiles.eligibility(talent)).thenReturn(Optional.of(new TalentEligibility("HIDDEN", Set.of())));

		assertThatThrownBy(() -> applying.apply(talent, UUID.randomUUID(), null, false)).isInstanceOf(ProfileNotActiveException.class);
	}

}
