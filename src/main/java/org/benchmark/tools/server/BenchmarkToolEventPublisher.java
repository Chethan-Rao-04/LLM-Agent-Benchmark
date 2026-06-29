package org.benchmark.tools.server;

import lombok.RequiredArgsConstructor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.CommandRejectionRecord;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.logging.BenchmarkEventLogger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Publishes tool-facing state and execution events into the benchmark event stream.
 */
@Component
@RequiredArgsConstructor
class BenchmarkToolEventPublisher {

    private final SessionStateManager stateManager;
    private final BenchmarkEventLogger eventLogger;

    void publishToolCatalogListed(String sessionId, List<String> tools) {
        eventLogger.logEvent("list_available_tools", Map.of(
                "sessionId", sessionId,
                "toolCount", tools.size(),
                "toolNames", tools));
    }

    void publishToolDocumentationRead(String sessionId, String toolName, String documentation) {
        eventLogger.logEvent("get_tool_documentation", Map.of(
                "sessionId", sessionId,
                "toolName", toolName,
                "documentationLength", documentation.length(),
                "documentation", documentation));
    }

    void publishStateRead(String sessionId) {
        eventLogger.logEvent("get_current_state", Map.of(
                "sessionId", sessionId,
                "state", stateManager.getSessionStateSnapshot(sessionId)));
    }

    void publishExecution(String sessionId, ExecutionRecord record, String eventType) {
        Map<String, Map<String, String>> toolStates = stateManager.getAllToolStatesSnapshot(sessionId);
        Map<String, Object> payload = eventLogger.newEventPayload(eventType);
        payload.put("sessionId", sessionId);
        payload.put("toolName", record.toolName());
        payload.put("commandName", record.commandName());
        payload.put("option", record.option());
        payload.put("success", record.success());
        payload.put("message", record.message());
        payload.put("state", Map.of("toolStates", toolStates));
        eventLogger.logPayload(payload);
    }

    void publishCommandRejection(String sessionId, CommandRejectionRecord record, String eventType) {
        Map<String, Map<String, String>> toolStates = stateManager.getAllToolStatesSnapshot(sessionId);
        Map<String, Object> payload = eventLogger.newEventPayload(eventType);
        payload.put("sessionId", sessionId);
        payload.put("toolName", record.toolName());
        payload.put("commandName", record.commandName());
        payload.put("option", record.option());
        payload.put("success", false);
        payload.put("message", record.message());
        payload.put("state", Map.of("toolStates", toolStates));
        eventLogger.logPayload(payload);
    }
}
