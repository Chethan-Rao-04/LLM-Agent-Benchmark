package org.benchmark.tools.server;

import lombok.RequiredArgsConstructor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.CommandRejectionRecord;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.logging.BenchmarkEventLogger;
import org.springframework.stereotype.Component;

/**
 * Publishes tool-facing state and execution events into the benchmark event stream.
 */
@Component
@RequiredArgsConstructor
class BenchmarkToolEventPublisher {

    private final SessionStateManager stateManager;
    private final BenchmarkEventLogger eventLogger;

    void publishExecution(String sessionId, ExecutionRecord record, String eventType) {
        java.util.Map<String, Object> payload = eventLogger.newEventPayload(eventType);
        payload.put("sessionId", sessionId);
        payload.put("toolName", record.toolName());
        payload.put("commandName", record.commandName());
        payload.put("option", record.option());
        payload.put("success", record.success());
        payload.put("message", record.message());
        payload.put("state", stateManager.getSessionStateSnapshot(sessionId));
        eventLogger.logPayload(payload);
    }

    void publishCommandRejection(String sessionId, CommandRejectionRecord record, String eventType) {
        java.util.Map<String, Object> payload = eventLogger.newEventPayload(eventType);
        payload.put("sessionId", sessionId);
        payload.put("toolName", record.toolName());
        payload.put("commandName", record.commandName());
        payload.put("option", record.option());
        payload.put("success", false);
        payload.put("message", record.message());
        payload.put("state", stateManager.getSessionStateSnapshot(sessionId));
        eventLogger.logPayload(payload);
    }
}
