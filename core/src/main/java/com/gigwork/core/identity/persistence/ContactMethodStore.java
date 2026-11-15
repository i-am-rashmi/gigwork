package com.gigwork.core.identity.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Contact methods and the verified details they point at. Native SQL: one upsert, and a read of the mobile number. */
@Repository
public class ContactMethodStore {

	/** The chosen channels. */
	public record Chosen(boolean whatsapp, boolean call, boolean email) {

		public boolean any() {
			return whatsapp || call || email;
		}

	}

	private final JdbcClient jdbc;

	ContactMethodStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void save(UUID accountId, Chosen chosen, Instant now) {
		jdbc.sql("""
				INSERT INTO identity.contact_method (account_id, whatsapp, call, email, updated_at)
				VALUES (:id, :w, :c, :e, :now)
				ON CONFLICT (account_id) DO UPDATE
				SET whatsapp = EXCLUDED.whatsapp, call = EXCLUDED.call, email = EXCLUDED.email, updated_at = EXCLUDED.updated_at""")
				.param("id", accountId).param("w", chosen.whatsapp()).param("c", chosen.call()).param("e", chosen.email())
				.param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).update();
	}

	public Optional<Chosen> find(UUID accountId) {
		return jdbc.sql("SELECT whatsapp, call, email FROM identity.contact_method WHERE account_id = :id")
				.param("id", accountId)
				.query((rs, i) -> new Chosen(rs.getBoolean("whatsapp"), rs.getBoolean("call"), rs.getBoolean("email")))
				.optional();
	}

	/** The account's verified mobile number, or empty if it has none. */
	public Optional<String> verifiedMobile(UUID accountId) {
		return jdbc.sql("SELECT mobile FROM identity.mobile_identity WHERE account_id = :id").param("id", accountId)
				.query(String.class).optional();
	}

}
