package org.benchmark.app;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles structured benchmark event logging and file flushing in one place.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BenchmarkEventLogger {

    private static final Logger EVENTS = LoggerFactory.getLogger("BENCHMARK_EVENTS");

    private final ObjectMapper objectMapper;

    public void logEvent(String eventType, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        payload.put("eventType", eventType);
        payload.putAll(data);
        logPayload(payload);
    }

    public void logPayload(Map<String, Object> payload) {
        try {
            EVENTS.info(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize event payload: {}", e.getMessage());
        }
    }

    public String toJson(Map<String, Object> payload, String failureMessage) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("{}: {}", failureMessage, e.getMessage());
            return payload.toString();
        }
    }

    public void blankLine() {
        EVENTS.info("");
    }

    public void flushFileAppender() {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger eventsLogger = loggerContext.getLogger("BENCHMARK_EVENTS");
            var appenders = eventsLogger.iteratorForAppenders();
            while (appenders.hasNext()) {
                Appender<?> appender = appenders.next();
                if (appender instanceof FileAppender<?> fileAppender) {
                    fileAppender.getOutputStream().flush();
                }
            }
        } catch (Exception e) {
            log.warn("Could not flush events file: {}", e.getMessage());
        }
    }
}
