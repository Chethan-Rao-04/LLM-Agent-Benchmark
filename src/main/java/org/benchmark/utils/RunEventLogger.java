package org.benchmark.utils;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal structured JSONL event logger for benchmark tracing.
 *
 * <p>
 * Writes one JSON object per line.
 * </p>
 */
@Slf4j
public class RunEventLogger implements AutoCloseable {

    private static final Gson GSON = new Gson();

    private final PrintWriter writer;

    /**
     * Creates a JSONL event logger appending to the given file.
     *
     * @param jsonlFilename JSONL output file path
     */
    public RunEventLogger(String jsonlFilename) {
        try {
            this.writer = new PrintWriter(new FileWriter(jsonlFilename, true));
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize event logger", e);
        }
    }

    /**
     * Writes one structured event envelope to the JSONL log.
     *
     * @param eventType event type name
     * @param payload   event-specific data
     */
    public synchronized void log(String eventType, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        envelope.put("eventType", eventType);
        envelope.putAll(payload);
        writer.println(GSON.toJson(envelope));
        writer.flush();
    }

    @Override
    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (Exception e) {
            log.warn("Error closing JSONL writer", e);
        }
    }
}
