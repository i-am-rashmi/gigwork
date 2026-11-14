package com.gigwork.core.reference.seed;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Name-based UUIDs (RFC 4122 version 5, SHA-1): the same name gives the same UUID in every environment. */
final class UuidV5 {

	private UuidV5() {
	}

	static UUID of(UUID namespace, String name) {
		try {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			sha1.update(ByteBuffer.allocate(16).putLong(namespace.getMostSignificantBits())
					.putLong(namespace.getLeastSignificantBits()).array());
			sha1.update(name.getBytes(StandardCharsets.UTF_8));
			byte[] hash = sha1.digest();
			hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
			hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
			ByteBuffer buffer = ByteBuffer.wrap(hash);
			return new UUID(buffer.getLong(), buffer.getLong());
		}
		catch (NoSuchAlgorithmException e) {
			throw new SeedDataException("SHA-1 is not available", e);
		}
	}

}
