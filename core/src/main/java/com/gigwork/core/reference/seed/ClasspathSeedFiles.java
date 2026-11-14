package com.gigwork.core.reference.seed;

import java.io.IOException;
import java.io.InputStream;

/** Reads {@code seed/<path>} from the classpath, where the build copies {@code infra/seed}. */
public final class ClasspathSeedFiles implements SeedFiles {

	@Override
	public byte[] read(String path) {
		String resource = "seed/" + path;
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new SeedDataException("seed file not found on the classpath: " + resource
						+ " (the build copies ../infra/seed into the jar)");
			}
			return in.readAllBytes();
		}
		catch (IOException e) {
			throw new SeedDataException("cannot read seed file " + resource, e);
		}
	}

}
