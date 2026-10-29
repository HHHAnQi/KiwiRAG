package com.xxx.ragdoc.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.xxx.ragdoc.application.chat.command.ChatCommand;
import com.xxx.ragdoc.application.chat.command.ChatResult;
import com.xxx.ragdoc.application.chat.port.ChatTracesRepository;
import com.xxx.ragdoc.application.metrics.MetricsPort;
import com.xxx.ragdoc.domain.chat.ChatTrace;
import com.xxx.ragdoc.domain.shared.StateHint;
import com.xxx.ragdoc.domain.shared.TraceId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatResultFinalizerTest {

    private final ChatTracesRepository tracesRepository = mock(ChatTracesRepository.class);
    private final MetricsPort metrics = mock(MetricsPort.class);
    private final ChatResultFinalizer finalizer =
            new ChatResultFinalizer(tracesRepository, metrics);

    @Test
    void persistsHashedTraceBuildsResultAndRecordsOutcome() {
        TraceId traceId = new TraceId("trace-finalizer");
        ChatCommand command = new ChatCommand("private question", null, null);

        ChatResult result =
                finalizer.finishAndRecord(
                        command,
                        traceId,
                        StateHint.EMPTY_KB,
                        "empty",
                        List.of(),
                        System.currentTimeMillis(),
                        null,
                        null);

        ArgumentCaptor<ChatTrace> trace = ArgumentCaptor.forClass(ChatTrace.class);
        verify(tracesRepository).save(trace.capture());
        assertThat(trace.getValue().queryHash()).matches("^[a-f0-9]{64}$");
        assertThat(trace.getValue().queryHash()).doesNotContain("private question");
        verify(metrics)
                .recordChatTotal(any(Long.class), org.mockito.ArgumentMatchers.eq("skipped"));
        assertThat(result.stateHint()).isEqualTo(StateHint.EMPTY_KB);
        assertThat(result.traceId()).isEqualTo(traceId);
    }

    @Test
    void streamingPersistenceFailureDoesNotEscape() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(tracesRepository)
                .save(any(ChatTrace.class));

        assertThatCode(
                        () ->
                                finalizer.persistStreamTrace(
                                        new ChatCommand("q", null, null),
                                        new TraceId("trace-stream"),
                                        "partial",
                                        StateHint.LLM_DEGRADED,
                                        null))
                .doesNotThrowAnyException();
    }
}
