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
    public List<KnowledgeSource> listSources(String ownerScopeId) {
        return sources.values().stream()
                .filter(source -> source.ownerScopeId().equals(ownerScopeId))
                .sorted(Comparator.comparing(KnowledgeSource::updatedAt).reversed())
                .toList();
    }

    @Override
    public void saveChunks(String sourceId, List<KnowledgeChunk> chunks) {
        chunksBySource.put(sourceId, List.copyOf(chunks));
    }

    @Override
    public List<KnowledgeChunk> listChunks(String ownerScopeId) {
        return chunksBySource.values().stream()
                .flatMap(List::stream)
                .filter(chunk -> chunk.ownerScopeId().equals(ownerScopeId))
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
    public List<RagMetric> listMetrics(String ownerScopeId) {
        synchronized (metrics) {
            return metrics.stream()
                    .filter(metric -> metric.ownerScopeId().equals(ownerScopeId))
                    .toList();
        }
    }

    @Override
    public void recordFeedback(RagFeedback feedback) {
        this.feedback.add(feedback);
    }

    @Override
    public List<RagFeedback> listFeedback(String ownerScopeId) {
        synchronized (feedback) {
            return feedback.stream()
                    .filter(item -> item.ownerScopeId().equals(ownerScopeId))
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
    public List<PersonalMemoryRecord> listMemories(String ownerScopeId, String ownerId, String agentId) {
        return memories.values().stream()
                .filter(memory -> memory.ownerScopeId().equals(ownerScopeId))
                .filter(memory -> memory.ownerId().equals(ownerId))
                .filter(memory -> memory.agentId().equals(agentId))
                .sorted(Comparator.comparing(PersonalMemoryRecord::updatedAt).reversed())
                .toList();
    }
}
