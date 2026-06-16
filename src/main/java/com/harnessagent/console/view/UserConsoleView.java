package com.harnessagent.console.view;

import com.harnessagent.rag.domain.KnowledgeCitation;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.SessionSummary;
import java.util.List;

public record UserConsoleView(
        List<SessionSummary> sessions,
        List<ChatMessage> messages,
        List<KnowledgeCitation> latestCitations,
        List<ToolStatusView> toolStatus,
        List<ToolConfirmationView> confirmationPrompts,
        List<FileUploadView> fileUploads) {
}
