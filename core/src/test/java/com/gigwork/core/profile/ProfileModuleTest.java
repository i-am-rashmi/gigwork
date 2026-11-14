package com.gigwork.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.gigwork.core.TestcontainersConfiguration;
import com.gigwork.core.identity.AccountNames;
import com.gigwork.core.profile.application.TalentProfileService;
import com.gigwork.core.profile.application.WorkCommand;
import com.gigwork.core.profile.domain.ProfileType;
import com.gigwork.core.reference.ReferenceData;

/**
 * The Profile module on its own, against real PostgreSQL: it starts with only its own beans plus what it asks other
 * modules for (Reference Data and Identity's name lookup, stubbed here, and the platform clock).
 */
@ApplicationModuleTest
@Import({ TestcontainersConfiguration.class, ProfileModuleTest.Platform.class })
class ProfileModuleTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class Platform {

		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}

	}

	@Autowired
	TalentProfileService profiles;
	@Autowired
	JdbcClient jdbc;
	@MockitoBean
	ReferenceData reference;
	@MockitoBean
	AccountNames names;

	@Test
	void theModuleStartsSavesAWorkSectionAndReadsItBack() {
		UUID account = UUID.randomUUID();
		UUID category = UUID.randomUUID();
		when(reference.isLiveCategory(category)).thenReturn(true);

		profiles.saveWork(account, new WorkCommand(ProfileType.INDIVIDUAL, null, "Module test", null, List.of(category), List.of(), List.of()));

		var view = profiles.ownerProfile(account).orElseThrow();
		assertThat(view.status()).isEqualTo("INCOMPLETE");
		assertThat(view.headline()).isEqualTo("Module test");
		assertThat(jdbc.sql("SELECT count(*) FROM profile.talent_category WHERE account_id = :a").param("a", account).query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void aVisitorGetsNoNameAndAnOwnerOfAHiddenProfileStillSeesIt() {
		UUID account = UUID.randomUUID();
		when(names.firstName(account)).thenReturn(Optional.of("Asha"));
		profiles.setHidden(account, true);

		assertThat(profiles.publicProfile(account, Optional.empty())).isEmpty();
		assertThat(profiles.publicProfile(account, Optional.of(UUID.randomUUID()))).isEmpty();
		assertThat(profiles.publicProfile(account, Optional.of(account))).hasValueSatisfying(p -> assertThat(p.firstName()).contains("Asha"));
	}

}
