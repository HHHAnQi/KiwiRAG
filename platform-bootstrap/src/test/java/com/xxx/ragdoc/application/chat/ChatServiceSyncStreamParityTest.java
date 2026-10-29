package com.xxx.ragdoc.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.xxx.ragdoc.application.chat.command.ChatCommand;
import com.xxx.ragdoc.application.chat.command.ChatResult;
import com.xxx.ragdoc.application.chat.command.ChatStreamEvent;
import com.xxx.ragdoc.application.chat.command.ChatStreamEvent.DoneEvent;
import com.xxx.ragdoc.application.chat.pipeline.OnlineExecutionProperties;
import com.xxx.ragdoc.application.chat.pipeline.TokenBudgetContextBuilder;
import com.xxx.ragdoc.application.chat.port.ChatClient;
import com.xxx.ragdoc.application.chat.port.ChatTracesRepository;
import com.xxx.ragdoc.application.chat.port.TraceObserver;
import com.xxx.ragdoc.application.document.port.DocumentRepository;
import com.xxx.ragdoc.application.metrics.MetricsPort;
import com.xxx.ragdoc.domain.document.DocumentStatus;
import com.xxx.ragdoc.domain.shared.StateHint;
import com.xxx.ragdoc.domain.shared.TraceId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

/** Contract gate: synchronous and SSE entry points must resolve to the same terminal state. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceSyncStreamParityTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private ChatTracesRepository tracesRepository;
    @Mock private ChatMessages chatMessages;
    @Mock private RetrieveService retrieveService;
    @Mock private ChatClient chatClient;
    @Mock private TraceObserver traceObserver;
    @Mock private MetricsPort metrics;

    @InjectMocks private ChatService chatService;

    private static final TraceId TRACE_ID = new TraceId("parity-trace");
    private static final ChatCommand COMMAND = new ChatCommand("Sentinel?", null, 5);

    @BeforeEach
    void setUp() {
        when(chatMessages.getEmptyKbMessage()).thenReturn("empty");
        when(chatMessages.getNoRecallMessage()).thenReturn("no recall");
        when(chatMessages.getLlmDegradedMessage()).thenReturn("degraded:");
        lenient().when(traceObserver.startTrace(any(), any(), any())).thenReturn(TRACE_ID.value());

        ConversationContextCoordinator conversationCoordinator =
                new ConversationContextCoordinator();
        ReflectionTestUtils.setField(
                chatService, "conversationContextCoordinator", conversationCoordinator);
        ReflectionTestUtils.setField(
                chatService, "conversationHistoryWriter", new ConversationHistoryWriter());
        ReflectionTestUtils.setField(
                chatService,
                "chatResultFinalizer",
                new ChatResultFinalizer(tracesRepository, metrics));
        ReflectionTestUtils.setField(
                chatService,
                "chatContextAssembler",
                new ChatContextAssembler(
                        conversationCoordinator,
                        chatMessages,
                        new TokenBudgetContextBuilder(),
                        new OnlineExecutionProperties(),
                        traceObserver));
    }

    @Test
    @DisplayName("EMPTY_KB terminal state is identical")
    void emptyKnowledgeBaseParity() {
        when(documentRepository.countByStatus(DocumentStatus.INDEXED)).thenReturn(0L);

        assertParity(StateHint.EMPTY_KB);
    }

    @Test
    @DisplayName("NO_RECALL terminal state is identical")
    void noRecallParity() {
        when(documentRepository.countByStatus(DocumentStatus.INDEXED)).thenReturn(1L);
        when(retrieveService.retrieve(any())).thenReturn(RetrieveService.RetrieveResult.empty());

        assertParity(StateHint.NO_RECALL);
    }

    @Test
    @DisplayName("LLM failure terminal state is identical")
    void llmFailureParity() throws Exception {
        mockRecall();
        when(chatClient.chat(any(), any())).thenThrow(new RuntimeException("timeout"));
        when(chatClient.chatStream(any(), any()))
                .thenReturn(Flux.error(new RuntimeException("timeout")));

        assertParity(StateHint.LLM_DEGRADED);
    }

    @Test
    @DisplayName("successful answer state and visible citations are identical")
    void successParity() throws Exception {
        mockRecall();
        when(chatClient.chat(any(), any())).thenReturn("answer");
        when(chatClient.chatStream(any(), any())).thenReturn(Flux.just("answer"));

        ChatResult synchronous = chatService.chat(COMMAND, TRACE_ID);
        List<ChatStreamEvent> events =
                chatService.chatStream(COMMAND, TRACE_ID).collectList().block();
        DoneEvent terminal = terminal(events);
        ChatStreamEvent.CitationsEvent streamedCitations =
                events.stream()
                        .filter(ChatStreamEvent.CitationsEvent.class::isInstance)
                        .map(ChatStreamEvent.CitationsEvent.class::cast)
                        .findFirst()
                        .orElseThrow();

        assertThat(synchronous.stateHint()).isEqualTo(StateHint.OK);
        assertThat(terminal.stateHint()).isEqualTo(StateHint.OK.name());
        assertThat(streamedCitations.citations())
                .extracting(ChatResult.Citation::chunkId)
                .containsExactlyElementsOf(
                        synchronous.citations().stream()
                                .map(ChatResult.Citation::chunkId)
                                .toList());
    }

    private void assertParity(StateHint expected) {
        ChatResult synchronous = chatService.chat(COMMAND, TRACE_ID);
        DoneEvent streamed =
                terminal(chatService.chatStream(COMMAND, TRACE_ID).collectList().block());

        assertThat(synchronous.stateHint()).isEqualTo(expected);
        assertThat(streamed.stateHint()).isEqualTo(expected.name());
    }

    private void mockRecall() {
        when(documentRepository.countByStatus(DocumentStatus.INDEXED)).thenReturn(1L);
        when(retrieveService.retrieve(any()))
                .thenReturn(
                        new RetrieveService.RetrieveResult(
                                List.of(
                                        new RetrieveService.Citation(
                                                19L,
                                                6L,
                                                0,
                                                "snippet",
                                                "full context",
                                                0.9f,
                                                List.of("section"))),
                                "not_enabled",
                                0.9f,
                                0f));
    }

    private static DoneEvent terminal(List<ChatStreamEvent> events) {
        assertThat(events).isNotNull();
        List<DoneEvent> terminals =
                events.stream()
                        .filter(DoneEvent.class::isInstance)
                        .map(DoneEvent.class::cast)
                        .toList();
        assertThat(terminals).hasSize(1);
        return terminals.get(0);
    }
}
