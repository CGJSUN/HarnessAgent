package com.harnessagent.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryKnowledgeStore implements KnowledgeStore {

    private final Map<String, KnowledgeSource> sources = new ConcurrentHashMap<>();
    private final Map<String, List<KnowledgeChunk>> chunksBySource = new ConcurrentHashMap<>();
    private final List<RagMetric> metrics = java.util.Collections.synchronizedList(new ArrayList<>());
    private final List<RagFeedback> feedback = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public KnowledgeSource saveSource(KnowledgeSource source) {
        sources.put(source.id(), source);
        return source;
    }

    @Override
    public Optional<KnowledgeSource> findSource(String sourceId) {
        return Optional.ofNullable(sources.get(sourceId));
    }

    @Override
    public List<KnowledgeSource> listSources(String tenantId) {
        return sources.values().stream()
                .filter(source -> source.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(KnowledgeSource::updatedAt).reversed())
                .toList();
    }

    @Override
    public void saveChunks(String sourceId, List<KnowledgeChunk> chunks) {
        chunksBySource.put(sourceId, List.copyOf(chunks));
    }

    @Override
    public List<KnowledgeChunk> listChunks(String tenantId) {
        return chunksBySource.values().stream()
                .flatMap(List::stream)
                .filter(chunk -> chunk.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public void removeChunks(String sourceId) {
        chunksBySource.remove(sourceId);
    }

    @Override
    public void recordMetric(RagMetric metric) {
        metrics.add(metric);
    }

    @Override
    public List<RagMetric> listMetrics(String tenantId) {
        synchronized (metrics) {
            return metrics.stream()
                    .filter(metric -> metric.tenantId().equals(tenantId))
                    .toList();
        }
    }

    @Override
    public void recordFeedback(RagFeedback feedback) {
        this.feedback.add(feedback);
    }

    @Override
    public List<RagFeedback> listFeedback(String tenantId) {
        synchronized (feedback) {
            return feedback.stream()
                    .filter(item -> item.tenantId().equals(tenantId))
                    .toList();
        }
    }
}
