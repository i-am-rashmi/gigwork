package com.gigwork.core.identity.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs tasks on separate threads and releases them together, to provoke real races. */
public final class Concurrently {

	private Concurrently() {
	}

	/** Runs every task at the same moment and returns the results in task order; any failure fails the test. */
	public static <T> List<T> runAll(List<Callable<T>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch ready = new CountDownLatch(tasks.size());
			CountDownLatch go = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return task.call();
				}));
			}
			ready.await(30, TimeUnit.SECONDS);
			go.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

	/** The same task {@code count} times. */
	public static <T> List<T> runAll(int count, Callable<T> task) throws Exception {
		List<Callable<T>> tasks = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			tasks.add(task);
		}
		return runAll(tasks);
	}

}
