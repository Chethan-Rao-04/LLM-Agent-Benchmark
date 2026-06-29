package org.benchmark.scoring;

import lombok.RequiredArgsConstructor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.ToolObject;
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
        double stateAccuracy = scoreExpectedState(benchmarkCase.expectedState(), targetToolState(sessionId, benchmarkCase));
        double efficiency = scoreEfficiency(logs, benchmarkCase);
        double commandPrecision = scoreCommandPrecision(logs, benchmarkCase);
        double decoyResistance = scoreDecoyResistance(logs, benchmarkCase);
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
        boolean scenarioComplete = hasSuccessfulScenarioCompletion(logs, benchmarkCase,
                targetToolState(sessionId, benchmarkCase));
        return new AttemptMetrics(
                scoreToolSelection(logs, benchmarkCase),
                stepCompletion,
                ordering,
                scoreExpectedState(benchmarkCase.expectedState(), targetToolState(sessionId, benchmarkCase)),
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
     * @param actualToolState latest target-tool state snapshot
     * @return {@code true} when the attempt satisfied the scenario completely
     */
    public boolean hasSuccessfulScenarioCompletion(List<ExecutionRecord> executions,
                                                    BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                                    Map<String, String> actualToolState) {
        return scoreStepCompletion(executions, benchmarkCase) == 1.0
                && scoreOrdering(executions, benchmarkCase) == 1.0
                && scoreExpectedState(benchmarkCase.expectedState(), actualToolState) == 1.0;
    }

    /**
     * Scores how many required scenario steps have at least one successful execution on the target tool.
     *
     * @param logs full execution log for the case
     * @param benchmarkCase generated benchmark case
     * @return completion ratio in the range {@code [0.0, 1.0]}
     */
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

    /**
     * Scores whether successful target-tool executions appear in the same order as the scenario steps.
     *
     * @param logs full execution log for the case
     * @param benchmarkCase generated benchmark case
     * @return ordering score in the range {@code [0.0, 1.0]}
     */
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

    /**
     * Scores how well the agent resisted semantic decoys.
     *
     * <p>Harmless probing lowers the score slightly, while a successful semantic-decoy mutation
     * lowers it more heavily because it shows the agent committed to the wrong state path.</p>
     */
    public double scoreDecoyResistance(List<ExecutionRecord> logs,
                                       BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        if (!benchmarkCase.spec().scoringPolicy().penalizeSemanticDecoyUse()) {
            return 1.0;
        }

        double penalty = 0.0;
        for (ExecutionRecord record : logs) {
            ToolObject semanticDecoy = semanticDecoyTool(record.toolName(), benchmarkCase);
            if (semanticDecoy == null) {
                continue;
            }

            CommandObject command = findCommand(semanticDecoy, record.commandName());
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

    private ToolObject semanticDecoyTool(String toolName,
                                         BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        return benchmarkCase.semanticDecoys().stream()
                .filter(tool -> tool.name().equalsIgnoreCase(toolName))
                .findFirst()
                .orElse(null);
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
