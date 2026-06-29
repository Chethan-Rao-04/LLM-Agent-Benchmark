package org.benchmark.exec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.llm.LlmClient;
import org.benchmark.llm.LlmServiceException;
import org.benchmark.llm.LlmToolCallbackException;
import org.benchmark.logging.BenchmarkCaseLogger;
import org.benchmark.prompt.BenchmarkPromptBuilder;
import org.benchmark.scoring.BenchmarkScorer;
import org.benchmark.scoring.BenchmarkScoringCoordinator;
import org.benchmark.tools.runtime.BenchmarkCaseToolCallbackFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs one multi-step benchmark case through the retry loop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BenchmarkCaseExecutor {

    private final BenchmarkProperties properties;
    private final LlmClient client;
    private final SessionStateManager stateManager;
    private final BenchmarkPromptBuilder promptBuilder;
    private final BenchmarkCaseToolCallbackFactory toolCallbackFactory;
    private final BenchmarkScoringCoordinator scoringCoordinator;
    private final BenchmarkScorer scorer;
    private final BenchmarkCaseLogger caseLogger;

    public CaseExecutionResult execute(String sessionId,
                                       BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                       String userQuery,
                                       int caseIndex) {
        caseLogger.logCaseStart(caseIndex, sessionId, userQuery, benchmarkCase);

        boolean goalAchieved = false;
        boolean failedFirstAttempt = false;
        int attempt = 0;
        long totalTimeTaken = 0;
        int totalTokenUsage = 0;
        List<Message> messageHistory = new ArrayList<>(promptBuilder.buildInitialMessages(sessionId, userQuery));
        String latestRetryFeedback = "";

        while (attempt < properties.getMaxExecutionSteps() && !goalAchieved) {
            attempt++;
            stateManager.startAttempt(sessionId);
            int logStartIndex = stateManager.executionLog(sessionId).size();
            int commandRejectionLogStartIndex = stateManager.commandRejectionLog(sessionId).size();

            if (attempt > 1) {
                messageHistory.add(promptBuilder.buildRetryMessage(sessionId, attempt - 1, latestRetryFeedback));
            }
            caseLogger.logAttemptStarted(sessionId, attempt, messageHistory);

            long startTime = System.nanoTime();
            AttemptExchange attemptExchange = executeAttempt(sessionId, attempt, messageHistory);

            long timeTaken = (System.nanoTime() - startTime) / 1_000_000;
            totalTimeTaken += timeTaken;
            totalTokenUsage += attemptExchange.totalTokenUsage();

            BenchmarkScoringCoordinator.AttemptOutcome attemptOutcome = scoringCoordinator.evaluateAttempt(
                    sessionId,
                    benchmarkCase,
                    attempt,
                    logStartIndex,
                    commandRejectionLogStartIndex
            );
            goalAchieved = attemptOutcome.goalAchieved();

            if (attempt == 1 && !goalAchieved) {
                failedFirstAttempt = true;
            }

            messageHistory.add(attemptExchange.assistantMessage());
            latestRetryFeedback = attemptOutcome.attemptFeedback();

            caseLogger.logAttemptCompleted(
                    sessionId,
                    attempt,
                    timeTaken,
                    attemptExchange.loggingResult(),
                    attemptOutcome.newExecutions(),
                    attemptOutcome.rejectedCommands(),
                    attemptOutcome.attemptFeedback(),
                    attemptOutcome.attemptMetrics()
            );
        }

        BenchmarkScorer.BenchmarkScore score = scorer.computeCaseScore(
                sessionId, benchmarkCase, attempt, goalAchieved, failedFirstAttempt);
        caseLogger.logCaseCompleted(sessionId, score, totalTimeTaken, totalTokenUsage, attempt);

        if (!goalAchieved) {
            log.info("Case {} did not reach the expected state after {} attempts", caseIndex, properties.getMaxExecutionSteps());
        }
        return new CaseExecutionResult(score, attempt);
    }

    private AttemptExchange executeAttempt(String sessionId,
                                           int attempt,
                                           List<Message> prompt) {
        ToolCallbackProvider callbacks = toolCallbackFactory.createExecution(sessionId);
        LlmClient.LlmResult executionResult;
        try {
            executionResult = client.execute(sessionId, attempt, prompt, callbacks);
        } catch (LlmServiceException e) {
            log.warn("Model call failed for session {} attempt {}. Treating it as a failed attempt.", sessionId, attempt, e);
            return AttemptExchange.modelFailure("Model call failed: " + blankSafe(e.getCause() == null
                    ? e.getMessage()
                    : e.getCause().getMessage()));
        }

        if (executionResult.toolCalls() == null || executionResult.toolCalls().isEmpty()) {
            log.error("No tool calls were made by the model in attempt {} for session {}", attempt, sessionId);
        }

        executeToolCalls(executionResult.toolCalls(), callbacks);
        return new AttemptExchange(executionResult);
    }

    private void executeToolCalls(List<AssistantMessage.ToolCall> toolCalls,
                                  ToolCallbackProvider toolCallbackProvider) {
        Map<String, ToolCallback> callbacksByName = indexCallbacksByName(toolCallbackProvider);
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            ToolCallback toolCallBack = callbacksByName.get(toolCall.name());
            if (toolCallBack == null) {
                throw new LlmToolCallbackException(
                        "Model attempted to call an unavailable tool",
                        new IllegalStateException("Unknown tool callback: " + toolCall.name())
                );
            }
            callTool(toolCallBack, toolCall);
        }
    }

    private Map<String, ToolCallback> indexCallbacksByName(ToolCallbackProvider toolCallbackProvider) {
        Map<String, ToolCallback> callbacksByName = new LinkedHashMap<>();
        Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .forEach(callback -> callbacksByName.put(callback.getToolDefinition().name(), callback));
        return callbacksByName;
    }

    private String callTool(ToolCallback callback, AssistantMessage.ToolCall toolCall) {
        try {
            return callback.call(toolCall.arguments() == null ? "{}" : toolCall.arguments());
        } catch (IllegalStateException e) {
            throw new LlmToolCallbackException("Model attempted to call an unavailable tool", e);
        } catch (RuntimeException e) {
            throw new LlmServiceException("Tool callback execution failed", e);
        }
    }

    private String blankSafe(String value) {
        return value == null || value.isBlank() ? "<no message>" : value;
    }

    private record AttemptExchange(LlmClient.LlmResult executionResult) {

        private static AttemptExchange modelFailure(String message) {
            AssistantMessage assistantMessage = AssistantMessage.builder()
                    .content(message)
                    .build();
            return new AttemptExchange(new LlmClient.LlmResult(
                    assistantMessage,
                    message,
                    List.of(),
                    0
            ));
        }

        private AssistantMessage assistantMessage() {
            return executionResult.assistantMessage();
        }

        private LlmClient.LlmResult loggingResult() {
            return new LlmClient.LlmResult(
                    executionResult.assistantMessage(),
                    executionResult.content(),
                    executionResult.toolCalls(),
                    executionResult.tokenUsage()
            );
        }

        private int totalTokenUsage() {
            return executionResult.tokenUsage();
        }
    }

    public record CaseExecutionResult(BenchmarkScorer.BenchmarkScore score, int attemptsUsed) {
    }
}
