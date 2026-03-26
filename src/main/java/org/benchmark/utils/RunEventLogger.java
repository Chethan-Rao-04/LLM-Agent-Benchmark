package org.benchmark.utils;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal structured JSONL event logger for benchmark tracing.
 */
public class RunEventLogger {

    private static final Gson GSON = new Gson();
    private static final String CASE_COMPLETED_EVENT = "case_completed";

    private final PrintWriter jsonlWriter;
    private final PrintWriter csvWriter;

    /**
     * Creates a JSONL event logger with optional CSV export for case summaries.
     *
     * @param jsonlFilename JSONL output file path
     * @param csvFilename optional CSV output file path, blank to disable
     */
    public RunEventLogger(String jsonlFilename, String csvFilename) {
        try {
            this.jsonlWriter = new PrintWriter(new FileWriter(jsonlFilename, true));
            this.csvWriter = initializeCsvWriter(csvFilename);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize event logger", e);
        }
    }

    /**
     * Writes one structured event envelope to the JSONL log.
     *
     * @param eventType event type name
     * @param payload event-specific data
     */
    public synchronized void log(String eventType, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        envelope.put("eventType", eventType);
        envelope.putAll(payload);
        jsonlWriter.println(GSON.toJson(envelope));
        jsonlWriter.flush();

        if (CASE_COMPLETED_EVENT.equals(eventType)) {
            appendCaseSummaryCsv(envelope);
        }
    }

    private PrintWriter initializeCsvWriter(String csvFilename) throws IOException {
        if (csvFilename == null || csvFilename.isBlank()) {
            return null;
        }

        File file = new File(csvFilename);
        PrintWriter writer = new PrintWriter(new FileWriter(file, true));
        if (file.length() == 0) {
            writer.println("Timestamp,Model,SessionId,Attempts,Latency_Total_ms,Tokens,Tool_Match,State_Score,Pass_Fail");
            writer.flush();
        }
        return writer;
    }

    private void appendCaseSummaryCsv(Map<String, Object> envelope) {
        if (csvWriter == null) {
            return;
        }

        csvWriter.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                csvValue(envelope.get("timestamp")),
                csvValue(envelope.get("model")),
                csvValue(envelope.get("sessionId")),
                csvValue(envelope.get("attempts")),
                csvValue(envelope.get("totalLatencyMs")),
                csvValue(envelope.get("totalTokenUsage")),
                csvValue(envelope.get("toolMatch")),
                csvValue(envelope.get("finalStateScore")),
                Boolean.TRUE.equals(envelope.get("passed")) ? "PASS" : "FAIL");
        csvWriter.flush();
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
