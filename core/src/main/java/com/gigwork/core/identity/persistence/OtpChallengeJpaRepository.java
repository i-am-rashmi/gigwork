package com.gigwork.core.identity.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OtpChallengeJpaRepository extends JpaRepository<OtpChallenge, UUID> {

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			UPDATE identity.otp_challenge SET used_at = :now WHERE target_hash = :target AND used_at IS NULL""")
	int supersedeLive(@Param("target") byte[] targetHash, @Param("now") Instant now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			UPDATE identity.otp_challenge SET used_at = :now WHERE id = :id AND used_at IS NULL""")
	int markUsed(@Param("id") UUID id, @Param("now") Instant now);

}
