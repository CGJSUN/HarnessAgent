package com.harnessagent.chat.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.chat.domain.AgentSessionRecoverySnapshot;
import com.harnessagent.chat.domain.PendingAgentExecution;
import com.harnessagent.production.health.ProductionRuntimeValidator;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.AgentStateStoreFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.persistence.SessionStore;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AgentSessionRecoveryService {

    static final String PENDING_EXECUTION_SCOPE = "harness:pending-execution";

    private final SessionStore sessionStore;
    private final ProductionRuntimeValidator runtimeValidator;
    private final AgentStateStoreFactory stateStoreFactory;
    private final ObjectMapper objectMapper;

    public AgentSessionRecoveryService(
            SessionStore sessionStore,
            ProductionRuntimeValidator runtimeValidator,
            AgentStateStoreFactory stateStoreFactory) {
        this(sessionStore, runtimeValidator, stateStoreFactory, new ObjectMapper().findAndRegisterModules());
    }

    public AgentSessionRecoveryService(
            SessionStore sessionStore,
            ProductionRuntimeValidator runtimeValidator,
            AgentStateStoreFactory stateStoreFactory,
            ObjectMapper objectMapper) {
        this.sessionStore = sessionStore;
        this.runtimeValidator = runtimeValidator;
        this.stateStoreFactory = stateStoreFactory;
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
    }

    public static AgentSessionRecoveryService noop(SessionStore sessionStore) {
        return new NoopAgentSessionRecoveryService(sessionStore);
    }

    public PendingAgentExecution markPending(
            RuntimeContextScope context,
            String mode,
            String userMessageId) {
        PendingAgentExecution pending = PendingAgentExecution.pending(
                mode,
                userMessageId,
                context.runtimeUserId(),
                context.runtimeSessionId());
        stateStore().save(context, PENDING_EXECUTION_SCOPE, write(pending));
        return pending;
    }

    public Optional<PendingAgentExecution> pendingExecution(RuntimeContextScope context) {
        return stateStore().load(context, PENDING_EXECUTION_SCOPE)
                .map(entry -> read(entry.value()));
    }

    public boolean agentScopeStatePresent(RuntimeContextScope context) {
        String scopePrefix = agentScopeStatePrefix(context);
        return stateStore().listSessionScopes(context).stream()
                .anyMatch(scope -> scope != null
                        && scope.startsWith(scopePrefix)
                        && AgentSessionRecoverySnapshot.isAgentScopeStateScope(scope));
    }

    public void clearPending(RuntimeContextScope context) {
        stateStore().delete(context, PENDING_EXECUTION_SCOPE);
    }

    public AgentSessionRecoverySnapshot snapshot(RuntimeContextScope context) {
        AgentStateStore store = stateStore();
        return new AgentSessionRecoverySnapshot(
                sessionStore.listMessages(context),
                store.listSessionScopes(context),
                store.load(context, PENDING_EXECUTION_SCOPE).map(entry -> read(entry.value())));
    }

    private AgentStateStore stateStore() {
        return stateStoreFactory.create(runtimeValidator.stateStorePlan());
    }

    private String write(PendingAgentExecution pending) {
        try {
            return objectMapper.writeValueAsString(pending);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize pending execution state", ex);
        }
    }

    private PendingAgentExecution read(String value) {
        try {
            return objectMapper.readValue(value, PendingAgentExecution.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize pending execution state", ex);
        }
    }

    private static String agentScopeStatePrefix(RuntimeContextScope context) {
        return "agentscope:" + context.runtimeUserId() + ":" + context.runtimeSessionId() + ":";
    }

    private static final class NoopAgentSessionRecoveryService extends AgentSessionRecoveryService {

        private final SessionStore sessionStore;

        private NoopAgentSessionRecoveryService(SessionStore sessionStore) {
            super(sessionStore, new ProductionRuntimeValidator(new com.harnessagent.production.config.ProductionRuntimeProperties()),
                    plan -> new com.harnessagent.production.infrastructure.InMemoryAgentStateStore(
                            new com.harnessagent.production.state.OwnerStateKeyStrategy(),
                            plan));
            this.sessionStore = sessionStore;
        }

        @Override
        public PendingAgentExecution markPending(RuntimeContextScope context, String mode, String userMessageId) {
            return PendingAgentExecution.pending(mode, userMessageId, context.runtimeUserId(), context.runtimeSessionId());
        }

        @Override
        public Optional<PendingAgentExecution> pendingExecution(RuntimeContextScope context) {
            return Optional.empty();
        }

        @Override
        public boolean agentScopeStatePresent(RuntimeContextScope context) {
            return false;
        }

        @Override
        public void clearPending(RuntimeContextScope context) {
        }

        @Override
        public AgentSessionRecoverySnapshot snapshot(RuntimeContextScope context) {
            return new AgentSessionRecoverySnapshot(sessionStore.listMessages(context), java.util.Set.of(), Optional.empty());
        }
    }
}
