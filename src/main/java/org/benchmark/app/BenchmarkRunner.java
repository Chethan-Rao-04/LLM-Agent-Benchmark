package org.benchmark.app;

import org.benchmark.config.Config;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.llm.LlmClient;
import org.benchmark.mcp.runtime.BenchmarkCaseToolCallbackFactory;
import org.benchmark.mcp.runtime.BenchmarkExecutionRecord;
import org.benchmark.mcp.runtime.BenchmarkSessionRegistry;
import org.benchmark.utils.RunEventLogger;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates end-to-end benchmark execution using Spring AI chat execution
 * with an MCP tool surface for documentation access and command execution.
 */
@Component
public class BenchmarkRunner implements ApplicationRunner {

    private final Config config;
    private final LlmClient client;
    private final List<McpSyncClient> mcpSyncClients;
    private final SessionStateManager stateManager;
    private final BenchmarkSessionRegistry sessionRegistry;
    private final BenchmarkCaseToolCallbackFactory toolCallbackFactory;
    private final RunEventLogger eventLogger;
    private final ConfigurableApplicationContext applicationContext;

    public BenchmarkRunner(Config config,
                           LlmClient client,
                           List<McpSyncClient> mcpSyncClients,
                           SessionStateManager stateManager,
                           BenchmarkSessionRegistry sessionRegistry,
                           BenchmarkCaseToolCallbackFactory toolCallbackFactory,
                           RunEventLogger eventLogger,
                           ConfigurableApplicationContext applicationContext) {
        this.config = config;
        this.client = client;
        this.mcpSyncClients = mcpSyncClients;
        this.stateManager = stateManager;
        this.sessionRegistry = sessionRegistry;
        this.toolCallbackFactory = toolCallbackFactory;
        this.eventLogger = eventLogger;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            runBenchmark();
        } finally {
            closeLoopbackMcpClients();
            applicationContext.close();
        }
    }

    /**
     * Runs the autonomous execution benchmark for the configured number of iterations.
     */
    public void runBenchmark() {

        // Initialise MCP clients and expose their tools available in the MCP servers
        ToolCallbackProvider mcpToolCallbacks = initializeLoopbackMcpClients();

        BenchmarkCaseGenerator caseGenerator = new BenchmarkCaseGenerator(config.documentComplexity());
        System.out.println("Generating Autonomous Execution Benchmark..");
        eventLogger.log("benchmark_run_started", Map.of(
                "model", config.getLlm().getModel(),
                "iterations", config.getBenchmark().getIterations(),
                "documentComplexity", config.getBenchmark().getDocumentComplexity(),
                "maxRetries", config.getBenchmark().getMaxRetries(),
                "distractorCount", config.getBenchmark().getDistractorCount()
        ));

        var benchmarkCases = caseGenerator.generateCases(
                config.getBenchmark().getIterations(),
                config.getBenchmark().getDistractorCount(),
                config.getBenchmark().getDomain()
        );
        int totalSuccess = 0;
        int autonomousRecoveries = 0;

        for (int i = 0; i < benchmarkCases.size(); i++) {
            var benchmarkCase = benchmarkCases.get(i);
            String sessionId = UUID.randomUUID().toString();
            sessionRegistry.register(sessionId, benchmarkCase);
            stateManager.initializeSession(sessionId, benchmarkCase.allTools());
            ToolCallbackProvider caseToolCallbacks = combineToolCallbacks(
                    mcpToolCallbacks,
                    toolCallbackFactory.create(sessionId, benchmarkCase)
            );

            String userQuery = benchmarkCase.generateUserQuery(benchmarkCase.targetToolObject());
            logCaseStart(i, sessionId, userQuery, benchmarkCase);

            boolean goalAchieved = false;
            boolean toolMatch = false;
            int attempt = 0;
            long totalTimeTaken = 0;
            int totalTokenUsage = 0;
            String conversationHistory = "";

            while (attempt < config.getBenchmark().getMaxRetries() && !goalAchieved) {
                attempt++;
                int logStartIndex = sessionRegistry.executionLog(sessionId).size();

                String systemInstruction = buildSystemInstruction(sessionId, conversationHistory);
                String userPrompt = buildUserPrompt(sessionId, userQuery);
                logAttemptStarted(sessionId, attempt, conversationHistory);

                long tStart = System.nanoTime();
                LlmClient.LlmResult result = client.execute(systemInstruction, userPrompt, caseToolCallbacks);
                long timeTaken = (System.nanoTime() - tStart) / 1_000_000;

                totalTimeTaken += timeTaken;
                totalTokenUsage += result.tokenUsage();

                List<BenchmarkExecutionRecord> newExecutions =
                        sessionRegistry.executionLog(sessionId).subList(logStartIndex, sessionRegistry.executionLog(sessionId).size());

                toolMatch = toolMatch || hasSuccessfulTargetExecution(sessionRegistry.executionLog(sessionId), benchmarkCase);
                goalAchieved = toolMatch
                        && scoreExpectedState(benchmarkCase.expectedState(), targetToolState(sessionId, benchmarkCase)) == 1.0;

                String attemptFeedback = buildAttemptFeedback(newExecutions, sessionId, goalAchieved);
                conversationHistory += "\nAssistant: " + result.content();
                conversationHistory += "\nSystem: " + attemptFeedback;
                logAttemptCompleted(sessionId, attempt, timeTaken, result, newExecutions, goalAchieved, toolMatch);

                System.out.printf("  Attempt %d (%dms)%n", attempt, timeTaken);
                System.out.println("   [Assistant Response]: " + result.content());
                System.out.println("   [MCP Feedback]: " + attemptFeedback);
            }

            double finalStateScore = goalAchieved
                    ? scoreExpectedState(benchmarkCase.expectedState(), targetToolState(sessionId, benchmarkCase))
                    : 0.0;

            logCaseCompleted(sessionId, goalAchieved, toolMatch, attempt, totalTimeTaken, totalTokenUsage, finalStateScore);

            if (goalAchieved) {
                totalSuccess++;
                if (attempt > 1) {
                    autonomousRecoveries++;
                }
            } else {
                System.out.println("   [FAILURE]: Unable to achieve goal after "
                        + config.getBenchmark().getMaxRetries() + " attempts.");
            }

            sessionRegistry.clear(sessionId);
            stateManager.clearSession(sessionId);
        }

        System.out.println("\nFinal Success Rate: " + totalSuccess + "/" + config.getBenchmark().getIterations());
        System.out.println("Autonomous Recoveries: " + autonomousRecoveries);
        eventLogger.log("benchmark_run_completed", Map.of(
                "model", config.getLlm().getModel(),
                "successes", totalSuccess,
                "iterations", config.getBenchmark().getIterations(),
                "autonomousRecoveries", autonomousRecoveries
        ));
    }

    private ToolCallbackProvider initializeLoopbackMcpClients() {
        for (McpSyncClient mcpSyncClient : mcpSyncClients) {
            if (!mcpSyncClient.isInitialized()) {
                mcpSyncClient.initialize();
            }
        }
        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClients)
                .build();
    }

    private ToolCallbackProvider combineToolCallbacks(ToolCallbackProvider... providers) {
        List<ToolCallback> combined = new java.util.ArrayList<>();
        for (ToolCallbackProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            for (ToolCallback toolCallback : provider.getToolCallbacks()) {
                combined.add(toolCallback);
            }
        }
        return new StaticToolCallbackProvider(combined);
    }

    private void closeLoopbackMcpClients() {
        for (McpSyncClient mcpSyncClient : mcpSyncClients) {
            try {
                if (mcpSyncClient.isInitialized() && !mcpSyncClient.closeGracefully()) {
                    mcpSyncClient.close();
                }
            } catch (Exception ignored) {
                try {
                    mcpSyncClient.close();
                } catch (Exception ignoredAgain) {
                    // Shutdown should not fail because an MCP client was already torn down.
                }
            }
        }
    }

    private void logCaseStart(int index,
                              String sessionId,
                              String userQuery,
                              BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        eventLogger.log("case_started", caseStartPayload(index, sessionId, userQuery, benchmarkCase));

        System.out.println("\n============================================================");
        System.out.printf(" Run %d: %s %n", index + 1, userQuery);
        System.out.println("------------------------------------------------------------");
        System.out.println("   [TARGET EXPECTATION]");
        System.out.printf("     Tool:          %s%n", benchmarkCase.targetToolObject().name());
        System.out.printf("     Command:       %s%n", benchmarkCase.targetCommand().name());
        System.out.printf("     Target Option: %s%n",
                benchmarkCase.targetOptionName().isBlank() ? "[ None ]" : benchmarkCase.targetOptionName());
        System.out.println("============================================================\n");
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
                                     List<BenchmarkExecutionRecord> newExecutions,
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
                                  boolean goalAchieved,
                                  boolean toolMatch,
                                  int attempt,
                                  long totalTimeTaken,
                                  int totalTokenUsage,
                                  double finalStateScore) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = sessionRegistry.getBenchmarkCase(sessionId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.getLlm().getModel());
        payload.put("sessionId", sessionId);
        payload.put("passed", goalAchieved);
        payload.put("toolMatch", toolMatch);
        payload.put("attempts", attempt);
        payload.put("totalLatencyMs", totalTimeTaken);
        payload.put("totalTokenUsage", totalTokenUsage);
        payload.put("finalStateScore", finalStateScore);
        payload.put("targetToolState", targetToolState(sessionId, benchmarkCase));
        payload.put("sessionState", stateManager.getSessionStateSnapshot(sessionId));
        payload.put("executionLog", sessionRegistry.executionLog(sessionId));
        eventLogger.log("case_completed", payload);
    }

    private String buildSystemInstruction(String sessionId, String conversationHistory) {
        String stateContext = "\n[CURRENT STATE]: " + stateManager.getSessionStateSnapshot(sessionId);
        String historyContext = "\n[HISTORY]:" + conversationHistory;

        return config.getPrompt().getBaseSystemPrompt()
                + "\nSession ID: " + sessionId
                + "\nUse the MCP tools to inspect degraded documentation and inspect state."
                + "\nUse the benchmark tool callbacks directly to execute commands on the generated tools."
                + "\nEach benchmark tool accepts exactly one command and one option per call."
                + "\nIf command execution fails, recover autonomously using documentation and state feedback."
                + "\nDo not invent tool names or commands. Use the provided tool callbacks."
                + stateContext
                + historyContext;
    }

    private String buildUserPrompt(String sessionId, String userQuery) {
        return "Benchmark session: " + sessionId + "\nUser goal: " + userQuery;
    }

    private Map<String, Object> caseStartPayload(int index,
                                                 String sessionId,
                                                 String userQuery,
                                                 BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseIndex", index + 1);
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

    /**
     * Builds the retry feedback appended to conversation history between attempts.
     */
    private String buildAttemptFeedback(List<BenchmarkExecutionRecord> newExecutions,
                                        String sessionId,
                                        boolean goalAchieved) {
        if (goalAchieved) {
            return "SUCCESS: Goal achieved. Final state: " + stateManager.getSessionStateSnapshot(sessionId);
        }

        if (newExecutions.isEmpty()) {
            return "ERROR: No benchmark tool execution occurred. Use MCP for documentation/state and the benchmark tool callbacks for execution.";
        }

        StringBuilder feedback = new StringBuilder();
        for (BenchmarkExecutionRecord record : newExecutions) {
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

    /**
     * Returns whether the target tool/command/option was ever executed successfully.
     */
    private boolean hasSuccessfulTargetExecution(List<BenchmarkExecutionRecord> executions,
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

    private String normalize(String option) {
        return option == null ? "" : option.trim();
    }

    /**
     * Scores the expected state delta against the actual target-tool runtime state.
     */
    private double scoreExpectedState(Map<String, String> expectedState, Map<String, String> actualState) {
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

    private Map<String, String> targetToolState(String sessionId, BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return stateManager.getToolStateSnapshot(sessionId, benchmarkCase.targetToolObject().name());
    }
}
