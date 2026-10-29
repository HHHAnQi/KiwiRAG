package com.xxx.ragdoc.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xxx.ragdoc.application.chat.command.ChatResult;
import com.xxx.ragdoc.application.chat.conversation.ConversationContext;
import com.xxx.ragdoc.application.chat.conversation.port.ConversationStore;
import com.xxx.ragdoc.application.chat.conversation.port.HistoryCompressorPort;
import com.xxx.ragdoc.domain.shared.StateHint;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConversationHistoryWriterTest {

    private final ConversationStore store = mock(ConversationStore.class);
    private final HistoryCompressorPort compressor = mock(HistoryCompressorPort.class);
    private final ConversationProperties properties = mock(ConversationProperties.class);

    @Test
    void savesSuccessfulTurnAndCompressesAtConfiguredThreshold() {
        ConversationHistoryWriter writer = configuredWriter();
        when(properties.getCompressThreshold()).thenReturn(2);
        ConversationContext context =
                ConversationContext.empty("conv-1")
                        .appendTurn(
                                new ConversationContext.Turn(
                                        "q1", "a1", List.of(1L), StateHint.OK, Instant.now()));

        writer.recordSuccessfulTurn("conv-1", context, "q2", "a2", List.of(citation(2L)));

        ArgumentCaptor<ConversationContext> saved =
                ArgumentCaptor.forClass(ConversationContext.class);
        verify(store).save(saved.capture());
        assertThat(saved.getValue().recentTurns()).hasSize(2);
        assertThat(saved.getValue().recentTurns().get(1).citedChunkIds()).containsExactly(2L);
        verify(compressor).compress("conv-1");
    }

    @Test
    void storeFailureIsBestEffortAndDoesNotTriggerCompression() {
        ConversationHistoryWriter writer = configuredWriter();
        doThrow(new IllegalStateException("redis unavailable"))
                .when(store)
                .save(org.mockito.ArgumentMatchers.any());

        assertThatCode(
                        () ->
                                writer.recordSuccessfulTurn(
                                        "conv-1",
                                        ConversationContext.empty("conv-1"),
                                        "q",
                                        "a",
                                        List.of()))
                .doesNotThrowAnyException();
        verify(compressor, never()).compress("conv-1");
    }

    private ConversationHistoryWriter configuredWriter() {
        ConversationHistoryWriter writer = new ConversationHistoryWriter();
        writer.setStore(store);
        writer.setCompressor(compressor);
        writer.setProperties(properties);
        return writer;
    }

    private static ChatResult.Citation citation(long chunkId) {
        return new ChatResult.Citation(chunkId, 1L, 0, "snippet", "context", List.of(), null);
    }
}
