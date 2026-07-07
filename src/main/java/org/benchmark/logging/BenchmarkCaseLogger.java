package org.benchmark.logging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.CommandOptionNormalizer;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.llm.LlmClient;
import org.benchmark.scoring.BenchmarkScorer;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles console and JSONL logging for one benchmark case.
 *
 * <p>This logger translates runtime objects into human-readable console output and
 * structured event payloads so benchmark execution leaves an inspectable audit trail.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BenchmarkCaseLogger {

    private final BenchmarkProperties properties;
    private final SessionStateManager stateManager;
    private final BenchmarkEventLogger eventLogger;

    /**
     * Records the metadata for a newly started benchmark case.
     *
     * @param index one-based case index
     * @param sessionId active benchmark session identifier
     * @param userQuery generated user request
     * @param benchmarkCase generated benchmark case
     */
    public void logCaseStart(int index,
                             String sessionId,
                             String userQuery,
                             BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        eventLogger.blankLine();
        eventLogger.blankLine();
        Map<String, Object> payload = eventLogger.newEventPayload("case_started");
        payload.put("caseIndex", index);
        payload.put("sessionId", sessionId);
        payload.put("userQuery", userQuery);
        payload.put("scenarioPattern", benchmarkCase.scenario().patternName());
        payload.put("targetTools", targetToolNames(benchmarkCase));
        payload.put("targetPath", formatTargetPath(benchmarkCase));
        payload.put("targetSteps", formatTargetPath(benchmarkCase));
        payload.put("expectedState", benchmarkCase.expectedState());
        payload.put("expectedStateByTool", benchmarkCase.expectedStateByTool());
        payload.put("expectedSharedState", benchmarkCase.expectedSharedState());
        payload.put("semanticDecoys", benchmarkCase.semanticDecoys().stream().map(ToolObject::name).toList());
        payload.put("targetLikeWrongTools", targetLikeWrongToolPairs(benchmarkCase));
        payload.put("randomDistractors", benchmarkCase.randomDistractors().stream().map(ToolObject::name).toList());
        payload.put("documentComplexity", properties.getDocumentComplexity());
        CommandObject trap = benchmarkCase.trapCommand();
        if (trap != null) {
            payload.put("trapCommand", trap.name());
            payload.put("trapDocumentedEffect", formatEffects(trap.documentedEffects()));
            payload.put("trapRealEffect", formatEffects(trap.commandEffectObjects()));
            payload.put("recoveryCommand", benchmarkCase.recoveryCommandName());
        }
        eventLogger.logPayload(payload);

        log.info("""
                Case {}
                sessionId: {}
                query: {}
                targetTools: {}
                scenario: {}
                targetPath:
                {}
                availableTools: {}
                targetLikeWrongTools:
                {}
                randomDistractors: {}
                """,
                index,
                sessionId,
                userQuery,
                targetToolNames(benchmarkCase),
                benchmarkCase.scenario().patternName(),
                formatTargetSteps(benchmarkCase),
                benchmarkCase.allTools().size(),
                formatTargetLikeWrongTools(benchmarkCase),
                benchmarkCase.randomDistractors().size());
        if (trap != null) {
            log.info("""
                    Trap details
                    command: {}
                    documentedEffect: {}
                    actualEffect: {}
                    recoveryCommand: {}
                    """,
                    trap.name(),
                    formatEffects(trap.documentedEffects()),
                    formatEffects(trap.commandEffectObjects()),
                    benchmarkCase.recoveryCommandName());
        }
    }

    /**
     * Records the prompt and state snapshot at the beginning of one attempt.
     *
     * @param sessionId active benchmark session identifier
     * @param attempt one-based attempt number
     * @param messageHistory current multi-turn message history sent to the model
     */
    public void logAttemptStarted(String sessionId,
                                  int attempt,
                                  List<Message> messageHistory) {
        Map<String, Object> payload = eventLogger.newEventPayload("attempt_started");
        eventLogger.blankLine();
        payload.put("attempt", attempt);
        payload.put("currentState", stateManager.getSessionStateSnapshot(sessionId));
        payload.put("messageCount", messageHistory.size());
        payload.put("messages", messageHistory.stream()
                .map(message -> Map.of(
                        "messageType", message.getMessageType().name(),
                        "text", message.getText()))
                .toList());
        eventLogger.logPayload(payload);
    }

    /**
     * Records the observable outcome of one model attempt, including executions, scores, and updated state.
     *
     * @param sessionId active benchmark session identifier
     * @param attempt one-based attempt number
     * @param timeTaken end-to-end attempt latency in milliseconds
     * @param result model response wrapper
     * @param newExecutions executions produced during this attempt only
     * @param attemptFeedback retry feedback generated from runtime evidence
     * @param metrics attempt-level scoring snapshot
     */
    public void logAttemptCompleted(String sessionId,
                                    int attempt,
                                    long timeTaken,
                                    LlmClient.LlmResult result,
                                    List<ExecutionRecord> newExecutions,
                                    String attemptFeedback,
                                    BenchmarkScorer.AttemptMetrics metrics) {
        Map<String, Object> payload = eventLogger.newEventPayload("attempt_completed");
        payload.put("attempt", attempt);
        payload.put("latencyMs", timeTaken);
        payload.put("tokenUsage", result.tokenUsage());
        payload.put("assistantResponse", result.content());
        payload.put("newExecutions", newExecutions);
        payload.put("attemptFeedback", attemptFeedback);
        payload.put("toolSelection", metrics.toolSelection());
        payload.put("stepCompletionRate", metrics.stepCompletionRate());
        payload.put("orderingAccuracy", metrics.orderingAccuracy());
        payload.put("stateAccuracy", metrics.stateAccuracy());
        payload.put("efficiency", metrics.efficiency());
        payload.put("commandPrecision", metrics.commandPrecision());
        payload.put("decoyResistance", metrics.decoyResistance());
        payload.put("targetLikeWrongToolAvoidance", metrics.decoyResistance());
        payload.put("scenarioComplete", metrics.scenarioComplete());
        payload.put("goalAchieved", metrics.goalAchieved());
        payload.put("executionsTotal", metrics.executionsTotal());
        payload.put("currentState", stateManager.getSessionStateSnapshot(sessionId));
        eventLogger.logPayload(payload);

        log.info("""
                Attempt {} completed
                latencyMs: {}
                tokens: {}
                executions:
                {}
                """,
                attempt,
                timeTaken,
                result.tokenUsage(),
                formatExecutions(newExecutions));
    }

    /**
     * Records the final case outcome after the retry loop completes.
     *
     * @param sessionId active benchmark session identifier
     * @param score final benchmark score
     * @param totalTimeTaken cumulative attempt latency in milliseconds
     * @param totalTokenUsage cumulative token usage reported by the model provider
     * @param attemptsUsed number of attempts consumed by the case
     */
    public void logCaseCompleted(String sessionId,
                                 BenchmarkScorer.BenchmarkScore score,
                                 long totalTimeTaken,
                                 int totalTokenUsage,
                                 int attemptsUsed) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = stateManager.getBenchmarkCase(sessionId);
        Map<String, Object> payload = eventLogger.newEventPayload("case_completed");
        payload.put("model", properties.getLlm().getModel());
        payload.put("sessionId", sessionId);
        payload.put("passed", score.passed());
        payload.put("scenarioComplete", score.stepCompletionRate() == 1.0
                && score.orderingAccuracy() == 1.0);
        payload.put("score", score);
        payload.put("compositeScore", score.composite());
        payload.put("attemptsUsed", attemptsUsed);
        payload.put("executionCount", stateManager.executionLog(sessionId).size());
        payload.put("totalLatencyMs", totalTimeTaken);
        payload.put("totalTokenUsage", totalTokenUsage);
        payload.put("finalStateScore", score.stateAccuracy());
        payload.put("commandPrecision", score.commandPrecision());
        payload.put("decoyResistance", score.decoyResistance());
        payload.put("targetLikeWrongToolAvoidance", score.decoyResistance());
        payload.put("targetLikeWrongTools", targetLikeWrongToolPairs(benchmarkCase));
        payload.put("targetTools", targetToolNames(benchmarkCase));
        payload.put("targetPath", formatTargetPath(benchmarkCase));
        payload.put("targetToolStates", targetToolStates(sessionId, benchmarkCase));
        payload.put("expectedSharedState", benchmarkCase.expectedSharedState());
        payload.put("actualSharedState", stateManager.getSharedStateSnapshot(sessionId));
        payload.put("sessionState", stateManager.getSessionStateSnapshot(sessionId));
        payload.put("executionLog", stateManager.executionLog(sessionId));
        eventLogger.logPayload(payload);

        eventLogger.blankLine();

        log.info("""
                Case result
                status: {}
                recovery: {}
                attemptsUsed: {}/{}
                executions: {}
                scores:
                  composite: {}
                  toolSelection: {}
                  stepCompletionRate: {}
                  orderingAccuracy: {}
                  stateAccuracy: {}
                  efficiency: {}
                  commandPrecision: {}
                  targetLikeWrongToolAvoidance: {}
                """,
                score.passed() ? "SUCCESS" : "FAILURE",
                score.recovery(),
                attemptsUsed,
                properties.getMaxExecutionSteps(),
                stateManager.executionLog(sessionId).size(),
                fmt(score.composite(), 3),
                fmt(score.toolSelection(), 1),
                fmt(score.stepCompletionRate(), 2),
                fmt(score.orderingAccuracy(), 2),
                fmt(score.stateAccuracy(), 2),
                fmt(score.efficiency(), 2),
                fmt(score.commandPrecision(), 2),
                fmt(score.decoyResistance(), 2));
    }

    private String fmt(double value, int scale) {
        return String.format("%." + scale + "f", value);
    }

    private String formatEffects(List<EffectObject> effects) {
        if (effects == null || effects.isEmpty()) return "{}";
        return effects.stream()
                .map(e -> e.scope().name() + " " + e.variable() + " " + e.operation().name()
                        + (e.valueRef() != null ? "=" + e.valueRef() : ""))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private String formatTargetSteps(BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkCase.targetPath().stream()
                .map(step -> formatTargetStep(benchmarkCase, step))
                .map(step -> "  - " + step)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private List<String> targetToolNames(BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkCase.targetTools().stream()
                .map(ToolObject::name)
                .toList();
    }

    private List<String> formatTargetPath(BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkCase.targetPath().stream()
                .map(step -> formatTargetStep(benchmarkCase, step))
                .toList();
    }

    private Map<String, Map<String, String>> targetToolStates(String sessionId,
                                                              BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkCase.targetTools().stream()
                .collect(Collectors.toMap(
                        ToolObject::name,
                        tool -> stateManager.getToolStateSnapshot(sessionId, tool.name()),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

    private List<Map<String, String>> targetLikeWrongToolPairs(BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkCase.semanticDecoys().stream()
                .flatMap(decoy -> benchmarkCase.targetTools().stream()
                        .map(targetTool -> Map.of("targetTool", targetTool.name(), "wrongTool", decoy.name())))
                .toList();
    }

    private String formatTargetLikeWrongTools(BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        if (benchmarkCase.semanticDecoys().isEmpty()) {
            return "  none";
        }
        return benchmarkCase.semanticDecoys().stream()
                .map(decoy -> "  - " + String.join(", ", targetToolNames(benchmarkCase)) + " -> " + decoy.name())
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatExecutions(List<ExecutionRecord> executions) {
        if (executions == null || executions.isEmpty()) {
            return "  none";
        }
        return java.util.stream.IntStream.range(0, executions.size())
                .mapToObj(i -> formatExecution(i + 1, executions.get(i)))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatExecution(int index, ExecutionRecord record) {
        return """
                  %d. result: %s
                     tool: %s
                     command: %s
                     option: %s
                     message: %s""".formatted(
                index,
                record.success() ? "SUCCESS" : "ERROR",
                record.toolName(),
                record.commandName(),
                CommandOptionNormalizer.formatForModel(record.option()),
                blankSafe(record.message(), "none"));
    }

    private String blankSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatTargetStep(BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                    BenchmarkCaseGenerator.TargetStep targetStep) {
        ToolObject targetTool = benchmarkCase.findTool(targetStep.toolName());
        if (targetTool == null) {
            return targetStep.toolName() + " " + targetStep.commandName() + "{options=[]}";
        }
        return targetTool.commands().stream()
                .filter(command -> command.name().equalsIgnoreCase(targetStep.commandName()))
                .findFirst()
                .map(command -> targetStep.toolName() + " " + targetStep.commandName()
                        + formatOptions(command.commandOptions()))
                .orElse(targetStep.toolName() + " " + targetStep.commandName() + "{options=[]}");
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
