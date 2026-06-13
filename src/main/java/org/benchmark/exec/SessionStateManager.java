package org.benchmark.exec;

import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.model.objects.ToolObject;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stores all mutable benchmark session data in one place.
 *
 * <p>Each session keeps its benchmark case, one runtime environment per tool,
 * and an append-only execution log.</p>
 */
@Component
public class SessionStateManager {

    /**
     * Captures one benchmark tool execution for later scoring.
     *
     * @param toolName tool used by the agent
     * @param commandName command executed inside the tool
     * @param option selected option, or empty when none was used
     * @param success whether the command succeeded
     * @param message simulator response message
     */
    public record ExecutionRecord(String toolName, String commandName, String option, boolean success, String message) {
    }

    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    /**
     * Registers a benchmark case and creates one empty runtime environment per tool.
     *
     * @param sessionId unique benchmark session id
     * @param benchmarkCase immutable case metadata bound to the session
     * @param tools tools exposed to the model for this case
     */
    public void initializeSession(String sessionId,
                                  BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                  List<ToolObject> tools) {
        SessionData data = getOrCreateSession(sessionId);
        synchronized (data) {
            data.benchmarkCase = benchmarkCase;
            data.environments.clear();
            data.executionLog.clear();
            data.documentedTools.clear();
            data.discoveryCalls.set(0);
            data.attemptStartIndex.set(0);

            if (tools != null) {
                for (ToolObject tool : tools) {
                    data.environments.put(tool.name(), new ToolEnvironment(tool.stateVariables().keySet()));
                }
            }
        }
    }

    /**
     * Returns the benchmark case bound to a session, or {@code null} when missing.
     */
    public BenchmarkCaseGenerator.BenchmarkCase getBenchmarkCase(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return null;
        }
        synchronized (data) {
            return data.benchmarkCase;
        }
    }

    /**
     * Appends one execution record to the session log.
     */
    public void recordExecution(String sessionId, ExecutionRecord record) {
        SessionData data = getOrCreateSession(sessionId);
        synchronized (data) {
            data.executionLog.add(record);
        }
    }

    /**
     * Marks the beginning of one LLM attempt so per-attempt safeguards can inspect only
     * the executions generated within the current turn.
     */
    public void startAttempt(String sessionId, int attempt) {
        SessionData data = getOrCreateSession(sessionId);
        synchronized (data) {
            data.attemptStartIndex.set(data.executionLog.size());
        }
    }

    /**
     * Returns the executions recorded since the start of the current attempt.
     */
    public List<ExecutionRecord> currentAttemptExecutions(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return List.of();
        }
        return executionLogFromIndex(sessionId, data.attemptStartIndex.get());
    }

    /**
     * Returns how many executions happened during the current attempt.
     */
    public int currentAttemptExecutionCount(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return 0;
        }
        synchronized (data) {
            return Math.max(0, data.executionLog.size() - data.attemptStartIndex.get());
        }
    }

    /**
     * Counts how many times the same tool/command/option already failed in the current attempt.
     */
    public long repeatedFailuresInCurrentAttempt(String sessionId, String toolName, String commandName, String option) {
        String normalizedOption = option == null ? "" : option.trim();
        return currentAttemptExecutions(sessionId).stream()
                .filter(record -> !record.success())
                .filter(record -> record.toolName().equalsIgnoreCase(toolName))
                .filter(record -> record.commandName().equalsIgnoreCase(commandName))
                .filter(record -> record.option().equalsIgnoreCase(normalizedOption))
                .count();
    }

    /**
     * Returns the execution log for a session.
     */
    public List<ExecutionRecord> executionLog(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return List.of();
        }
        synchronized (data) {
            return List.copyOf(data.executionLog);
        }
    }

    /**
     * Returns a stable snapshot of execution records from a specific index onward.
     */
    public List<ExecutionRecord> executionLogFromIndex(String sessionId, int startIndex) {
        List<ExecutionRecord> snapshot = executionLog(sessionId);
        if (snapshot.isEmpty()) {
            return List.of();
        }
        int safeStartIndex = Math.max(0, Math.min(startIndex, snapshot.size()));
        return List.copyOf(snapshot.subList(safeStartIndex, snapshot.size()));
    }

    /**
     * Marks that the agent used discovery or documentation tools in this session.
     */
    public void recordDiscovery(String sessionId) {
        getOrCreateSession(sessionId).discoveryCalls.incrementAndGet();
    }

    /**
     * Records that the agent read the documentation for a specific tool.
     */
    public void recordDocumentationRead(String sessionId, String toolName) {
        SessionData data = getOrCreateSession(sessionId);
        synchronized (data) {
            data.documentedTools.add(toolName.toUpperCase());
        }
    }

    /**
     * Returns the set of tool names whose documentation was read in this session.
     */
    public Set<String> documentedTools(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return Set.of();
        }
        synchronized (data) {
            return Set.copyOf(data.documentedTools);
        }
    }

    /**
     * Returns whether the agent used discovery or documentation tools.
     */
    public boolean discoveryUsed(String sessionId) {
        return discoveryCount(sessionId) > 0;
    }

    /**
     * Returns how many MCP discovery calls were made in this session.
     */
    public int discoveryCount(String sessionId) {
        SessionData data = sessions.get(sessionId);
        return data == null ? 0 : data.discoveryCalls.get();
    }

    /**
     * Returns the environment for one tool in one session.
     */
    public ToolEnvironment getEnvironment(String sessionId, String toolName) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return null;
        }
        synchronized (data) {
            return data.environments.get(toolName);
        }
    }

    /**
     * Updates one state value inside a tool environment.
     */
    public void updateToolState(String sessionId, String toolName, String variable, String value) {
        ToolEnvironment environment = getOrCreateEnv(sessionId, toolName);
        environment.set(variable, value);
    }

    /**
     * Reads one state value from a tool environment.
     */
    public String getToolState(String sessionId, String toolName, String variable) {
        ToolEnvironment environment = getEnvironment(sessionId, toolName);
        return environment == null ? null : environment.get(variable);
    }

    /**
     * Returns a snapshot of one tool's current state.
     */
    public Map<String, String> getToolStateSnapshot(String sessionId, String toolName) {
        ToolEnvironment environment = getEnvironment(sessionId, toolName);
        return environment == null ? Map.of() : environment.snapshot();
    }

    /**
     * Returns a stable snapshot of all tool states in a session.
     */
    public Map<String, Map<String, String>> getAllToolStatesSnapshot(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return Map.of();
        }

        Map<String, Map<String, String>> snapshots = new LinkedHashMap<>();
        synchronized (data) {
            data.environments.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> snapshots.put(entry.getKey(), entry.getValue().snapshot()));
        }
        return snapshots;
    }

    /**
     * Returns a session-level state payload used in prompts and logs.
     */
    public Map<String, Object> getSessionStateSnapshot(String sessionId) {
        return Map.of("toolStates", getAllToolStatesSnapshot(sessionId));
    }

    /**
     * Removes all state for a completed session.
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    private ToolEnvironment getOrCreateEnv(String sessionId, String toolName) {
        SessionData data = getOrCreateSession(sessionId);
        synchronized (data) {
            return data.environments.computeIfAbsent(toolName, ignored -> new ToolEnvironment(null));
        }
    }

    private SessionData getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, ignored -> new SessionData());
    }

    /**
     * Container for all per-session mutable state.
     */
    private static final class SessionData {
        private BenchmarkCaseGenerator.BenchmarkCase benchmarkCase;
        private final Map<String, ToolEnvironment> environments = new LinkedHashMap<>();
        private final List<ExecutionRecord> executionLog = new ArrayList<>();
        private final Set<String> documentedTools = new HashSet<>();
        private final AtomicInteger discoveryCalls = new AtomicInteger();
        private final AtomicInteger attemptStartIndex = new AtomicInteger();
    }
}
