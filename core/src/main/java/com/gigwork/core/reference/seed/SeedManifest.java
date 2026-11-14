package com.gigwork.core.reference.seed;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The list of seed migrations ({@code db/seed-migrations.csv}): {@code version,description,kind,folder}. Adding a
 * city is one new line here (plus its folder under infra/seed and its row in cities.csv): no code changes.
 */
public final class SeedManifest {

	static final String RESOURCE = "db/seed-migrations.csv";

	public enum Kind {
		/** categories.csv, specialisations.csv and languages.csv in the seed root. */
		REFERENCE,
		/** One city: its row in cities.csv plus {@code <folder>/zones.geojson} and {@code <folder>/localities.csv}; the folder is the city slug. */
		CITY
	}

	public record Entry(String version, String description, Kind kind, String folder) {
	}

	private SeedManifest() {
	}

	public static List<Entry> read() {
		try (InputStream in = SeedManifest.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new SeedDataException("seed manifest not found on the classpath: " + RESOURCE);
			}
			return parse(in.readAllBytes());
		}
		catch (IOException e) {
			throw new SeedDataException("cannot read " + RESOURCE, e);
		}
	}

	static List<Entry> parse(byte[] bytes) {
		List<Entry> entries = new ArrayList<>();
		Set<String> versions = new HashSet<>();
		for (Map<String, String> row : Csv.parse(bytes, RESOURCE)) {
			String version = row.get("version").trim();
			Kind kind;
			try {
				kind = Kind.valueOf(row.get("kind").trim().toUpperCase());
			}
			catch (IllegalArgumentException e) {
				throw new SeedDataException(RESOURCE + ": unknown kind '" + row.get("kind") + "'", e);
			}
			if (!version.matches("[0-9]+(\\.[0-9]+)*") || !versions.add(version)) {
				throw new SeedDataException(RESOURCE + ": version '" + version + "' is invalid or repeated");
			}
			String folder = row.get("folder").trim();
			if (kind == Kind.CITY && !folder.matches("[a-z0-9][a-z0-9-]*")) {
				throw new SeedDataException(RESOURCE + ": a city folder must be its lowercase slug, got '" + folder + "'");
			}
			entries.add(new Entry(version, row.get("description").trim(), kind, folder));
		}
		return List.copyOf(entries);
	}

}
