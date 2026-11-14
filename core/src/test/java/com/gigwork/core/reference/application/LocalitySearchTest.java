package com.gigwork.core.reference.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gigwork.core.reference.LocalityInfo;

class LocalitySearchTest {

	private static final UUID CITY = UUID.randomUUID();
	private static final UUID ZONE = UUID.randomUUID();

	private static LocalityInfo place(String name, String pin) {
		return new LocalityInfo(UUID.randomUUID(), CITY, ZONE, name, pin, 12.9, 77.6);
	}

	private final LocalityInfo indiranagar = place("Indiranagar", "560038");
	private final LocalityInfo indiraNagarColony = place("Indira Nagar Colony", "560075");
	private final LocalityInfo bellandur = place("Bellandür", "560103");
	private final LocalityInfo hsr1 = place("HSR Layout Sector 1", "560102");
	private final LocalityInfo hsr2 = place("HSR Layout Sector 2", "560102");
	private final LocalityInfo noPin = place("Kachamaranahalli", null);
	private final LocalitySearch search = new LocalitySearch(
			List.of(indiranagar, indiraNagarColony, bellandur, hsr1, hsr2, noPin));

	private List<String> names(String text, int limit) {
		return search.search(text, limit).stream().map(LocalityInfo::name).toList();
	}

	@Test
	void matchesTheStartOfTheNameAndOfLaterWords() {
		assertThat(names("indira", 10)).containsExactly("Indira Nagar Colony", "Indiranagar");
		assertThat(names("nagar", 10)).containsExactly("Indira Nagar Colony");
		assertThat(names("sector 2", 10)).containsExactly("HSR Layout Sector 2");
	}

	@Test
	void doesNotMatchInTheMiddleOfAWord() {
		assertThat(names("ranagar", 10)).isEmpty();
	}

	@Test
	void ignoresCaseAndAccentsInBothDirections() {
		assertThat(names("BELLANDUR", 10)).containsExactly("Bellandür");
		assertThat(names("bellandür", 10)).containsExactly("Bellandür");
		assertThat(names("  hsr   layout  ", 10)).hasSize(2);
	}

	@Test
	void everyTypedWordMustStartSomeWordOfTheName() {
		assertThat(names("layout sector", 10)).hasSize(2);
		assertThat(names("layout colony", 10)).isEmpty();
	}

	@Test
	void digitsMatchThePinPrefixAndPlacesWithoutAPinNeverMatch() {
		assertThat(names("5601", 10)).containsExactly("HSR Layout Sector 1", "HSR Layout Sector 2", "Bellandür");
		assertThat(names("560038", 10)).containsExactly("Indiranagar");
		assertThat(names("9", 10)).isEmpty();
	}

	@Test
	void sameNamesAreBothReturnedAsSeparateLocalities() {
		LocalityInfo a = place("Maruthi Nagara", "560110");
		LocalityInfo b = place("Maruthi Nagara", "560063");

		List<LocalityInfo> found = new LocalitySearch(List.of(a, b, indiranagar)).search("maruthi", 10);

		assertThat(found).containsExactlyInAnyOrder(a, b);
		assertThat(found).extracting(LocalityInfo::id).doesNotHaveDuplicates();
	}

	@Test
	void namesStartingWithTheTextComeFirstAndTheOrderIsStable() {
		LocalitySearch s = new LocalitySearch(List.of(place("New Layout", null), place("Layout One", null), place("Layout Two", null)));

		assertThat(s.search("layout", 10)).extracting(LocalityInfo::name).containsExactly("Layout One", "Layout Two", "New Layout");
		assertThat(s.search("layout", 10)).isEqualTo(s.search("layout", 10));
	}

	@Test
	void limitIsRespectedAndNonsenseGivesNothing() {
		assertThat(search.search("hsr", 1)).hasSize(1);
		assertThat(search.search("hsr", 0)).isEmpty();
		assertThat(search.search("hsr", -3)).isEmpty();
		assertThat(search.search(null, 10)).isEmpty();
		assertThat(search.search("   ", 10)).isEmpty();
		assertThat(search.search("!!", 10)).isEmpty();
	}

}
