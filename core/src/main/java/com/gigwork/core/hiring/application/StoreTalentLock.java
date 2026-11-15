package com.gigwork.core.hiring.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.gigwork.core.hiring.persistence.ApplicationStore;

@Component
class StoreTalentLock implements TalentLock {

	private final ApplicationStore store;

	StoreTalentLock(ApplicationStore store) {
		this.store = store;
	}

	@Override
	public void lock(UUID talentId) {
		store.lockTalent(talentId);
	}

}
