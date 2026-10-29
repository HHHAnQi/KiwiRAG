package com.xxx.ragdoc.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xxx.ragdoc.application.chat.command.ChatResult;
import com.xxx.ragdoc.application.chat.conversation.ConversationContext;
import com.xxx.ragdoc.application.chat.pipeline.OnlineExecutionProperties;
import com.xxx.ragdoc.application.chat.pipeline.TokenBudgetContextBuilder;
import com.xxx.ragdoc.application.chat.port.TraceObserver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatContextAssemblerTest {

    private final ConversationContextCoordinator conversationCoordinator =
            mock(ConversationContextCoordinator.class);
    private final ChatMessages chatMessages = mock(ChatMessages.class);
    private final TraceObserver traceObserver = mock(TraceObserver.class);
    private final OnlineExecutionProperties properties = new OnlineExecutionProperties();
    private ChatContextAssembler assembler;

    @BeforeEach
    void setUp() {
        properties.setContextTokenBudget(3000);
        properties.setContextMaxChars(3800);
        assembler =
                new ChatContextAssembler(
                        conversationCoordinator,
                        chatMessages,
                        new TokenBudgetContextBuilder(),
                        properties,
                        traceObserver);
    }

    @Test
    void reordersContextsAndCitationsTogether() {
        when(chatMessages.isLitmReorder()).thenReturn(true);
        List<ChatResult.Citation> citations =
                List.of(
                        citation(1L, "one"),
                        citation(2L, "two"),
                        citation(3L, "three"),
                        citation(4L, "four"));

        ChatContextAssembler.Result result =
                assembler.assemble(null, false, citations, "trace", "observer");

        assertThat(result.citations())
                .extracting(ChatResult.Citation::chunkId)
                .containsExactly(1L, 3L, 4L, 2L);
        assertThat(result.context()).containsExactly("one", "three", "four", "two");
    }

    @Test
    void budgetTruncationRemovesInvisibleCitations() {
        properties.setContextMaxChars(12);
        List<ChatResult.Citation> citations =
                List.of(
                        citation(1L, "1234567890"),
                        citation(2L, "abcdefghij"),
                        citation(3L, "unused"));

        ChatContextAssembler.Result result =
                assembler.assemble(null, false, citations, "trace", "observer");

        assertThat(result.context()).hasSize(2);
        assertThat(result.citations())
                .extracting(ChatResult.Citation::chunkId)
                .containsExactly(1L, 2L);
        verify(traceObserver)
                .observe(
                        org.mockito.ArgumentMatchers.eq("observer"),
                        org.mockito.ArgumentMatchers.eq(TraceObserver.ObservationType.DECISION),
                        org.mockito.ArgumentMatchers.eq("context.token_budget"),
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        any());
    }

    @Test
    void historyDoesNotConsumeCitationNumbering() {
        ConversationContext context = ConversationContext.empty("conv-1");
        when(conversationCoordinator.buildHistoryBlock(context, false))
                .thenReturn("<<CONVERSATION_HISTORY>> history");

        ChatContextAssembler.Result result =
                assembler.assemble(
                        context, false, List.of(citation(7L, "evidence")), "trace", "observer");

        assertThat(result.context()).hasSize(2);
        assertThat(result.citations()).extracting(ChatResult.Citation::chunkId).containsExactly(7L);
    }

    private static ChatResult.Citation citation(long chunkId, String context) {
        return new ChatResult.Citation(chunkId, 1L, 0, context, context, List.of(), null);
    }
}
