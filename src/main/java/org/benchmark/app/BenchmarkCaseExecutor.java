package org.benchmark.app;

import org.benchmark.config.Config;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.llm.LlmClient;
import org.benchmark.utils.RunEventLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes a single benchmark case through the retry loop, scoring and logging.
 */
@Slf4j
@RequiredArgsConstructor
public class BenchmarkCaseExecutor {

    private final Config config;
    private final LlmClient client;
    private final SessionStateManager stateManager;
    private final RunEventLogger eventLogger;

    /**
     * Multi-dimensional benchmark score for one case execution.
     * All scores are in the range [0.0, 1.0].
     */
    public record BenchmarkScore(
            double toolSelection,
            double commandSelection,
            double optionSelection,
            double stateAccuracy,
            double efficiency,
            double planningAccuracy,
            boolean recovery,
            boolean passed
    ) {
        public double composite() {
            return (toolSelection + commandSelection + optionSelection + stateAccuracy + efficiency + planningAccuracy) / 6.0;
        }
    }

    /**
     * Executes a single benchmark case: retry loop, LLM interaction, scoring.
     */
    public BenchmarkScore execute(String sessionId,
                               BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                               ToolCallbackProvider caseToolCallbacks,
                               String userQuery,
                               int caseIndex) {
        logCaseStart(caseIndex, sessionId, userQuery, benchmarkCase);

        boolean goalAchieved = false;
        boolean toolMatch = false;
        int attempt = 0;
        long totalTimeTaken = 0;
        int totalTokenUsage = 0;
        String conversationHistory = "";
        boolean failedFirstAttempt = false;

        while (attempt < config.getBenchmark().getMaxRetries() && !goalAchieved) {
            attempt++;
            int logStartIndex = stateManager.executionLog(sessionId).size();

            String systemInstruction = buildSystemInstruction(sessionId, conversationHistory);
            String userPrompt = buildUserPrompt(sessionId, userQuery);
            logAttemptStarted(sessionId, attempt, conversationHistory);

            long tStart = System.nanoTime();
            LlmClient.LlmResult result = client.execute(systemInstruction, userPrompt, caseToolCallbacks);
            long timeTaken = (System.nanoTime() - tStart) / 1_000_000;

            totalTimeTaken += timeTaken;
            totalTokenUsage += result.tokenUsage();

            List<ExecutionRecord> newExecutions =
                    stateManager.executionLog(sessionId).subList(logStartIndex, stateManager.executionLog(sessionId).size());

            toolMatch = toolMatch || hasSuccessfulTargetExecution(stateManager.executionLog(sessionId), benchmarkCase);
            goalAchieved = toolMatch
                    && scoreExpectedState(benchmarkCase.expectedState(), targetToolState(sessionId, benchmarkCase)) == 1.0;

            if (attempt == 1 && !goalAchieved) {
                failedFirstAttempt = true;
            }

            String attemptFeedback = buildAttemptFeedback(newExecutions, sessionId, goalAchieved);
            conversationHistory += "\nAssistant: " + result.content();
            conversationHistory += "\nSystem: " + attemptFeedback;
            logAttemptCompleted(sessionId, attempt, timeTaken, result, newExecutions, goalAchieved, toolMatch);

            log.info("  [ATTEMPT {}] latency={}ms tokens={} toolMatch={} goal={}",
                    attempt, timeTaken, result.tokenUsage(), toolMatch, goalAchieved);
            log.info("   executions: {}", summarizeExecutions(newExecutions));
            log.info("   response: {}", summarizeText(result.content(), 220));
            log.info("   feedback: {}", summarizeText(attemptFeedback, 260));
        }

        BenchmarkScore score = computeScore(sessionId, benchmarkCase, attempt, goalAchieved, failedFirstAttempt);
        logCaseCompleted(sessionId, score, totalTimeTaken, totalTokenUsage);

        if (!goalAchieved) {
            log.info("   [FAILURE]: Unable to achieve goal after {} attempts.", config.getBenchmark().getMaxRetries());
        }

        return score;
    }

    private BenchmarkScore computeScore(String sessionId, BenchmarkCaseGenerator.BenchmarkCase benchmarkCase, int attempts, boolean goalAchieved, boolean failedFirstAttempt) {
        List<ExecutionRecord> logs = stateManager.executionLog(sessionId);
        String tTool = benchmarkCase.targetToolObject().name();
        String tCmd = benchmarkCase.targetCommand().name();
        String tOpt = benchmarkCase.targetOptionName() == null ? "" : benchmarkCase.targetOptionName().trim();

        double toolSel = logs.stream().anyMatch(e -> e.toolName().equalsIgnoreCase(tTool)) ? 1.0 : 0.0;
        double cmdSel = logs.stream().anyMatch(e -> e.toolName().equalsIgnoreCase(tTool) && e.commandName().equalsIgnoreCase(tCmd)) ? 1.0 : 0.0;
        double optSel = logs.stream().anyMatch(e -> e.toolName().equalsIgnoreCase(tTool) && e.commandName().equalsIgnoreCase(tCmd) && normalize(e.option()).equalsIgnoreCase(tOpt)) ? 1.0 : 0.0;
        double stateAcc = scoreExpectedState(benchmarkCase.expectedState(), targetToolState(sessionId, benchmarkCase));
        double efficiency = 1.0 / attempts;
        boolean recovery = failedFirstAttempt && goalAchieved;

        double planningAcc = scorePlanningAccuracy(logs, benchmarkCase);

        return new BenchmarkScore(toolSel, cmdSel, optSel, stateAcc, efficiency, planningAcc, recovery, goalAchieved);
    }

    private double scorePlanningAccuracy(List<ExecutionRecord> logs, BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        List<org.benchmark.model.objects.WorkflowStep> steps = benchmarkCase.workflowSteps();
        if (steps == null || steps.size() <= 1) {
            return 1.0; // Single-step case, no planning needed
        }
        
        String toolName = benchmarkCase.targetToolObject().name();
        List<ExecutionRecord> toolLogs = logs.stream()
            .filter(e -> e.toolName().equalsIgnoreCase(toolName) && e.success())
            .toList();
        
        int stepIndex = 0;
        for (ExecutionRecord log : toolLogs) {
            if (stepIndex < steps.size() 
                && log.commandName().equalsIgnoreCase(steps.get(stepIndex).commandName())) {
                stepIndex++;
            }
        }
        return (double) stepIndex / steps.size();
    }

    // ---- Prompt Building ----

    String buildSystemInstruction(String sessionId, String conversationHistory) {
        String stateContext = "\n[CURRENT STATE]: " + stateManager.getSessionStateSnapshot(sessionId);
        String historyContext = "\n[HISTORY]:" + conversationHistory;

        return config.getPrompt().getBaseSystemPrompt()
                + "\nSession ID: " + sessionId
                + stateContext
                + historyContext;
    }

    String buildUserPrompt(String sessionId, String userQuery) {
        return "Benchmark session: " + sessionId + "\nUser goal: " + userQuery;
    }

    // ---- Scoring ----

    boolean hasSuccessfulTargetExecution(List<ExecutionRecord> executions,
                                         BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        String expectedTool = benchmarkCase.targetToolObject().name();
        String expectedCommand = benchmarkCase.targetCommand().name();
        String expectedOption = benchmarkCase.targetOptionName() == null ? "" : benchmarkCase.targetOptionName().trim();

        return executions.stream().anyMatch(record ->
                record.success()
                        && record.toolName().equalsIgnoreCase(expectedTool)
                        && record.commandName().equalsIgnoreCase(expectedCommand)
                        && normalize(record.option()).equalsIgnoreCase(expectedOption));
    }

    double scoreExpectedState(Map<String, String> expectedState, Map<String, String> actualState) {
        if (expectedState.isEmpty()) {
            return 1.0;
        }

        int matches = 0;
        for (Map.Entry<String, String> expected : expectedState.entrySet()) {
            String actualValue = actualState.get(expected.getKey());
            String expectedValue = expected.getValue();
            if (expectedValue == null) {
                if (actualValue == null) {
                    matches++;
                }
                continue;
            }
            if (expectedValue.equals(actualValue)) {
                matches++;
            }
        }
        return (double) matches / expectedState.size();
    }

    // ---- Feedback ----

    String buildAttemptFeedback(List<ExecutionRecord> newExecutions,
                                String sessionId,
                                boolean goalAchieved) {
        if (goalAchieved) {
            return "SUCCESS: Goal achieved. Final state: " + stateManager.getSessionStateSnapshot(sessionId);
        }

        if (newExecutions.isEmpty()) {
            return "ERROR: No benchmark tool execution occurred. Use MCP for documentation/state and the benchmark tool callbacks for execution.";
        }

        StringBuilder feedback = new StringBuilder();
        for (ExecutionRecord record : newExecutions) {
            feedback.append("\n- ")
                    .append(record.success() ? "SUCCESS" : "ERROR")
                    .append(" tool=").append(record.toolName())
                    .append(", command=").append(record.commandName())
                    .append(", option=").append(record.option())
                    .append(", message=").append(record.message());
        }
        feedback.append("\nCurrent state: ").append(stateManager.getSessionStateSnapshot(sessionId));
        return feedback.toString();
    }

    // ---- Helpers ----

    private String normalize(String option) {
        return option == null ? "" : option.trim();
    }

    private Map<String, String> targetToolState(String sessionId, BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return stateManager.getToolStateSnapshot(sessionId, benchmarkCase.targetToolObject().name());
    }

    String summarizeExecutions(List<ExecutionRecord> executions) {
        if (executions == null || executions.isEmpty()) {
            return "none";
        }
        String summary = executions.stream()
                .map(record -> String.format(
                        "%s/%s(%s)=%s",
                        record.toolName(),
                        record.commandName(),
                        normalize(record.option()).isBlank() ? "no-option" : normalize(record.option()),
                        record.success() ? "ok" : "err"
                ))
                .collect(Collectors.joining(", "));
        return summarizeText(summary, 260);
    }

    String summarizeText(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "<empty>";
        }
        String singleLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() <= maxLen) {
            return singleLine;
        }
        return singleLine.substring(0, maxLen) + "...";
    }

    // ---- Logging / Telemetry ----

    private void logCaseStart(int index,
                              String sessionId,
                              String userQuery,
                              BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        eventLogger.log("case_started", caseStartPayload(index, sessionId, userQuery, benchmarkCase));

        log.info("\n============================================================");
        log.info(" Run {}: {} ", index, userQuery);
        log.info("------------------------------------------------------------");
        log.info("   [TARGET EXPECTATION]");
        if (benchmarkCase.workflowSteps() != null && benchmarkCase.workflowSteps().size() > 1) {
            log.info("     Workflow Steps:");
            for (int j = 0; j < benchmarkCase.workflowSteps().size(); j++) {
                org.benchmark.model.objects.WorkflowStep step = benchmarkCase.workflowSteps().get(j);
                log.info("       {}. {} (opt: {}) - {}", j+1, step.commandName(), 
                        step.optionName().isEmpty() ? "none" : step.optionName(), step.description());
            }
        } else {
            log.info("     Tool:          {}", benchmarkCase.targetToolObject().name());
            log.info("     Command:       {}", benchmarkCase.targetCommand().name());
            log.info("     Target Option: {}",
                    benchmarkCase.targetOptionName().isBlank() ? "[ None ]" : benchmarkCase.targetOptionName());
        }
        log.info("============================================================\n");
    }

    private void logAttemptStarted(String sessionId, int attempt, String conversationHistory) {
        eventLogger.log("attempt_started", Map.of(
                "sessionId", sessionId,
                "attempt", attempt,
                "currentState", stateManager.getSessionStateSnapshot(sessionId),
                "historyLength", conversationHistory.length()
        ));
    }

    private void logAttemptCompleted(String sessionId,
                                     int attempt,
                                     long timeTaken,
                                     LlmClient.LlmResult result,
                                     List<ExecutionRecord> newExecutions,
                                     boolean goalAchieved,
                                     boolean toolMatch) {
        eventLogger.log("attempt_completed", Map.of(
                "sessionId", sessionId,
                "attempt", attempt,
                "latencyMs", timeTaken,
                "tokenUsage", result.tokenUsage(),
                "assistantResponse", result.content(),
                "newExecutions", newExecutions,
                "goalAchieved", goalAchieved,
                "toolMatch", toolMatch,
                "currentState", stateManager.getSessionStateSnapshot(sessionId)
        ));
    }

    private void logCaseCompleted(String sessionId,
                                  BenchmarkScore score,
                                  long totalTimeTaken,
                                  int totalTokenUsage) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = stateManager.getBenchmarkCase(sessionId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.getLlm().getModel());
        payload.put("sessionId", sessionId);
        payload.put("passed", score.passed());
        payload.put("toolMatch", score.toolSelection() == 1.0);
        payload.put("score", score); // logs all dimensions
        payload.put("totalLatencyMs", totalTimeTaken);
        payload.put("totalTokenUsage", totalTokenUsage);
        payload.put("finalStateScore", score.stateAccuracy());
        payload.put("targetToolState", targetToolState(sessionId, benchmarkCase));
        payload.put("sessionState", stateManager.getSessionStateSnapshot(sessionId));
        payload.put("executionLog", stateManager.executionLog(sessionId));
        eventLogger.log("case_completed", payload);
    }

    private Map<String, Object> caseStartPayload(int index,
                                                  String sessionId,
                                                  String userQuery,
                                                  BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseIndex", index);
        payload.put("sessionId", sessionId);
        payload.put("userQuery", userQuery);
        payload.put("targetTool", benchmarkCase.targetToolObject().name());
        payload.put("targetCommand", benchmarkCase.targetCommand().name());
        payload.put("targetOption", benchmarkCase.targetOptionName());
        payload.put("expectedState", benchmarkCase.expectedState());
        payload.put("candidateTools", benchmarkCase.allTools().stream().map(tool -> tool.name()).toList());
        payload.put("documentComplexity", config.getBenchmark().getDocumentComplexity());
        return payload;
    }
}
