package org.benchmark.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.logging.BenchmarkEventLogger;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes the final benchmark summary to both console logs and the structured event stream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BenchmarkRunSummaryPublisher {

    private final BenchmarkProperties properties;
    private final BenchmarkEventLogger eventLogger;
    private final BenchmarkRunSummaryAggregator summaryAggregator;
    private final BenchmarkRunArtifactWriter artifactWriter;

    void publish(BenchmarkRunSummary summary, String benchmarkRunId) {
        int iterations = properties.getIterations();
        Map<String, Number> summaryMetrics = summaryAggregator.metrics(summary, iterations);

        log.info("Benchmark summary");
        log.info("Model: {}", properties.getLlm().getModel());
        log.info("Documentation type: {}", properties.getDocumentComplexity());
        log.info("Iterations: {}", iterations);
        log.info("Distractors per case: {}", properties.getDistractorCount());
        log.info("Max retries per case: {}", properties.getMaxExecutionSteps());
        log.info("Successes: {}/{}", summary.totalSuccess, iterations);
        log.info("Autonomous recoveries: {}", summary.autonomousRecoveries);
        log.info("Benchmark executions: {}", summary.totalExecutions);
        log.info("Average composite: {}", formatDecimal(summaryMetrics.get("averageComposite").doubleValue(), 3));
        log.info("Average tool selection: {}", formatDecimal(summaryMetrics.get("averageToolSelection").doubleValue(), 2));
        log.info("Average step completion: {}", formatDecimal(summaryMetrics.get("averageStepCompletion").doubleValue(), 2));
        log.info("Average ordering: {}", formatDecimal(summaryMetrics.get("averageOrdering").doubleValue(), 2));
        log.info("Average state accuracy: {}", formatDecimal(summaryMetrics.get("averageStateAccuracy").doubleValue(), 2));
        log.info("Average efficiency: {}", formatDecimal(summaryMetrics.get("averageEfficiency").doubleValue(), 2));
        log.info("Average command precision: {}", formatDecimal(summaryMetrics.get("averageCommandPrecision").doubleValue(), 2));
        log.info("Average target-like wrong tool avoidance: {}",
                formatDecimal(summaryMetrics.get("averageTargetLikeWrongToolAvoidance").doubleValue(), 2));

        eventLogger.logEvent("benchmark_run_completed", buildSummaryPayload(summaryMetrics, benchmarkRunId));
        artifactWriter.writeArtifacts(summary, summaryMetrics, benchmarkRunId);

        eventLogger.flushFileAppender();
    }

    private String formatDecimal(double value, int scale) {
        return String.format("%." + scale + "f", value);
    }

    private Map<String, Object> buildSummaryPayload(Map<String, Number> summaryMetrics, String benchmarkRunId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("benchmarkRunId", benchmarkRunId);
        payload.put("model", properties.getLlm().getModel());
        payload.put("documentComplexity", properties.getDocumentComplexity());
        payload.put("iterations", properties.getIterations());
        payload.put("distractorCount", properties.getDistractorCount());
        payload.put("maxRetries", properties.getMaxExecutionSteps());
        payload.putAll(summaryMetrics);
        return payload;
    }
}
