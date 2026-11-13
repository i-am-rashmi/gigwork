package com.gigwork.core.identity.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SessionJpaRepository extends JpaRepository<Session, UUID> {

}
