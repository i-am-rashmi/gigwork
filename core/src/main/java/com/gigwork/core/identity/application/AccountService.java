package com.gigwork.core.identity.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.persistence.AccountRepository;
import com.gigwork.core.identity.persistence.AccountRepository.AccountRef;
import com.gigwork.core.identity.security.KeyedHasher;

/** Finds the account of a verified mobile number, or creates it (FR-1, FR-3). */
@Service
public class AccountService {

	/** @param newAccount true only for the call that created the account */
	public record VerifiedAccount(UUID accountId, boolean newAccount) {
	}

	private final AccountRepository accounts;
	private final KeyedHasher hasher;
	private final Clock clock;

	AccountService(AccountRepository accounts, KeyedHasher hasher, Clock clock) {
		this.accounts = accounts;
		this.hasher = hasher;
		this.clock = clock;
	}

	/**
	 * The mobile number must already be verified by OTP. Empty if the account exists but is not active (account
	 * deletion and restore arrive with FR-3a).
	 */
	@Transactional
	public Optional<VerifiedAccount> findOrCreate(MobileNumber verifiedMobile) {
		byte[] hash = hasher.hashMobile(verifiedMobile);

		Optional<AccountRef> existing = accounts.findByMobileHash(hash);
		if (existing.isPresent()) {
			return existing.filter(AccountRef::isActive).map(a -> new VerifiedAccount(a.accountId(), false));
		}
		UUID id = UUID.randomUUID();
		if (accounts.createWithVerifiedMobile(id, verifiedMobile, hash, clock.instant())) {
			return Optional.of(new VerifiedAccount(id, true));
		}
		// another request created it between our lookup and insert: use theirs
		return accounts.findByMobileHash(hash).filter(AccountRef::isActive)
				.map(a -> new VerifiedAccount(a.accountId(), false));
	}

}
