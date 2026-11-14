package com.gigwork.core.reference.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

interface SpecialisationRepository extends Repository<Specialisation, UUID> {

	List<Specialisation> findAll();

}
