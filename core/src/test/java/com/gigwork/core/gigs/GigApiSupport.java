package com.gigwork.core.gigs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.profile.ProfileApiSupport;
import com.gigwork.core.reference.ReferenceData;

/** Builds gig requests and calls the endpoints. Times are relative to the test clock. */
public final class GigApiSupport {

	private final MockMvc mvc;
	private final ReferenceData reference;
	private final MutableClock clock;
	private final ProfileApiSupport slugs;

	public GigApiSupport(MockMvc mvc, ReferenceData reference, MutableClock clock) {
		this.mvc = mvc;
		this.reference = reference;
		this.clock = clock;
		this.slugs = new ProfileApiSupport(mvc, reference);
	}

	public UUID category(String slug) {
		return slugs.categoryId(slug);
	}

	public UUID specialisation(String categorySlug, String slug) {
		return slugs.specialisationId(categorySlug, slug);
	}

	public UUID indiranagar() {
		return reference.searchLocalities("indiranagar", 1).get(0).id();
	}

	/** A valid on-site photographer gig starting in 3 days, as a builder to bend one rule at a time. */
	public Gig onSite() {
		return new Gig(clock).category(category("photographer")).workMode("ON_SITE").locality(indiranagar()).ta(true);
	}

	/** A valid remote video-editing gig starting in 3 days. */
	public Gig remote() {
		return new Gig(clock).category(category("video-editor")).workMode("REMOTE");
	}

	public ResultActions publish(TestAccount poster, Gig gig) throws Exception {
		return publish(poster, gig.json());
	}

	public ResultActions publish(TestAccount poster, String json) throws Exception {
		return mvc.perform(auth(post("/v1/gigs"), poster).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	public ResultActions edit(TestAccount poster, UUID id, Gig gig) throws Exception {
		return mvc.perform(auth(put("/v1/gigs/" + id), poster).contentType(MediaType.APPLICATION_JSON).content(gig.json()));
	}

	public ResultActions cancel(TestAccount poster, UUID id, String body) throws Exception {
		return mvc.perform(auth(post("/v1/gigs/" + id + "/cancel"), poster).contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	public ResultActions get(TestAccount viewer, String path) throws Exception {
		return mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1" + path), viewer));
	}

	private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, TestAccount account) {
		return account == null ? request : request.header("Authorization", account.header());
	}

	/** Publishes and returns the new gig's ID. */
	public UUID publishOk(TestAccount poster, Gig gig) throws Exception {
		String body = publish(poster, gig).andReturn().getResponse().getContentAsString();
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
	}

	/** A gig request under construction; every setter returns the builder. */
	public static final class Gig {

		private final MutableClock clock;
		private UUID category;
		private UUID specialisation;
		private String language;
		private String title = "Photographer for a two-day wedding";
		private String description = "Candid photography for the mehendi and the wedding day.";
		private Instant start;
		private Instant end;
		private Duration length = Duration.ofHours(8);
		private String workMode;
		private UUID locality;
		private Double lat;
		private Double lng;
		private Boolean ta;
		private String budgetMin;
		private String budgetMax;
		private String extra = "";

		Gig(MutableClock clock) {
			this.clock = clock;
			this.start = clock.instant().plus(Duration.ofDays(3)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
		}

		public Gig category(UUID value) { this.category = value; return this; }
		public Gig specialisation(UUID value) { this.specialisation = value; return this; }
		public Gig language(String value) { this.language = value; return this; }
		public Gig title(String value) { this.title = value; return this; }
		public Gig description(String value) { this.description = value; return this; }
		public Gig start(Instant value) { this.start = value; return this; }
		public Gig end(Instant value) { this.end = value; return this; }
		public Gig startsIn(Duration value) { this.start = clock.instant().plus(value).truncatedTo(java.time.temporal.ChronoUnit.SECONDS); return this; }
		public Gig lasting(Duration value) { this.length = value; this.end = null; return this; }
		public Gig workMode(String value) { this.workMode = value; return this; }
		public Gig locality(UUID value) { this.locality = value; this.lat = null; this.lng = null; return this; }
		public Gig point(double latitude, double longitude) { this.lat = latitude; this.lng = longitude; this.locality = null; return this; }
		public Gig noLocation() { this.locality = null; this.lat = null; this.lng = null; return this; }
		public Gig ta(Boolean value) { this.ta = value; return this; }
		public Gig budget(String min, String max) { this.budgetMin = min; this.budgetMax = max; return this; }
		/** Raw JSON members appended to the request (for broken shapes). */
		public Gig extra(String membersJson) { this.extra = membersJson; return this; }

		public Instant start() { return start; }
		public Instant end() { return end != null ? end : start.plus(length); }

		public String json() {
			StringBuilder json = new StringBuilder("{");
			json.append("\"categoryId\":").append(ProfileApiSupport.quote(category == null ? null : category.toString()));
			json.append(",\"specialisationId\":").append(ProfileApiSupport.quote(specialisation == null ? null : specialisation.toString()));
			json.append(",\"languageCode\":").append(ProfileApiSupport.quote(language));
			json.append(",\"title\":").append(ProfileApiSupport.quote(title));
			json.append(",\"description\":").append(ProfileApiSupport.quote(description));
			json.append(",\"startAt\":").append(ProfileApiSupport.quote(start == null ? null : start.toString()));
			json.append(",\"endAt\":").append(ProfileApiSupport.quote(start == null ? null : end().toString()));
			json.append(",\"workMode\":").append(ProfileApiSupport.quote(workMode));
			if (locality != null) {
				json.append(",\"location\":{\"localityId\":\"").append(locality).append("\"}");
			}
			else if (lat != null || lng != null) {
				json.append(",\"location\":{\"point\":{\"lat\":").append(lat).append(",\"lng\":").append(lng).append("}}");
			}
			if (ta != null) {
				json.append(",\"taOffered\":").append(ta);
			}
			if (budgetMin != null) {
				json.append(",\"budgetMin\":").append(budgetMin);
			}
			if (budgetMax != null) {
				json.append(",\"budgetMax\":").append(budgetMax);
			}
			if (!extra.isEmpty()) {
				json.append(',').append(extra);
			}
			return json.append('}').toString();
		}

	}

}
