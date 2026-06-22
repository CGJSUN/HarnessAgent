package com.harnessagent.rag.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import com.harnessagent.rag.domain.KnowledgeChunk;
import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.rag.domain.PersonalMemoryRecord;
import com.harnessagent.rag.domain.RagFeedback;
import com.harnessagent.rag.domain.RagMetric;

@Repository
@Profile("!production")
public class InMemoryKnowledgeStore implements KnowledgeStore {

    private final Map<String, KnowledgeSource> sources = new ConcurrentHashMap<>();
    private final Map<String, List<KnowledgeChunk>> chunksBySource = new ConcurrentHashMap<>();
    private final Map<String, PersonalMemoryRecord> memories = new ConcurrentHashMap<>();
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

    @Override
    public PersonalMemoryRecord saveMemory(PersonalMemoryRecord memory) {
        memories.put(memory.id(), memory);
        return memory;
    }

    @Override
    public Optional<PersonalMemoryRecord> findMemory(String memoryId) {
        return Optional.ofNullable(memories.get(memoryId));
    }

    @Override
    public List<PersonalMemoryRecord> listMemories(String tenantId, String ownerId, String agentId) {
        return memories.values().stream()
                .filter(memory -> memory.tenantId().equals(tenantId))
                .filter(memory -> memory.ownerId().equals(ownerId))
                .filter(memory -> memory.agentId().equals(agentId))
                .sorted(Comparator.comparing(PersonalMemoryRecord::updatedAt).reversed())
                .toList();
    }
}
