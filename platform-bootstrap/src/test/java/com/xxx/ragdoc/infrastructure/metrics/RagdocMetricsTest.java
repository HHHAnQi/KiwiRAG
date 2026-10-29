package com.xxx.ragdoc.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class RagdocMetricsTest {

    @Test
    void recordsZeroRecallAsAnAlertableRequestOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RagdocMetrics metrics = new RagdocMetrics(registry);

        metrics.recordRetrieveRecall(0);
        metrics.recordRetrieveRecall(3);

        assertThat(registry.counter("ragdoc.retrieve.requests", "outcome", "zero").count())
                .isEqualTo(1);
        assertThat(registry.counter("ragdoc.retrieve.requests", "outcome", "hit").count())
                .isEqualTo(1);
        assertThat(registry.summary("ragdoc.retrieve.recall_count").count()).isEqualTo(2);
    }
}
