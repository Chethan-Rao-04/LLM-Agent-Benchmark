package org.benchmark.monitoring;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.benchmark.config.BenchmarkProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * Wires OpenTelemetry trace export directly to Langfuse's OTLP endpoint.
 */
@Configuration
public class LangfuseTracingConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "benchmark.langfuse", name = "enabled", havingValue = "true")
    public SpanExporter langfuseSpanExporter(BenchmarkProperties properties) {
        BenchmarkProperties.LangfuseProperties langfuse = properties.getLangfuse();
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(LangfuseApiSupport.apiUrl(langfuse, "/api/public/otel/v1/traces"))
                .addHeader(HttpHeaders.AUTHORIZATION, LangfuseApiSupport.basicAuthHeader(langfuse))
                .addHeader("x-langfuse-ingestion-version", "4")
                .build();
    }
}
