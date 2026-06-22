package com.harnessagent.rag.persistence;

import java.util.List;
import java.util.Optional;
import com.harnessagent.rag.domain.KnowledgeChunk;
import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.rag.domain.PersonalMemoryRecord;
import com.harnessagent.rag.domain.RagFeedback;
import com.harnessagent.rag.domain.RagMetric;

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

    PersonalMemoryRecord saveMemory(PersonalMemoryRecord memory);

    Optional<PersonalMemoryRecord> findMemory(String memoryId);

    List<PersonalMemoryRecord> listMemories(String tenantId, String ownerId, String agentId);
}
