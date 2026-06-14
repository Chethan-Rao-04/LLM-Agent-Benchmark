package org.benchmark.app;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import io.micrometer.observation.Observation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.benchmark.app.BenchmarkScorer.BenchmarkScore;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.monitoring.BenchmarkObservability;
import org.benchmark.monitoring.LangfuseScoreClient;
import org.benchmark.tools.runtime.BenchmarkCaseToolCallbackFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The primary entry point for the benchmark application. It coordinates the
 * entire benchmark lifecycle: generating test cases, sending prompts to the LLM,
 * managing session states, and collecting telemetry/scores across multiple
 * iterations.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class BenchmarkRunner implements ApplicationRunner {

    private static final String EVENTS_JSONL = "benchmark_run_events.jsonl";

    private final BenchmarkProperties properties;
    private final SessionStateManager stateManager;
    private final BenchmarkCaseGenerator caseGenerator;
    private final BenchmarkCaseToolCallbackFactory toolCallbackFactory;
    private final ConfigurableApplicationContext applicationContext;
    private final BenchmarkCaseExecutor caseExecutor;
    private final BenchmarkObservability observability;
    private final LangfuseScoreClient langfuseScoreClient;
    private final BenchmarkEventLogger eventLogger;

    @Override
    public void run(ApplicationArguments args) {
        try {
            runBenchmark();
        } finally {
            applicationContext.close();
        }
    }

    /**
     * Runs the autonomous execution benchmark for the configured number of
     * iterations.
     */
    public void runBenchmark() {
        String benchmarkRunId = UUID.randomUUID().toString();
        Observation benchmarkObservation = observability.startBenchmarkRunObservation(properties, benchmarkRunId);

        try (Observation.Scope ignored = benchmarkObservation.openScope()) {
            log.info("Generating Autonomous Execution Benchmark..");
            logEvent("benchmark_run_started", Map.of(
                    "benchmarkRunId", benchmarkRunId,
                    "model", properties.getLlm().getModel(),
                    "iterations", properties.getIterations(),
                    "documentComplexity", properties.getDocumentComplexity(),
                    "maxRetries", properties.getMaxRetries(),
                    "distractorCount", properties.getDistractorCount()));

            var benchmarkCases = caseGenerator.generateCases(
                    properties.getIterations(),
                    properties.getDistractorCount(),
                    properties.getDomain());

            BenchmarkRunSummary summary = new BenchmarkRunSummary();

            for (int i = 0; i < benchmarkCases.size(); i++) {
                var benchmarkCase = benchmarkCases.get(i);
                String sessionId = UUID.randomUUID().toString();

                stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());

                ToolCallbackProvider caseToolCallbacks = toolCallbackFactory.create(sessionId, benchmarkCase);
                String userQuery = benchmarkCase.generateUserQuery();
                BenchmarkScore result = caseExecutor.execute(
                        sessionId, benchmarkCase, caseToolCallbacks, userQuery, i + 1);
                summary.record(result, sessionId);

                stateManager.clearSession(sessionId);
            }

            logBenchmarkSummary(summary, benchmarkRunId, observability.currentTraceId().orElse(null));
        } catch (RuntimeException e) {
            benchmarkObservation.error(e);
            observability.tagCurrentSpan("langfuse.observation.level", "ERROR");
            observability.tagCurrentSpan("langfuse.observation.status_message", e.getMessage());
            throw e;
        } finally {
            benchmarkObservation.stop();
        }
    }

    /**
     * Writes the final benchmark summary to the console and Langfuse trace.
     */
    private void logBenchmarkSummary(BenchmarkRunSummary summary, String benchmarkRunId, String traceId) {
        int iterations = properties.getIterations();
        Map<String, Number> summaryMetrics = summaryMetrics(summary, iterations);

        log.info("\n==================== BENCHMARK SUMMARY ====================");
        log.info("Model:                {}", properties.getLlm().getModel());
        log.info("Documentation Type:   {}", properties.getDocumentComplexity());
        log.info("Iterations:           {}", iterations);
        log.info("Distractors / Case:   {}", properties.getDistractorCount());
        log.info("Max Retries / Case:   {}", properties.getMaxRetries());
        log.info("Successes:            {}/{}", summary.totalSuccess, iterations);
        log.info("Autonomous Recoveries: {}", summary.autonomousRecoveries);
        log.info("Sessions Using Discovery:   {}/{}", summary.sessionsUsingDiscovery, iterations);
        log.info("Discovery Calls:      {}", summary.totalDiscoveryCalls);
        log.info("Benchmark Executions: {}", summary.totalExecutions);
        log.info("Average Composite:    {}", formatDecimal(summaryMetrics.get("averageComposite").doubleValue(), 3));
        log.info("Average Tool Select:  {}", formatDecimal(summaryMetrics.get("averageToolSelection").doubleValue(), 2));
        log.info("Average Step Compl:   {}", formatDecimal(summaryMetrics.get("averageStepCompletion").doubleValue(), 2));
        log.info("Average Ordering:     {}", formatDecimal(summaryMetrics.get("averageOrdering").doubleValue(), 2));
        log.info("Average State Acc:    {}", formatDecimal(summaryMetrics.get("averageStateAccuracy").doubleValue(), 2));
        log.info("Average Efficiency:   {}", formatDecimal(summaryMetrics.get("averageEfficiency").doubleValue(), 2));
        log.info("Average Cmd Precision:{}", formatDecimal(summaryMetrics.get("averageCommandPrecision").doubleValue(), 2));
        log.info("===========================================================\n");

        tagSummaryMetrics(summaryMetrics);
        observability.tagCurrentSpan("langfuse.trace.output", buildSummaryPayload(summary, iterations, benchmarkRunId));

        flushEventsFile();

        File artifactFile = new File(EVENTS_JSONL);
        if (artifactFile.exists()) {
            observability.tagCurrentSpan("langfuse.trace.metadata.events_file", artifactFile.getAbsolutePath());
        } else {
            log.warn("Events file not found for Langfuse summary metadata: {}", EVENTS_JSONL);
        }

        publishSummaryScores(summary, iterations, benchmarkRunId, traceId);
    }

    private void flushEventsFile() {
        eventLogger.flushFileAppender();
    }

    private void logEvent(String eventType, Map<String, Object> data) {
        eventLogger.logEvent(eventType, data);
    }

    private String formatDecimal(double value, int scale) {
        return String.format("%." + scale + "f", value);
    }

    private String buildSummaryPayload(BenchmarkRunSummary summary, int iterations, String benchmarkRunId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("benchmarkRunId", benchmarkRunId);
        payload.putAll(summaryMetrics(summary, iterations));
        return eventLogger.toJson(payload, "Failed to serialize benchmark summary for Langfuse trace output");
    }

    private void publishSummaryScores(BenchmarkRunSummary summary, int iterations, String benchmarkRunId, String traceId) {
        Map<String, Object> metadata = Map.of(
                "benchmarkRunId", benchmarkRunId,
                "iterations", iterations,
                "model", properties.getLlm().getModel());

        for (Map.Entry<String, Number> score : summaryMetrics(summary, iterations).entrySet()) {
            langfuseScoreClient.createTraceScore(traceId, score.getKey(), score.getValue(), metadata);
        }
    }

    private void tagSummaryMetrics(Map<String, Number> summaryMetrics) {
        observability.tagCurrentSpan("langfuse.trace.metadata.successes", summaryMetrics.get("successes"));
        observability.tagCurrentSpan("langfuse.trace.metadata.autonomous_recoveries", summaryMetrics.get("autonomousRecoveries"));
        observability.tagCurrentSpan("langfuse.trace.metadata.sessions_using_discovery", summaryMetrics.get("sessionsUsingDiscovery"));
        observability.tagCurrentSpan("langfuse.trace.metadata.discovery_calls", summaryMetrics.get("discoveryCalls"));
        observability.tagCurrentSpan("langfuse.trace.metadata.benchmark_executions", summaryMetrics.get("benchmarkExecutions"));
        observability.tagCurrentSpan("langfuse.trace.metadata.average_composite", summaryMetrics.get("averageComposite"));
        observability.tagCurrentSpan("langfuse.trace.metadata.average_tool_selection", summaryMetrics.get("averageToolSelection"));
        observability.tagCurrentSpan("langfuse.trace.metadata.average_step_completion", summaryMetrics.get("averageStepCompletion"));
        observability.tagCurrentSpan("langfuse.trace.metadata.average_ordering", summaryMetrics.get("averageOrdering"));
        observability.tagCurrentSpan("langfuse.trace.metadata.average_state_accuracy", summaryMetrics.get("averageStateAccuracy"));
        observability.tagCurrentSpan("langfuse.trace.metadata.average_efficiency", summaryMetrics.get("averageEfficiency"));
        observability.tagCurrentSpan("langfuse.trace.metadata.average_command_precision", summaryMetrics.get("averageCommandPrecision"));
    }

    private Map<String, Number> summaryMetrics(BenchmarkRunSummary summary, int iterations) {
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("successes", summary.totalSuccess);
        metrics.put("autonomousRecoveries", summary.autonomousRecoveries);
        metrics.put("sessionsUsingDiscovery", summary.sessionsUsingDiscovery);
        metrics.put("discoveryCalls", summary.totalDiscoveryCalls);
        metrics.put("benchmarkExecutions", summary.totalExecutions);
        metrics.put("averageComposite", summary.totalComposite / iterations);
        metrics.put("averageToolSelection", summary.totalToolSelection / iterations);
        metrics.put("averageStepCompletion", summary.totalStepCompletion / iterations);
        metrics.put("averageOrdering", summary.totalOrdering / iterations);
        metrics.put("averageStateAccuracy", summary.totalStateAccuracy / iterations);
        metrics.put("averageEfficiency", summary.totalEfficiency / iterations);
        metrics.put("averageCommandPrecision", summary.totalCommandPrecision / iterations);
        return metrics;
    }

    /**
     * Collects benchmark-wide summary metrics while cases are executed.
     */
    private final class BenchmarkRunSummary {
        private int totalSuccess;
        private int autonomousRecoveries;
        private int sessionsUsingDiscovery;
        private int totalDiscoveryCalls;
        private int totalExecutions;
        private double totalComposite;
        private double totalToolSelection;
        private double totalStepCompletion;
        private double totalOrdering;
        private double totalStateAccuracy;
        private double totalEfficiency;
        private double totalCommandPrecision;

        private void record(BenchmarkScore result, String sessionId) {
            if (result.passed()) {
                totalSuccess++;
            }
            if (result.recovery()) {
                autonomousRecoveries++;
            }
            if (stateManager.discoveryUsed(sessionId)) {
                sessionsUsingDiscovery++;
            }
            totalDiscoveryCalls += stateManager.discoveryCount(sessionId);
            totalExecutions += stateManager.executionLog(sessionId).size();
            totalComposite += result.composite();
            totalToolSelection += result.toolSelection();
            totalStepCompletion += result.stepCompletionRate();
            totalOrdering += result.orderingAccuracy();
            totalStateAccuracy += result.stateAccuracy();
            totalEfficiency += result.efficiency();
            totalCommandPrecision += result.commandPrecision();
        }
    }
}
