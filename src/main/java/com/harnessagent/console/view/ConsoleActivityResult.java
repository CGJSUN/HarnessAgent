package com.harnessagent.console.view;

import com.harnessagent.security.persistence.SecurityActivityRecord;
import com.harnessagent.tooling.activity.ToolActivityRecord;
import java.util.List;

public record ConsoleActivityResult(
        List<ToolActivityRecord> toolActivity,
        List<SecurityActivityRecord> securityActivity) {
}
