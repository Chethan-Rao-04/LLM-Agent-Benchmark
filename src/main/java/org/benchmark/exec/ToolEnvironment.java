package org.benchmark.exec;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable runtime environment for one generated tool within one benchmark session.
 *
 * <p>The underlying tool specification remains immutable. This class holds only
 * the session-scoped live values for that tool's declared local state variables.</p>
 */
public class ToolEnvironment {
    /** Shared local-state key used by every tool for readiness checks. */
    public static final String SYSTEM_STATUS_KEY = "system_status";
    /** Ready state for a tool that can execute normal commands. */
    public static final String SYSTEM_STATUS_RUNNING = "RUNNING";
    /** Initial/offline state for a tool that still needs initialization. */
    public static final String SYSTEM_STATUS_SHUTDOWN = "SHUTDOWN";

    private final Set<String> declaredVariables = ConcurrentHashMap.newKeySet();
    private final Map<String, String> currentState = new ConcurrentHashMap<>();

    /**
     * Declares the schema variables that may appear in this tool's runtime state.
     */
    public void declareVariables(Collection<String> variableNames) {
        if (variableNames == null || variableNames.isEmpty()) {
            return;
        }
        declaredVariables.addAll(variableNames);
    }

    /**
     * Creates, updates, or removes one runtime value in this tool environment.
     */
    public void updateState(String variable, String value) {
        declaredVariables.add(variable);
        if (value == null) {
            currentState.remove(variable);
            return;
        }
        currentState.put(variable, value);
    }

    /**
     * Returns the current runtime value for one declared tool variable.
     */
    public String getState(String variable) {
        return currentState.get(variable);
    }

    /**
     * Returns a snapshot that includes all declared variables, even when their
     * current values are still uninitialized.
     */
    public Map<String, String> snapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String variable : declaredVariables) {
            snapshot.put(variable, currentState.get(variable));
        }
        for (Map.Entry<String, String> entry : currentState.entrySet()) {
            snapshot.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return snapshot;
    }
}
