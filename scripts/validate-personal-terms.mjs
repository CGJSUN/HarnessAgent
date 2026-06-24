#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const TERMS = [
  { label: "tenant", pattern: /\btenant(?:Id|_id|s)?\b/gi },
  { label: "rbac", pattern: /\brbac\b/gi },
  { label: "role", pattern: /\broles\b|\ballowedRoles\b|\ballowed_roles_json\b|\bX-Roles\b|\badmin role\b|\bops role\b|\bauditor role\b|\brole required\b|企业角色|部门\/角色|角色权限/gi },
  { label: "department", pattern: /\bdepartments?\b/gi },
  { label: "admin", pattern: /\badmin(?:istrator)?\b/gi },
  { label: "ops", pattern: /\bops\b|\bOperationsWorkspace\b|\boperations workspace\b|运维/gi },
  { label: "audit", pattern: /\baudit(?:or|ing)?\b/gi },
  { label: "release-gate", pattern: /\brelease\s+gate\b|\bphase\s+gate\b|\brelease[-_]?gate\b|\bphase[-_]?gate\b/gi },
  { label: "enterprise", pattern: /\bEnterprise[A-Za-z0-9_]*\b|\benterprise-assistant\b|\benterprise\s+console\b|\benterprise-shaped\b|企业平台|企业助手/gi }
];

const TEXT_EXTENSIONS = new Set([
  ".css", ".html", ".java", ".js", ".json", ".md", ".mjs", ".sql", ".ts", ".tsx", ".txt", ".xml", ".yaml", ".yml"
]);

const args = new Set(process.argv.slice(2));
const inventoryMode = args.has("--inventory");
const jsonMode = args.has("--json");
const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = dirname(scriptDir);
const configPath = resolve(scriptDir, "enterprise-term-guardrail.config.json");
const config = JSON.parse(readFileSync(configPath, "utf8"));

const ignoredPatterns = (config.ignoredPathPatterns ?? []).map(compileGlob);
const allowedPatterns = (config.allowedPathPatterns ?? []).map((entry) => ({
  pattern: entry.pattern,
  reason: entry.reason,
  matcher: compileGlob(entry.pattern)
}));

const matches = [];
for (const scanPath of config.checkedPaths ?? []) {
  const absolutePath = resolve(repoRoot, scanPath);
  if (!existsSync(absolutePath)) {
    continue;
  }
  scan(absolutePath);
}

const blocked = matches.filter((match) => !match.allowed);
const summary = summarize(matches);

if (jsonMode) {
  nodeWrite(JSON.stringify({ summary, blocked, matches }, null, 2));
} else if (inventoryMode) {
  printInventory(summary, matches);
} else if (blocked.length > 0) {
  console.error(`Enterprise-term guardrail failed: ${blocked.length} unallowed matches.`);
  printBlocked(blocked);
  console.error("Run `node scripts/validate-personal-terms.mjs --inventory` for a categorized inventory.");
  process.exit(1);
} else {
  console.log(`Enterprise-term guardrail passed (${matches.length} matches, all allowlisted).`);
}

function scan(path) {
  const repoPath = toRepoPath(path);
  if (ignoredPatterns.some((matcher) => matcher(repoPath))) {
    return;
  }
  const stats = statSync(path);
  if (stats.isDirectory()) {
    for (const child of readdirSync(path)) {
      scan(join(path, child));
    }
    return;
  }
  if (!stats.isFile() || !isTextFile(path)) {
    return;
  }

  const content = readFileSync(path, "utf8");
  const lines = content.split(/\r?\n/);
  lines.forEach((line, index) => {
    for (const term of TERMS) {
      term.pattern.lastIndex = 0;
      let match;
      while ((match = term.pattern.exec(line)) !== null) {
        const allowEntry = allowedPatterns.find((entry) => entry.matcher(repoPath));
        matches.push({
          category: categorize(repoPath),
          term: term.label,
          text: match[0],
          path: repoPath,
          line: index + 1,
          allowed: Boolean(allowEntry),
          reason: allowEntry?.reason ?? "",
          preview: line.trim().slice(0, 180)
        });
      }
    }
  });
}

function isTextFile(path) {
  const dot = path.lastIndexOf(".");
  const extension = dot === -1 ? "" : path.slice(dot).toLowerCase();
  return TEXT_EXTENSIONS.has(extension);
}

function toRepoPath(path) {
  return relative(repoRoot, path).split(sep).join("/");
}

function categorize(path) {
  if (path.startsWith("src/main/java/com/harnessagent/api/") || path.startsWith("web/src/api/")) {
    return "public-api";
  }
  if (path.startsWith("src/main/resources/db/") || path.includes("/persistence/") || path.includes("/infrastructure/")) {
    return "persistence";
  }
  if (path.startsWith("src/main/java/")) {
    return "domain-service";
  }
  if (path.startsWith("web/src/") || path.startsWith("web/tests/")) {
    return "frontend";
  }
  if (path.startsWith("src/test/")) {
    return "tests";
  }
  if (path.startsWith("docs/") || path === "README.md" || path === "AGENTS.md" || path === "web/README.md") {
    return "docs";
  }
  return "other";
}

function summarize(items) {
  const byCategory = {};
  const byTerm = {};
  let allowed = 0;
  let blockedCount = 0;
  for (const item of items) {
    byCategory[item.category] = (byCategory[item.category] ?? 0) + 1;
    byTerm[item.term] = (byTerm[item.term] ?? 0) + 1;
    if (item.allowed) {
      allowed += 1;
    } else {
      blockedCount += 1;
    }
  }
  return {
    total: items.length,
    allowed,
    blocked: blockedCount,
    byCategory,
    byTerm
  };
}

function printInventory(summary, items) {
  console.log("Enterprise-term inventory");
  console.log(`Total matches: ${summary.total}`);
  console.log(`Allowed matches: ${summary.allowed}`);
  console.log(`Blocked matches: ${summary.blocked}`);
  printMap("By category", summary.byCategory);
  printMap("By term", summary.byTerm);
  console.log("");
  console.log("Blocked samples:");
  printBlocked(items.filter((item) => !item.allowed), 30);
}

function printMap(title, map) {
  console.log("");
  console.log(`${title}:`);
  for (const [key, value] of Object.entries(map).sort((left, right) => right[1] - left[1])) {
    console.log(`- ${key}: ${value}`);
  }
}

function printBlocked(items, limit = 50) {
  for (const item of items.slice(0, limit)) {
    console.error(`- ${item.path}:${item.line} [${item.category}/${item.term}] ${item.preview}`);
  }
  if (items.length > limit) {
    console.error(`... ${items.length - limit} more blocked matches`);
  }
}

function compileGlob(pattern) {
  const source = pattern
    .replace(/[.+^${}()|[\]\\]/g, "\\$&")
    .replace(/\*\*/g, "\u0000")
    .replace(/\*/g, "[^/]*")
    .replace(/\u0000/g, ".*");
  return (path) => new RegExp(`^${source}$`).test(path);
}

function nodeWrite(text) {
  process.stdout.write(`${text}\n`);
}
