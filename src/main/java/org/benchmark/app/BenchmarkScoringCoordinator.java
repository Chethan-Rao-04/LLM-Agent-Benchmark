package org.benchmark.app;

import lombok.RequiredArgsConstructor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Coordinates score calculation inputs so executor flow does not manage scorer bookkeeping directly.
 */
@Component
@RequiredArgsConstructor
public class BenchmarkScoringCoordinator {

    private final SessionStateManager stateManager;
    private final BenchmarkScorer scorer;
    private final BenchmarkAttemptFeedbackBuilder feedbackBuilder;

    public AttemptOutcome evaluateAttempt(String sessionId,
                                          BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                          int attempt,
                                          int logStartIndex,
                                          int discoveryCountBeforeAttempt) {
        List<ExecutionRecord> allExecutions = stateManager.executionLog(sessionId);
        List<ExecutionRecord> newExecutions = stateManager.executionLogFromIndex(sessionId, logStartIndex);
        int discoveryCallsThisAttempt = stateManager.discoveryCount(sessionId) - discoveryCountBeforeAttempt;
        Map<String, String> actualToolState = stateManager.getToolStateSnapshot(
                sessionId, benchmarkCase.targetToolObject().name());
        boolean goalAchieved = scorer.hasSuccessfulScenarioCompletion(
                allExecutions, benchmarkCase, actualToolState);
        String attemptFeedback = feedbackBuilder.build(newExecutions, sessionId, goalAchieved, benchmarkCase);
        BenchmarkScorer.AttemptMetrics attemptMetrics = scorer.computeAttemptMetrics(
                sessionId, benchmarkCase, attempt, goalAchieved);
        return new AttemptOutcome(
                allExecutions,
                newExecutions,
                discoveryCallsThisAttempt,
                goalAchieved,
                attemptFeedback,
                attemptMetrics
        );
    }

    public BenchmarkScorer.BenchmarkScore computeCaseScore(String sessionId,
                                                           BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                                           int attemptsUsed,
                                                           boolean goalAchieved,
                                                           boolean failedFirstAttempt) {
        return scorer.computeCaseScore(sessionId, benchmarkCase, attemptsUsed, goalAchieved, failedFirstAttempt);
    }

    public record AttemptOutcome(List<ExecutionRecord> allExecutions,
                                 List<ExecutionRecord> newExecutions,
                                 int discoveryCallsThisAttempt,
                                 boolean goalAchieved,
                                 String attemptFeedback,
                                 BenchmarkScorer.AttemptMetrics attemptMetrics) {
    }
}
