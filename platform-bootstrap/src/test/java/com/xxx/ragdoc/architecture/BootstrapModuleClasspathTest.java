package com.xxx.ragdoc.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BootstrapModuleClasspathTest {

    @Test
    void chatUsesSharedAdaptersWithoutDependingOnParserExecutable() throws Exception {
        assertThat(
                        Class.forName(
                                "com.xxx.ragdoc.infrastructure.persistence.jpa.JpaParserDocumentRepository"))
                .isNotNull();
        assertThatThrownBy(() -> Class.forName("com.xxx.ragdoc.parser.ParserServiceApplication"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
