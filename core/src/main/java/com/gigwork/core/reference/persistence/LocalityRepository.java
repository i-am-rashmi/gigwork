package com.gigwork.core.reference.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

interface LocalityRepository extends Repository<Locality, UUID> {

	List<Locality> findAll();

}
