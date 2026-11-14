package com.gigwork.core.profile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.ReferenceData;
import com.gigwork.core.reference.SpecialisationInfo;

/** Builds requests for the profile endpoints and finds reference rows by their slugs. */
public final class ProfileApiSupport {

	private final MockMvc mvc;
	private final ReferenceData reference;

	public ProfileApiSupport(MockMvc mvc, ReferenceData reference) {
		this.mvc = mvc;
		this.reference = reference;
	}

	public CategoryInfo category(String slug) {
		return reference.liveCategories().stream().filter(c -> c.slug().equals(slug)).findFirst().orElseThrow();
	}

	public UUID categoryId(String slug) {
		return category(slug).id();
	}

	public UUID specialisationId(String categorySlug, String slug) {
		return category(categorySlug).specialisations().stream().map(SpecialisationInfo::id)
				.filter(id -> reference.specialisation(id).orElseThrow().slug().equals(slug)).findFirst().orElseThrow();
	}

	public static String ids(List<UUID> ids) {
		return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
	}

	public static String strings(List<String> values) {
		return values.stream().map(v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
				.collect(Collectors.joining(",", "[", "]"));
	}

	public static String quote(String text) {
		return text == null ? "null" : "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	public String work(String type, Integer memberCount, String headline, String bio, List<UUID> categories,
			List<UUID> specialisations, List<String> languages) {
		return "{\"profileType\":" + quote(type) + ",\"memberCount\":" + memberCount + ",\"headline\":" + quote(headline)
				+ ",\"bio\":" + quote(bio) + ",\"categoryIds\":" + ids(categories) + ",\"specialisationIds\":"
				+ ids(specialisations) + ",\"languageCodes\":" + strings(languages) + "}";
	}

	public String simpleWork(UUID... categories) {
		return work("INDIVIDUAL", null, "Wedding storyteller", "Candid and warm.", List.of(categories), List.of(), List.of());
	}

	public static String locationByLocality(UUID localityId, int radius, boolean travel) {
		return "{\"localityId\":\"" + localityId + "\",\"serviceRadiusKm\":" + radius + ",\"willingToTravel\":" + travel + "}";
	}

	public static String locationByPoint(double lat, double lng, int radius, boolean travel) {
		return "{\"point\":{\"lat\":" + lat + ",\"lng\":" + lng + "},\"serviceRadiusKm\":" + radius
				+ ",\"willingToTravel\":" + travel + "}";
	}

	public static String links(String... links) {
		return "{\"links\":" + strings(List.of(links)) + "}";
	}

	public ResultActions put(TestAccount account, String path, String body) throws Exception {
		var request = MockMvcRequestBuilders.put("/v1/me/talent-profile/" + path).contentType(MediaType.APPLICATION_JSON)
				.content(body);
		if (account != null) {
			request.header("Authorization", account.header());
		}
		return mvc.perform(request);
	}

	public ResultActions getOwn(TestAccount account) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.get("/v1/me/talent-profile").header("Authorization", account.header()));
	}

	public ResultActions view(UUID accountId, String bearerHeader) throws Exception {
		var request = MockMvcRequestBuilders.get("/v1/talent/" + accountId);
		if (bearerHeader != null) {
			request.header("Authorization", bearerHeader);
		}
		return mvc.perform(request);
	}

}
