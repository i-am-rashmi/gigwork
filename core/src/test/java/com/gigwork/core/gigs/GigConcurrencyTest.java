package com.gigwork.core.gigs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.gigs.application.GigDraft;
import com.gigwork.core.gigs.application.GigExpiry;
import com.gigwork.core.gigs.application.GigService;
import com.gigwork.core.gigs.application.GigView;
import com.gigwork.core.gigs.domain.GigNotOpenException;
import com.gigwork.core.gigs.domain.WorkMode;
import com.gigwork.core.gigs.persistence.GigFields;
import com.gigwork.core.gigs.persistence.GigStore;
import com.gigwork.core.gigs.persistence.StoredGig;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * Edit against cancel on the same gig at the same time (NFR-17, ADR-0008). The two statements are conditional, so
 * whichever takes the row first decides, and the other one then sees the new state: nothing is edited after it was
 * cancelled, and nobody sees a half-way gig.
 */
@IdentityIntegrationTest
@Import(EventCollector.class)
class GigConcurrencyTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;
	@Autowired
	MutableClock clock;
	@Autowired
	TestAccounts accounts;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	GigService gigs;
	@Autowired
	GigStore store;
	@Autowired
	TransactionTemplate transaction;
	@Autowired
	EventCollector collected;
	@Autowired
	GigExpiry expiry;

	GigApiSupport api;
	TestAccount poster;

	@BeforeEach
	void setUp() {
		clock.reset();
		api = new GigApiSupport(mvc, reference, clock);
		poster = accounts.create();
		collected.clear();
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	private GigDraft draft(String title) {
		return new GigDraft(api.category("photographer"), null, null, title, "Reception photography", clock.instant().plus(Duration.ofDays(3)),
				clock.instant().plus(Duration.ofDays(3)).plus(Duration.ofHours(4)), WorkMode.ON_SITE, api.indiranagar(), null, null,
				true, null, null);
	}

	private GigFields fieldsOf(StoredGig g, String title) {
		return new GigFields(g.categoryId(), null, null, title, g.description(), g.startAt(), g.endAt(), g.workMode(), 12.97, 77.6,
				g.cityId(), g.zoneId(), g.locationLabel(), null, true, null, null);
	}

	private String titleOf(UUID id) {
		return jdbc.sql("SELECT title FROM gigs.gig WHERE id = :id").param("id", id).query(String.class).single();
	}

	private String statusOf(UUID id) {
		return jdbc.sql("SELECT status FROM gigs.gig WHERE id = :id").param("id", id).query(String.class).single();
	}

	// ---- the ordering, made certain: one transaction holds the row while the other arrives ---------------------------

	@Test
	void anEditThatArrivesWhileACancelHoldsTheRowFindsTheGigClosedAndChangesNothing() throws Exception {
		GigView gig = gigs.publish(poster.id(), draft("Original"));
		StoredGig stored = store.find(gig.id()).orElseThrow();
		CountDownLatch cancelHoldsTheRow = new CountDownLatch(1);
		CountDownLatch editIsWaiting = new CountDownLatch(1);

		CompletableFuture<Boolean> cancel = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
			boolean done = store.cancel(gig.id(), poster.id(), null, clock.instant());
			cancelHoldsTheRow.countDown();
			await(editIsWaiting);
			sleep(300); // the edit is blocked on the row lock, as far as a test can tell
			return done;
		}));
		await(cancelHoldsTheRow);
		CompletableFuture<Optional<GigStore.Changes>> edit = CompletableFuture.supplyAsync(() -> {
			editIsWaiting.countDown();
			return transaction.execute(status -> store.update(gig.id(), poster.id(), fieldsOf(stored, "Edited after cancel"), clock.instant()));
		});

		assertThat(cancel.get(30, TimeUnit.SECONDS)).isTrue();
		assertThat(edit.get(30, TimeUnit.SECONDS)).isEmpty();
		assertThat(statusOf(gig.id())).isEqualTo("CANCELLED");
		assertThat(titleOf(gig.id())).isEqualTo("Original");
	}

	@Test
	void aCancelThatArrivesWhileAnEditHoldsTheRowWaitsAndThenClosesTheEditedGig() throws Exception {
		GigView gig = gigs.publish(poster.id(), draft("Original"));
		StoredGig stored = store.find(gig.id()).orElseThrow();
		CountDownLatch editHoldsTheRow = new CountDownLatch(1);
		CountDownLatch cancelIsWaiting = new CountDownLatch(1);

		CompletableFuture<Optional<GigStore.Changes>> edit = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
			Optional<GigStore.Changes> changes = store.update(gig.id(), poster.id(), fieldsOf(stored, "Edited first"), clock.instant());
			editHoldsTheRow.countDown();
			await(cancelIsWaiting);
			sleep(300);
			return changes;
		}));
		await(editHoldsTheRow);
		CompletableFuture<Boolean> cancel = CompletableFuture.supplyAsync(() -> {
			cancelIsWaiting.countDown();
			return transaction.execute(status -> store.cancel(gig.id(), poster.id(), "late", clock.instant()));
		});

		assertThat(edit.get(30, TimeUnit.SECONDS)).isPresent();
		assertThat(cancel.get(30, TimeUnit.SECONDS)).isTrue();
		assertThat(statusOf(gig.id())).isEqualTo("CANCELLED");
		assertThat(titleOf(gig.id())).isEqualTo("Edited first");
	}

	// ---- the race itself, many times ----------------------------------------------------------------------------

	@Test
	void simultaneousEditsAndCancelsAlwaysLeaveOneConsistentOutcome() throws Exception {
		int rounds = 40;
		int editedAndCancelled = 0;
		int cancelledBeforeEdit = 0;
		for (int round = 0; round < rounds; round++) {
			GigView gig = gigs.publish(poster.id(), draft("Original"));
			collected.clear();
			List<Callable<String>> tasks = new ArrayList<>();
			tasks.add(() -> attempt(() -> gigs.edit(poster.id(), gig.id(), draft("Edited"))));
			tasks.add(() -> attempt(() -> gigs.edit(poster.id(), gig.id(), draft("Edited"))));
			tasks.add(() -> attempt(() -> gigs.cancel(poster.id(), gig.id(), "changed my mind")));
			tasks.add(() -> attempt(() -> gigs.cancel(poster.id(), gig.id(), "changed my mind")));
			List<String> results = Concurrently.runAll(tasks);

			// exactly one cancel wins; the other finds the gig closed
			assertThat(results.subList(2, 4)).containsExactlyInAnyOrder("ok", "not-open");
			long editsThatWon = results.subList(0, 2).stream().filter("ok"::equals).count();
			assertThat(results.subList(0, 2)).allMatch(r -> r.equals("ok") || r.equals("not-open"));
			assertThat(statusOf(gig.id())).isEqualTo("CANCELLED");
			// an edit that won ran before the cancel and is what is stored; if none won, nothing was edited
			assertThat(titleOf(gig.id())).isEqualTo(editsThatWon > 0 ? "Edited" : "Original");
			// events were written exactly for the changes that happened: one cancel, and the edits that changed anything
			assertThat(collected.of(GigCancelled.class)).hasSize(1);
			assertThat(collected.of(GigChanged.class).size()).isLessThanOrEqualTo((int) editsThatWon);
			if (editsThatWon > 0) {
				editedAndCancelled++;
			}
			else {
				cancelledBeforeEdit++;
			}
		}
		// both orders are possible; the test does not insist on seeing each of them
		assertThat(editedAndCancelled + cancelledBeforeEdit).isEqualTo(rounds);
	}

	// ---- edit, cancel and expiry on one gig -------------------------------------------------------------------

	/** A gig that starts in an hour; the clock is then moved to its start, as if the job runs the moment it is due. */
	private StoredGig gigDueAtTheStart() {
		GigDraft draft = new GigDraft(api.category("photographer"), null, null, "Original", "Reception photography",
				clock.instant().plus(Duration.ofHours(1)), clock.instant().plus(Duration.ofHours(5)), WorkMode.ON_SITE,
				api.indiranagar(), null, null, true, null, null);
		StoredGig gig = store.find(gigs.publish(poster.id(), draft).id()).orElseThrow();
		clock.set(gig.startAt());
		collected.clear();
		return gig;
	}

	/** The same gig, moved three days on: what an edit that arrived just before the start would write. */
	private GigFields movedLater(StoredGig g) {
		return new GigFields(g.categoryId(), null, null, "Edited", g.description(), g.startAt().plus(Duration.ofDays(3)),
				g.startAt().plus(Duration.ofDays(3)).plus(Duration.ofHours(4)), g.workMode(), 12.97, 77.6, g.cityId(), g.zoneId(),
				g.locationLabel(), null, true, null, null);
	}

	@Test
	void aSweepThatHoldsTheRowMakesAnEditThatArrivesNextFindTheGigClosed() throws Exception {
		StoredGig gig = gigDueAtTheStart();
		CountDownLatch sweepHoldsTheRow = new CountDownLatch(1);
		CountDownLatch editIsWaiting = new CountDownLatch(1);

		CompletableFuture<Integer> sweep = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
			int expired = store.expireDue(clock.instant(), 500).size();
			sweepHoldsTheRow.countDown();
			await(editIsWaiting);
			sleep(300);
			return expired;
		}));
		await(sweepHoldsTheRow);
		CompletableFuture<Optional<GigStore.Changes>> edit = CompletableFuture.supplyAsync(() -> {
			editIsWaiting.countDown();
			// the request began a second before the start, so by its own clock the gig had not started
			return transaction.execute(status -> store.update(gig.id(), poster.id(), movedLater(gig), gig.startAt().minusSeconds(1)));
		});

		assertThat(sweep.get(30, TimeUnit.SECONDS)).isEqualTo(1);
		assertThat(edit.get(30, TimeUnit.SECONDS)).isEmpty();
		assertThat(statusOf(gig.id())).isEqualTo("EXPIRED");
		assertThat(titleOf(gig.id())).isEqualTo("Original");
	}

	@Test
	void aSweepThatArrivesWhileAnEditHoldsTheRowLeavesTheGigOpenWithItsNewStart() throws Exception {
		StoredGig gig = gigDueAtTheStart();
		CountDownLatch editHoldsTheRow = new CountDownLatch(1);
		CountDownLatch sweepHasRun = new CountDownLatch(1);

		CompletableFuture<Optional<GigStore.Changes>> edit = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
			Optional<GigStore.Changes> changes = store.update(gig.id(), poster.id(), movedLater(gig), gig.startAt().minusSeconds(1));
			editHoldsTheRow.countDown();
			await(sweepHasRun);
			return changes;
		}));
		await(editHoldsTheRow);
		long sweepChanged = expiry.run(); // skips the locked row instead of waiting for it
		sweepHasRun.countDown();

		assertThat(edit.get(30, TimeUnit.SECONDS)).isPresent();
		assertThat(sweepChanged).isZero();
		assertThat(statusOf(gig.id())).isEqualTo("OPEN");
		assertThat(titleOf(gig.id())).isEqualTo("Edited");
		assertThat(expiry.run()).isZero(); // and the next sweep finds it no longer due
		assertThat(statusOf(gig.id())).isEqualTo("OPEN");
	}

	@Test
	void anEditARunOfTheSweepAndACancelAtTheMomentOfTheStartLeaveOneConsistentOutcome() throws Exception {
		int expired = 0;
		int cancelled = 0;
		for (int round = 0; round < 40; round++) {
			StoredGig gig = gigDueAtTheStart();
			List<Callable<String>> tasks = new ArrayList<>();
			tasks.add(() -> transaction.execute(status -> store.update(gig.id(), poster.id(), movedLater(gig), gig.startAt().minusSeconds(1)))
					.isPresent() ? "edited" : "edit-refused");
			tasks.add(() -> attempt(() -> gigs.cancel(poster.id(), gig.id(), null)));
			tasks.add(() -> "swept " + expiry.run());
			List<String> results = Concurrently.runAll(tasks);

			String finalStatus = statusOf(gig.id());
			assertThat(finalStatus).isIn("CANCELLED", "EXPIRED"); // the cancel always reaches the gig: it either closes it or finds it expired
			if (finalStatus.equals("EXPIRED")) {
				expired++;
				assertThat(results.get(0)).as("an expired gig was not edited").isEqualTo("edit-refused");
				assertThat(titleOf(gig.id())).isEqualTo("Original");
				assertThat(results.get(1)).isEqualTo("not-open");
				assertThat(collected.of(GigExpired.class)).hasSize(1);
				assertThat(collected.of(GigCancelled.class)).isEmpty();
			}
			else {
				cancelled++;
				assertThat(results.get(1)).isEqualTo("ok");
				assertThat(titleOf(gig.id())).isEqualTo(results.get(0).equals("edited") ? "Edited" : "Original");
				assertThat(collected.of(GigCancelled.class)).hasSize(1);
				assertThat(collected.of(GigExpired.class)).isEmpty();
			}
			assertThat(expiry.run()).as("nothing is left to sweep for this gig").isZero();
		}
		assertThat(expired + cancelled).isEqualTo(40);
	}

	private static String attempt(Runnable action) {
		try {
			action.run();
			return "ok";
		}
		catch (GigNotOpenException e) {
			return "not-open";
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
