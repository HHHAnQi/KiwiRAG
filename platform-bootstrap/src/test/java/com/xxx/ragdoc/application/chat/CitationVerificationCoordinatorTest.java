package com.xxx.ragdoc.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xxx.ragdoc.application.chat.command.ChatResult;
import com.xxx.ragdoc.application.chat.verification.VerificationResult;
import com.xxx.ragdoc.application.chat.verification.port.CitationVerifierPort;
import com.xxx.ragdoc.domain.shared.StateHint;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CitationVerificationCoordinatorTest {

    private final CitationVerifierProperties properties = mock(CitationVerifierProperties.class);
    private final ChatMessages messages = mock(ChatMessages.class);
    private final CitationVerifierPort verifier = mock(CitationVerifierPort.class);
    private CitationVerificationCoordinator coordinator;

    private final List<ChatResult.Citation> citations =
            List.of(new ChatResult.Citation(7L, 3L, 1, "snippet", "context", List.of("s"), null));

    @BeforeEach
    void setUp() {
        coordinator = new CitationVerificationCoordinator(properties, messages);
        coordinator.setVerifier(verifier);
    }

    @Test
    void skipsVerificationWhenDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        CitationVerificationCoordinator.Result result = coordinator.verify("answer", citations);

        assertThat(result.stateHint()).isEqualTo(StateHint.OK);
        assertThat(result.verification()).isNull();
        verifyNoInteractions(verifier);
    }

    @Test
    void annotatesCitationScoresWhenVerificationPasses() {
        when(properties.isEnabled()).thenReturn(true);
        when(verifier.verify("answer", List.of(new CitationVerifierPort.Evidence(7L, "context"))))
                .thenReturn(
                        new VerificationResult(
                                VerificationResult.Outcome.PASS,
                                0.91,
                                List.of(
                                        new VerificationResult.CitationScore(
                                                7L, VerificationResult.Verdict.ENTAILMENT, 0.91)),
                                null));

        CitationVerificationCoordinator.Result result = coordinator.verify("answer", citations);

        assertThat(result.stateHint()).isEqualTo(StateHint.OK);
        assertThat(result.citations().get(0).verifyScore()).isEqualTo(0.91);
    }

    @Test
    void warnsWithoutDegradingChatWhenConfiguredWarnOnly() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getOnFail()).thenReturn(CitationVerifierProperties.OnFail.WARN_ONLY);
        when(verifier.verify("answer", List.of(new CitationVerifierPort.Evidence(7L, "context"))))
                .thenReturn(failedVerification());

        CitationVerificationCoordinator.Result result = coordinator.verify("answer", citations);

        assertThat(result.stateHint()).isEqualTo(StateHint.OK);
        assertThat(result.answer()).isEqualTo("answer");
        assertThat(result.verification().errorMessage()).startsWith("WARN_ONLY:");
    }

    @Test
    void refusesAnswerWhenConfiguredRefuse() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getOnFail()).thenReturn(CitationVerifierProperties.OnFail.REFUSE);
        when(properties.getScoreThreshold()).thenReturn(0.5);
        when(messages.verifierRefusal(anyDouble())).thenReturn("verification refused");
        when(verifier.verify("answer", List.of(new CitationVerifierPort.Evidence(7L, "context"))))
                .thenReturn(failedVerification());

        CitationVerificationCoordinator.Result result = coordinator.verify("answer", citations);

        assertThat(result.stateHint()).isEqualTo(StateHint.VERIFY_FAILED);
        assertThat(result.answer()).isEqualTo("verification refused");
    }

    private static VerificationResult failedVerification() {
        return new VerificationResult(VerificationResult.Outcome.FAIL, 0.2, List.of(), null);
    }
}
