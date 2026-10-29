package com.xxx.ragdoc.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class ProductionConfigurationTest {

    private static final ClassPathResource PROD_CONFIG =
            new ClassPathResource("application-prod.yml");

    @Test
    void productionYamlHasExactlyOneTopLevelRagSection() throws IOException {
        String yaml = PROD_CONFIG.getContentAsString(StandardCharsets.UTF_8);
        long topLevelRagSections = yaml.lines().filter("rag:"::equals).count();

        assertThat(topLevelRagSections)
                .as("duplicate top-level rag sections can silently override production settings")
                .isEqualTo(1);
    }

    @Test
    void productionYamlLoadsAuthParserRetrieveAndAgentSettingsTogether() throws IOException {
        var propertySources = new YamlPropertySourceLoader().load("production", PROD_CONFIG);

        assertThat(propertySources).hasSize(1);
        var properties = propertySources.get(0);
        assertThat(properties.getProperty("rag.auth.dev-default-principal-enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("rag.parser.mode")).isEqualTo("async");
        assertThat(properties.getProperty("rag.retrieve.mode")).isEqualTo("hybrid");
        assertThat(properties.getProperty("rag.citation-verifier.enabled")).isEqualTo(true);
        assertThat(properties.getProperty("rag.citation-verifier.on-fail"))
                .isIn("REFUSE", "WARN_ONLY");
        assertThat(properties.getProperty("rag.agent.planner.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("rag.agent.lease.enabled")).isEqualTo(true);
    }
}
