package com.xxx.ragdoc.application.document.port;

import com.xxx.ragdoc.domain.document.Document;
import java.util.Optional;

/** Minimal document persistence contract required by the asynchronous parser runtime. */
public interface ParserDocumentRepository {

    Document save(Document document);

    Optional<Document> findById(Long id);
}
