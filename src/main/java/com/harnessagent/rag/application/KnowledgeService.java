package com.harnessagent.rag.application;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.harnessagent.rag.domain.KnowledgeChunk;
import com.harnessagent.rag.domain.KnowledgeCitation;
import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.rag.domain.KnowledgeSourceRegistration;
import com.harnessagent.rag.domain.KnowledgeSourceStatus;
import com.harnessagent.rag.domain.KnowledgeVisibility;
import com.harnessagent.rag.domain.RagFeedback;
import com.harnessagent.rag.domain.RagMetric;
import com.harnessagent.rag.domain.RetrievalPrincipal;
import com.harnessagent.rag.domain.RetrievedKnowledge;
import com.harnessagent.rag.persistence.KnowledgeStore;
import com.harnessagent.rag.retrieval.KnowledgeRetrievalResult;
import com.harnessagent.security.application.SafeLogFields;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeStore store;
    private final TextChunker chunker;
    private final TextTokenizer tokenizer;
    private final KnowledgeRetrievalPolicy retrievalPolicy;

    public KnowledgeService(
            KnowledgeStore store,
            TextChunker chunker,
            TextTokenizer tokenizer,
            KnowledgeRetrievalPolicy retrievalPolicy) {
        this.store = store;
        this.chunker = chunker;
        this.tokenizer = tokenizer;
        this.retrievalPolicy = retrievalPolicy;
    }

    public KnowledgeSource registerSource(KnowledgeSourceRegistration registration) {
        validateRegistration(registration);
        Instant now = Instant.now();
        KnowledgeSource source = new KnowledgeSource(
                UUID.randomUUID().toString(),
                registration.tenantId().trim(),
                registration.ownerId().trim(),
                registration.title().trim(),
                defaultString(registration.version(), "v1"),
                registration.visibility() == null ? KnowledgeVisibility.RESTRICTED : registration.visibility(),
                safeSet(registration.allowedDepartments()),
                safeSet(registration.allowedRoles()),
                safeSet(registration.allowedUsers()),
                defaultString(registration.updatePolicy(), "manual"),
                KnowledgeSourceStatus.ACTIVE,
                now,
                now);
        KnowledgeSource saved = store.saveSource(source);
        log.info(
                "rag source registered tenantId={} sourceId={} visibility={} status={}",
                saved.tenantId(),
                saved.id(),
                saved.visibility(),
                saved.status());
        return saved;
    }

    public KnowledgeSource ingestDocument(KnowledgeDocumentInput input) {
        if (input == null || input.source() == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (input.content() == null || input.content().isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        KnowledgeSource source = registerSource(input.source());
        List<String> rawChunks = chunker.chunk(input.content());
        List<KnowledgeChunk> chunks = java.util.stream.IntStream.range(0, rawChunks.size())
                .mapToObj(index -> new KnowledgeChunk(
                        source.id() + ":" + index,
                        source.id(),
                        source.tenantId(),
                        source.title(),
                        source.version(),
                        index,
                        rawChunks.get(index),
                        tokenizer.tokenize(rawChunks.get(index))))
                .toList();
        store.saveChunks(source.id(), chunks);
        log.info(
                "rag document ingested tenantId={} sourceId={} chunkCount={}",
                source.tenantId(),
                source.id(),
                chunks.size());
        return source;
    }

    public KnowledgeRetrievalResult retrieve(RetrievalPrincipal principal, String query, int limit) {
        validatePrincipal(principal);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        int effectiveLimit = limit <= 0 ? retrievalPolicy.defaultLimit() : limit;
        Set<String> queryTokens = tokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            recordMetric(principal, query, false, 0, 0, "empty_query_tokens");
            log.warn(
                    "rag no_answer tenantId={} userHash={} reason={} candidateCount={} permittedCount={}",
                    principal.tenantId(),
                    SafeLogFields.user(principal.userId()),
                    "empty_query_tokens",
                    0,
                    0);
            return KnowledgeRetrievalResult.noAnswer("无法从当前可用知识中确定答案。");
        }

        List<KnowledgeChunk> tenantChunks = store.listChunks(principal.tenantId());
        List<ScoredChunk> candidates = tenantChunks.stream()
                .map(chunk -> score(chunk, queryTokens))
                .filter(scored -> scored.score() > 0)
                .toList();
        List<RetrievedKnowledge> permitted = candidates.stream()
                .filter(scored -> isPermitted(principal, scored.chunk().sourceId()))
                .sorted(java.util.Comparator.comparing(ScoredChunk::score).reversed())
                .limit(effectiveLimit)
                .map(ScoredChunk::toRetrievedKnowledge)
                .toList();
        log.debug(
                "rag retrieval filtered tenantId={} userHash={} candidateCount={} permittedCount={} limit={}",
                principal.tenantId(),
                SafeLogFields.user(principal.userId()),
                candidates.size(),
                permitted.size(),
                effectiveLimit);

        boolean hit = !permitted.isEmpty() && permitted.get(0).score() >= retrievalPolicy.minimumScore();
        recordMetric(
                principal,
                query,
                hit,
                candidates.size(),
                permitted.size(),
                hit ? null : "no_permitted_evidence");
        if (!hit) {
            log.warn(
                    "rag no_answer tenantId={} userHash={} reason={} candidateCount={} permittedCount={}",
                    principal.tenantId(),
                    SafeLogFields.user(principal.userId()),
                    "no_permitted_evidence",
                    candidates.size(),
                    permitted.size());
            return KnowledgeRetrievalResult.noAnswer("无法从当前可用知识中确定答案。");
        }
        return KnowledgeRetrievalResult.answered(permitted);
    }

    public KnowledgeSource revokeSource(String sourceId) {
        KnowledgeSource source = store.findSource(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown knowledge source: " + sourceId));
        store.removeChunks(sourceId);
        KnowledgeSource revoked = store.saveSource(source.withStatus(KnowledgeSourceStatus.REVOKED));
        log.info("rag source revoked tenantId={} sourceId={}", revoked.tenantId(), revoked.id());
        return revoked;
    }

    public KnowledgeSource deleteSource(String sourceId) {
        KnowledgeSource source = store.findSource(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown knowledge source: " + sourceId));
        store.removeChunks(sourceId);
        KnowledgeSource deleted = store.saveSource(source.withStatus(KnowledgeSourceStatus.DELETED));
        log.info("rag source deleted tenantId={} sourceId={}", deleted.tenantId(), deleted.id());
        return deleted;
    }

    public List<KnowledgeSource> listSources(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return store.listSources(tenantId.trim());
    }

    public List<RagMetric> listMetrics(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return store.listMetrics(tenantId.trim());
    }

    public RagFeedback recordFeedback(
            String tenantId, String userId, String query, boolean helpful, String comment) {
        require(tenantId, "tenantId");
        require(userId, "userId");
        require(query, "query");
        RagFeedback feedback = new RagFeedback(
                tenantId.trim(),
                userId.trim(),
                query.trim(),
                helpful,
                comment == null ? "" : comment.trim(),
                Instant.now());
        store.recordFeedback(feedback);
        log.info(
                "rag feedback recorded tenantId={} userHash={} helpful={}",
                tenantId.trim(),
                SafeLogFields.user(userId),
                helpful);
        return feedback;
    }

    public List<RagFeedback> listFeedback(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return store.listFeedback(tenantId.trim());
    }

    private ScoredChunk score(KnowledgeChunk chunk, Set<String> queryTokens) {
        Set<String> intersection = new HashSet<>(chunk.tokens());
        intersection.retainAll(queryTokens);
        double keywordScore = intersection.size();
        Set<String> union = new HashSet<>(chunk.tokens());
        union.addAll(queryTokens);
        double vectorScore = union.isEmpty() ? 0d : (double) intersection.size() / union.size();
        return new ScoredChunk(
                chunk,
                keywordScore * retrievalPolicy.keywordWeight()
                        + vectorScore * retrievalPolicy.vectorWeight(),
                keywordScore,
                vectorScore);
    }

    private boolean isPermitted(RetrievalPrincipal principal, String sourceId) {
        // Permission filtering happens before citation construction, so callers never see inaccessible evidence.
        return store.findSource(sourceId)
                .filter(source -> source.status() == KnowledgeSourceStatus.ACTIVE)
                .filter(source -> source.tenantId().equals(principal.tenantId()))
                .filter(source -> source.visibility() == KnowledgeVisibility.PUBLIC
                        || source.ownerId().equals(principal.userId())
                        || source.allowedUsers().contains(principal.userId())
                        || intersects(source.allowedDepartments(), principal.departments())
                        || intersects(source.allowedRoles(), principal.roles()))
                .isPresent();
    }

    private void recordMetric(
            RetrievalPrincipal principal,
            String query,
            boolean hit,
            int candidateCount,
            int permittedCount,
            String failureReason) {
        store.recordMetric(new RagMetric(
                principal.tenantId(),
                principal.userId(),
                query,
                hit,
                candidateCount,
                permittedCount,
                failureReason,
                Instant.now()));
    }

    private static void validateRegistration(KnowledgeSourceRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("source is required");
        }
        require(registration.tenantId(), "tenantId");
        require(registration.ownerId(), "ownerId");
        require(registration.title(), "title");
    }

    private static void validatePrincipal(RetrievalPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("principal is required");
        }
        require(principal.tenantId(), "tenantId");
        require(principal.userId(), "userId");
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static boolean intersects(Set<String> first, Set<String> second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            return false;
        }
        return first.stream().anyMatch(second::contains);
    }

    private static Set<String> safeSet(Set<String> input) {
        if (input == null) {
            return Set.of();
        }
        return input.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record ScoredChunk(
            KnowledgeChunk chunk, double score, double keywordScore, double vectorScore) {
        RetrievedKnowledge toRetrievedKnowledge() {
            return new RetrievedKnowledge(
                    new KnowledgeCitation(
                            chunk.sourceId(),
                            chunk.title(),
                            chunk.version(),
                            chunk.chunkIndex(),
                            chunk.id()),
                    chunk.content(),
                    score,
                    keywordScore,
                    vectorScore);
        }
    }
}
