package com.xxx.ragdoc.application.chat;

import com.xxx.ragdoc.application.chat.conversation.ContextualizeResult;
import com.xxx.ragdoc.application.chat.conversation.ConversationContext;
import com.xxx.ragdoc.application.chat.conversation.port.ConversationStore;
import com.xxx.ragdoc.application.chat.conversation.port.PromptAssemblerPort;
import com.xxx.ragdoc.application.chat.conversation.port.QueryContextualizerPort;
import com.xxx.ragdoc.application.chat.conversation.port.TopicShiftDetectorPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Loads conversation memory and derives the effective retrieval query for every chat path. */
@Slf4j
@Component
public class ConversationContextCoordinator {

    private ConversationStore store;
    private QueryContextualizerPort contextualizer;
    private PromptAssemblerPort promptAssembler;
    private TopicShiftDetectorPort topicShiftDetector;

    @Autowired(required = false)
    public void setCoreDependencies(
            ConversationStore store,
            QueryContextualizerPort contextualizer,
            PromptAssemblerPort promptAssembler) {
        this.store = store;
        this.contextualizer = contextualizer;
        this.promptAssembler = promptAssembler;
        log.info(
                "chat.multi_turn_enabled store={}, rewriter={}, promptAssembler={}",
                store == null ? "none" : store.getClass().getSimpleName(),
                contextualizer == null ? "none" : "QueryContextualizer",
                promptAssembler == null ? "none" : "PromptAssembler");
    }

    @Autowired(required = false)
    public void setTopicShiftDetector(TopicShiftDetectorPort topicShiftDetector) {
        this.topicShiftDetector = topicShiftDetector;
        log.info("chat.topic_shift_detector_enabled={}", topicShiftDetector != null);
    }

    public boolean isEnabled() {
        return store != null && contextualizer != null && promptAssembler != null;
    }

    public PreparedContext prepare(String query, String conversationId) {
        if (!isEnabled() || conversationId == null || conversationId.isBlank()) {
            return new PreparedContext(null, false, query, null);
        }

        ConversationContext context =
                store.findById(conversationId)
                        .orElseGet(() -> ConversationContext.empty(conversationId));
        boolean topicShift =
                topicShiftDetector != null && topicShiftDetector.isTopicShift(query, context);
        ContextualizeResult rewrite = null;
        String retrieveQuery = query;
        if (context.isEnabled() && !topicShift) {
            rewrite = contextualizer.contextualize(query, context.recentTurns());
            retrieveQuery = rewrite.retrieveQuery();
        }
        return new PreparedContext(context, topicShift, retrieveQuery, rewrite);
    }

    public String buildHistoryBlock(ConversationContext context, boolean topicShift) {
        if (context == null || !context.isEnabled() || promptAssembler == null) {
            return null;
        }
        return promptAssembler.buildHistoryBlock(context, topicShift);
    }

    public record PreparedContext(
            ConversationContext context,
            boolean topicShift,
            String retrieveQuery,
            ContextualizeResult rewriteResult) {}
}
