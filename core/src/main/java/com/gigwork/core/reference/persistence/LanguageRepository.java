package com.gigwork.core.reference.persistence;

import java.util.List;

import org.springframework.data.repository.Repository;

interface LanguageRepository extends Repository<Language, String> {

	List<Language> findAll();

}
