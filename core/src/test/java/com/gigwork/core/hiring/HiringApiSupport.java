package com.gigwork.core.hiring;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gigwork.core.gigs.GigApiSupport;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.profile.ProfileApiSupport;
import com.gigwork.core.reference.ReferenceData;
import com.jayway.jsonpath.JsonPath;

/** Builds the requests of the Hiring endpoints and the Talent, gigs and calendar they need. */
public final class HiringApiSupport {

	private final MockMvc mvc;
	private final ReferenceData reference;
	private final TestAccounts accounts;
	public final GigApiSupport gigs;
	public final ProfileApiSupport profiles;

	public HiringApiSupport(MockMvc mvc, ReferenceData reference, TestAccounts accounts, MutableClock clock) {
		this.mvc = mvc;
		this.reference = reference;
		this.accounts = accounts;
		this.gigs = new GigApiSupport(mvc, reference, clock);
		this.profiles = new ProfileApiSupport(mvc, reference);
	}

	/** A Talent with an ACTIVE profile in the given categories (by slug) and a first name. */
	public TestAccount talent(String firstName, String... categorySlugs) throws Exception {
		TestAccount talent = accounts.create();
		accounts.setFirstName(talent.id(), firstName);
		UUID[] categories = java.util.Arrays.stream(categorySlugs).map(profiles::categoryId).toArray(UUID[]::new);
		profiles.put(talent, "work", profiles.simpleWork(categories)).andExpect(status().isOk());
		profiles.put(talent, "location",
				ProfileApiSupport.locationByLocality(reference.searchLocalities("indiranagar", 1).get(0).id(), 10, true))
				.andExpect(status().isOk());
		profiles.put(talent, "portfolio", ProfileApiSupport.links("instagram.com/" + firstName.toLowerCase()))
				.andExpect(status().isOk());
		return talent;
	}

	/** An open on-site photographer gig, built by the gig API, by {@code poster}. */
	public UUID openGig(TestAccount poster) throws Exception {
		return gigs.publishOk(poster, gigs.onSite());
	}

	public UUID openGig(TestAccount poster, GigApiSupport.Gig gig) throws Exception {
		return gigs.publishOk(poster, gig);
	}

	public ResultActions apply(TestAccount account, UUID gigId, String body) throws Exception {
		return mvc.perform(auth(post("/v1/gigs/" + gigId + "/applications"), account)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	public ResultActions apply(TestAccount account, UUID gigId) throws Exception {
		return apply(account, gigId, "{}");
	}

	public ResultActions applyAcknowledgingBusy(TestAccount account, UUID gigId) throws Exception {
		return apply(account, gigId, "{\"acknowledgeBusy\":true}");
	}

	public ResultActions withdraw(TestAccount account, UUID applicationId) throws Exception {
		return mvc.perform(auth(post("/v1/applications/" + applicationId + "/withdraw"), account));
	}

	public ResultActions mine(TestAccount account, String query) throws Exception {
		return mvc.perform(auth(get("/v1/me/applications" + query), account));
	}

	public ResultActions applicants(TestAccount account, UUID gigId, String query) throws Exception {
		return mvc.perform(auth(get("/v1/gigs/" + gigId + "/applications" + query), account));
	}

	public ResultActions busyBlock(TestAccount account, Instant start, Instant end) throws Exception {
		return mvc.perform(auth(post("/v1/me/busy-blocks"), account).contentType(MediaType.APPLICATION_JSON)
				.content("{\"start\":\"" + start + "\",\"end\":\"" + end + "\"}"));
	}

	/** Applies and returns the application ID; the call must succeed. */
	public UUID applyOk(TestAccount account, UUID gigId) throws Exception {
		return id(apply(account, gigId).andExpect(status().isCreated()));
	}

	public static UUID id(ResultActions result) throws Exception {
		return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	/** The account shares WhatsApp and call (its verified mobile number). */
	public void sharesContact(TestAccount account) throws Exception {
		contactMethods(account, "{\"whatsapp\":true,\"call\":true,\"email\":false}").andExpect(status().isOk());
	}

	public ResultActions contactMethods(TestAccount account, String body) throws Exception {
		return mvc.perform(auth(put("/v1/me/contact-methods"), account).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	public ResultActions shortlist(TestAccount poster, UUID applicationId, boolean shareMyContact) throws Exception {
		return mvc.perform(auth(post("/v1/applications/" + applicationId + "/shortlist"), poster)
				.contentType(MediaType.APPLICATION_JSON).content("{\"shareMyContact\":" + shareMyContact + "}"));
	}

	public ResultActions removeFromShortlist(TestAccount poster, UUID applicationId) throws Exception {
		return mvc.perform(auth(post("/v1/applications/" + applicationId + "/shortlist/remove"), poster));
	}

	public ResultActions posterShare(TestAccount poster, UUID applicationId, boolean share) throws Exception {
		return mvc.perform(auth(put("/v1/applications/" + applicationId + "/shortlist/poster-share"), poster)
				.contentType(MediaType.APPLICATION_JSON).content("{\"share\":" + share + "}"));
	}

	public ResultActions consent(TestAccount talent, UUID applicationId, boolean consent) throws Exception {
		return mvc.perform(auth(put("/v1/applications/" + applicationId + "/shortlist/consent"), talent)
				.contentType(MediaType.APPLICATION_JSON).content("{\"consent\":" + consent + "}"));
	}

	private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, TestAccount account) {
		return account == null ? request : request.header("Authorization", account.header());
	}

}
