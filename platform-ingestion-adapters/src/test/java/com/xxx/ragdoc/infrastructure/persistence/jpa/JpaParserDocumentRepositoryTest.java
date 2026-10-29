package com.xxx.ragdoc.infrastructure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxx.ragdoc.infrastructure.persistence.jpa.entity.DocumentEntity;
import com.xxx.ragdoc.infrastructure.persistence.jpa.repository.DocumentJpaRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JpaParserDocumentRepositoryTest {

    private final DocumentJpaRepository jpa = mock(DocumentJpaRepository.class);
    private final JpaParserDocumentRepository repository = new JpaParserDocumentRepository(jpa);

    @Test
    void loadsDomainDocumentWithoutExposingJpaEntityToParser() {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(42L);
        entity.setContentHash("a".repeat(64));
        entity.setOriginalFilename("guide.md");
        entity.setMimeType("text/markdown");
        entity.setSizeBytes(128L);
        entity.setTenantId("tenant-a");
        entity.setStatus("UPLOADED");
        when(jpa.findById(42L)).thenReturn(Optional.of(entity));

        var document = repository.findById(42L).orElseThrow();

        assertThat(document.id().value()).isEqualTo(42L);
        assertThat(document.originalFilename()).isEqualTo("guide.md");
        assertThat(document.tenantId()).isEqualTo("tenant-a");
    }
}
