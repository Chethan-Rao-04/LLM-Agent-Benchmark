package org.benchmark.exec;

import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.model.enums.StateScope;
import org.benchmark.model.objects.ToolObject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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

    /**
     * Captures one command request rejected before simulator execution.
     *
     * @param toolName tool requested by the agent
     * @param commandName command requested by the agent
     * @param option selected option, or empty when none was used
     * @param message rejection reason returned to the agent
     */
    public record CommandRejectionRecord(String toolName, String commandName, String option, String message) {
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
        SessionData data = sessions.computeIfAbsent(sessionId, ignored -> new SessionData());
        synchronized (data) {
            data.benchmarkCase = benchmarkCase;
            data.environments.clear();
            data.sharedState.clear();
            data.executionLog.clear();
            data.commandRejectionLog.clear();
            data.attemptStartIndex = 0;
            data.attemptExecutionConsumed = false;

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
     * Returns the benchmark case for a session and fails fast when the session is missing or incomplete.
     *
     * @param sessionId benchmark session identifier
     * @return benchmark case bound to the session
     * @throws IllegalArgumentException when the session has not been initialized with a benchmark case
     */
    public BenchmarkCaseGenerator.BenchmarkCase requireBenchmarkCase(String sessionId) {
        SessionData data = requireSessionData(sessionId);
        synchronized (data) {
            if (data.benchmarkCase == null) {
                throw new IllegalArgumentException("Unknown benchmark session: " + sessionId);
            }
            return data.benchmarkCase;
        }
    }

    /**
     * Appends one execution record to the session log.
     */
    public void recordExecution(String sessionId, ExecutionRecord record) {
        SessionData data = requireSessionData(sessionId);
        synchronized (data) {
            data.executionLog.add(record);
        }
    }

    /**
     * Appends one rejected command request to the session log.
     */
    public void recordCommandRejection(String sessionId, CommandRejectionRecord record) {
        SessionData data = requireSessionData(sessionId);
        synchronized (data) {
            data.commandRejectionLog.add(record);
        }
    }

    /**
     * Marks the beginning of one LLM attempt so per-attempt safeguards can inspect only
     * the executions generated within the current turn.
     */
    public void startAttempt(String sessionId) {
        SessionData data = requireSessionData(sessionId);
        synchronized (data) {
            data.attemptStartIndex = data.executionLog.size();
            data.attemptExecutionConsumed = false;
        }
    }

    /**
     * Returns whether the current attempt has already consumed its one real execution.
     */
    public boolean attemptExecutionConsumed(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return false;
        }
        synchronized (data) {
            return data.attemptExecutionConsumed;
        }
    }

    /**
     * Marks that the current attempt already reached the simulator once.
     */
    public void markAttemptExecutionConsumed(String sessionId) {
        SessionData data = requireSessionData(sessionId);
        synchronized (data) {
            data.attemptExecutionConsumed = true;
        }
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
            return Math.max(0, data.executionLog.size() - data.attemptStartIndex);
        }
    }

    /**
     * Counts how many times the same tool/command/option already failed in the current attempt.
     */
    public long repeatedFailuresInCurrentAttempt(String sessionId, String toolName, String commandName, String option) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return 0;
        }
        String normalizedOption = CommandOptionNormalizer.normalize(option);
        synchronized (data) {
            return data.executionLog.subList(data.attemptStartIndex, data.executionLog.size()).stream()
                    .filter(record -> !record.success())
                    .filter(record -> record.toolName().equalsIgnoreCase(toolName))
                    .filter(record -> record.commandName().equalsIgnoreCase(commandName))
                    .filter(record -> record.option().equalsIgnoreCase(normalizedOption))
                    .count();
        }
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
     * Returns the command rejection log for a session.
     */
    public List<CommandRejectionRecord> commandRejectionLog(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return List.of();
        }
        synchronized (data) {
            return List.copyOf(data.commandRejectionLog);
        }
    }

    /**
     * Updates one state value inside a tool environment.
     */
    public void updateToolState(String sessionId, String toolName, String variable, String value) {
        SessionData data = requireSessionData(sessionId);
        synchronized (data) {
            data.environments
                    .computeIfAbsent(toolName, ignored -> new ToolEnvironment(null))
                    .set(variable, value);
        }
    }

    /**
     * Reads one state value from a tool environment.
     */
    public String getToolState(String sessionId, String toolName, String variable) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return null;
        }
        synchronized (data) {
            ToolEnvironment environment = data.environments.get(toolName);
            return environment == null ? null : environment.get(variable);
        }
    }

    /**
     * Updates one value inside the session shared state.
     */
    public void updateSharedState(String sessionId, String variable, String value) {
        SessionData data = requireSessionData(sessionId);
        synchronized (data) {
            if (value == null) {
                data.sharedState.remove(variable);
            } else {
                data.sharedState.put(variable, value);
            }
        }
    }

    /**
     * Reads one value from the session shared state.
     */
    public String getSharedState(String sessionId, String variable) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return null;
        }
        synchronized (data) {
            return data.sharedState.get(variable);
        }
    }

    /**
     * Updates state in the map selected by {@code scope}.
     */
    public void updateState(String sessionId, String toolName, StateScope scope, String variable, String value) {
        if (scope == StateScope.SHARED) {
            updateSharedState(sessionId, variable, value);
            return;
        }
        updateToolState(sessionId, toolName, variable, value);
    }

    /**
     * Reads state from the map selected by {@code scope}.
     */
    public String getState(String sessionId, String toolName, StateScope scope, String variable) {
        if (scope == StateScope.SHARED) {
            return getSharedState(sessionId, variable);
        }
        return getToolState(sessionId, toolName, variable);
    }

    /**
     * Returns a snapshot of one tool's current state.
     */
    public Map<String, String> getToolStateSnapshot(String sessionId, String toolName) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return Map.of();
        }
        synchronized (data) {
            ToolEnvironment environment = data.environments.get(toolName);
            return environment == null ? Map.of() : environment.snapshot();
        }
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
     * Returns a stable snapshot of session shared state.
     */
    public Map<String, String> getSharedStateSnapshot(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            return Map.of();
        }
        synchronized (data) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(data.sharedState));
        }
    }

    /**
     * Returns an aggregate read-only view across all target-tool states.
     *
     * <p>Each underlying tool still owns its own mutable environment. This helper only flattens
     * target snapshots for migration paths that still consume a single state map.</p>
     */
    public Map<String, String> getTargetFamilyStateSnapshot(String sessionId,
                                                            BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        if (benchmarkCase == null) {
            return Map.of();
        }

        Map<String, String> snapshot = new LinkedHashMap<>();
        for (ToolObject targetTool : benchmarkCase.targetTools()) {
            snapshot.putAll(getToolStateSnapshot(sessionId, targetTool.name()));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Returns a session-level state payload used in prompts and logs.
     */
    public Map<String, Object> getSessionStateSnapshot(String sessionId) {
        return Map.of(
                "toolStates", getAllToolStatesSnapshot(sessionId),
                "sharedState", getSharedStateSnapshot(sessionId)
        );
    }

    /**
     * Removes all state for a completed session.
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * Executes an action while holding the per-session monitor so multiple reads and writes see consistent state.
     *
     * @param sessionId benchmark session identifier
     * @param action callback to run while the session is locked
     * @param <T> result type returned by the callback
     * @return callback result
     */
    public <T> T withSessionLock(String sessionId, Supplier<T> action) {
        SessionData data = requireSessionData(sessionId);
        synchronized (data) {
            return action.get();
        }
    }

    private SessionData requireSessionData(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) {
            throw new IllegalArgumentException("Unknown benchmark session: " + sessionId);
        }
        return data;
    }

    /**
     * Container for all per-session mutable state.
     */
    private static final class SessionData {
        private BenchmarkCaseGenerator.BenchmarkCase benchmarkCase;
        private final Map<String, ToolEnvironment> environments = new LinkedHashMap<>();
        private final Map<String, String> sharedState = new LinkedHashMap<>();
        private final List<ExecutionRecord> executionLog = new ArrayList<>();
        private final List<CommandRejectionRecord> commandRejectionLog = new ArrayList<>();
        private int attemptStartIndex;
        private boolean attemptExecutionConsumed;
    }
}
