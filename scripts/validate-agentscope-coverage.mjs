#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BASELINE_DATE = "2026-06-09";
const REQUIRED_COLUMNS = ["官方页面", "官方 URL", "版本 / 更新时间", "capability 映射", "实现状态", "测试状态"];
const ALLOWED_IMPLEMENTATION_STATUSES = new Set(["已实现", "部分覆盖", "待实现", "文档/接口"]);
const BLOCKED_STATUS_WORDS = ["未评估", "未覆盖"];
const PENDING_WORDS = ["待补", "待后续", "待完善", "仍待", "后续增强", "后续接线"];

const REQUIRED_ROWS = [
  ["快速开始与核心组件", "Quickstart", "https://java.agentscope.io/v2/zh/docs/quickstart.html"],
  ["快速开始与核心组件", "Agent", "https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html"],
  ["快速开始与核心组件", "Message / Event", "https://java.agentscope.io/v2/zh/docs/building-blocks/message-and-event.html"],
  ["快速开始与核心组件", "Middleware", "https://java.agentscope.io/v2/zh/docs/building-blocks/middleware.html"],
  ["快速开始与核心组件", "Model", "https://java.agentscope.io/v2/zh/docs/building-blocks/model.html"],
  ["快速开始与核心组件", "Permission System", "https://java.agentscope.io/v2/zh/docs/building-blocks/permission-system.html"],
  ["快速开始与核心组件", "Tool", "https://java.agentscope.io/v2/zh/docs/building-blocks/tool.html"],
  ["快速开始与核心组件", "Context / AgentState", "https://java.agentscope.io/v2/zh/docs/building-blocks/context.html"],
  ["Harness 能力", "Harness architecture", "https://java.agentscope.io/v2/zh/docs/harness/architecture.html"],
  ["Harness 能力", "Workspace", "https://java.agentscope.io/v2/zh/docs/harness/workspace.html"],
  ["Harness 能力", "Filesystem", "https://java.agentscope.io/v2/zh/docs/harness/filesystem.html"],
  ["Harness 能力", "Sandbox", "https://java.agentscope.io/v2/zh/docs/harness/sandbox.html"],
  ["Harness 能力", "Memory", "https://java.agentscope.io/v2/zh/docs/harness/memory.html"],
  ["Harness 能力", "Subagent", "https://java.agentscope.io/v2/zh/docs/harness/subagent.html"],
  ["Harness 能力", "Skill", "https://java.agentscope.io/v2/zh/docs/harness/skill.html"],
  ["Harness 能力", "Plan Mode", "https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html"],
  ["Harness 能力", "Channel", "https://java.agentscope.io/v2/zh/docs/harness/channel.html"],
  ["Harness 能力", "Compaction", "https://java.agentscope.io/v2/zh/docs/harness/compaction.html"],
  ["参考与集成", "Change log / Migration", "https://java.agentscope.io/v2/zh/docs/change-log.html"],
  ["参考与集成", "Going to production", "https://java.agentscope.io/v2/zh/docs/others/going-to-production.html"],
  ["参考与集成", "FAQ / Release notes", "https://java.agentscope.io/v2/zh/docs/others/faq.html"],
  ["参考与集成", "Memory integration", "https://java.agentscope.io/v2/zh/integration/memory/overview.html"],
  ["参考与集成", "RAG integration", "https://java.agentscope.io/v2/zh/integration/rag/overview.html"],
  ["参考与集成", "Session / State integration", "https://java.agentscope.io/v2/zh/integration/session/overview.html"],
  ["参考与集成", "Skill repository integration", "https://java.agentscope.io/v2/zh/integration/skill/overview.html"],
  ["参考与集成", "Protocol integration", "https://java.agentscope.io/v2/zh/integration/protocol/overview.html"],
  ["参考与集成", "Ecosystem integration", "https://java.agentscope.io/v2/zh/integration/ecosystem/overview.html"],
  ["参考与集成", "Infrastructure integration", "https://java.agentscope.io/v2/zh/integration/infrastructure/overview.html"],
];

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = dirname(scriptDir);
const coveragePath = resolve(repoRoot, "docs/agentscope-java-v2-coverage.md");
const claimComplete = process.argv.slice(2).includes("--claim-complete");

const content = readFileSync(coveragePath, "utf8");
const lines = content.split(/\r?\n/);
const errors = [];
const warnings = [];

if (!content.includes(BASELINE_DATE)) {
  errors.push(`Coverage baseline date ${BASELINE_DATE} is missing.`);
}
if (!content.includes("https://java.agentscope.io/v2/zh/docs/index.html")) {
  errors.push("Official AgentScope Java v2 docs index URL is missing.");
}

const tables = parseTables(lines);
const rows = tables.flatMap((table) => table.rows);
const rowsByKey = new Map();

for (const table of tables) {
  const missingColumns = REQUIRED_COLUMNS.filter((column) => !table.headers.includes(column));
  if (missingColumns.length > 0) {
    errors.push(`Table under "${table.section}" is missing columns: ${missingColumns.join(", ")}`);
  }
}

for (const row of rows) {
  const key = `${row.section}::${row["官方页面"]}`;
  if (rowsByKey.has(key)) {
    errors.push(`Duplicate coverage row: ${key}`);
  }
  rowsByKey.set(key, row);

  for (const column of REQUIRED_COLUMNS) {
    if (!row[column] || row[column].trim().length === 0) {
      errors.push(`${key} has an empty "${column}" cell.`);
    }
  }

  const url = row["官方 URL"] ?? "";
  if (!url.startsWith("https://java.agentscope.io/v2/zh/")) {
    errors.push(`${key} has an unexpected official URL: ${url}`);
  }

  const version = row["版本 / 更新时间"] ?? "";
  if (!version.includes("v2") || !version.includes(BASELINE_DATE)) {
    errors.push(`${key} must record v2 and ${BASELINE_DATE} in the version/date column.`);
  }

  const implementationStatus = row["实现状态"] ?? "";
  const implementationStatusName = statusName(implementationStatus);
  if (!ALLOWED_IMPLEMENTATION_STATUSES.has(implementationStatusName)) {
    errors.push(`${key} has unsupported implementation status "${implementationStatusName}".`);
  }

  for (const word of BLOCKED_STATUS_WORDS) {
    if (Object.values(row).some((value) => value.includes(word))) {
      errors.push(`${key} contains blocked status word "${word}".`);
    }
  }

  if (!claimComplete && implementationStatusName !== "已实现") {
    warnings.push(`${key} is "${implementationStatusName}", so it is not evidence for a full-coverage claim.`);
  }
  if (!claimComplete && PENDING_WORDS.some((word) => row["测试状态"]?.includes(word))) {
    warnings.push(`${key} has pending test evidence: ${row["测试状态"]}`);
  }

  if (claimComplete) {
    assertCompleteClaimEligible(key, row, implementationStatusName, errors);
  }
}

for (const [section, page, expectedUrl] of REQUIRED_ROWS) {
  const key = `${section}::${page}`;
  const row = rowsByKey.get(key);
  if (!row) {
    errors.push(`Missing required coverage row: ${key}`);
    continue;
  }
  if (row["官方 URL"] !== expectedUrl) {
    errors.push(`${key} URL mismatch. Expected ${expectedUrl}, got ${row["官方 URL"]}`);
  }
}

if (rows.length !== REQUIRED_ROWS.length) {
  errors.push(`Expected ${REQUIRED_ROWS.length} coverage rows, parsed ${rows.length}.`);
}

if (errors.length > 0) {
  console.error("AgentScope Java v2 coverage matrix validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log(`AgentScope Java v2 coverage matrix validation passed (${rows.length} rows, baseline ${BASELINE_DATE}).`);
if (claimComplete) {
  console.log("Full-coverage claim gate passed.");
} else if (warnings.length > 0) {
  console.log("Warnings:");
  for (const warning of warnings) {
    console.log(`- ${warning}`);
  }
  console.log("Run with --claim-complete only when the release is claiming full AgentScope Java v2 coverage.");
}

function parseTables(sourceLines) {
  const parsedTables = [];
  let currentSection = "";

  for (let index = 0; index < sourceLines.length; index += 1) {
    const heading = sourceLines[index].match(/^##\s+(.+)$/);
    if (heading) {
      currentSection = heading[1].trim();
      continue;
    }

    const header = splitMarkdownRow(sourceLines[index]);
    const separator = splitMarkdownRow(sourceLines[index + 1] ?? "");
    if (!header || !separator || !isSeparator(separator)) {
      continue;
    }

    const rowsInTable = [];
    let rowIndex = index + 2;
    while (rowIndex < sourceLines.length) {
      const cells = splitMarkdownRow(sourceLines[rowIndex]);
      if (!cells) {
        break;
      }
      if (cells.length !== header.length) {
        errors.push(`Malformed row in "${currentSection}" table: ${sourceLines[rowIndex]}`);
        rowIndex += 1;
        continue;
      }
      rowsInTable.push(Object.fromEntries(header.map((column, columnIndex) => [column, cells[columnIndex]])));
      rowIndex += 1;
    }

    parsedTables.push({
      section: currentSection,
      headers: header,
      rows: rowsInTable.map((row) => ({ section: currentSection, ...row })),
    });
    index = rowIndex - 1;
  }

  return parsedTables;
}

function splitMarkdownRow(line) {
  const trimmed = line.trim();
  if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) {
    return null;
  }
  return trimmed.slice(1, -1).split("|").map((cell) => cell.trim());
}

function isSeparator(cells) {
  return cells.length > 0 && cells.every((cell) => /^:?-{3,}:?$/.test(cell));
}

function statusName(value) {
  return value.split(/[：:]/)[0].trim();
}

function assertCompleteClaimEligible(key, row, implementationStatusName, targetErrors) {
  if (implementationStatusName === "部分覆盖" || implementationStatusName === "待实现") {
    targetErrors.push(`${key} is "${implementationStatusName}" and cannot support a full-coverage claim.`);
  }
  if (PENDING_WORDS.some((word) => Object.values(row).some((value) => value.includes(word)))) {
    targetErrors.push(`${key} still contains pending follow-up wording.`);
  }
  if (implementationStatusName === "文档/接口") {
    const explanation = row["实现状态"] ?? "";
    const documentsBoundary = ["文档", "接口", "预留", "扩展", "非目标"].some((word) => explanation.includes(word));
    if (!documentsBoundary) {
      targetErrors.push(`${key} is documented/interface-only but does not explain the boundary.`);
    }
  }
}
