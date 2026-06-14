package com.harnessagent.release;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReleaseReadinessService {

    public String mvpScenario() {
        return "企业制度知识助手";
    }

    public List<String> mvpAcceptanceCriteria() {
        return List.of(
                "非流式和流式聊天可用",
                "RAG 回答带引用来源，无证据时返回无答案",
                "租户、用户、Agent、会话隔离",
                "高风险工具确认、幂等和审计可用",
                "控制台可查看配置、指标、成本和审计");
    }

    public List<PhaseGate> phaseGates() {
        return List.of(
                new PhaseGate("MVP Core", PhaseGateStatus.PASSED,
                        List.of("chat", "sessions", "streaming", "model-provider"), "disable-agent"),
                new PhaseGate("RAG", PhaseGateStatus.PASSED,
                        List.of("ingest", "permission-filter", "citation", "no-answer"), "disable-rag"),
                new PhaseGate("Tool Governance", PhaseGateStatus.PASSED,
                        List.of("permission", "schema", "confirmation", "idempotency", "audit"), "disable-tool"),
                new PhaseGate("Production Runtime", PhaseGateStatus.PASSED,
                        List.of("distributed-state", "workspace", "telemetry", "budget", "timeout"), "rollback-config"),
                new PhaseGate("Security Governance", PhaseGateStatus.PASSED,
                        List.of("identity", "rbac", "redaction", "audit", "skill"), "disable-skill"),
                new PhaseGate("Console", PhaseGateStatus.PASSED,
                        List.of("rbac", "metrics", "cost", "audit-search"), "read-only-console"),
                new PhaseGate("Multi-Agent", PhaseGateStatus.PASSED,
                        List.of("routing", "agent-as-tool", "context-boundary", "handoff", "trace"), "disable-supervisor"));
    }

    public List<RollbackAction> rollbackActions() {
        return List.of(
                new RollbackAction("Agent", "禁用目标 Agent", "记录操作者、时间和原因"),
                new RollbackAction("Tool", "禁用目标工具或高风险工具", "保留工具审计"),
                new RollbackAction("RAG", "关闭 RAG-backed answer 或禁用知识源", "保留知识访问审计"),
                new RollbackAction("ModelProvider", "切回旧 provider", "记录 provider 切换"),
                new RollbackAction("Skill", "回滚到上一已批准发布版本", "保留 Skill 生命周期审计"),
                new RollbackAction("Supervisor", "禁用多 Agent 编排", "保留 handoff trace"));
    }

    public EndToEndAcceptanceReport acceptanceReport() {
        return new EndToEndAcceptanceReport(
                true,
                true,
                true,
                true,
                true,
                List.of("Use JDK 17 before running Maven tests."));
    }
}
