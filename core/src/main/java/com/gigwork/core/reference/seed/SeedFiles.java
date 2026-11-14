package com.gigwork.core.reference.seed;

/** Where seed files are read from: the classpath in the application, a directory in tests. */
public interface SeedFiles {

	/** @param path relative to the seed root, e.g. {@code categories.csv} or {@code bengaluru/zones.geojson} */
	byte[] read(String path);

}
