package com.harnessagent.rag.application;

import com.harnessagent.rag.domain.KnowledgeCitation;
import com.harnessagent.rag.domain.KnowledgeIndexMetadata;
import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.rag.domain.KnowledgeSourceRegistration;
import com.harnessagent.rag.domain.KnowledgeSourceType;
import com.harnessagent.rag.domain.KnowledgeVisibility;
import com.harnessagent.rag.domain.MemoryWriteStatus;
import com.harnessagent.rag.domain.PersonalDataExport;
import com.harnessagent.rag.domain.PersonalMemoryRecord;
import com.harnessagent.rag.persistence.KnowledgeStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalMemoryService {

    private final KnowledgeService knowledgeService;
    private final KnowledgeStore store;

    public PersonalMemoryService(KnowledgeService knowledgeService, KnowledgeStore store) {
        this.knowledgeService = knowledgeService;
        this.store = store;
    }

    @Transactional
    public PersonalMemoryRecord requestWrite(MemoryWriteCommand command) {
        validate(command);
        Instant now = Instant.now();
        PersonalMemoryRecord memory = new PersonalMemoryRecord(
                UUID.randomUUID().toString(),
                command.ownerScopeId().trim(),
                command.ownerId().trim(),
                command.agentId().trim(),
                command.sessionId().trim(),
                command.layer(),
                command.title().trim(),
                command.content().trim(),
                command.requireConfirmation() ? MemoryWriteStatus.PENDING_CONFIRMATION : MemoryWriteStatus.CONFIRMED,
                Optional.empty(),
                now,
                now);
        store.saveMemory(memory);
        return command.requireConfirmation() ? memory : confirmWrite(memory.id());
    }

    @Transactional
    public PersonalMemoryRecord confirmWrite(String memoryId) {
        PersonalMemoryRecord memory = find(memoryId);
        if (memory.status() == MemoryWriteStatus.DELETED || memory.status() == MemoryWriteStatus.REJECTED) {
            throw new IllegalStateException("Memory write is no longer confirmable: " + memoryId);
        }
        if (memory.sourceId().isPresent()) {
            return store.saveMemory(memory.withStatus(MemoryWriteStatus.CONFIRMED));
        }
        KnowledgeSource source = knowledgeService.ingestDocument(new KnowledgeDocumentInput(
                new KnowledgeSourceRegistration(
                        memory.ownerScopeId(),
                        memory.ownerId(),
                        memory.agentId(),
                        memory.title(),
                        memory.layer().name().toLowerCase(),
                        KnowledgeVisibility.RESTRICTED,
                        Set.of(),
                        "memory",
                        KnowledgeSourceType.MEMORY,
                        "memory://" + memory.id()),
                memory.content()));
        return store.saveMemory(memory.withSource(source.id()));
    }

    @Transactional
    public PersonalMemoryRecord rejectWrite(String memoryId) {
        PersonalMemoryRecord memory = find(memoryId);
        if (memory.sourceId().isPresent()) {
            throw new IllegalStateException("Confirmed memory must be deleted instead of rejected: " + memoryId);
        }
        return store.saveMemory(memory.withStatus(MemoryWriteStatus.REJECTED));
    }

    @Transactional
    public PersonalMemoryRecord deleteMemory(String memoryId) {
        PersonalMemoryRecord memory = find(memoryId);
        memory.sourceId().ifPresent(knowledgeService::deleteSource);
        return store.saveMemory(memory.redactedDeleted());
    }

    @Transactional
    public PersonalMemoryRecord confirmWrite(String memoryId, String ownerScopeId, String ownerId, String agentId) {
        PersonalMemoryRecord memory = requireOwnership(find(memoryId), ownerScopeId, ownerId, agentId);
        return confirmWrite(memory.id());
    }

    @Transactional
    public PersonalMemoryRecord rejectWrite(String memoryId, String ownerScopeId, String ownerId, String agentId) {
        PersonalMemoryRecord memory = requireOwnership(find(memoryId), ownerScopeId, ownerId, agentId);
        return rejectWrite(memory.id());
    }

    @Transactional
    public PersonalMemoryRecord deleteMemory(String memoryId, String ownerScopeId, String ownerId, String agentId) {
        PersonalMemoryRecord memory = requireOwnership(find(memoryId), ownerScopeId, ownerId, agentId);
        return deleteMemory(memory.id());
    }

    public List<PersonalMemoryRecord> listMemories(String ownerScopeId, String ownerId, String agentId) {
        require(ownerScopeId, "ownerScopeId");
        require(ownerId, "ownerId");
        require(agentId, "agentId");
        return store.listMemories(ownerScopeId.trim(), ownerId.trim(), agentId.trim()).stream()
                .filter(memory -> memory.status() != MemoryWriteStatus.DELETED)
                .toList();
    }

    public PersonalDataExport exportPersonalData(String ownerScopeId, String ownerId, String agentId) {
        require(ownerScopeId, "ownerScopeId");
        require(ownerId, "ownerId");
        require(agentId, "agentId");
        String normalizedTenantId = ownerScopeId.trim();
        String normalizedOwnerId = ownerId.trim();
        String normalizedAgentId = agentId.trim();
        List<PersonalMemoryRecord> memories = listMemories(normalizedTenantId, normalizedOwnerId, normalizedAgentId);
        List<KnowledgeSource> sources = store.listSources(normalizedTenantId).stream()
                .filter(source -> source.ownerId().equals(normalizedOwnerId))
                .filter(source -> source.agentId().isBlank() || source.agentId().equals(normalizedAgentId))
                .filter(source -> source.status() != com.harnessagent.rag.domain.KnowledgeSourceStatus.DELETED)
                .toList();
        List<KnowledgeIndexMetadata> metadata = sources.stream()
                .map(source -> new KnowledgeIndexMetadata(
                        source.id(),
                        source.agentId(),
                        source.sourceType(),
                        source.sourceUri(),
                        source.version(),
                        source.indexStatus(),
                        source.status(),
                        source.indexedAt()))
                .toList();
        List<String> sourceIds = sources.stream().map(KnowledgeSource::id).toList();
        List<KnowledgeCitation> citationRecords = store.listChunks(normalizedTenantId).stream()
                .filter(chunk -> sourceIds.contains(chunk.sourceId()))
                .map(chunk -> new KnowledgeCitation(
                        chunk.sourceId(),
                        chunk.title(),
                        chunk.version(),
                        chunk.chunkIndex(),
                        chunk.id(),
                        chunk.sourceType(),
                        chunk.sourceUri()))
                .toList();
        return new PersonalDataExport(
                normalizedTenantId,
                normalizedOwnerId,
                normalizedAgentId,
                memories,
                sources,
                metadata,
                citationRecords);
    }

    private PersonalMemoryRecord find(String memoryId) {
        require(memoryId, "memoryId");
        return store.findMemory(memoryId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown memory record: " + memoryId));
    }

    private static PersonalMemoryRecord requireOwnership(
            PersonalMemoryRecord memory,
            String ownerScopeId,
            String ownerId,
            String agentId) {
        require(ownerScopeId, "ownerScopeId");
        require(ownerId, "ownerId");
        require(agentId, "agentId");
        if (!memory.ownerScopeId().equals(ownerScopeId.trim())
                || !memory.ownerId().equals(ownerId.trim())
                || !memory.agentId().equals(agentId.trim())) {
            throw new IllegalStateException("Memory record is not owned by the authenticated context");
        }
        return memory;
    }

    private static void validate(MemoryWriteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("memory write command is required");
        }
        require(command.ownerScopeId(), "ownerScopeId");
        require(command.ownerId(), "ownerId");
        require(command.agentId(), "agentId");
        require(command.sessionId(), "sessionId");
        require(command.title(), "title");
        require(command.content(), "content");
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
