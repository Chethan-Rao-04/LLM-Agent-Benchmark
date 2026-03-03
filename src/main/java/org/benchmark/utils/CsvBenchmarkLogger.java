package org.benchmark.utils;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CsvBenchmarkLogger {
    private final PrintWriter writer;

    public CsvBenchmarkLogger(String filename) {
        try {
            // Appends to existing file or creates new one
            FileWriter fw = new FileWriter(filename, true);
            writer = new PrintWriter(fw);

            // Write Header if file is empty (simple heuristic)
            if (new java.io.File(filename).length() == 0) {
                writer.println("Timestamp,Model,Latency_Inference_ms,Latency_Total_ms,Tokens,Tool_Match,State_Score,Pass_Fail");
                writer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize logger", e);
        }
    }

    public void log(String model, long inferenceMs, long totalMs, int tokens,
                    boolean toolMatch, double stateScore, boolean passed) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);

        // CSV Format: Timestamp, Model, Inf_Latency, Tot_Latency, Tokens, Tool_Match, State_Score, Pass/Fail
        writer.printf("%s,%s,%d,%d,%d,%b,%.2f,%s%n",
                timestamp, model, inferenceMs, totalMs, tokens, toolMatch, stateScore, passed ? "PASS" : "FAIL");
        writer.flush();
    }
}
