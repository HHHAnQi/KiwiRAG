package com.xxx.ragdoc.application.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CitationVerifierPropertiesTest {

    @Test
    void acceptsImplementedFailurePolicies() {
        CitationVerifierProperties properties = new CitationVerifierProperties();

        properties.setOnFail(CitationVerifierProperties.OnFail.REFUSE);
        assertThatCode(properties::rejectUnsupportedPolicy).doesNotThrowAnyException();
        properties.setOnFail(CitationVerifierProperties.OnFail.WARN_ONLY);
        assertThatCode(properties::rejectUnsupportedPolicy).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnimplementedRegeneratePolicy() {
        CitationVerifierProperties properties = new CitationVerifierProperties();
        properties.setOnFail(CitationVerifierProperties.OnFail.REGENERATE);

        assertThatThrownBy(properties::rejectUnsupportedPolicy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REGENERATE is not implemented");
    }
}
