package com.harnessagent.rag.domain;

import java.util.List;

public record PersonalDataExport(
        String ownerScopeId,
        String ownerId,
        String agentId,
        List<PersonalMemoryRecord> memories,
        List<KnowledgeSource> knowledgeSources,
        List<KnowledgeIndexMetadata> indexMetadata,
        List<KnowledgeCitation> citationRecords) {
}
