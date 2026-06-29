package org.benchmark.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.BenchmarkCaseExecutor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.logging.BenchmarkEventLogger;
import org.slf4j.MDC;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

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

    private static final String BENCHMARK_RUN_ID_MDC_KEY = "benchmarkRunId";

    private final BenchmarkProperties properties;
    private final SessionStateManager stateManager;
    private final BenchmarkCaseGenerator caseGenerator;
    private final ConfigurableApplicationContext applicationContext;
    private final BenchmarkCaseExecutor caseExecutor;
    private final BenchmarkEventLogger eventLogger;
    private final BenchmarkRunSummaryAggregator summaryAggregator;
    private final BenchmarkRunSummaryPublisher summaryPublisher;

    /**
     * Starts the benchmark after Spring Boot finishes initialization and then closes the application context.
     *
     * @param args raw application arguments
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            runBenchmark();
        } finally {
            applicationContext.close();
        }
    }

    /**
     * Executes the configured number of benchmark cases and publishes the aggregate summary at the end.
     */
    public void runBenchmark() {
        String benchmarkRunId = UUID.randomUUID().toString();
        MDC.put(BENCHMARK_RUN_ID_MDC_KEY, benchmarkRunId);
        try {
            log.info("""
                    Benchmark run started
                    runId: {}
                    model: {}
                    iterations: {}
                    documentComplexity: {}
                    maxAttemptsPerCase: {}
                    distractorCount: {}
                    eventLogFile: logs/benchmark-run-{}.jsonl
                    markdownReportFile: logs/benchmark-run-{}.md
                    csvSummaryFile: logs/benchmark-run-{}.csv
                    """,
                    benchmarkRunId,
                    properties.getLlm().getModel(),
                    properties.getIterations(),
                    properties.getDocumentComplexity(),
                    properties.getMaxExecutionSteps(),
                    properties.getDistractorCount(),
                    benchmarkRunId,
                    benchmarkRunId,
                    benchmarkRunId);
            eventLogger.logEvent("benchmark_run_started", java.util.Map.of(
                    "benchmarkRunId", benchmarkRunId,
                    "model", properties.getLlm().getModel(),
                    "iterations", properties.getIterations(),
                    "documentComplexity", properties.getDocumentComplexity(),
                    "maxRetries", properties.getMaxExecutionSteps(),
                    "distractorCount", properties.getDistractorCount()));

            var benchmarkCases = caseGenerator.generateCases(
                    properties.getIterations(),
                    properties.getDistractorCount(),
                    properties.getDomain());

            BenchmarkRunSummary summary = summaryAggregator.createSummary();

            for (int i = 0; i < benchmarkCases.size(); i++) {
                var benchmarkCase = benchmarkCases.get(i);
                String sessionId = UUID.randomUUID().toString();

                stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
                String userQuery = benchmarkCase.generateUserQuery();

                try {
                    var result = caseExecutor.execute(sessionId, benchmarkCase, userQuery, i + 1);
                    summaryAggregator.record(summary, result.score(), sessionId, i + 1, result.attemptsUsed(), benchmarkCase);
                } finally {
                    stateManager.clearSession(sessionId);
                }
            }

            summaryPublisher.publish(summary, benchmarkRunId);
        } finally {
            MDC.remove(BENCHMARK_RUN_ID_MDC_KEY);
        }
    }

}
