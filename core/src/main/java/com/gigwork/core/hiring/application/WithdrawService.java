package com.gigwork.core.hiring.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.hiring.ApplicationWithdrawn;
import com.gigwork.core.hiring.domain.AlreadyWithdrawnException;
import com.gigwork.core.hiring.domain.ApplicationNotFoundException;
import com.gigwork.core.hiring.domain.HireInProgressException;
import com.gigwork.core.hiring.persistence.ApplicationStore;
import com.gigwork.core.hiring.persistence.ApplicationStore.Owner;
import com.gigwork.core.hiring.persistence.ApplicationStore.Withdrawn;
import com.gigwork.core.hiring.persistence.HireStore;
import com.gigwork.core.hiring.persistence.ShortlistStore;

/**
 * Withdrawing an application. One conditional UPDATE does the change (ACTIVE to WITHDRAWN, applicant only), in the
 * transaction that publishes the event. Why it matched nothing is worked out only afterwards, so the check and the
 * change can never disagree. The submission log is never touched, so apply/withdraw loops still count (NFR-39).
 */
@Service
public class WithdrawService {

	private final ApplicationStore store;
	private final ShortlistStore shortlists;
	private final HireStore hires;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	WithdrawService(ApplicationStore store, ShortlistStore shortlists, HireStore hires, TransactionTemplate transaction,
			ApplicationEventPublisher events, Clock clock) {
		this.store = store;
		this.hires = hires;
		this.shortlists = shortlists;
		this.transaction = transaction;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * @throws ApplicationNotFoundException unknown, or someone else's (never says which)
	 * @throws AlreadyWithdrawnException the caller's own application is already withdrawn
	 * @throws HireInProgressException the application has a pending or confirmed hire: end the hire first
	 */
	public void withdraw(UUID talentId, UUID applicationId) {
		Instant now = clock.instant();
		Boolean done = transaction.execute(status -> {
			// lock the row, then look for a hire in a separate statement (which sees what a hire started a moment ago
			// committed), then change: a hire that starts at the same time waits for this lock or is seen here
			if (store.lockForUpdate(applicationId).filter(a -> a.talentId().equals(talentId)).isPresent()
					&& hires.inProgress(applicationId)) {
				throw new HireInProgressException();
			}
			Optional<Withdrawn> withdrawn = store.withdraw(applicationId, talentId, now);
			withdrawn.ifPresent(w -> {
				shortlists.endForWithdrawnApplication(applicationId, now); // access to contacts ends in the same transaction
				events.publishEvent(new ApplicationWithdrawn(applicationId, w.gigId(), talentId, w.posterId(), now));
			});
			return withdrawn.isPresent();
		});
		if (!Boolean.TRUE.equals(done)) {
			Optional<Owner> owner = store.owner(applicationId).filter(o -> o.talentId().equals(talentId));
			throw owner.isEmpty() ? new ApplicationNotFoundException() : new AlreadyWithdrawnException();
		}
	}

}
