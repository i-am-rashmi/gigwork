package com.gigwork.core.profile.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

interface TalentProfileJpaRepository extends Repository<TalentProfile, UUID> {

	Optional<TalentProfile> findById(UUID accountId);

}
