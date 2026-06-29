package org.benchmark.scoring;

import lombok.RequiredArgsConstructor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.CommandRejectionRecord;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.prompt.BenchmarkAttemptFeedbackBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Coordinates score calculation inputs so executor flow does not manage scorer bookkeeping directly.
 *
 * <p>The executor hands this component the attempt boundaries, and this component derives
 * the attempt-local executions, state snapshot, retry feedback, and scoring payload.</p>
 */
@Component
@RequiredArgsConstructor
public class BenchmarkScoringCoordinator {

    private final SessionStateManager stateManager;
    private final BenchmarkScorer scorer;
    private final BenchmarkAttemptFeedbackBuilder feedbackBuilder;

    /**
     * Evaluates one completed attempt using the latest execution log and session state.
     *
     * @param sessionId active benchmark session identifier
     * @param benchmarkCase generated benchmark case
     * @param attempt one-based attempt number
     * @param logStartIndex execution-log index captured before the attempt started
     * @return attempt outcome containing new executions, derived feedback, and scoring metrics
     */
    public AttemptOutcome evaluateAttempt(String sessionId,
                                          BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                          int attempt,
                                          int logStartIndex,
                                          int commandRejectionLogStartIndex) {
        List<ExecutionRecord> allExecutions = stateManager.executionLog(sessionId);
        List<ExecutionRecord> newExecutions = stateManager.executionLogFromIndex(sessionId, logStartIndex);
        List<CommandRejectionRecord> rejectedCommands = stateManager.commandRejectionLogFromIndex(
                sessionId, commandRejectionLogStartIndex);
        Map<String, String> actualToolState = stateManager.getToolStateSnapshot(
                sessionId, benchmarkCase.targetToolObject().name());
        boolean goalAchieved = scorer.hasSuccessfulScenarioCompletion(
                allExecutions, benchmarkCase, actualToolState);
        String attemptFeedback = feedbackBuilder.build(newExecutions, sessionId, goalAchieved);
        BenchmarkScorer.AttemptMetrics attemptMetrics = scorer.computeAttemptMetrics(
                sessionId, benchmarkCase, goalAchieved);
        return new AttemptOutcome(
                newExecutions,
                rejectedCommands,
                goalAchieved,
                attemptFeedback,
                attemptMetrics
        );
    }

    /**
     * Immutable attempt evaluation result returned to the executor.
     */
    public record AttemptOutcome(List<ExecutionRecord> newExecutions,
                                 List<CommandRejectionRecord> rejectedCommands,
                                 boolean goalAchieved,
                                 String attemptFeedback,
                                 BenchmarkScorer.AttemptMetrics attemptMetrics) {
    }
}
