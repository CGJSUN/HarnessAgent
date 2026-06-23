package com.harnessagent.workspace.application;

import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.workspace.domain.PersonalPlan;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PlanModeService {

    public static final String PLAN_MODE_PARAMETER = "__planMode";

    private final PersonalWorkspaceService workspaceService;

    public PlanModeService(PersonalWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public PersonalPlan createPlan(RuntimeContextScope context, String goal, List<String> steps) {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("plan goal is required");
        }
        List<String> safeSteps = steps == null ? List.of() : steps.stream()
                .filter(step -> step != null && !step.isBlank())
                .map(String::trim)
                .toList();
        if (safeSteps.isEmpty()) {
            throw new IllegalArgumentException("plan steps are required");
        }
        String id = "plan-" + sha256(context.userId()
                + ":" + context.agentId()
                + ":" + context.sessionId()
                + ":" + goal).substring(0, 16);
        String relativePath = "plans/" + id + ".md";
        String uri = "workspace://" + relativePath;
        PersonalPlan plan = new PersonalPlan(
                id,
                context.userId(),
                context.agentId(),
                context.sessionId(),
                goal,
                safeSteps,
                uri,
                Instant.now());
        writePlan(context, relativePath, plan);
        return plan;
    }

    public Map<String, Object> planModeParameters(Map<String, Object> parameters) {
        java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>(
                parameters == null ? Map.of() : parameters);
        copy.put(PLAN_MODE_PARAMETER, true);
        return Map.copyOf(copy);
    }

    public List<PersonalPlan> listPlans(RuntimeContextScope context) {
        PersonalWorkspaceLayout layout = workspaceService.initialize(context);
        try (var paths = Files.list(layout.plansDirectory())) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .map(path -> readPlan(context, path))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list personal plans.", ex);
        }
    }

    public PersonalPlan readPlan(RuntimeContextScope context, String planId) {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId is required");
        }
        PersonalWorkspaceLayout layout = workspaceService.initialize(context);
        Path file = layout.plansDirectory().resolve(planId.trim() + ".md").toAbsolutePath().normalize();
        if (!file.startsWith(layout.plansDirectory())) {
            throw new IllegalArgumentException("plan file escapes workspace plans directory");
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("plan file does not exist");
        }
        return readPlan(context, file);
    }

    public static boolean planModeRequested(Map<String, Object> parameters) {
        Object value = parameters == null ? null : parameters.get(PLAN_MODE_PARAMETER);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    public static Map<String, Object> stripPlanModeParameter(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty() || !parameters.containsKey(PLAN_MODE_PARAMETER)) {
            return parameters == null ? Map.of() : parameters;
        }
        java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>(parameters);
        copy.remove(PLAN_MODE_PARAMETER);
        return Map.copyOf(copy);
    }

    private void writePlan(RuntimeContextScope context, String relativePath, PersonalPlan plan) {
        PersonalWorkspaceLayout layout = workspaceService.initialize(context);
        Path file = layout.root().resolve(relativePath).toAbsolutePath().normalize();
        if (!file.startsWith(layout.plansDirectory())) {
            throw new IllegalStateException("Plan file escapes workspace plans directory.");
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, markdown(plan), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write personal plan.", ex);
        }
    }

    private PersonalPlan readPlan(RuntimeContextScope context, Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String fileName = file.getFileName().toString();
            String id = fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;
            String goal = readPrefixedLine(lines, "# Plan: ", id);
            String ownerId = readPrefixedLine(lines, "- Owner: ", context.userId());
            String agentId = readPrefixedLine(lines, "- Agent: ", context.agentId());
            String sessionId = readPrefixedLine(lines, "- Session: ", context.sessionId());
            Pattern stepPattern = Pattern.compile("^\\d+\\.\\s+(.+)$");
            List<String> steps = lines.stream()
                    .map(stepPattern::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group(1).trim())
                    .filter(step -> !step.isBlank())
                    .toList();
            if (steps.isEmpty()) {
                steps = List.of("Review plan file " + fileName);
            }
            return new PersonalPlan(
                    id,
                    ownerId,
                    agentId,
                    sessionId,
                    goal,
                    steps,
                    "workspace://plans/" + fileName,
                    Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read personal plan.", ex);
        }
    }

    private static String readPrefixedLine(List<String> lines, String prefix, String fallback) {
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private static String markdown(PersonalPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Plan: ").append(plan.goal()).append("\n\n");
        builder.append("- Owner: ").append(plan.ownerId()).append("\n");
        builder.append("- Agent: ").append(plan.agentId()).append("\n");
        builder.append("- Session: ").append(plan.sessionId()).append("\n");
        builder.append("- Mode: read-only plan\n\n");
        for (int index = 0; index < plan.steps().size(); index++) {
            builder.append(index + 1).append(". ").append(plan.steps().get(index)).append("\n");
        }
        return builder.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available.", ex);
        }
    }
}
