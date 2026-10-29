package com.xxx.ragdoc.infrastructure.persistence.jpa;

import com.xxx.ragdoc.application.document.port.ParserDocumentRepository;
import com.xxx.ragdoc.domain.document.Document;
import com.xxx.ragdoc.domain.shared.DocumentId;
import com.xxx.ragdoc.infrastructure.persistence.jpa.entity.DocumentEntity;
import com.xxx.ragdoc.infrastructure.persistence.jpa.repository.DocumentJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Parser-facing JPA adapter containing only the document operations required by ingestion. */
@Component
@RequiredArgsConstructor
public class JpaParserDocumentRepository implements ParserDocumentRepository {

    private final DocumentJpaRepository repository;

    @Override
    public Document save(Document document) {
        DocumentEntity entity;
        if (document.id() == null) {
            entity = repository.save(DocumentMapper.toNewEntity(document));
            document.assignId(new DocumentId(entity.getId()));
        } else {
            entity =
                    repository
                            .findById(document.id().value())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Document 不存在: " + document.id().value()));
            repository.save(DocumentMapper.toEntity(document, entity));
        }
        return DocumentMapper.toDomain(entity);
    }

    @Override
    public Optional<Document> findById(Long id) {
        return repository.findById(id).map(DocumentMapper::toDomain);
    }
}
