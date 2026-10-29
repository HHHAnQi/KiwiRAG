package com.xxx.ragdoc.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xxx.ragdoc.application.chat.conversation.ContextualizeResult;
import com.xxx.ragdoc.application.chat.conversation.ConversationContext;
import com.xxx.ragdoc.application.chat.conversation.port.ConversationStore;
import com.xxx.ragdoc.application.chat.conversation.port.PromptAssemblerPort;
import com.xxx.ragdoc.application.chat.conversation.port.QueryContextualizerPort;
import com.xxx.ragdoc.application.chat.conversation.port.TopicShiftDetectorPort;
import com.xxx.ragdoc.domain.shared.StateHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationContextCoordinatorTest {

    private final ConversationStore store = mock(ConversationStore.class);
    private final QueryContextualizerPort contextualizer = mock(QueryContextualizerPort.class);
    private final PromptAssemblerPort promptAssembler = mock(PromptAssemblerPort.class);
    private final TopicShiftDetectorPort topicShiftDetector = mock(TopicShiftDetectorPort.class);
    private ConversationContextCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new ConversationContextCoordinator();
        coordinator.setCoreDependencies(store, contextualizer, promptAssembler);
        coordinator.setTopicShiftDetector(topicShiftDetector);
    }

    @Test
    void keepsOriginalQueryWhenConversationIsMissing() {
        when(store.findById("conv-1")).thenReturn(Optional.empty());

        ConversationContextCoordinator.PreparedContext prepared =
                coordinator.prepare("original", "conv-1");

        assertThat(prepared.context()).isNotNull();
        assertThat(prepared.retrieveQuery()).isEqualTo("original");
        verify(contextualizer, never())
                .contextualize(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rewritesQueryAndBuildsHistoryFromExistingContext() {
        ConversationContext context = contextWithTurn();
        when(store.findById("conv-1")).thenReturn(Optional.of(context));
        when(contextualizer.contextualize("it?", context.recentTurns()))
                .thenReturn(ContextualizeResult.success("it?", "Sentinel?", 12));
        when(promptAssembler.buildHistoryBlock(context, false)).thenReturn("history");

        ConversationContextCoordinator.PreparedContext prepared =
                coordinator.prepare("it?", "conv-1");

        assertThat(prepared.retrieveQuery()).isEqualTo("Sentinel?");
        assertThat(prepared.rewriteResult().outcome()).isEqualTo("ok");
        assertThat(coordinator.buildHistoryBlock(context, false)).isEqualTo("history");
    }

    @Test
    void topicShiftSkipsRewrite() {
        ConversationContext context = contextWithTurn();
        when(store.findById("conv-1")).thenReturn(Optional.of(context));
        when(topicShiftDetector.isTopicShift("new topic", context)).thenReturn(true);

        ConversationContextCoordinator.PreparedContext prepared =
                coordinator.prepare("new topic", "conv-1");

        assertThat(prepared.topicShift()).isTrue();
        assertThat(prepared.retrieveQuery()).isEqualTo("new topic");
        verify(contextualizer, never())
                .contextualize(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static ConversationContext contextWithTurn() {
        return ConversationContext.empty("conv-1")
                .appendTurn(
                        new ConversationContext.Turn(
                                "Sentinel",
                                "rate limiter",
                                List.of(1L),
                                StateHint.OK,
                                Instant.now()));
    }
}
