package com.harnessagent.console;

import com.harnessagent.security.SecurityAuditRecord;
import com.harnessagent.tooling.ToolAuditRecord;
import java.util.List;

public record ConsoleAuditResult(
        List<ToolAuditRecord> toolAudit,
        List<SecurityAuditRecord> securityAudit) {
}
