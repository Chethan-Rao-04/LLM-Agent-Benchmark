package org.benchmark.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.benchmark.config.BenchmarkProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Best-effort writer for benchmark summary scores in Langfuse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseScoreClient {

    private final BenchmarkProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void createTraceScore(String traceId, String name, Number value, Map<String, Object> metadata) {
        BenchmarkProperties.LangfuseProperties langfuse = properties.getLangfuse();
        if (!langfuse.isEnabled() || !langfuse.isCreateScores() || traceId == null || traceId.isBlank()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("name", name);
        payload.put("value", value);
        payload.put("dataType", "NUMERIC");
        payload.put("metadata", metadata);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LangfuseApiSupport.apiUrl(langfuse, "/api/public/scores")))
                    .header(HttpHeaders.AUTHORIZATION, LangfuseApiSupport.basicAuthHeader(langfuse))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Langfuse score ingestion failed for {} with status {}: {}",
                        name, response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to write Langfuse score {}: {}", name, e.getMessage());
        }
    }
}
