package org.benchmark.exec;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionStateManager {
    private final Map<String, Map<String, String>> sessionStates = new ConcurrentHashMap<>();

    public void updateState(String sessionId, String variable, String value) {
        Map<String, String> state = sessionStates.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        if (value == null) {
            state.remove(variable);
            return;
        }
        state.put(variable, value);
    }

    public String getState(String sessionId, String variable) {
        return sessionStates.getOrDefault(sessionId, new HashMap<>()).get(variable);
    }

    public Map<String, String> getAllStates(String sessionId) {
        return sessionStates.getOrDefault(sessionId, new HashMap<>());
    }

    public void clearSession(String sessionId) {
        sessionStates.remove(sessionId);
    }

    // Error recovery state tracking
    public void incrementRetryCount(String sessionId, String commandName) {
        String key = commandName + "_retry_count";
        String current = getState(sessionId, key);
        int count = current != null ? Integer.parseInt(current) : 0;
        updateState(sessionId, key, String.valueOf(count + 1));
    }

    public int getRetryCount(String sessionId, String commandName) {
        String current = getState(sessionId, commandName + "_retry_count");
        return current != null ? Integer.parseInt(current) : 0;
    }
}
