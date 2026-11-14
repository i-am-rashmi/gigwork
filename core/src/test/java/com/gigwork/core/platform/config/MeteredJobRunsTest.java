package com.gigwork.core.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MeteredJobRunsTest {

	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
	private final MeteredJobRuns runs = new MeteredJobRuns(meters);

	@Test
	void aRunRecordsItsRowsAndDuration() {
		assertThat(runs.record("demo", () -> 7)).isEqualTo(7);
		assertThat(runs.record("demo", () -> 3)).isEqualTo(3);

		assertThat(meters.get("gigwork.job.rows").tag("job", "demo").counter().count()).isEqualTo(10);
		assertThat(meters.get("gigwork.job.duration").tag("job", "demo").tag("outcome", "success").timer().count())
				.isEqualTo(2);
	}

	@Test
	void aFailedRunIsRecordedAsAFailureAndTheErrorStillPropagates() {
		assertThatThrownBy(() -> runs.record("broken", () -> {
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class).hasMessage("boom");

		assertThat(meters.get("gigwork.job.duration").tag("job", "broken").tag("outcome", "failure").timer().count())
				.isEqualTo(1);
	}

}
