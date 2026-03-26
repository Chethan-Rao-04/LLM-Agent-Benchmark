package org.benchmark.mcp.runtime;

import org.benchmark.gen.BenchmarkCaseGenerator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared in-memory registry used by the runner and MCP surface to access
 * benchmark-case data and execution traces by session ID.
 */
@Component
public class BenchmarkSessionRegistry {

    private final Map<String, BenchmarkCaseGenerator.BenchmarkCase> casesBySessionId = new ConcurrentHashMap<>();
    private final Map<String, List<BenchmarkExecutionRecord>> executionLogBySessionId = new ConcurrentHashMap<>();

    public void register(String sessionId, BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        casesBySessionId.put(sessionId, benchmarkCase);
        executionLogBySessionId.put(sessionId, new CopyOnWriteArrayList<>());
    }

    public BenchmarkCaseGenerator.BenchmarkCase getBenchmarkCase(String sessionId) {
        return casesBySessionId.get(sessionId);
    }

    public void record(String sessionId, BenchmarkExecutionRecord executionRecord) {
        executionLogBySessionId.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>())
                .add(executionRecord);
    }

    public List<BenchmarkExecutionRecord> executionLog(String sessionId) {
        return executionLogBySessionId.getOrDefault(sessionId, List.of());
    }

    public void clear(String sessionId) {
        casesBySessionId.remove(sessionId);
        executionLogBySessionId.remove(sessionId);
    }
}
