package com.gigwork.core.platform.config;

import java.time.Duration;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.gigwork.core.platform.JobRuns;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
class MeteredJobRuns implements JobRuns {

	private static final Logger log = LoggerFactory.getLogger(MeteredJobRuns.class);

	private final MeterRegistry meters;

	MeteredJobRuns(MeterRegistry meters) {
		this.meters = meters;
	}

	@Override
	public long record(String job, LongSupplier work) {
		Timer.Sample sample = Timer.start(meters);
		String outcome = "failure";
		long rows = 0;
		try {
			rows = work.getAsLong();
			outcome = "success";
			return rows;
		}
		finally {
			long nanos = sample.stop(Timer.builder("gigwork.job.duration").tag("job", job).tag("outcome", outcome)
					.register(meters));
			meters.counter("gigwork.job.rows", "job", job).increment(rows);
			log.info("Job {} {}: {} rows changed in {} ms", job, outcome, rows, Duration.ofNanos(nanos).toMillis());
		}
	}

}
