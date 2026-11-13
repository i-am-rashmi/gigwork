package com.gigwork.core.identity.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
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

	private final JdbcClient jdbc;

	AccountRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<AccountRef> findByMobileHash(byte[] mobileHash) {
		return jdbc.sql("""
				SELECT a.id, a.status
				FROM identity.mobile_identity m JOIN identity.account a ON a.id = m.account_id
				WHERE m.mobile_hash = :hash""")
				.param("hash", mobileHash)
				.query((rs, rowNum) -> new AccountRef(rs.getObject("id", UUID.class), rs.getString("status")))
				.optional();
	}

	/**
	 * Creates an account with its verified mobile identity. The unique mobile_hash constraint decides a race:
	 * returns false, and leaves nothing behind, if another request created the account for this number first.
	 */
	public boolean createWithVerifiedMobile(UUID accountId, MobileNumber mobile, byte[] mobileHash, Instant now) {
		jdbc.sql("INSERT INTO identity.account (id, created_at) VALUES (:id, :now)")
				.param("id", accountId).param("now", utc(now)).update();
		// TODO(NFR-44): store the mobile encrypted; the hash column is already the only lookup path
		int inserted = jdbc.sql("""
				INSERT INTO identity.mobile_identity (account_id, mobile, mobile_hash, verified_at)
				VALUES (:id, :mobile, :hash, :now)
				ON CONFLICT (mobile_hash) DO NOTHING""")
				.param("id", accountId).param("mobile", mobile.value()).param("hash", mobileHash)
				.param("now", utc(now)).update();
		if (inserted == 0) {
			// lost the race: remove our unreferenced account row (the winner's row stays)
			jdbc.sql("DELETE FROM identity.account WHERE id = :id").param("id", accountId).update();
			return false;
		}
		return true;
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
