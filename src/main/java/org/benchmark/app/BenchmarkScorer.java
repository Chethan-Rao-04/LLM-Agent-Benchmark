package org.benchmark.app;

import lombok.RequiredArgsConstructor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes benchmark metrics from execution logs and tool state for multi-step scenarios.
 */
@Component
@RequiredArgsConstructor
public class BenchmarkScorer {

    private final SessionStateManager stateManager;

    /**
     * Final score for one multi-step benchmark case.
     */
    public record BenchmarkScore(
            double toolSelection,
            double stepCompletionRate,
            double orderingAccuracy,
            double stateAccuracy,
            double efficiency,
            double commandPrecision,
            boolean recovery,
            boolean passed
    ) {
        public double composite() {
            return (toolSelection + stepCompletionRate + orderingAccuracy + stateAccuracy + efficiency + commandPrecision) / 6.0;
        }
    }

    /**
     * Snapshot of scoring metrics after one attempt.
     */
    public record AttemptMetrics(
            double toolSelection,
            double stepCompletionRate,
            double orderingAccuracy,
            double stateAccuracy,
            double efficiency,
            double commandPrecision,
            boolean scenarioComplete,
            boolean goalAchieved,
            int executionsTotal,
            int discoveryCallsTotal
    ) {
    }

    public BenchmarkScore computeCaseScore(String sessionId,
                                           BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                           int attempts,
                                           boolean goalAchieved,
                                           boolean failedFirstAttempt) {
        List<ExecutionRecord> logs = stateManager.executionLog(sessionId);
        double toolSelection = scoreToolSelection(logs, benchmarkCase);
        double stepCompletion = scoreStepCompletion(logs, benchmarkCase);
        double ordering = scoreOrdering(logs, benchmarkCase);
        double stateAccuracy = scoreExpectedState(benchmarkCase.expectedState(), targetToolState(sessionId, benchmarkCase));
        double efficiency = scoreEfficiency(logs, benchmarkCase);
        double commandPrecision = scoreCommandPrecision(logs, benchmarkCase);
        boolean usedRecoveryCommand = benchmarkCase.recoveryCommandName() != null
                && logs.stream().anyMatch(r -> commandMatches(r.commandName(), benchmarkCase.recoveryCommandName()));
        boolean recovery = usedRecoveryCommand || (failedFirstAttempt && goalAchieved);
        boolean passed = goalAchieved;
        return new BenchmarkScore(
                toolSelection,
                stepCompletion,
                ordering,
                stateAccuracy,
                efficiency,
                commandPrecision,
                recovery,
                passed
        );
    }

    public AttemptMetrics computeAttemptMetrics(String sessionId,
                                                BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                                int attemptsUsed,
                                                boolean goalAchieved) {
        List<ExecutionRecord> logs = stateManager.executionLog(sessionId);
        double stepCompletion = scoreStepCompletion(logs, benchmarkCase);
        double ordering = scoreOrdering(logs, benchmarkCase);
        boolean scenarioComplete = hasSuccessfulScenarioCompletion(logs, benchmarkCase,
                targetToolState(sessionId, benchmarkCase));
        return new AttemptMetrics(
                scoreToolSelection(logs, benchmarkCase),
                stepCompletion,
                ordering,
                scoreExpectedState(benchmarkCase.expectedState(), targetToolState(sessionId, benchmarkCase)),
                scoreEfficiency(logs, benchmarkCase),
                scoreCommandPrecision(logs, benchmarkCase),
                scenarioComplete,
                goalAchieved,
                logs.size(),
                stateManager.discoveryCount(sessionId)
        );
    }

    public boolean hasSuccessfulScenarioCompletion(List<ExecutionRecord> executions,
                                                    BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                                    Map<String, String> actualToolState) {
        return scoreStepCompletion(executions, benchmarkCase) == 1.0
                && scoreOrdering(executions, benchmarkCase) == 1.0
                && scoreExpectedState(benchmarkCase.expectedState(), actualToolState) == 1.0;
    }

    public double scoreStepCompletion(List<ExecutionRecord> logs,
                                       BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        String targetTool = benchmarkCase.targetToolObject().name();
        List<ResolvedStep> steps = benchmarkCase.targetSteps();
        if (steps.isEmpty()) return 1.0;

        int completed = 0;
        for (ResolvedStep step : steps) {
            String abbreviated = CommandAbbreviator.commandName(step.verb(), step.noun());
            boolean found = logs.stream().anyMatch(r ->
                    r.success()
                            && r.toolName().equalsIgnoreCase(targetTool)
                            && commandMatches(r.commandName(), abbreviated));
            if (found) completed++;
        }
        return (double) completed / steps.size();
    }

    public double scoreOrdering(List<ExecutionRecord> logs,
                                 BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        String targetTool = benchmarkCase.targetToolObject().name();
        List<ResolvedStep> steps = benchmarkCase.targetSteps();
        if (steps.isEmpty()) return 1.0;

        List<String> abbreviatedNames = steps.stream()
                .map(s -> CommandAbbreviator.commandName(s.verb(), s.noun()))
                .toList();

        List<Integer> stepIndices = new ArrayList<>();
        for (ExecutionRecord log : logs) {
            if (!log.success() || !log.toolName().equalsIgnoreCase(targetTool)) continue;
            for (int i = 0; i < abbreviatedNames.size(); i++) {
                if (commandMatches(log.commandName(), abbreviatedNames.get(i))) {
                    stepIndices.add(i);
                    break;
                }
            }
        }

        if (stepIndices.isEmpty()) return 0.0;
        int lisLength = longestIncreasingSubsequence(stepIndices);
        return (double) lisLength / steps.size();
    }

    public double scoreExpectedState(Map<String, String> expectedState, Map<String, String> actualState) {
        if (expectedState.isEmpty()) return 1.0;

        int matches = 0;
        for (Map.Entry<String, String> expected : expectedState.entrySet()) {
            String actualValue = actualState.get(expected.getKey());
            if (expected.getValue() == null) {
                if (actualValue == null) matches++;
            } else if (expected.getValue().equals(actualValue)) {
                matches++;
            }
        }
        return (double) matches / expectedState.size();
    }

    /**
     * Efficiency = requiredSteps / weightedExecutions, capped at 1.0.
     * Failed executions (e.g. option hallucination, precondition failures) count 1.5x
     * to penalize bad reasoning more heavily than mere extra calls.
     * When a trap is active, the recovery command and one retry are expected.
     */
    private double scoreEfficiency(List<ExecutionRecord> logs,
                                   BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        if (logs.isEmpty()) return 0.0;
        int requiredSteps = benchmarkCase.targetSteps().size();
        if (benchmarkCase.hasTrap()) {
            requiredSteps += 2; // recovery command + retry of failed step
        }
        double weightedExecutions = 0;
        for (ExecutionRecord log : logs) {
            weightedExecutions += log.success() ? 1.0 : 1.5;
        }
        return Math.min(1.0, requiredSteps / weightedExecutions);
    }

    /**
     * Command precision = required-step executions on target tool / all executions across all tools.
     * Penalizes extra non-scenario commands on the target tool AND executions on wrong (decoy/distractor) tools.
     */
    public double scoreCommandPrecision(List<ExecutionRecord> logs,
                                        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        if (logs.isEmpty()) return 0.0;

        String targetTool = benchmarkCase.targetToolObject().name();
        List<String> requiredCommands = new ArrayList<>(benchmarkCase.targetSteps().stream()
                .map(s -> CommandAbbreviator.commandName(s.verb(), s.noun()))
                .toList());
        if (benchmarkCase.recoveryCommandName() != null) {
            requiredCommands.add(benchmarkCase.recoveryCommandName());
        }

        long requiredExecs = logs.stream()
                .filter(r -> r.toolName().equalsIgnoreCase(targetTool))
                .filter(r -> requiredCommands.stream()
                        .anyMatch(req -> commandMatches(r.commandName(), req)))
                .count();

        return Math.min(1.0, (double) requiredExecs / logs.size());
    }

    private double scoreToolSelection(List<ExecutionRecord> logs,
                                      BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        String targetTool = benchmarkCase.targetToolObject().name();
        return logs.stream().anyMatch(log -> log.toolName().equalsIgnoreCase(targetTool)) ? 1.0 : 0.0;
    }

    private Map<String, String> targetToolState(String sessionId,
                                                BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return stateManager.getToolStateSnapshot(sessionId, benchmarkCase.targetToolObject().name());
    }

    private boolean commandMatches(String actualCommandName, String expectedCommandName) {
        return actualCommandName != null
                && expectedCommandName != null
                && actualCommandName.equalsIgnoreCase(expectedCommandName);
    }

    static int longestIncreasingSubsequence(List<Integer> sequence) {
        if (sequence.isEmpty()) return 0;
        List<Integer> tails = new ArrayList<>();
        for (int val : sequence) {
            int pos = lowerBound(tails, val);
            if (pos == tails.size()) tails.add(val);
            else tails.set(pos, val);
        }
        return tails.size();
    }

    private static int lowerBound(List<Integer> list, int target) {
        int lo = 0, hi = list.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (list.get(mid) < target) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}
