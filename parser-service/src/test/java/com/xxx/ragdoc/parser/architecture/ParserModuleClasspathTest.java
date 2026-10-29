package com.xxx.ragdoc.parser.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ParserModuleClasspathTest {

    @Test
    void parserUsesSharedAdaptersWithoutDependingOnChatExecutable() throws Exception {
        assertThat(
                        Class.forName(
                                "com.xxx.ragdoc.infrastructure.persistence.jpa.JpaParserDocumentRepository"))
                .isNotNull();
        assertThatThrownBy(() -> Class.forName("com.xxx.ragdoc.RagDocApplication"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
