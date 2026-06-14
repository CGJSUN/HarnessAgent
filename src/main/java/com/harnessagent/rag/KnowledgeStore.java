package com.harnessagent.rag;

import java.util.List;
import java.util.Optional;

public interface KnowledgeStore {

    KnowledgeSource saveSource(KnowledgeSource source);

    Optional<KnowledgeSource> findSource(String sourceId);

    List<KnowledgeSource> listSources(String tenantId);

    void saveChunks(String sourceId, List<KnowledgeChunk> chunks);

    List<KnowledgeChunk> listChunks(String tenantId);

    void removeChunks(String sourceId);

    void recordMetric(RagMetric metric);

    List<RagMetric> listMetrics(String tenantId);

    void recordFeedback(RagFeedback feedback);

    List<RagFeedback> listFeedback(String tenantId);
}
