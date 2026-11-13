package com.gigwork.core.identity.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.gigwork.core.identity.domain.MobileNumber;

/** Accounts and their mobile identities. Accounts are found only by the keyed hash of the mobile number. */
@Repository
public class AccountRepository {

	public record AccountRef(UUID accountId, String status) {

		public boolean isActive() {
			return "ACTIVE".equals(status);
		}
	}

	private final AccountJpaRepository accounts;

	AccountRepository(AccountJpaRepository accounts) {
		this.accounts = accounts;
	}

	public Optional<AccountRef> findByMobileHash(byte[] mobileHash) {
		return accounts.findByMobileHash(mobileHash).map(a -> new AccountRef(a.getId(), a.getStatus()));
	}

	/**
	 * Creates an account with its verified mobile identity. The unique mobile_hash constraint decides a race:
	 * returns false, and leaves nothing behind, if another request created the account for this number first.
	 * The account is an ordinary JPA insert; the mobile identity is one conflict-tolerant native statement
	 * (ADR-0008).
	 */
	public boolean createWithVerifiedMobile(UUID accountId, MobileNumber mobile, byte[] mobileHash, Instant now) {
		accounts.save(new Account(accountId, now));
		// TODO(NFR-44): store the mobile encrypted; the hash column is already the only lookup path
		int inserted = accounts.insertMobileIdentityIfAbsent(accountId, mobile.value(), mobileHash, now);
		if (inserted == 0) {
			// lost the race: remove our unreferenced account row (the winner's row stays)
			accounts.deleteById(accountId);
			return false;
		}
		return true;
	}

}
