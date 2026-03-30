package org.benchmark.exec;

import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.model.objects.PreconditionObject;
import org.benchmark.model.objects.ToolObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Unified session manager: tool environments, case metadata, and execution logs.
 *
 * <p>Every benchmark session is represented by one entry here, containing:
 * <ul>
 *   <li>A {@link ToolEnvironment} per tool (mutable machine state)</li>
 *   <li>The immutable {@link BenchmarkCaseGenerator.BenchmarkCase}</li>
 *   <li>An append-only execution log of {@link ExecutionRecord}</li>
 * </ul></p>
 */
@Component
public class SessionStateManager {

    /**
     * Captures one benchmark-tool execution attempt for later scoring.
     */
    public record ExecutionRecord(String toolName, String commandName, String option, boolean success, String message) {}

    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    /**
     * Initializes a session: registers the case, creates tool environments,
     * and sets random initial system_status using the provided seeded Random.
     *
     * @param sessionId unique session identifier
     * @param benchmarkCase the benchmark case to bind
     * @param tools all tools visible in the case
     * @param random seeded random source for deterministic initialization
     */
    public void initializeSession(String sessionId,
                                   BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                   List<ToolObject> tools,
                                   Random random) {
        SessionData data = sessions.computeIfAbsent(sessionId, k -> new SessionData());
        data.benchmarkCase = benchmarkCase;

        if (tools == null || tools.isEmpty()) {
            return;
        }
        boolean isMultiStep = benchmarkCase.workflowSteps() != null
                && benchmarkCase.workflowSteps().size() > 1;

        for (ToolObject tool : tools) {
            ToolEnvironment env = new ToolEnvironment(tool.stateVariables().keySet());

            // Multi-step: target tool always starts SHUTDOWN so init step is required
            String initialStatus;
            if (isMultiStep && tool.name().equals(benchmarkCase.targetToolObject().name())) {
                initialStatus = ToolEnvironment.SYSTEM_STATUS_SHUTDOWN;
            } else {
                initialStatus = random.nextBoolean()
                        ? ToolEnvironment.SYSTEM_STATUS_RUNNING
                        : ToolEnvironment.SYSTEM_STATUS_SHUTDOWN;
            }
            env.set(ToolEnvironment.SYSTEM_STATUS_KEY, initialStatus);

            // Multi-step: domain precondition vars on target tool start "disabled"
            if (isMultiStep && tool.name().equals(benchmarkCase.targetToolObject().name())
                    && benchmarkCase.targetCommand().commandPreConditions() != null) {
                for (PreconditionObject precond : benchmarkCase.targetCommand().commandPreConditions()) {
                    if (!precond.variable().equals(ToolEnvironment.SYSTEM_STATUS_KEY)) {
                        env.set(precond.variable(), "disabled");
                    }
                }
            }

            data.initialSystemStatus.put(tool.name(), initialStatus);
            data.environments.put(tool.name(), env);
        }
    }

    // ---- Case metadata (formerly BenchmarkSessionRegistry) ----

    /** Returns the benchmark case bound to a session. */
    public BenchmarkCaseGenerator.BenchmarkCase getBenchmarkCase(String sessionId) {
        SessionData data = sessions.get(sessionId);
        return data == null ? null : data.benchmarkCase;
    }

    /** Appends one execution record to the session log. */
    public void recordExecution(String sessionId, ExecutionRecord record) {
        sessions.computeIfAbsent(sessionId, k -> new SessionData())
                .executionLog.add(record);
    }

    /** Returns the execution log for a session. */
    public List<ExecutionRecord> executionLog(String sessionId) {
        SessionData data = sessions.get(sessionId);
        return data == null ? List.of() : data.executionLog;
    }

    /** Marks that the LLM used MCP documentation/discovery tools in this session. */
    public void recordDiscovery(String sessionId) {
        sessions.computeIfAbsent(sessionId, k -> new SessionData())
                .discoveryUsed = true;
    }

    /** Returns true if the LLM used MCP discovery tools. */
    public boolean discoveryUsed(String sessionId) {
        SessionData data = sessions.get(sessionId);
        return data != null && data.discoveryUsed;
    }

    // ---- Tool state (ToolEnvironment access) ----

    /** Returns the environment for a specific tool, or {@code null}. */
    public ToolEnvironment getEnvironment(String sessionId, String toolName) {
        SessionData data = sessions.get(sessionId);
        if (data == null) return null;
        return data.environments.get(toolName);
    }

    public void updateToolState(String sessionId, String toolName, String variable, String value) {
        ToolEnvironment env = getOrCreateEnv(sessionId, toolName);
        env.set(variable, value);
    }

    public String getToolState(String sessionId, String toolName, String variable) {
        ToolEnvironment env = getEnvironment(sessionId, toolName);
        return env == null ? null : env.get(variable);
    }

    public Map<String, String> getToolStateSnapshot(String sessionId, String toolName) {
        ToolEnvironment env = getEnvironment(sessionId, toolName);
        return env == null ? Map.of() : env.snapshot();
    }

    public Map<String, Map<String, String>> getAllToolStatesSnapshot(String sessionId) {
        SessionData data = sessions.get(sessionId);
        if (data == null) return Map.of();

        Map<String, Map<String, String>> snapshots = new LinkedHashMap<>();
        data.environments.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> snapshots.put(e.getKey(), e.getValue().snapshot()));
        return snapshots;
    }

    public Map<String, Object> getSessionStateSnapshot(String sessionId) {
        return Map.of("toolStates", getAllToolStatesSnapshot(sessionId));
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public String getInitialSystemStatus(String sessionId, String toolName) {
        SessionData data = sessions.get(sessionId);
        if (data == null) return null;
        return data.initialSystemStatus.get(toolName);
    }

    private ToolEnvironment getOrCreateEnv(String sessionId, String toolName) {
        return sessions.computeIfAbsent(sessionId, k -> new SessionData())
                .environments.computeIfAbsent(toolName, k -> new ToolEnvironment(null));
    }

    /**
     * All per-session data in one object: case, environments, execution log.
     */
    private static final class SessionData {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase;
        final Map<String, ToolEnvironment> environments = new ConcurrentHashMap<>();
        final Map<String, String> initialSystemStatus = new ConcurrentHashMap<>();
        final List<ExecutionRecord> executionLog = new CopyOnWriteArrayList<>();
        boolean discoveryUsed = false;
    }
}
