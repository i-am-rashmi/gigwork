package com.gigwork.core.identity.support;

import java.time.Instant;
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

	private final JdbcClient jdbc;
	private final AccessTokenIssuer issuer;

	public TestAccounts(JdbcClient jdbc, AccessTokenIssuer issuer) {
		this.jdbc = jdbc;
		this.issuer = issuer;
	}

	public TestAccount create() {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO identity.account (id) VALUES (:id)").param("id", id).update();
		return new TestAccount(id, token(id));
	}

	/** A token for any account ID, existing or not. */
	public String token(UUID accountId) {
		return issuer.issue(accountId, UUID.randomUUID(), Instant.now()).token();
	}

	public void setFirstName(UUID accountId, String firstName) {
		jdbc.sql("UPDATE identity.account SET first_name = :n WHERE id = :id").param("n", firstName).param("id", accountId).update();
	}

}
