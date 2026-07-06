package org.benchmark.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes human-readable benchmark artifacts beside the raw JSONL event log.
 */
@Slf4j
@Component
class BenchmarkRunArtifactWriter {

    private static final Path LOG_DIRECTORY = Path.of("logs");

    void writeArtifacts(BenchmarkRunSummary summary,
                        Map<String, Number> summaryMetrics,
                        String benchmarkRunId) {
        try {
            Files.createDirectories(LOG_DIRECTORY);
            Files.writeString(markdownPath(benchmarkRunId), toMarkdown(summary, summaryMetrics, benchmarkRunId));
            Files.writeString(csvPath(benchmarkRunId), toCsv(summary));
        } catch (IOException e) {
            log.warn("Could not write benchmark report artifacts: {}", e.getMessage());
        }
    }

    Path markdownPath(String benchmarkRunId) {
        return LOG_DIRECTORY.resolve("benchmark-run-" + benchmarkRunId + ".md");
    }

    Path csvPath(String benchmarkRunId) {
        return LOG_DIRECTORY.resolve("benchmark-run-" + benchmarkRunId + ".csv");
    }

    String toMarkdown(BenchmarkRunSummary summary,
                      Map<String, Number> summaryMetrics,
                      String benchmarkRunId) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Benchmark Run Report\n\n");
        markdown.append("Run ID: `").append(benchmarkRunId).append("`\n\n");
        markdown.append("## Summary\n");
        markdown.append("- Successes: ").append(summaryMetrics.get("successes")).append('\n');
        markdown.append("- Autonomous recoveries: ").append(summaryMetrics.get("autonomousRecoveries")).append('\n');
        markdown.append("- Executions: ").append(summaryMetrics.get("benchmarkExecutions")).append('\n');
        markdown.append("- Average composite: ").append(format(summaryMetrics.get("averageComposite").doubleValue(), 3)).append('\n');
        markdown.append("- Average tool selection: ").append(format(summaryMetrics.get("averageToolSelection").doubleValue(), 2)).append('\n');
        markdown.append("- Average step completion: ").append(format(summaryMetrics.get("averageStepCompletion").doubleValue(), 2)).append('\n');
        markdown.append("- Average ordering: ").append(format(summaryMetrics.get("averageOrdering").doubleValue(), 2)).append('\n');
        markdown.append("- Average state accuracy: ").append(format(summaryMetrics.get("averageStateAccuracy").doubleValue(), 2)).append('\n');
        markdown.append("- Average efficiency: ").append(format(summaryMetrics.get("averageEfficiency").doubleValue(), 2)).append('\n');
        markdown.append("- Average command precision: ").append(format(summaryMetrics.get("averageCommandPrecision").doubleValue(), 2)).append('\n');
        markdown.append("- Average target-like wrong tool avoidance: ")
                .append(format(metric(summaryMetrics, "averageTargetLikeWrongToolAvoidance", "averageDecoyResistance"), 2))
                .append("\n\n");

        for (BenchmarkRunCaseReport report : summary.caseReports) {
            markdown.append("## Case ").append(report.caseIndex()).append('\n');
            markdown.append("Session: ").append(report.sessionId()).append('\n');
            markdown.append("Target tool: ").append(report.targetTool()).append('\n');
            markdown.append("Target-like wrong tools:\n");
            appendList(markdown, report.targetLikeWrongTools());
            markdown.append("Result: ").append(report.passed() ? "passed" : "failed").append('\n');
            markdown.append("Recovery: ").append(report.recovery()).append('\n');
            markdown.append("Attempts: ").append(report.attemptsUsed()).append('\n');
            markdown.append("Executions: ").append(report.executionCount()).append('\n');
            markdown.append("Score: ").append(format(report.compositeScore(), 3)).append('\n');
            markdown.append("Target-like wrong tool avoidance: ")
                    .append(format(report.decoyResistance(), 2))
                    .append('\n');
            markdown.append('\n');
            markdown.append("### Expected Steps\n");
            appendList(markdown, report.expectedSteps());
            markdown.append('\n');
            markdown.append("### Executions\n");
            appendList(markdown, report.executions());
            markdown.append('\n');
        }
        return markdown.toString();
    }

    String toCsv(BenchmarkRunSummary summary) {
        StringBuilder csv = new StringBuilder();
        csv.append("case,session,passed,score,attempts,executions,toolSelection,stepCompletion,orderingAccuracy,stateAccuracy,efficiency,commandPrecision,targetLikeWrongToolAvoidance,recovery\n");
        for (BenchmarkRunCaseReport report : summary.caseReports) {
            csv.append(report.caseIndex()).append(',')
                    .append(escapeCsv(report.sessionId())).append(',')
                    .append(report.passed()).append(',')
                    .append(format(report.compositeScore(), 3)).append(',')
                    .append(report.attemptsUsed()).append(',')
                    .append(report.executionCount()).append(',')
                    .append(format(report.toolSelection(), 2)).append(',')
                    .append(format(report.stepCompletionRate(), 2)).append(',')
                    .append(format(report.orderingAccuracy(), 2)).append(',')
                    .append(format(report.stateAccuracy(), 2)).append(',')
                    .append(format(report.efficiency(), 2)).append(',')
                    .append(format(report.commandPrecision(), 2)).append(',')
                    .append(format(report.decoyResistance(), 2)).append(',')
                    .append(report.recovery())
                    .append('\n');
        }
        return csv.toString();
    }

    private void appendList(StringBuilder markdown, java.util.List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            markdown.append("- None\n");
            return;
        }
        for (String line : lines) {
            markdown.append("- ").append(line).append('\n');
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String format(double value, int scale) {
        return String.format("%." + scale + "f", value);
    }

    private double metric(Map<String, Number> metrics, String primaryKey, String fallbackKey) {
        Number value = metrics.get(primaryKey);
        if (value == null) {
            value = metrics.get(fallbackKey);
        }
        return value == null ? 0.0 : value.doubleValue();
    }
}
