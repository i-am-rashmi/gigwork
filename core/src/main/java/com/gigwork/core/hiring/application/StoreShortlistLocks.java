package com.gigwork.core.hiring.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.gigwork.core.hiring.persistence.ShortlistStore;

@Component
class StoreShortlistLocks implements ShortlistLocks {

	private final ShortlistStore store;

	StoreShortlistLocks(ShortlistStore store) {
		this.store = store;
	}

	@Override
	public void lock(UUID posterId) {
		store.lockPoster(posterId);
	}

}
