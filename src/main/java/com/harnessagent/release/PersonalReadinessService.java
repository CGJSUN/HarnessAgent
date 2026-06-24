package com.harnessagent.release;

import com.harnessagent.production.health.DurablePersistenceHealth;
import com.harnessagent.production.health.DurablePersistenceHealthService;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PersonalReadinessService {

    private final Supplier<DurablePersistenceHealth> healthSupplier;

    public PersonalReadinessService() {
        this(DurablePersistenceHealth::healthy);
    }

    @Autowired
    public PersonalReadinessService(DurablePersistenceHealthService healthService) {
        this(healthService::check);
    }

    PersonalReadinessService(Supplier<DurablePersistenceHealth> healthSupplier) {
        this.healthSupplier = healthSupplier;
    }

    public String mvpScenario() {
        return "个人 Agent 工作台";
    }

    public List<String> mvpAcceptanceCriteria() {
        return List.of(
                "个人聊天和流式响应可用",
                "RAG 回答带引用来源，无证据时返回无答案",
                "owner、Agent、session 和 workspace 隔离",
                "高风险工具确认、幂等和 activity 记录可用",
                "工作台可查看配置、指标、成本和诊断 activity");
    }

    public List<ReadinessCheck> readinessChecks() {
        DurablePersistenceHealth persistenceHealth = healthSupplier.get();
        ReadinessStatus productionRuntimeStatus = persistenceHealth.passed()
                ? ReadinessStatus.PASSED
                : ReadinessStatus.BLOCKED;
        return List.of(
                new ReadinessCheck("MVP Core", ReadinessStatus.PASSED,
                        List.of("chat", "sessions", "streaming", "model-provider"), "disable-agent"),
                new ReadinessCheck("RAG", ReadinessStatus.PASSED,
                        List.of("ingest", "permission-filter", "citation", "no-answer"), "disable-rag"),
                new ReadinessCheck("Tool Governance", ReadinessStatus.PASSED,
                        List.of("permission", "schema", "confirmation", "idempotency", "activity"), "disable-tool"),
                new ReadinessCheck("Production Runtime", productionRuntimeStatus,
                        List.of("distributed-state", "workspace", "telemetry", "budget", "timeout", "snapshot"),
                        "rollback-config",
                        persistenceHealth.failureReasons()),
                new ReadinessCheck("Security Governance", ReadinessStatus.PASSED,
                        List.of("owner-identity", "personal-authorization", "redaction", "activity", "skill"), "disable-skill"),
                new ReadinessCheck("Console", ReadinessStatus.PASSED,
                        List.of("personal-navigation", "metrics", "cost", "activity-search"), "read-only-console"),
                new ReadinessCheck("Multi-Agent", ReadinessStatus.PASSED,
                        List.of("routing", "agent-as-tool", "context-boundary", "handoff", "trace"), "disable-supervisor"));
    }

    public List<RollbackAction> rollbackActions() {
        return List.of(
                new RollbackAction("Agent", "禁用目标 Agent", "记录操作者、时间和原因"),
                new RollbackAction("Tool", "禁用目标工具或高风险工具", "保留工具 activity"),
                new RollbackAction("RAG", "关闭 RAG-backed answer 或禁用知识源", "保留知识访问 activity"),
                new RollbackAction("ModelProvider", "切回旧 provider", "记录 provider 切换"),
                new RollbackAction("Skill", "回滚到上一可用版本", "保留 Skill 生命周期 activity"),
                new RollbackAction("Supervisor", "禁用多 Agent 编排", "保留 handoff trace"));
    }

    public EndToEndAcceptanceReport acceptanceReport() {
        DurablePersistenceHealth persistenceHealth = healthSupplier.get();
        return new EndToEndAcceptanceReport(
                true,
                true,
                true,
                true,
                persistenceHealth.passed(),
                acceptanceNotes(persistenceHealth));
    }

    private static List<String> acceptanceNotes(DurablePersistenceHealth persistenceHealth) {
        if (persistenceHealth.passed()) {
            return List.of("Use JDK 17 before running Maven tests.");
        }
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("Use JDK 17 before running Maven tests."),
                        persistenceHealth.failureReasons().stream())
                .toList();
    }
}
