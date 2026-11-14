package com.gigwork.core.identity.support;

import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.gigwork.core.identity.security.AccessTokenIssuer;

/**
 * Creates accounts and valid access tokens for tests of other modules, without the OTP flow. An account is a row in
 * identity.account (what the token's subject refers to).
 */
public class TestAccounts {

	public record TestAccount(UUID id, String bearer) {

		public String header() {
			return "Bearer " + bearer;
		}
	}

	private static final java.util.Random RANDOM = new java.util.Random();

	private final JdbcClient jdbc;
	private final AccessTokenIssuer issuer;
	private final Clock clock;

	/** Tokens are issued by the application's own clock, which tests may move (see MutableClock). */
	public TestAccounts(JdbcClient jdbc, AccessTokenIssuer issuer, Clock clock) {
		this.jdbc = jdbc;
		this.issuer = issuer;
		this.clock = clock;
	}

	/** An account with a first name, as a Talent needs before saving a profile. */
	public TestAccount create() {
		TestAccount account = createWithoutName();
		setFirstName(account.id(), "Tester");
		return account;
	}

	public TestAccount createWithoutName() {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO identity.account (id) VALUES (:id)").param("id", id).update();
		// a verified number, like every real account has: tests of the login flow count accounts without one as orphans
		byte[] hash = new byte[32];
		RANDOM.nextBytes(hash);
		String mobile = "+9199" + String.format("%08d", RANDOM.nextInt(100_000_000));
		jdbc.sql("INSERT INTO identity.mobile_identity (account_id, mobile, mobile_hash, verified_at) VALUES (:id, :mobile, :hash, now())")
				.param("id", id).param("mobile", mobile).param("hash", hash).update();
		return new TestAccount(id, token(id));
	}

	/** A token for any account ID, existing or not. */
	public String token(UUID accountId) {
		return issuer.issue(accountId, UUID.randomUUID(), clock.instant()).token();
	}

	public void setFirstName(UUID accountId, String firstName) {
		jdbc.sql("UPDATE identity.account SET first_name = :n WHERE id = :id").param("n", firstName).param("id", accountId).update();
	}

}
