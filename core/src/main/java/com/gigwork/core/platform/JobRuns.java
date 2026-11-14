package com.gigwork.core.platform;

import java.util.function.LongSupplier;

/**
 * How a scheduled job reports a run (ADR-0010): it is logged, and the rows it changed and its duration are recorded
 * as metrics, so a job that stops working or falls behind can be alerted on (NFR-62).
 */
public interface JobRuns {

	/**
	 * Runs one job run and records it.
	 *
	 * @param job the job's name, used in the log line and as the metric tag (for example {@code gig-expiry})
	 * @param work does the job's work and returns how many rows it changed
	 * @return the number of rows changed
	 */
	long record(String job, LongSupplier work);

}
