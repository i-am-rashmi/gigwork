package com.gigwork.core.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MobileNumberTest {

	@ParameterizedTest
	@ValueSource(strings = { "9876543210", "+919876543210", "919876543210", "09876543210", "+91 98765 43210",
			"98765-43210", "  9876543210  ", "+91-98765-43210", "6000000000", "7123456789", "8123456789" })
	void normalisesEveryAcceptedFormToTheCanonicalForm(String input) {
		var parsed = MobileNumber.parse(input.strip());

		assertThat(parsed.value()).matches("\\+91[6-9][0-9]{9}");
	}

	@Test
	void sameNumberInDifferentFormatsIsEqual() {
		assertThat(MobileNumber.parse("9876543210")).isEqualTo(MobileNumber.parse("+91 98765-43210"))
				.isEqualTo(MobileNumber.parse("09876543210")).isEqualTo(MobileNumber.parse("919876543210"));
		assertThat(MobileNumber.parse("9876543210").value()).isEqualTo("+919876543210");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "12345", "5876543210", "+14155550123", "+447911123456",
			"98765432101", "+91987654321", "+9198765432100", "abcdefghij", "+91 98765 4321a", "0919876543210",
			"+91+919876543210", "++919876543210", "9876543210\n", "(98765) 43210", "+91987654321O",
			"٩٨٧٦٥٤٣٢١٠", "9876543210987654321098765432109876543210" })
	void rejectsAnythingThatIsNotAnIndianMobileNumber(String input) {
		assertThatThrownBy(() -> MobileNumber.parse(input)).isInstanceOf(InvalidMobileNumberException.class);
	}

	@Test
	void constructorOnlyAcceptsTheCanonicalForm() {
		assertThatThrownBy(() -> new MobileNumber("9876543210")).isInstanceOf(InvalidMobileNumberException.class);
		assertThatThrownBy(() -> new MobileNumber(null)).isInstanceOf(InvalidMobileNumberException.class);
	}

	@Test
	void toStringAndErrorsNeverRevealTheNumber() {
		var number = MobileNumber.parse("9876543210");

		assertThat(number.toString()).isEqualTo("+91******3210").doesNotContain("98765");
		assertThat(number.masked()).isEqualTo("+91******3210");
		assertThatThrownBy(() -> MobileNumber.parse("5876543210")).hasMessageNotContaining("5876543210");
	}

}
