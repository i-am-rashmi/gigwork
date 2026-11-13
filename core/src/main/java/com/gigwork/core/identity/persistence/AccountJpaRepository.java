package com.gigwork.core.identity.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccountJpaRepository extends JpaRepository<Account, UUID> {

	@Query("select a from Account a join MobileIdentity m on m.accountId = a.id where m.mobileHash = :hash")
	Optional<Account> findByMobileHash(@Param("hash") byte[] hash);

	/**
	 * One statement: the unique mobile_hash constraint decides a race. Returns 0 if the number already has an
	 * account. Flushes the pending account insert first (foreign key) and clears the persistence context.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			INSERT INTO identity.mobile_identity (account_id, mobile, mobile_hash, verified_at)
			VALUES (:id, :mobile, :hash, :now)
			ON CONFLICT (mobile_hash) DO NOTHING""")
	int insertMobileIdentityIfAbsent(@Param("id") UUID accountId, @Param("mobile") String mobile,
			@Param("hash") byte[] mobileHash, @Param("now") Instant now);

}
