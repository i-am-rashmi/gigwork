package com.gigwork.core.reference.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

interface CategoryRepository extends Repository<Category, UUID> {

	List<Category> findAll();

}
