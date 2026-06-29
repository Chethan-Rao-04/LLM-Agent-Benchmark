package org.benchmark.runner;

import lombok.RequiredArgsConstructor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;
import org.benchmark.scoring.BenchmarkScorer.BenchmarkScore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregates per-case scores and session telemetry into benchmark-wide totals.
 */
@Component
@RequiredArgsConstructor
class BenchmarkRunSummaryAggregator {

    private final SessionStateManager stateManager;

    BenchmarkRunSummary createSummary() {
        return new BenchmarkRunSummary();
    }

    void record(BenchmarkRunSummary summary,
                BenchmarkScore result,
                String sessionId,
                int caseIndex,
                int attemptsUsed,
                BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        List<SessionStateManager.ExecutionRecord> executionLog = stateManager.executionLog(sessionId);
        List<SessionStateManager.CommandRejectionRecord> commandRejections = stateManager.commandRejectionLog(sessionId);
        if (result.passed()) {
            summary.totalSuccess++;
        }
        if (result.recovery()) {
            summary.autonomousRecoveries++;
        }
        summary.totalExecutions += executionLog.size();
        summary.totalComposite += result.composite();
        summary.totalToolSelection += result.toolSelection();
        summary.totalStepCompletion += result.stepCompletionRate();
        summary.totalOrdering += result.orderingAccuracy();
        summary.totalStateAccuracy += result.stateAccuracy();
        summary.totalEfficiency += result.efficiency();
        summary.totalCommandPrecision += result.commandPrecision();
        summary.totalDecoyResistance += result.decoyResistance();
        summary.caseReports.add(new BenchmarkRunCaseReport(
                caseIndex,
                sessionId,
                benchmarkCase.targetToolObject().name(),
                result.passed(),
                result.recovery(),
                attemptsUsed,
                executionLog.size(),
                result.composite(),
                result.toolSelection(),
                result.stepCompletionRate(),
                result.orderingAccuracy(),
                result.stateAccuracy(),
                result.efficiency(),
                result.commandPrecision(),
                result.decoyResistance(),
                formatExpectedSteps(benchmarkCase),
                formatExecutions(executionLog),
                formatRejections(commandRejections)
        ));
    }

    Map<String, Number> metrics(BenchmarkRunSummary summary, int iterations) {
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("successes", summary.totalSuccess);
        metrics.put("autonomousRecoveries", summary.autonomousRecoveries);
        metrics.put("benchmarkExecutions", summary.totalExecutions);
        metrics.put("averageComposite", average(summary.totalComposite, iterations));
        metrics.put("averageToolSelection", average(summary.totalToolSelection, iterations));
        metrics.put("averageStepCompletion", average(summary.totalStepCompletion, iterations));
        metrics.put("averageOrdering", average(summary.totalOrdering, iterations));
        metrics.put("averageStateAccuracy", average(summary.totalStateAccuracy, iterations));
        metrics.put("averageEfficiency", average(summary.totalEfficiency, iterations));
        metrics.put("averageCommandPrecision", average(summary.totalCommandPrecision, iterations));
        metrics.put("averageDecoyResistance", average(summary.totalDecoyResistance, iterations));
        return metrics;
    }

    private double average(double total, int iterations) {
        return iterations <= 0 ? 0.0 : total / iterations;
    }

    private List<String> formatExpectedSteps(BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        List<String> steps = new ArrayList<>();
        for (ResolvedStep step : benchmarkCase.targetSteps()) {
            String commandName = CommandAbbreviator.commandName(step.verb(), step.noun());
            steps.add(formatTargetStep(benchmarkCase.targetToolObject(), commandName));
        }
        return List.copyOf(steps);
    }

    private List<String> formatExecutions(List<SessionStateManager.ExecutionRecord> executionLog) {
        return executionLog.stream()
                .map(record -> "%s %s %s%s".formatted(
                        record.success() ? "SUCCESS" : "ERROR",
                        record.toolName(),
                        record.commandName(),
                        formatOption(record.option())))
                .toList();
    }

    private List<String> formatRejections(List<SessionStateManager.CommandRejectionRecord> commandRejections) {
        return commandRejections.stream()
                .map(record -> "%s %s%s: %s".formatted(
                        record.toolName(),
                        record.commandName(),
                        formatOption(record.option()),
                        record.message()))
                .toList();
    }

    private String formatTargetStep(ToolObject targetTool, String commandName) {
        return targetTool.commands().stream()
                .filter(command -> command.name().equalsIgnoreCase(commandName))
                .findFirst()
                .map(command -> commandName + formatOptions(command.commandOptions()))
                .orElse(commandName);
    }

    private String formatOptions(List<OptionEntity> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        return options.stream()
                .map(OptionEntity::optionName)
                .collect(Collectors.joining(", ", " {options: ", "}"));
    }

    private String formatOption(String option) {
        return option == null || option.isBlank() ? "" : " " + option;
    }
}
