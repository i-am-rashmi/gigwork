package com.gigwork.core.reference.seed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * A <em>repeatable</em> Flyway migration that loads seed files. Its checksum is a hash of the bytes of the files it
 * reads, so Flyway runs it again, at the next start, whenever one of them has changed (and never otherwise). Until
 * Admin editing exists (Phase 3), editing the seed files is how reference data changes. The load is idempotent:
 * every row is upserted by its stable key, and rows that disappeared from the files are retired, never deleted.
 */
public final class SeedMigration implements JavaMigration {

	private final SeedManifest.Entry entry;
	private final SeedFiles files;

	public SeedMigration(SeedManifest.Entry entry, SeedFiles files) {
		this.entry = entry;
		this.files = files;
	}

	/** Null: repeatable migrations have no version (they run after all versioned ones, ordered by description). */
	@Override
	public MigrationVersion getVersion() {
		return null;
	}

	@Override
	public String getDescription() {
		return entry.description();
	}

	@Override
	public Integer getChecksum() {
		MessageDigest sha256 = sha256();
		for (Map.Entry<String, byte[]> input : inputs().entrySet()) {
			sha256.update(input.getKey().getBytes(StandardCharsets.UTF_8));
			sha256.update((byte) 0);
			sha256.update(input.getValue());
			sha256.update((byte) 0);
		}
		byte[] hash = sha256.digest();
		return ((hash[0] & 0xff) << 24) | ((hash[1] & 0xff) << 16) | ((hash[2] & 0xff) << 8) | (hash[3] & 0xff);
	}

	@Override
	public boolean canExecuteInTransaction() {
		return true;
	}

	@Override
	public void migrate(Context context) {
		JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
		switch (entry.kind()) {
			case REFERENCE -> ReferenceSeedLoader.loadReference(jdbc, files);
			case CITY -> ReferenceSeedLoader.loadCity(jdbc, files, entry.folder());
		}
	}

	/** The named inputs of this migration, in a fixed order, with line endings normalised (git may change them). */
	private java.util.SequencedMap<String, byte[]> inputs() {
		java.util.SequencedMap<String, byte[]> inputs = new java.util.LinkedHashMap<>();
		switch (entry.kind()) {
			case REFERENCE -> {
				for (String name : List.of("categories.csv", "specialisations.csv", "languages.csv")) {
					inputs.put(name, normalise(files.read(name)));
				}
			}
			case CITY -> {
				String slug = entry.folder();
				inputs.put("cities.csv#" + slug, ReferenceSeedLoader.cityRow(files, slug).canonical());
				for (String name : List.of("zones.geojson", "localities.csv")) {
					inputs.put(slug + "/" + name, normalise(files.read(slug + "/" + name)));
				}
			}
		}
		return inputs;
	}

	private static byte[] normalise(byte[] bytes) {
		return new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n')
				.getBytes(StandardCharsets.UTF_8);
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new SeedDataException("SHA-256 is not available", e);
		}
	}

	@Override
	public String toString() {
		return "SeedMigration " + entry;
	}

	/** One migration per manifest line. */
	public static List<JavaMigration> fromManifest(SeedFiles files) {
		List<JavaMigration> migrations = new ArrayList<>();
		for (SeedManifest.Entry entry : SeedManifest.read()) {
			migrations.add(new SeedMigration(entry, files));
		}
		return migrations;
	}

}
