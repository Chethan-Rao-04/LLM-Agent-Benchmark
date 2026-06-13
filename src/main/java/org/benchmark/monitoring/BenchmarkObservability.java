package org.benchmark.monitoring;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Creates benchmark-specific observations on top of Spring's tracing support.
 */
@Component
@RequiredArgsConstructor
public class BenchmarkObservability {

    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;

    public Observation startBenchmarkRunObservation(BenchmarkProperties properties, String benchmarkRunId) {
        return Observation.start("benchmark.run", observationRegistry)
                .contextualName("benchmark-run")
                .lowCardinalityKeyValue("benchmark.model", safe(properties.getLlm().getModel()))
                .lowCardinalityKeyValue("benchmark.domain", safe(properties.getDomain()))
                .lowCardinalityKeyValue("benchmark.document_complexity", safe(properties.getDocumentComplexity()))
                .highCardinalityKeyValue("langfuse.trace.name", "benchmark-run")
                .highCardinalityKeyValue("langfuse.session.id", benchmarkRunId)
                .highCardinalityKeyValue("langfuse.trace.metadata.benchmark_run_id", benchmarkRunId)
                .highCardinalityKeyValue("langfuse.trace.metadata.model", safe(properties.getLlm().getModel()))
                .highCardinalityKeyValue("langfuse.trace.metadata.domain", safe(properties.getDomain()))
                .highCardinalityKeyValue("langfuse.trace.metadata.document_complexity", safe(properties.getDocumentComplexity()))
                .highCardinalityKeyValue("langfuse.trace.metadata.iterations", String.valueOf(properties.getIterations()))
                .highCardinalityKeyValue("langfuse.trace.metadata.distractor_count", String.valueOf(properties.getDistractorCount()))
                .highCardinalityKeyValue("langfuse.trace.metadata.max_retries", String.valueOf(properties.getMaxRetries()))
                .highCardinalityKeyValue("langfuse.trace.input", buildRunInput(properties));
    }

    public Observation startCaseObservation(int caseIndex,
                                            String sessionId,
                                            String userQuery,
                                            BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return Observation.start("benchmark.case", observationRegistry)
                .contextualName("benchmark-case")
                .lowCardinalityKeyValue("benchmark.case.index", String.valueOf(caseIndex))
                .lowCardinalityKeyValue("benchmark.case.target_tool", safe(benchmarkCase.targetToolObject().name()))
                .highCardinalityKeyValue("langfuse.observation.input", safe(userQuery))
                .highCardinalityKeyValue("langfuse.observation.metadata.case_index", String.valueOf(caseIndex))
                .highCardinalityKeyValue("langfuse.observation.metadata.session_id", sessionId)
                .highCardinalityKeyValue("langfuse.observation.metadata.target_tool", safe(benchmarkCase.targetToolObject().name()))
                .highCardinalityKeyValue("langfuse.observation.metadata.scenario_pattern", safe(benchmarkCase.scenario().patternName()));
    }

    public Optional<String> currentTraceId() {
        return Optional.ofNullable(tracer.currentSpan())
                .map(Span::context)
                .map(TraceContext::traceId);
    }

    public void tagCurrentSpan(String key, Object value) {
        if (value == null) {
            return;
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, String.valueOf(value));
        }
    }

    private String buildRunInput(BenchmarkProperties properties) {
        return """
                {"model":"%s","domain":"%s","documentComplexity":"%s","iterations":%d,"distractorCount":%d,"maxRetries":%d}
                """.formatted(
                safe(properties.getLlm().getModel()),
                safe(properties.getDomain()),
                safe(properties.getDocumentComplexity()),
                properties.getIterations(),
                properties.getDistractorCount(),
                properties.getMaxRetries()).replace("\n", "");
    }

    private String safe(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }
}
