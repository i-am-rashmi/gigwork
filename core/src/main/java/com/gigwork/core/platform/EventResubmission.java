package com.gigwork.core.platform;

/**
 * Delivers again the events whose listener did not complete (ADR-0004, ADR-0010). Events between modules are written
 * with the change that caused them; a listener that failed or whose instance stopped leaves its event incomplete,
 * and this retries it. Listeners must therefore be idempotent: the same event can arrive more than once.
 */
public interface EventResubmission {

	/** One run: resubmits every incomplete publication older than the configured age. Returns how many were due. */
	long run();

}
