package com.gigwork.core.reference.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class CsvTest {

	private static byte[] b(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void readsRowsKeyedByTheHeader() {
		var rows = Csv.parse(b("a,b\n1,2\n3,4\n"), "f.csv");

		assertThat(rows).hasSize(2);
		assertThat(rows.get(1)).containsEntry("a", "3").containsEntry("b", "4");
	}

	@Test
	void handlesQuotesCommasEmptyFieldsAndCrlf() {
		var rows = Csv.parse(b("name,pin,lat\r\n\"Doe, \"\"J\"\" Layout\",,12.5\r\nPlain,560001,13\r\n"), "f.csv");

		assertThat(rows.get(0)).containsEntry("name", "Doe, \"J\" Layout").containsEntry("pin", "").containsEntry("lat", "12.5");
		assertThat(rows.get(1)).containsEntry("pin", "560001");
	}

	@Test
	void keepsUnicodeAndAMissingFinalNewline() {
		var rows = Csv.parse(b("name\nDécorator\nಇಂದಿರಾನಗರ"), "f.csv");

		assertThat(rows).extracting(r -> r.get("name")).containsExactly("Décorator", "ಇಂದಿರಾನಗರ");
	}

	@Test
	void refusesARowWithTheWrongNumberOfFields() {
		assertThatThrownBy(() -> Csv.parse(b("a,b\n1\n"), "f.csv")).isInstanceOf(SeedDataException.class)
				.hasMessageContaining("f.csv line 2");
	}

	@Test
	void refusesAnUnterminatedQuote() {
		assertThatThrownBy(() -> Csv.parse(b("a\n\"open\n"), "f.csv")).isInstanceOf(SeedDataException.class);
	}

}
