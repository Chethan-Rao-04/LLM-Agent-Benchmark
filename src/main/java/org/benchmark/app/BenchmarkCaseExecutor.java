package org.benchmark.app;

import io.micrometer.observation.Observation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.llm.LlmClient;
import org.benchmark.monitoring.BenchmarkObservability;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

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
    private final BenchmarkRetryPolicy retryPolicy;
    private final BenchmarkScoringCoordinator scoringCoordinator;
    private final BenchmarkCaseLogger caseLogger;
    private final BenchmarkObservability observability;

    public BenchmarkScorer.BenchmarkScore execute(String sessionId,
                                                  BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                                  ToolCallbackProvider caseToolCallbacks,
                                                  String userQuery,
                                                  int caseIndex) {
        Observation caseObservation = observability.startCaseObservation(caseIndex, sessionId, userQuery, benchmarkCase);

        try (Observation.Scope ignored = caseObservation.openScope()) {
            caseLogger.logCaseStart(caseIndex, sessionId, userQuery, benchmarkCase);

            boolean goalAchieved = false;
            boolean failedFirstAttempt = false;
            int attempt = 0;
            long totalTimeTaken = 0;
            int totalTokenUsage = 0;
            String conversationHistory = "";

            while (retryPolicy.shouldContinue(attempt, properties.getMaxRetries(), goalAchieved)) {
                attempt++;
                stateManager.startAttempt(sessionId, attempt);
                int logStartIndex = stateManager.executionLog(sessionId).size();
                int discoveryCountBeforeAttempt = stateManager.discoveryCount(sessionId);

                String systemInstruction = promptBuilder.buildSystemPrompt(sessionId, conversationHistory);
                String userPrompt = promptBuilder.buildUserPrompt(sessionId, userQuery);
                caseLogger.logAttemptStarted(sessionId, attempt, conversationHistory);

                long startTime = System.nanoTime();
                LlmClient.LlmResult result = client.execute(systemInstruction, userPrompt, caseToolCallbacks);
                long timeTaken = (System.nanoTime() - startTime) / 1_000_000;

                totalTimeTaken += timeTaken;
                totalTokenUsage += result.tokenUsage();

                BenchmarkScoringCoordinator.AttemptOutcome attemptOutcome = scoringCoordinator.evaluateAttempt(
                        sessionId,
                        benchmarkCase,
                        attempt,
                        logStartIndex,
                        discoveryCountBeforeAttempt
                );
                goalAchieved = attemptOutcome.goalAchieved();

                if (retryPolicy.failedFirstAttempt(attempt, goalAchieved)) {
                    failedFirstAttempt = true;
                }

                conversationHistory = retryPolicy.appendAttemptHistory(
                        conversationHistory,
                        attempt,
                        result.content(),
                        attemptOutcome.attemptFeedback(),
                        attemptOutcome.newExecutions().size(),
                        attemptOutcome.discoveryCallsThisAttempt(),
                        properties.getMaxHistoryChars()
                );

                caseLogger.logAttemptCompleted(
                        sessionId, attempt, timeTaken, result, attemptOutcome.newExecutions(),
                        attemptOutcome.attemptFeedback(), attemptOutcome.discoveryCallsThisAttempt(),
                        attemptOutcome.attemptMetrics());
            }

            BenchmarkScorer.BenchmarkScore score = scoringCoordinator.computeCaseScore(
                    sessionId, benchmarkCase, attempt, goalAchieved, failedFirstAttempt);
            caseLogger.logCaseCompleted(sessionId, score, totalTimeTaken, totalTokenUsage, attempt);

            observability.tagCurrentSpan("langfuse.observation.output",
                    """
                    {"passed":%s,"recovery":%s,"attemptsUsed":%d,"discoveryCalls":%d,"executionCount":%d}
                    """.formatted(
                            score.passed(),
                            score.recovery(),
                            attempt,
                            stateManager.discoveryCount(sessionId),
                            stateManager.executionLog(sessionId).size()).replace("\n", ""));
            observability.tagCurrentSpan("langfuse.observation.metadata.passed", score.passed());
            observability.tagCurrentSpan("langfuse.observation.metadata.recovery", score.recovery());
            observability.tagCurrentSpan("langfuse.observation.metadata.attempts_used", attempt);
            observability.tagCurrentSpan("langfuse.observation.metadata.total_latency_ms", totalTimeTaken);
            observability.tagCurrentSpan("langfuse.observation.metadata.total_token_usage", totalTokenUsage);
            observability.tagCurrentSpan("langfuse.observation.metadata.discovery_calls", stateManager.discoveryCount(sessionId));
            observability.tagCurrentSpan("langfuse.observation.metadata.execution_count", stateManager.executionLog(sessionId).size());
            observability.tagCurrentSpan("langfuse.observation.metadata.composite_score", score.composite());
            observability.tagCurrentSpan("langfuse.observation.metadata.tool_selection", score.toolSelection());
            observability.tagCurrentSpan("langfuse.observation.metadata.step_completion_rate", score.stepCompletionRate());
            observability.tagCurrentSpan("langfuse.observation.metadata.ordering_accuracy", score.orderingAccuracy());
            observability.tagCurrentSpan("langfuse.observation.metadata.state_accuracy", score.stateAccuracy());
            observability.tagCurrentSpan("langfuse.observation.metadata.efficiency", score.efficiency());
            observability.tagCurrentSpan("langfuse.observation.metadata.command_precision", score.commandPrecision());

            if (!goalAchieved) {
                log.info("   [FAILURE]: Unable to achieve goal after {} attempts.", properties.getMaxRetries());
            }
            return score;
        } catch (RuntimeException e) {
            caseObservation.error(e);
            observability.tagCurrentSpan("langfuse.observation.level", "ERROR");
            observability.tagCurrentSpan("langfuse.observation.status_message", e.getMessage());
            throw e;
        } finally {
            caseObservation.stop();
        }
    }
}
