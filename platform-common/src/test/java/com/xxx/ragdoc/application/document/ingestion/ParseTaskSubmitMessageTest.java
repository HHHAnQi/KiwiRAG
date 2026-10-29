package com.xxx.ragdoc.application.document.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParseTaskSubmitMessageTest {

    @Test
    void legacyConstructorDefaultsToFirstGeneration() {
        Instant submittedAt = Instant.parse("2026-08-02T10:15:30Z");

        var message = new ParseTaskSubmitMessage(123L, 678L, "a3f8", submittedAt);

        assertThat(message.taskId()).isEqualTo(123L);
        assertThat(message.documentId()).isEqualTo(678L);
        assertThat(message.contentHash()).isEqualTo("a3f8");
        assertThat(message.generation()).isEqualTo(1);
        assertThat(message.submittedAt()).isEqualTo(submittedAt);
    }
}
