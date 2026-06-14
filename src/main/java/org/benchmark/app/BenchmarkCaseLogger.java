package org.benchmark.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.benchmark.llm.LlmClient;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles console and JSONL logging for one benchmark case.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BenchmarkCaseLogger {

    private final BenchmarkProperties properties;
    private final SessionStateManager stateManager;
    private final BenchmarkEventLogger eventLogger;

    public void logCaseStart(int index,
                             String sessionId,
                             String userQuery,
                             BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {

        eventLogger.blankLine();
        eventLogger.blankLine();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        payload.put("eventType", "case_started");
        payload.put("caseIndex", index);
        payload.put("sessionId", sessionId);
        payload.put("userQuery", userQuery);
        payload.put("targetTool", benchmarkCase.targetToolObject().name());
        payload.put("scenarioPattern", benchmarkCase.scenario().patternName());
        payload.put("targetSteps", benchmarkCase.targetSteps().stream()
                .map(s -> formatTargetStep(benchmarkCase.targetToolObject(),
                        CommandAbbreviator.commandName(s.verb(), s.noun())))
                .toList());
        payload.put("expectedState", benchmarkCase.expectedState());
        payload.put("candidateTools", benchmarkCase.allTools().stream().map(ToolObject::name).toList());
        payload.put("semanticDecoys", benchmarkCase.semanticDecoys().stream().map(ToolObject::name).toList());
        payload.put("randomDistractors", benchmarkCase.randomDistractors().stream().map(ToolObject::name).toList());
        payload.put("documentComplexity", properties.getDocumentComplexity());
        CommandObject trap = benchmarkCase.trapCommand();
        if (trap != null) {
            payload.put("trapCommand", trap.name());
            payload.put("trapDocumentedEffect", formatEffects(trap.documentedEffects()));
            payload.put("trapRealEffect", formatEffects(trap.commandEffectObjects()));
            payload.put("recoveryCommand", benchmarkCase.recoveryCommandName());
        }
        logEvent(payload);

        String steps = benchmarkCase.targetSteps().stream()
                .map(s -> formatTargetStep(benchmarkCase.targetToolObject(),
                        CommandAbbreviator.commandName(s.verb(), s.noun())))
                .collect(Collectors.joining(" -> "));
        log.info("\n============================================================");
        log.info(" Run {}: {}", index,userQuery);
        log.info(" tool={} | scenario={}  | steps=[{}] | tools={} | decoys={} | distractors={}",
                benchmarkCase.targetToolObject().name(),
                benchmarkCase.scenario().patternName(),
                steps,
                benchmarkCase.allTools().size(),
                benchmarkCase.semanticDecoys().size(),
                benchmarkCase.randomDistractors().size());
        if (trap != null) {
            log.info("   [TRAP] cmd={} | claims: {} | actually: {} | recovery={}",
                    trap.name(),
                    formatEffects(trap.documentedEffects()),
                    formatEffects(trap.commandEffectObjects()),
                    benchmarkCase.recoveryCommandName());
        }
        log.info("============================================================\n");
    }

    public void logAttemptStarted(String sessionId, int attempt, String conversationHistory) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        eventLogger.blankLine();
        payload.put("eventType", "attempt_started");
        payload.put("sessionId", sessionId);
        payload.put("attempt", attempt);
        payload.put("currentState", stateManager.getSessionStateSnapshot(sessionId));
        payload.put("historyLength", conversationHistory.length());
        logEvent(payload);
    }

    public void logAttemptCompleted(String sessionId,
                                    int attempt,
                                    long timeTaken,
                                    LlmClient.LlmResult result,
                                    List<ExecutionRecord> newExecutions,
                                    String attemptFeedback,
                                    int discoveryCallsThisAttempt,
                                    BenchmarkScorer.AttemptMetrics metrics) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        payload.put("eventType", "attempt_completed");
        payload.put("sessionId", sessionId);
        payload.put("attempt", attempt);
        payload.put("latencyMs", timeTaken);
        payload.put("tokenUsage", result.tokenUsage());
        payload.put("assistantResponse", result.content());
        payload.put("newExecutions", newExecutions);
        payload.put("attemptFeedback", attemptFeedback);
        payload.put("discoveryCallsThisAttempt", discoveryCallsThisAttempt);
        payload.put("toolSelection", metrics.toolSelection());
        payload.put("stepCompletionRate", metrics.stepCompletionRate());
        payload.put("orderingAccuracy", metrics.orderingAccuracy());
        payload.put("stateAccuracy", metrics.stateAccuracy());
        payload.put("efficiency", metrics.efficiency());
        payload.put("commandPrecision", metrics.commandPrecision());
        payload.put("scenarioComplete", metrics.scenarioComplete());
        payload.put("goalAchieved", metrics.goalAchieved());
        payload.put("discoveryCallsTotal", metrics.discoveryCallsTotal());
        payload.put("executionsTotal", metrics.executionsTotal());
        payload.put("currentState", stateManager.getSessionStateSnapshot(sessionId));
        logEvent(payload);

        log.info("  [ATTEMPT {}] latency={}, tokens={}",
                attempt, timeTaken, result.tokenUsage());
        for (int i = 0; i < newExecutions.size(); i++) {
            ExecutionRecord record = newExecutions.get(i);
            log.info("    [EXEC {}.{}] {} tool={} command={} option={} message={}",
                    attempt,
                    i + 1,
                    record.success() ? "SUCCESS" : "ERROR",
                    record.toolName(),
                    record.commandName(),
                    record.option().isBlank() ? "<none>" : record.option(),
                    record.message());
        }
    }

    public void logCaseCompleted(String sessionId,
                                 BenchmarkScorer.BenchmarkScore score,
                                 long totalTimeTaken,
                                 int totalTokenUsage,
                                 int attemptsUsed) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = stateManager.getBenchmarkCase(sessionId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        payload.put("eventType", "case_completed");
        payload.put("model", properties.getLlm().getModel());
        payload.put("sessionId", sessionId);
        payload.put("passed", score.passed());
        payload.put("scenarioComplete", score.stepCompletionRate() == 1.0
                && score.orderingAccuracy() == 1.0);
        payload.put("score", score);
        payload.put("compositeScore", score.composite());
        payload.put("attemptsUsed", attemptsUsed);
        payload.put("discoveryCalls", stateManager.discoveryCount(sessionId));
        payload.put("executionCount", stateManager.executionLog(sessionId).size());
        payload.put("totalLatencyMs", totalTimeTaken);
        payload.put("totalTokenUsage", totalTokenUsage);
        payload.put("finalStateScore", score.stateAccuracy());
        payload.put("commandPrecision", score.commandPrecision());
        payload.put("targetToolState", stateManager.getToolStateSnapshot(sessionId, benchmarkCase.targetToolObject().name()));
        payload.put("sessionState", stateManager.getSessionStateSnapshot(sessionId));
        payload.put("executionLog", stateManager.executionLog(sessionId));
        logEvent(payload);

        eventLogger.blankLine();

        log.info("   [CASE RESULT]");
        log.info("     Status:        {}", score.passed() ? "SUCCESS" : "FAILURE");
        log.info("     Recovery:      {}", score.recovery());
        log.info("     Attempts Used: {}/{}", attemptsUsed, properties.getMaxRetries());
        log.info("     Discovery:     {} call(s)", stateManager.discoveryCount(sessionId));
        log.info("     Executions:    {} total", stateManager.executionLog(sessionId).size());
        log.info("     Scores:        composite={}, tool={}, steps={}, ordering={}, state={}, efficiency={}, precision={}",
                fmt(score.composite(), 3),
                fmt(score.toolSelection(), 1),
                fmt(score.stepCompletionRate(), 2),
                fmt(score.orderingAccuracy(), 2),
                fmt(score.stateAccuracy(), 2),
                fmt(score.efficiency(), 2),
                fmt(score.commandPrecision(), 2));
    }

    private void logEvent(Map<String, Object> payload) {
        eventLogger.logPayload(payload);
    }

    private String fmt(double value, int scale) {
        return String.format("%." + scale + "f", value);
    }

    private String formatEffects(List<EffectObject> effects) {
        if (effects == null || effects.isEmpty()) return "{}";
        return effects.stream()
                .map(e -> e.variable() + " " + e.operation().name()
                        + (e.valueRef() != null ? "=" + e.valueRef() : ""))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private String formatTargetStep(ToolObject targetTool, String commandName) {
        return targetTool.commands().stream()
                .filter(command -> command.name().equalsIgnoreCase(commandName))
                .findFirst()
                .map(command -> commandName + formatOptions(command.commandOptions()))
                .orElse(commandName + "{options=[]}");
    }

    private String formatOptions(List<OptionEntity> options) {
        if (options == null || options.isEmpty()) {
            return "{options=[]}";
        }
        return options.stream()
                .map(OptionEntity::optionName)
                .collect(Collectors.joining(", ", "{options=[", "]}"));
    }
}
