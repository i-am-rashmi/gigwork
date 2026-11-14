package com.gigwork.core.reference.seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads seed files from a directory (tests use a copy they can edit). */
public final class DirectorySeedFiles implements SeedFiles {

	private final Path root;

	public DirectorySeedFiles(Path root) {
		this.root = root;
	}

	@Override
	public byte[] read(String path) {
		try {
			return Files.readAllBytes(root.resolve(path));
		}
		catch (IOException e) {
			throw new SeedDataException("cannot read seed file " + root.resolve(path), e);
		}
	}

}
