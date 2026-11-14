package com.gigwork.core.reference.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads the seed files the way a person would, to compare with what the database holds. */
public final class SeedFixtures {

	/** The tests run in core/; the single source of seed data is ../infra/seed. */
	public static final Path SEED_DIR = Path.of("..", "infra", "seed");

	private SeedFixtures() {
	}

	/** Number of data rows (lines minus the header) of a seed CSV. Valid for files without multi-line fields. */
	public static int rows(String relativePath) {
		try {
			List<String> lines = Files.readAllLines(SEED_DIR.resolve(relativePath));
			return (int) lines.stream().skip(1).filter(l -> !l.isBlank()).count();
		}
		catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	public static int zones() {
		try {
			String json = Files.readString(SEED_DIR.resolve("bengaluru/zones.geojson"));
			return json.split("\"type\":\"Feature\"", -1).length - 1;
		}
		catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

}
