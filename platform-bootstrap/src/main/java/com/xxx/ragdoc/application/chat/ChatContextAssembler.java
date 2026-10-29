package com.xxx.ragdoc.application.chat;

import com.xxx.ragdoc.application.chat.command.ChatResult;
import com.xxx.ragdoc.application.chat.conversation.ConversationContext;
import com.xxx.ragdoc.application.chat.pipeline.OnlineExecutionProperties;
import com.xxx.ragdoc.application.chat.pipeline.TokenBudgetContextBuilder;
import com.xxx.ragdoc.application.chat.port.TraceObserver;
import com.xxx.ragdoc.application.chat.router.OnlineReasonCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Builds the exact context visible to the LLM and keeps returned citations aligned with it. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatContextAssembler {

    private final ConversationContextCoordinator conversationCoordinator;
    private final ChatMessages chatMessages;
    private final TokenBudgetContextBuilder budgetBuilder;
    private final OnlineExecutionProperties properties;
    private final TraceObserver traceObserver;

    public Result assemble(
            ConversationContext conversation,
            boolean topicShift,
            List<ChatResult.Citation> retrievedCitations,
            String traceId,
            String observerTraceId) {
        String historyBlock = conversationCoordinator.buildHistoryBlock(conversation, topicShift);
        boolean hasHistory = historyBlock != null && !historyBlock.isBlank();

        List<ChatResult.Citation> working = new ArrayList<>(retrievedCitations);
        if (chatMessages.isLitmReorder() && working.size() > 2) {
            working = reorderCitations(working);
        }

        List<String> candidates = new ArrayList<>();
        if (hasHistory) {
            candidates.add(historyBlock.trim());
        }
        for (ChatResult.Citation citation : working) {
            candidates.add(citation.llmContext());
        }

        TokenBudgetContextBuilder.BuildResult built =
                budgetBuilder.build(
                        candidates,
                        properties.getContextTokenBudget(),
                        properties.getContextMaxChars());
        observeBudget(observerTraceId, built);

        int keptEvidence =
                hasHistory ? Math.max(0, built.context().size() - 1) : built.context().size();
        List<ChatResult.Citation> aligned =
                List.copyOf(working.subList(0, Math.min(keptEvidence, working.size())));
        if (aligned.size() < working.size()) {
            log.info(
                    "chat.citations_aligned_to_budget trace_id={}, kept={}/{}",
                    traceId,
                    aligned.size(),
                    working.size());
        }
        if (built.truncated()) {
            log.info(
                    "chat.context_truncated trace_id={}, estimated_tokens={}, budget={}",
                    traceId,
                    built.estimatedTokens(),
                    built.tokenBudget());
        }
        return new Result(built.context(), aligned);
    }

    static List<ChatResult.Citation> reorderCitations(List<ChatResult.Citation> sortedDesc) {
        if (sortedDesc == null || sortedDesc.size() <= 2) {
            return sortedDesc;
        }
        List<ChatResult.Citation> reordered = new ArrayList<>(sortedDesc.size());
        reordered.add(sortedDesc.get(0));
        ArrayDeque<ChatResult.Citation> middle = new ArrayDeque<>();
        for (int index = 2; index < sortedDesc.size(); index++) {
            if ((index & 1) == 0) {
                middle.addFirst(sortedDesc.get(index));
            } else {
                middle.addLast(sortedDesc.get(index));
            }
        }
        reordered.addAll(middle);
        reordered.add(sortedDesc.get(1));
        return reordered;
    }

    private void observeBudget(
            String observerTraceId, TokenBudgetContextBuilder.BuildResult built) {
        traceObserver.observe(
                observerTraceId,
                TraceObserver.ObservationType.DECISION,
                "context.token_budget",
                null,
                Map.of(
                        "estimated_tokens",
                        built.estimatedTokens(),
                        "token_budget",
                        built.tokenBudget(),
                        "truncated",
                        built.truncated(),
                        "reason_code",
                        built.truncated()
                                ? OnlineReasonCode.CONTEXT_TOKEN_BUDGET_APPLIED.name()
                                : "CONTEXT_WITHIN_BUDGET"),
                0,
                null);
    }

    public record Result(List<String> context, List<ChatResult.Citation> citations) {}
}
