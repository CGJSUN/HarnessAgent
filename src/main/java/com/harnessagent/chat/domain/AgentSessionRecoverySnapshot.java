package com.harnessagent.chat.domain;

import com.harnessagent.session.domain.ChatMessage;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record AgentSessionRecoverySnapshot(
        List<ChatMessage> messages,
        Set<String> agentStateScopes,
        Optional<PendingAgentExecution> pendingExecution) {

    private static final Set<String> RECOVERABLE_AGENT_SCOPE_KEYS = Set.of(
            "agent_state",
            "memory_messages",
            "toolkit_activeGroups");

    public AgentSessionRecoverySnapshot {
        messages = messages == null ? List.of() : List.copyOf(messages);
        agentStateScopes = agentStateScopes == null ? Set.of() : Set.copyOf(agentStateScopes);
        pendingExecution = pendingExecution == null ? Optional.empty() : pendingExecution;
    }

    public boolean agentStatePresent() {
        return agentStateScopes.stream().anyMatch(AgentSessionRecoverySnapshot::isAgentScopeStateScope);
    }

    public static boolean isAgentScopeStateScope(String scope) {
        if (scope == null || !scope.startsWith("agentscope:")) {
            return false;
        }
        int index = scope.lastIndexOf(':');
        if (index < 0 || index == scope.length() - 1) {
            return false;
        }
        return RECOVERABLE_AGENT_SCOPE_KEYS.contains(scope.substring(index + 1));
    }
}
