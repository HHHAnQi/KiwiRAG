package com.xxx.ragdoc.application.chat;

import com.xxx.ragdoc.application.chat.command.ChatCommand;
import com.xxx.ragdoc.application.chat.command.ChatResult;
import com.xxx.ragdoc.application.chat.evidence.EvidenceSnapshot;
import com.xxx.ragdoc.application.chat.port.ChatTracesRepository;
import com.xxx.ragdoc.application.chat.verification.VerificationResult;
import com.xxx.ragdoc.application.metrics.MetricsPort;
import com.xxx.ragdoc.domain.chat.ChatTrace;
import com.xxx.ragdoc.domain.shared.StateHint;
import com.xxx.ragdoc.domain.shared.TraceId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Builds final chat responses and persists the audit trace for sync and streaming paths. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatResultFinalizer {

    private final ChatTracesRepository tracesRepository;
    private final MetricsPort metrics;

    public ChatResult finishAndRecord(
            ChatCommand command,
            TraceId traceId,
            StateHint hint,
            String answer,
            List<ChatResult.Citation> citations,
            long startedAtMillis,
            VerificationResult verification,
            EvidenceSnapshot evidenceSnapshot) {
        persist(command, traceId, answer, hint, evidenceSnapshot);
        metrics.recordChatTotal(System.currentTimeMillis() - startedAtMillis, metricOutcome(hint));
        return new ChatResult(answer, citations, hint, traceId, verification, evidenceSnapshot);
    }

    /** Streaming persistence is best-effort and must never terminate a client response. */
    public void persistStreamTrace(
            ChatCommand command,
            TraceId traceId,
            String answer,
            StateHint hint,
            EvidenceSnapshot evidenceSnapshot) {
        try {
            persist(command, traceId, answer, hint, evidenceSnapshot);
            log.info("chat.stream_end trace_id={}, state_hint={}", traceId.value(), hint);
        } catch (Exception exception) {
            log.warn(
                    "chat.stream_persist_failed trace_id={}, err={}",
                    traceId.value(),
                    exception.getMessage());
        }
    }

    private void persist(
            ChatCommand command,
            TraceId traceId,
            String answer,
            StateHint hint,
            EvidenceSnapshot evidenceSnapshot) {
        ChatTrace trace =
                new ChatTrace(
                        traceId,
                        sha256(command.query()),
                        command.query().length(),
                        answer.length(),
                        hint,
                        null);
        if (evidenceSnapshot == null) {
            tracesRepository.save(trace);
        } else {
            tracesRepository.save(trace, evidenceSnapshot);
        }
        log.info("chat.end trace_id={}, state_hint={}", traceId.value(), hint);
    }

    private static String metricOutcome(StateHint hint) {
        return switch (hint) {
            case OK -> "ok";
            case NO_RECALL, EMPTY_KB -> "skipped";
            case REFUSED -> "refused";
            case LLM_DEGRADED, VERIFY_FAILED -> "degraded";
        };
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
