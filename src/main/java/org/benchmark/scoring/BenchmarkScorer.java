package org.benchmark.scoring;

import lombok.RequiredArgsConstructor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.ToolObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            double decoyResistance,
            boolean recovery,
            boolean passed
    ) {
        /**
         * Computes the overall benchmark score using the original six primary dimensions.
         *
         * <p>Decoy resistance is reported separately so historical composite baselines stay stable
         * while the new metric is rolled out.</p>
         *
         * @return composite case score
         */
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
            double decoyResistance,
            boolean scenarioComplete,
            boolean goalAchieved,
            int executionsTotal
    ) {
    }

    /**
     * Computes the final score for a completed benchmark case.
     *
     * @param sessionId active benchmark session identifier
     * @param benchmarkCase generated benchmark case
     * @param attempts attempts consumed by the case
     * @param goalAchieved whether the expected end state was reached
     * @param failedFirstAttempt whether the first attempt failed before eventual success
     * @return final benchmark score
     */
    public BenchmarkScore computeCaseScore(String sessionId,
                                           BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                           int attempts,
                                           boolean goalAchieved,
                                           boolean failedFirstAttempt) {
        List<ExecutionRecord> logs = stateManager.executionLog(sessionId);
        double toolSelection = scoreToolSelection(logs, benchmarkCase);
        double stepCompletion = scoreStepCompletion(logs, benchmarkCase);
        double ordering = scoreOrdering(logs, benchmarkCase);
        double stateAccuracy = scoreExpectedStateByTool(
                benchmarkCase.expectedStateByTool(), targetToolStates(sessionId, benchmarkCase));
        double efficiency = scoreEfficiency(logs, benchmarkCase);
        double commandPrecision = scoreCommandPrecision(logs, benchmarkCase);
        double decoyResistance = scoreDecoyResistance(logs, benchmarkCase);
        boolean hadFailedExecution = logs.stream().anyMatch(record -> !record.success());
        boolean recovery = hadFailedExecution && failedFirstAttempt && goalAchieved;
        boolean passed = goalAchieved;
        return new BenchmarkScore(
                toolSelection,
                stepCompletion,
                ordering,
                stateAccuracy,
                efficiency,
                commandPrecision,
                decoyResistance,
                recovery,
                passed
        );
    }

    /**
     * Computes a scoring snapshot after one attempt without waiting for the whole case to finish.
     *
     * @param sessionId active benchmark session identifier
     * @param benchmarkCase generated benchmark case
     * @param goalAchieved whether the current attempt already reached the expected outcome
     * @return attempt-level metrics
     */
    public AttemptMetrics computeAttemptMetrics(String sessionId,
                                                BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                                boolean goalAchieved) {
        List<ExecutionRecord> logs = stateManager.executionLog(sessionId);
        double stepCompletion = scoreStepCompletion(logs, benchmarkCase);
        double ordering = scoreOrdering(logs, benchmarkCase);
        boolean scenarioComplete = hasSuccessfulScenarioCompletion(sessionId, logs, benchmarkCase);
        return new AttemptMetrics(
                scoreToolSelection(logs, benchmarkCase),
                stepCompletion,
                ordering,
                scoreExpectedStateByTool(benchmarkCase.expectedStateByTool(), targetToolStates(sessionId, benchmarkCase)),
                scoreEfficiency(logs, benchmarkCase),
                scoreCommandPrecision(logs, benchmarkCase),
                scoreDecoyResistance(logs, benchmarkCase),
                scenarioComplete,
                goalAchieved,
                logs.size()
        );
    }

    /**
     * Determines whether the scenario is fully complete based on command coverage, ordering, and final state.
     *
     * @param executions full execution log for the case
     * @param benchmarkCase generated benchmark case
     * @param sessionId active benchmark session identifier
     * @return {@code true} when the attempt satisfied the scenario completely
     */
    public boolean hasSuccessfulScenarioCompletion(String sessionId,
                                                   List<ExecutionRecord> executions,
                                                   BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return scoreStepCompletion(executions, benchmarkCase) == 1.0
                && scoreOrdering(executions, benchmarkCase) == 1.0
                && scoreExpectedStateByTool(
                benchmarkCase.expectedStateByTool(), targetToolStates(sessionId, benchmarkCase)) == 1.0;
    }

    /**
     * Scores how many required target-path steps have at least one successful execution.
     *
     * @param logs full execution log for the case
     * @param benchmarkCase generated benchmark case
     * @return completion ratio in the range {@code [0.0, 1.0]}
     */
    public double scoreStepCompletion(List<ExecutionRecord> logs,
                                       BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        List<BenchmarkCaseGenerator.TargetStep> targetPath = benchmarkCase.targetPath();
        if (targetPath.isEmpty()) return 1.0;

        int completed = 0;
        for (BenchmarkCaseGenerator.TargetStep requiredStep : targetPath) {
            boolean found = logs.stream().anyMatch(r ->
                    r.success()
                            && targetStepMatches(r, requiredStep));
            if (found) completed++;
        }
        return (double) completed / targetPath.size();
    }

    /**
     * Scores whether successful target-tool executions appear in the same order as the scenario steps.
     *
     * @param logs full execution log for the case
     * @param benchmarkCase generated benchmark case
     * @return ordering score in the range {@code [0.0, 1.0]}
     */
    public double scoreOrdering(List<ExecutionRecord> logs,
                                 BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        List<BenchmarkCaseGenerator.TargetStep> targetPath = benchmarkCase.targetPath();
        if (targetPath.isEmpty()) return 1.0;

        List<Integer> stepIndices = new ArrayList<>();
        for (ExecutionRecord log : logs) {
            if (!log.success()) continue;
            for (int i = 0; i < targetPath.size(); i++) {
                if (targetStepMatches(log, targetPath.get(i))) {
                    stepIndices.add(i);
                    break;
                }
            }
        }

        if (stepIndices.isEmpty()) return 0.0;
        int lisLength = longestIncreasingSubsequence(stepIndices);
        return (double) lisLength / targetPath.size();
    }

    /**
     * Scores how closely the current target-tool state matches the scenario's expected final state.
     *
     * @param expectedState expected end-state snapshot
     * @param actualState observed target-tool state snapshot
     * @return state-match ratio in the range {@code [0.0, 1.0]}
     */
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
     * Scores expected final state separately for each target tool.
     */
    public double scoreExpectedStateByTool(Map<String, Map<String, String>> expectedStateByTool,
                                           Map<String, Map<String, String>> actualStateByTool) {
        if (expectedStateByTool.isEmpty()) return 1.0;

        int expectedValues = 0;
        int matches = 0;
        for (Map.Entry<String, Map<String, String>> expectedToolState : expectedStateByTool.entrySet()) {
            Map<String, String> actualToolState = actualStateByTool.getOrDefault(expectedToolState.getKey(), Map.of());
            for (Map.Entry<String, String> expected : expectedToolState.getValue().entrySet()) {
                expectedValues++;
                String actualValue = actualToolState.get(expected.getKey());
                if (expected.getValue() == null) {
                    if (actualValue == null) matches++;
                } else if (expected.getValue().equals(actualValue)) {
                    matches++;
                }
            }
        }
        return expectedValues == 0 ? 1.0 : (double) matches / expectedValues;
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
        int requiredSteps = benchmarkCase.targetPath().size();
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

        long requiredExecs = logs.stream()
                .filter(ExecutionRecord::success)
                .filter(record -> isRequiredTargetExecution(record, benchmarkCase)
                        || isRecoveryExecution(record, benchmarkCase))
                .count();

        return Math.min(1.0, (double) requiredExecs / logs.size());
    }

    /**
     * Scores how well the agent avoided target-like wrong neighboring tools.
     *
     * <p>Harmless probing lowers the score slightly, while a successful wrong-tool mutation
     * lowers it more heavily because it shows the agent selected the target-looking wrong resource.</p>
     */
    public double scoreDecoyResistance(List<ExecutionRecord> logs,
                                       BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        if (!benchmarkCase.spec().scoringPolicy().penalizeSemanticDecoyUse()) {
            return 1.0;
        }

        double penalty = 0.0;
        Set<String> targetToolNames = targetToolNames(benchmarkCase);
        for (ExecutionRecord record : logs) {
            if (record.toolName() == null || targetToolNames.contains(record.toolName().toLowerCase())) {
                continue;
            }

            ToolObject tool = benchmarkCase.findTool(record.toolName());
            CommandObject command = findCommand(tool, record.commandName());
            boolean mutatesState = record.success()
                    && command != null
                    && command.commandEffectObjects() != null
                    && !command.commandEffectObjects().isEmpty();
            penalty += mutatesState ? 0.60 : 0.25;
        }
        return Math.max(0.0, 1.0 - penalty);
    }

    private double scoreToolSelection(List<ExecutionRecord> logs,
                                      BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        Set<String> targetToolNames = targetToolNames(benchmarkCase);
        if (targetToolNames.isEmpty()) return 1.0;

        Set<String> selectedTargets = logs.stream()
                .map(ExecutionRecord::toolName)
                .filter(name -> name != null && targetToolNames.contains(name.toLowerCase()))
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return (double) selectedTargets.size() / targetToolNames.size();
    }

    private Map<String, Map<String, String>> targetToolStates(String sessionId,
                                                              BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        Map<String, Map<String, String>> targetStates = new LinkedHashMap<>();
        for (ToolObject targetTool : benchmarkCase.targetTools()) {
            targetStates.put(targetTool.name(), stateManager.getToolStateSnapshot(sessionId, targetTool.name()));
        }
        return Map.copyOf(targetStates);
    }

    private boolean isRequiredTargetExecution(ExecutionRecord record,
                                              BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkCase.targetPath().stream()
                .anyMatch(targetStep -> targetStepMatches(record, targetStep));
    }

    private boolean isRecoveryExecution(ExecutionRecord record,
                                        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkCase.recoveryCommandName() != null
                && record.toolName() != null
                && targetToolNames(benchmarkCase).contains(record.toolName().toLowerCase())
                && commandMatches(record.commandName(), benchmarkCase.recoveryCommandName());
    }

    private boolean targetStepMatches(ExecutionRecord record, BenchmarkCaseGenerator.TargetStep targetStep) {
        return record.toolName() != null
                && record.toolName().equalsIgnoreCase(targetStep.toolName())
                && commandMatches(record.commandName(), targetStep.commandName());
    }

    private Set<String> targetToolNames(BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkCase.targetTools().stream()
                .map(ToolObject::name)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean commandMatches(String actualCommandName, String expectedCommandName) {
        return actualCommandName != null
                && expectedCommandName != null
                && actualCommandName.equalsIgnoreCase(expectedCommandName);
    }

    private CommandObject findCommand(ToolObject tool, String commandName) {
        if (tool == null || commandName == null || commandName.isBlank()) {
            return null;
        }
        return tool.commands().stream()
                .filter(command -> commandMatches(command.name(), commandName))
                .findFirst()
                .orElse(null);
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
