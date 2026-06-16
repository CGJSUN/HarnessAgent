package com.harnessagent.console.view;

import com.harnessagent.security.persistence.SecurityAuditRecord;
import com.harnessagent.tooling.audit.ToolAuditRecord;
import java.util.List;

public record ConsoleAuditResult(
        List<ToolAuditRecord> toolAudit,
        List<SecurityAuditRecord> securityAudit) {
}
