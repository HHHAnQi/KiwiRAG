# Alert response runbook

This runbook maps every checked-in Prometheus alert to a first response. It is an operational
contract, not evidence that Alertmanager delivery has been tested in a real environment.

## RagdocChatFirstTokenP95High

Check the primary LLM route, outbound latency, connection-pool saturation, and SSE proxy buffering.
If only first-token latency regresses, avoid changing retrieval before confirming the model route.

## RagdocChatTotalP95High

Compare retrieve, rerank, and LLM timers. Disable optional rerank only when its timer is the dominant
regression and record the configuration change for rollback.

## RagdocLlmDegradedRateHigh

Check primary/fallback credentials and provider health. Keep the degraded response path enabled;
do not bypass citation or tenant checks to restore availability.

## RagdocRetrieveZeroRecallHigh

Check `ragdoc_retrieve_requests_total` by outcome, Milvus health, embedding model/version alignment,
and active corpus size. A low-traffic window is not sufficient evidence of a retrieval regression.

## RagdocRerankFailures

Check the reranker endpoint and timeout. The system should fall back to the pre-rerank order; verify
that fallback before restarting the chat application.

## RagdocRewriteLlmFailures

Check the fallback LLM route and quota. Query rewriting is allowed to fall back to the original
query, so assess answer quality separately from request availability.

## RagdocTopicShiftDetectFailures

Check the embedding service used by topic-shift detection. Confirm that failures do not leak history
across tenant or conversation scope.

## RagdocCompressionSuccessRate

Check summary LLM and Redis. Watch history-buffer size and force-truncate metrics before changing the
compression threshold.

## RagdocHistoryForceTruncate

Inspect compression failures and conversation-store errors. Treat sustained growth as loss of
conversation quality, not a chat availability incident.

## RagdocCompressionStalled

Confirm conversation memory is enabled and chat traffic is present, then check store writes and the
compressor bean. A disabled feature must be excluded from the alert route.

## Validation procedure

1. Run `python3 scripts/nfr/verify_assets.py` in CI.
2. In staging, load the rule file with `promtool check rules`.
3. Inject one synthetic series per alert, confirm Alertmanager delivery, and record delivery time.
4. Follow the matching section and attach the incident timeline to the release evidence.

Steps 2-4 require a real Prometheus/Alertmanager environment and remain unverified until evidence is
archived.
