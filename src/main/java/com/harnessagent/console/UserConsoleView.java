package com.harnessagent.console;

import com.harnessagent.rag.KnowledgeCitation;
import com.harnessagent.session.ChatMessage;
import com.harnessagent.session.SessionSummary;
import java.util.List;

public record UserConsoleView(
        List<SessionSummary> sessions,
        List<ChatMessage> messages,
        List<KnowledgeCitation> latestCitations,
        List<ToolStatusView> toolStatus,
        List<ToolConfirmationView> confirmationPrompts,
        List<FileUploadView> fileUploads) {
}
