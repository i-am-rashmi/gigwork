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
import com.gigwork.core.hiring.persistence.ApplicationStore;
import com.gigwork.core.hiring.persistence.ApplicationStore.Owner;
import com.gigwork.core.hiring.persistence.ApplicationStore.Withdrawn;

/**
 * Withdrawing an application. One conditional UPDATE does the change (ACTIVE to WITHDRAWN, applicant only), in the
 * transaction that publishes the event. Why it matched nothing is worked out only afterwards, so the check and the
 * change can never disagree. The submission log is never touched, so apply/withdraw loops still count (NFR-39).
 */
@Service
public class WithdrawService {

	private final ApplicationStore store;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	WithdrawService(ApplicationStore store, TransactionTemplate transaction, ApplicationEventPublisher events, Clock clock) {
		this.store = store;
		this.transaction = transaction;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * @throws ApplicationNotFoundException unknown, or someone else's (never says which)
	 * @throws AlreadyWithdrawnException the caller's own application is already withdrawn
	 */
	public void withdraw(UUID talentId, UUID applicationId) {
		Instant now = clock.instant();
		Boolean done = transaction.execute(status -> {
			Optional<Withdrawn> withdrawn = store.withdraw(applicationId, talentId, now);
			withdrawn.ifPresent(w -> events.publishEvent(
					new ApplicationWithdrawn(applicationId, w.gigId(), talentId, w.posterId(), now)));
			return withdrawn.isPresent();
		});
		if (!Boolean.TRUE.equals(done)) {
			Optional<Owner> owner = store.owner(applicationId).filter(o -> o.talentId().equals(talentId));
			throw owner.isEmpty() ? new ApplicationNotFoundException() : new AlreadyWithdrawnException();
		}
	}

}
