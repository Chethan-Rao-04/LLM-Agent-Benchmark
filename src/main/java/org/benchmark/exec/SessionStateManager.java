package org.benchmark.exec;

import org.benchmark.model.objects.ToolObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thread-safe session state manager that stores one mutable tool environment
 * per generated tool in a benchmark case.
 */
public class SessionStateManager {

    private final Map<String, Map<String, ToolEnvironment>> toolEnvironmentsBySession = new ConcurrentHashMap<>();

    /**
     * Initializes tool-scoped runtime state for every tool visible in a case.
     *
     * <p>Each tool receives all declared schema variables up front and a random
     * initial {@code system_status} of either {@code SHUTDOWN} or {@code RUNNING}.</p>
     */
    public void initializeSession(String sessionId, List<ToolObject> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }

        for (ToolObject tool : tools) {
            ToolEnvironment toolEnvironment = getOrCreateToolEnvironment(sessionId, tool.name());
            toolEnvironment.declareVariables(tool.toolStateObject().variables().keySet());
            // Each tool begins independently in either RUNNING or SHUTDOWN.
            toolEnvironment.updateState(
                    ToolEnvironment.SYSTEM_STATUS_KEY,
                    ThreadLocalRandom.current().nextBoolean()
                            ? ToolEnvironment.SYSTEM_STATUS_RUNNING
                            : ToolEnvironment.SYSTEM_STATUS_SHUTDOWN
            );
        }
    }

    /**
     * Writes one tool-local runtime value for the given session and tool.
     */
    public void updateToolState(String sessionId, String toolName, String variable, String value) {
        getOrCreateToolEnvironment(sessionId, toolName).updateState(variable, value);
    }

    /**
     * Reads one tool-local runtime value for the given session and tool.
     */
    public String getToolState(String sessionId, String toolName, String variable) {
        ToolEnvironment toolEnvironment = getToolEnvironment(sessionId, toolName);
        if (toolEnvironment == null) {
            return null;
        }
        return toolEnvironment.getState(variable);
    }

    /**
     * Returns the live state snapshot for one tool in one session.
     *
     * <p>The snapshot includes all declared variables, even if some of them are
     * still uninitialized.</p>
     */
    public Map<String, String> getToolStateSnapshot(String sessionId, String toolName) {
        ToolEnvironment toolEnvironment = getToolEnvironment(sessionId, toolName);
        if (toolEnvironment == null) {
            return Map.of();
        }
        return toolEnvironment.snapshot();
    }

    /**
     * Returns tool-state snapshots for every tool currently registered in a session.
     */
    public Map<String, Map<String, String>> getAllToolStatesSnapshot(String sessionId) {
        Map<String, ToolEnvironment> toolEnvironments = toolEnvironmentsBySession.get(sessionId);
        if (toolEnvironments == null) {
            return Map.of();
        }

        Map<String, Map<String, String>> snapshots = new LinkedHashMap<>();
        toolEnvironments.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> snapshots.put(entry.getKey(), entry.getValue().snapshot()));
        return snapshots;
    }

    /**
     * Returns a structured view of the full session state for prompts and logs.
     */
    public Map<String, Object> getSessionStateSnapshot(String sessionId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("toolStates", getAllToolStatesSnapshot(sessionId));
        return snapshot;
    }

    /**
     * Removes all runtime tool environments associated with a completed session.
     */
    public void clearSession(String sessionId) {
        toolEnvironmentsBySession.remove(sessionId);
    }

    private ToolEnvironment getOrCreateToolEnvironment(String sessionId, String toolName) {
        return toolEnvironmentsBySession
                .computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(toolName, unused -> new ToolEnvironment());
    }

    private ToolEnvironment getToolEnvironment(String sessionId, String toolName) {
        Map<String, ToolEnvironment> toolEnvironments = toolEnvironmentsBySession.get(sessionId);
        if (toolEnvironments == null) {
            return null;
        }
        return toolEnvironments.get(toolName);
    }
}
