package com.xxx.ragdoc.application.chat;

import com.xxx.ragdoc.application.chat.command.ChatResult;
import com.xxx.ragdoc.application.chat.verification.VerificationResult;
import com.xxx.ragdoc.application.chat.verification.port.CitationVerifierPort;
import com.xxx.ragdoc.domain.shared.StateHint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Applies citation verification, score annotation, and configured failure policy. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CitationVerificationCoordinator {

    private final CitationVerifierProperties properties;
    private final ChatMessages chatMessages;
    private CitationVerifierPort verifier;

    @Autowired(required = false)
    public void setVerifier(CitationVerifierPort verifier) {
        this.verifier = verifier;
        log.info("chat.citation_verifier_enabled={}", verifier != null);
    }

    public Result verify(String answer, List<ChatResult.Citation> citations) {
        if (verifier == null
                || !properties.isEnabled()
                || answer == null
                || citations == null
                || citations.isEmpty()) {
            return new Result(answer, StateHint.OK, citations, null);
        }

        List<CitationVerifierPort.Evidence> evidences =
                citations.stream()
                        .map(
                                citation ->
                                        new CitationVerifierPort.Evidence(
                                                citation.chunkId() == null
                                                        ? 0L
                                                        : citation.chunkId(),
                                                citation.llmContext() == null
                                                        ? citation.snippet()
                                                        : citation.llmContext()))
                        .toList();
        VerificationResult verification = verifier.verify(answer, evidences);
        List<ChatResult.Citation> annotated = annotateScores(citations, verification);

        if (verification.outcome() != VerificationResult.Outcome.FAIL) {
            return new Result(answer, StateHint.OK, annotated, verification);
        }
        if (properties.getOnFail() == CitationVerifierProperties.OnFail.WARN_ONLY) {
            VerificationResult warned =
                    new VerificationResult(
                            verification.outcome(),
                            verification.overallScore(),
                            verification.citationScores(),
                            "WARN_ONLY:fail_score=" + verification.overallScore());
            return new Result(answer, StateHint.OK, annotated, warned);
        }

        String resolvedAnswer =
                properties.getOnFail() == CitationVerifierProperties.OnFail.REFUSE
                        ? chatMessages.verifierRefusal(properties.getScoreThreshold())
                        : answer;
        return new Result(resolvedAnswer, StateHint.VERIFY_FAILED, annotated, verification);
    }

    private static List<ChatResult.Citation> annotateScores(
            List<ChatResult.Citation> citations, VerificationResult verification) {
        if (verification.citationScores() == null || verification.citationScores().isEmpty()) {
            return citations;
        }
        Map<Long, Double> scoreByChunkId = new HashMap<>();
        for (var score : verification.citationScores()) {
            scoreByChunkId.put(score.chunkId(), score.score());
        }
        return citations.stream()
                .map(
                        citation -> {
                            Double score =
                                    citation.chunkId() == null
                                            ? null
                                            : scoreByChunkId.get(citation.chunkId());
                            if (score == null) return citation;
                            return new ChatResult.Citation(
                                    citation.chunkId(),
                                    citation.docId(),
                                    citation.page(),
                                    citation.snippet(),
                                    citation.llmContext(),
                                    citation.sectionPath(),
                                    score);
                        })
                .toList();
    }

    public record Result(
            String answer,
            StateHint stateHint,
            List<ChatResult.Citation> citations,
            VerificationResult verification) {}
}
