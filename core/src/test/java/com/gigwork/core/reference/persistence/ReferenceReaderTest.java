package com.gigwork.core.reference.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.LanguageInfo;
import com.gigwork.core.reference.LocalityInfo;
import com.gigwork.core.reference.SpecialisationInfo;
import com.gigwork.core.reference.support.ReferenceIntegrationTest;

/** Retired and not-live rows stay in the database but never reach the snapshot's live lists. */
@ReferenceIntegrationTest
@Transactional
class ReferenceReaderTest {

	@Autowired
	ReferenceReader reader;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	EntityManager entityManager;

	@Test
	void retiredSpecialisationsAreLeftOutOfTheirCategoryButStillLookUpable() {
		jdbc.sql("UPDATE reference.specialisation SET status = 'RETIRED' WHERE slug = 'wedding' AND category_id = (SELECT id FROM reference.category WHERE slug = 'photographer')").update();

		ReferenceRows rows = reader.readAll();

		CategoryInfo photographer = rows.categories().stream().filter(c -> c.slug().equals("photographer")).findFirst().orElseThrow();
		assertThat(photographer.specialisations()).extracting(SpecialisationInfo::slug).doesNotContain("wedding");
		assertThat(rows.specialisations()).filteredOn(s -> s.slug().equals("wedding") && s.categoryId().equals(photographer.id()))
				.singleElement().satisfies(s -> assertThat(s.live()).isFalse());
	}

	@Test
	void retiredCategoriesStayVisibleToLookupsButNotLive() {
		jdbc.sql("UPDATE reference.category SET status = 'RETIRED' WHERE slug = 'dj'").update();

		CategoryInfo dj = reader.readAll().categories().stream().filter(c -> c.slug().equals("dj")).findFirst().orElseThrow();

		assertThat(dj.live()).isFalse();
	}

	@Test
	void retiredLanguagesAreNotReturned() {
		jdbc.sql("UPDATE reference.language SET status = 'RETIRED' WHERE code = 'tcy'").update();

		assertThat(reader.readAll().languages()).extracting(LanguageInfo::code).doesNotContain("tcy").contains("en");
	}

	@Test
	void retiredLocalitiesAreNotReturned() {
		int before = reader.readAll().localities().size();
		jdbc.sql("UPDATE reference.locality SET status = 'RETIRED' WHERE source_id = (SELECT min(source_id) FROM reference.locality)").update();
		entityManager.clear(); // the first read cached the entities; read them again after the native update

		assertThat(reader.readAll().localities()).hasSize(before - 1);
	}

	@Test
	void aCityThatIsNotLiveTakesItsZonesAndLocalitiesWithIt() {
		jdbc.sql("UPDATE reference.city SET status = 'NOT_LIVE' WHERE slug = 'bengaluru'").update();

		ReferenceRows rows = reader.readAll();

		assertThat(rows.cities()).isEmpty();
		assertThat(rows.zones()).isEmpty();
		assertThat(rows.localities()).extracting(LocalityInfo::id).isEmpty();
	}

}
