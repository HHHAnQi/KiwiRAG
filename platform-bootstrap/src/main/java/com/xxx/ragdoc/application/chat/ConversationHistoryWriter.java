package com.xxx.ragdoc.application.chat;

import com.xxx.ragdoc.application.chat.command.ChatResult;
import com.xxx.ragdoc.application.chat.conversation.ConversationContext;
import com.xxx.ragdoc.application.chat.conversation.ConversationContext.Turn;
import com.xxx.ragdoc.application.chat.conversation.port.ConversationStore;
import com.xxx.ragdoc.application.chat.conversation.port.HistoryCompressorPort;
import com.xxx.ragdoc.domain.shared.StateHint;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Persists successful conversation turns and triggers best-effort history compression. */
@Slf4j
@Component
public class ConversationHistoryWriter {

    private ConversationStore store;
    private HistoryCompressorPort compressor;
    private ConversationProperties properties;

    @Autowired(required = false)
    public void setStore(ConversationStore store) {
        this.store = store;
    }

    @Autowired(required = false)
    public void setCompressor(HistoryCompressorPort compressor) {
        this.compressor = compressor;
    }

    @Autowired(required = false)
    public void setProperties(ConversationProperties properties) {
        this.properties = properties;
    }

    public void recordSuccessfulTurn(
            String conversationId,
            ConversationContext context,
            String query,
            String answer,
            List<ChatResult.Citation> citations) {
        if (store == null
                || context == null
                || conversationId == null
                || conversationId.isBlank()) {
            return;
        }

        ConversationContext updated;
        try {
            List<Long> citedChunkIds =
                    citations.stream().map(ChatResult.Citation::chunkId).toList();
            updated =
                    context.appendTurn(
                            new Turn(query, answer, citedChunkIds, StateHint.OK, Instant.now()));
            store.save(updated);
        } catch (Exception exception) {
            log.warn(
                    "chat.history_write_failed conv_id={}, reason={}",
                    conversationId,
                    exception.getMessage());
            return;
        }

        int threshold = properties == null ? 6 : properties.getCompressThreshold();
        if (compressor == null
                || updated.recentTurns() == null
                || updated.recentTurns().size() < threshold) {
            return;
        }
        try {
            compressor.compress(conversationId);
        } catch (Exception exception) {
            log.debug(
                    "chat.compress_submit_skipped conv_id={}, reason={}",
                    conversationId,
                    exception.getMessage());
        }
    }
}
