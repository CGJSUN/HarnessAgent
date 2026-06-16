package com.harnessagent.rag.application;

import com.harnessagent.rag.domain.KnowledgeSourceRegistration;

public record KnowledgeDocumentInput(KnowledgeSourceRegistration source, String content) {
}
