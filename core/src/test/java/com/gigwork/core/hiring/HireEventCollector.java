package com.gigwork.core.hiring;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.event.EventListener;

/** Collects the hire events from any thread (the test's ApplicationEvents only sees the test thread). */
@TestComponent
public class HireEventCollector {

	private final List<Object> events = new CopyOnWriteArrayList<>();

	@EventListener({ HireStarted.class, HireConfirmed.class, HireDeclined.class, HireWithdrawn.class, HireLapsed.class, HireCancelled.class })
	void collect(Object event) {
		events.add(event);
	}

	public <T> List<T> of(Class<T> type) {
		return events.stream().filter(type::isInstance).map(type::cast).toList();
	}

	public void clear() {
		events.clear();
	}

}
