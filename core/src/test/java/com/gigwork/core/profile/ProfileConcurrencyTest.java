package com.gigwork.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.profile.application.LocationCommand;
import com.gigwork.core.profile.application.TalentProfileService;
import com.gigwork.core.profile.application.WorkCommand;
import com.gigwork.core.profile.domain.ProfileType;
import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.ReferenceData;
import com.gigwork.core.reference.SpecialisationInfo;

/**
 * ADR-0008: saves of one Talent are serialised by the lock on the profile row, so after two or more saves race the
 * database holds exactly one request's lists, never a union or a mixture. Repeated many times because a race is
 * not found by one run.
 */
@IdentityIntegrationTest
class ProfileConcurrencyTest {

	private static final int ROUNDS = 40;

	@Autowired
	TalentProfileService service;
	@Autowired
	ReferenceData reference;
	@Autowired
	JdbcClient jdbc;

	private List<UUID> categories(int from, int count) {
		return reference.liveCategories().subList(from, from + count).stream().map(CategoryInfo::id).toList();
	}

	/** Up to {@code max} specialisations spread over the given categories. */
	private List<UUID> specialisations(List<UUID> categoryIds, int max) {
		return categoryIds.stream().flatMap(id -> reference.category(id).orElseThrow().specialisations().stream().limit(3))
				.map(SpecialisationInfo::id).limit(max).toList();
	}

	private WorkCommand work(List<UUID> categoryIds, List<UUID> specialisationIds, String headline) {
		return new WorkCommand(ProfileType.INDIVIDUAL, null, headline, null, categoryIds, specialisationIds, List.of());
	}

	// sets: the database orders UUIDs as unsigned bytes and Java as signed longs, so lists are never compared in order
	private Set<UUID> storedCategories(UUID account) {
		return new HashSet<>(jdbc.sql("SELECT category_id FROM profile.talent_category WHERE account_id = :a").param("a", account)
				.query(UUID.class).list());
	}

	private Set<UUID> storedSpecialisations(UUID account) {
		return new HashSet<>(jdbc.sql("SELECT specialisation_id FROM profile.talent_specialisation WHERE account_id = :a")
				.param("a", account).query(UUID.class).list());
	}

	private static Set<UUID> set(List<UUID> ids) {
		return new HashSet<>(ids);
	}

	@Test
	void twoSimultaneousWorkSavesLeaveExactlyOneOfTheTwoLists() throws Exception {
		List<UUID> three = categories(0, 3);
		List<UUID> four = categories(3, 4); // disjoint, so a union would be visible
		List<UUID> specsOfThree = specialisations(three, 5);
		List<UUID> specsOfFour = specialisations(four, 7);
		int firstWins = 0;
		int secondWins = 0;

		for (int round = 0; round < ROUNDS; round++) {
			UUID account = UUID.randomUUID();
			Concurrently.runAll(List.<Callable<Object>>of(() -> service.saveWork(account, work(three, specsOfThree, "Three")),
					() -> service.saveWork(account, work(four, specsOfFour, "Four"))));

			Set<UUID> categories = storedCategories(account);
			Set<UUID> specs = storedSpecialisations(account);
			String headline = jdbc.sql("SELECT headline FROM profile.talent_profile WHERE account_id = :a").param("a", account).query(String.class).single();
			if (categories.equals(set(three))) {
				assertThat(specs).as("round %d: specialisations of the 3-category save", round).isEqualTo(set(specsOfThree));
				assertThat(headline).isEqualTo("Three");
				firstWins++;
			}
			else {
				assertThat(categories).as("round %d: categories", round).isEqualTo(set(four));
				assertThat(specs).as("round %d: specialisations of the 4-category save", round).isEqualTo(set(specsOfFour));
				assertThat(headline).isEqualTo("Four");
				secondWins++;
			}
		}
		assertThat(firstWins + secondWins).isEqualTo(ROUNDS);
	}

	@Test
	void manySimultaneousSavesStillLeaveExactlyOneRequestsLists() throws Exception {
		List<List<UUID>> categorySets = List.of(categories(0, 1), categories(1, 2), categories(3, 3), categories(6, 4), categories(10, 5));
		for (int i = 0; i < 15; i++) {
			int round = i;
			UUID account = UUID.randomUUID();
			List<Callable<Object>> tasks = new ArrayList<>();
			for (List<UUID> set : categorySets) {
				List<UUID> specs = specialisations(set, 10);
				tasks.add(() -> service.saveWork(account, work(set, specs, "Set " + set.size())));
			}
			Concurrently.runAll(tasks);

			Set<UUID> categories = storedCategories(account);
			Set<UUID> specs = storedSpecialisations(account);
			List<UUID> chosen = categorySets.stream().filter(s -> set(s).equals(categories)).findFirst()
					.orElseThrow(() -> new AssertionError("round " + round + ": categories are not any request's list: " + categories));
			assertThat(specs).as("round %d", round).isEqualTo(set(specialisations(chosen, 10)));
		}
	}

	@Test
	void theLimitsHoldUnderConcurrentSaves() throws Exception {
		List<UUID> five = categories(0, 5);
		List<UUID> tenSpecs = specialisations(five, 10);
		UUID account = UUID.randomUUID();

		Concurrently.runAll(12, () -> service.saveWork(account, work(five, tenSpecs, "Max")));

		assertThat(storedCategories(account)).hasSize(5);
		assertThat(storedSpecialisations(account)).hasSize(10);
	}

	@Test
	void simultaneousSavesOfDifferentSectionsAllLand() throws Exception {
		LocationCommand location = new LocationCommand(reference.searchLocalities("indiranagar", 1).get(0).id(), null, null, 12, true);
		for (int round = 0; round < 20; round++) {
			UUID account = UUID.randomUUID();
			Concurrently.runAll(List.<Callable<Object>>of(() -> service.saveWork(account, work(categories(0, 2), List.of(), "Work")),
					() -> service.saveLocation(account, location), () -> service.savePortfolio(account, List.of("example.com", "instagram.com/a")),
					() -> service.setHidden(account, false)));

			var row = jdbc.sql("SELECT status, headline, location_label FROM profile.talent_profile WHERE account_id = :a").param("a", account)
					.query((rs, n) -> new String[] { rs.getString("status"), rs.getString("headline"), rs.getString("location_label") }).single();
			assertThat(row).containsExactly("ACTIVE", "Work", "Indiranagar");
			assertThat(jdbc.sql("SELECT count(*) FROM profile.portfolio_link WHERE account_id = :a").param("a", account).query(Integer.class).single()).isEqualTo(2);
			assertThat(storedCategories(account)).hasSize(2);
		}
	}

	@Test
	void simultaneousFirstSavesCreateOneProfile() throws Exception {
		for (int round = 0; round < 20; round++) {
			UUID account = UUID.randomUUID();

			Concurrently.runAll(8, () -> service.setHidden(account, true));

			assertThat(jdbc.sql("SELECT count(*) FROM profile.talent_profile WHERE account_id = :a").param("a", account).query(Integer.class).single()).isEqualTo(1);
		}
	}

	@Test
	void theCheckThatMakesThisTestsMeaningful() {
		// the two category lists above must really be disjoint, or a union could not be told from a winner
		assertThat(Stream.concat(categories(0, 3).stream(), categories(3, 4).stream()).distinct().count()).isEqualTo(7);
	}

}
