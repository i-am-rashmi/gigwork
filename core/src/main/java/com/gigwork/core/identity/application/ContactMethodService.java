package com.gigwork.core.identity.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.gigwork.core.identity.ContactCard;
import com.gigwork.core.identity.ContactMethods;
import com.gigwork.core.identity.domain.EmailNotAvailableException;
import com.gigwork.core.identity.persistence.ContactMethodStore;
import com.gigwork.core.identity.persistence.ContactMethodStore.Chosen;

/**
 * The contact methods a user shares (FR-2, minimal) and the card other modules fetch at each view (FR-26).
 * WhatsApp and call use the verified mobile number. Email is refused until email verification exists.
 */
@Service
public class ContactMethodService implements ContactMethods {

	private final ContactMethodStore store;
	private final Clock clock;

	ContactMethodService(ContactMethodStore store, Clock clock) {
		this.store = store;
		this.clock = clock;
	}

	/** @throws EmailNotAvailableException if {@code email} is true */
	public Chosen save(UUID accountId, boolean whatsapp, boolean call, boolean email) {
		if (email) {
			throw new EmailNotAvailableException();
		}
		Chosen chosen = new Chosen(whatsapp, call, false);
		store.save(accountId, chosen, clock.instant());
		return chosen;
	}

	/** What the account has chosen; nothing shared if it never chose. */
	public Chosen chosen(UUID accountId) {
		return store.find(accountId).orElse(new Chosen(false, false, false));
	}

	@Override
	public boolean hasContactMethod(UUID accountId) {
		return contactCard(accountId).isPresent();
	}

	@Override
	public Optional<ContactCard> contactCard(UUID accountId) {
		Chosen chosen = chosen(accountId);
		boolean needsMobile = chosen.whatsapp() || chosen.call();
		String mobile = needsMobile ? store.verifiedMobile(accountId).orElse(null) : null;
		boolean whatsapp = chosen.whatsapp() && mobile != null;
		boolean call = chosen.call() && mobile != null;
		if (!whatsapp && !call) { // email can't be chosen yet
			return Optional.empty();
		}
		return Optional.of(new ContactCard(accountId, whatsapp, call, false, mobile, null));
	}

}
