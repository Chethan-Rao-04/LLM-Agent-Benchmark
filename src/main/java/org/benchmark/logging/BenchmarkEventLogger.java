package org.benchmark.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.AppenderAttachable;
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
 *
 * <p>This component is the single writer for JSONL benchmark events so higher-level
 * services can focus on assembling payloads rather than serialization details.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BenchmarkEventLogger {

    private static final Logger EVENTS = LoggerFactory.getLogger("BENCHMARK_EVENTS");

    private final ObjectMapper objectMapper;

    /**
     * Writes one structured benchmark event after merging caller data into the standard envelope.
     *
     * @param eventType stable event identifier
     * @param data event-specific fields
     */
    public void logEvent(String eventType, Map<String, Object> data) {
        Map<String, Object> payload = newEventPayload(eventType);
        payload.putAll(data);
        logPayload(payload);
    }

    /**
     * Creates the standard event envelope shared by all JSONL entries.
     *
     * @param eventType stable event identifier
     * @return mutable payload pre-populated with timestamp and type
     */
    public Map<String, Object> newEventPayload(String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        payload.put("eventType", eventType);
        return payload;
    }

    /**
     * Serializes and writes a fully assembled event payload.
     *
     * @param payload event payload ready for JSON serialization
     */
    public void logPayload(Map<String, Object> payload) {
        try {
            EVENTS.info(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize event payload: {}", e.getMessage());
        }
    }

    /**
     * Inserts a blank line into the event stream to improve readability during local inspection.
     */
    public void blankLine() {
        EVENTS.info("");
    }

    /**
     * Flushes any file-backed event appenders so local analysis can read the JSONL file immediately after a run.
     */
    public void flushFileAppender() {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger eventsLogger = loggerContext.getLogger("BENCHMARK_EVENTS");
            flushAppenderTree(eventsLogger);
        } catch (Exception e) {
            log.warn("Could not flush events file: {}", e.getMessage());
        }
    }

    private void flushAppenderTree(AppenderAttachable<?> appenderAttachable) throws java.io.IOException {
        var appenders = appenderAttachable.iteratorForAppenders();
        while (appenders.hasNext()) {
            Appender<?> appender = appenders.next();
            if (appender instanceof FileAppender<?> fileAppender && fileAppender.getOutputStream() != null) {
                fileAppender.getOutputStream().flush();
            }
            if (appender instanceof AppenderAttachable<?> nestedAppenderAttachable) {
                flushAppenderTree(nestedAppenderAttachable);
            }
        }
    }
}
