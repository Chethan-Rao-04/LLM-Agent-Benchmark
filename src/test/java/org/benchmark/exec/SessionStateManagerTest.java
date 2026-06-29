package org.benchmark.exec;

import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.objects.ToolObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStateManagerTest {

    @Test
    void recordsExecutionsSafelyAcrossConcurrentCallbacks() throws Exception {
        SessionStateManager stateManager = new SessionStateManager();
        String sessionId = "concurrent-session";
        String toolName = "TEST-TOOL-001";
        ToolObject tool = new ToolObject(
                toolName,
                "Test tool",
                Domain.MANUFACTURING,
                List.of(),
                Map.of("status", "string")
        );
        stateManager.initializeSession(sessionId, null, List.of(tool));

        int taskCount = 500;
        var tasks = IntStream.range(0, taskCount)
                .mapToObj(index -> (Callable<Void>) () -> {
                    stateManager.recordExecution(sessionId,
                            new ExecutionRecord(toolName, "cmd_" + index, "", true, "OK"));
                    stateManager.updateToolState(sessionId, toolName, "status", "value_" + index);
                    stateManager.getAllToolStatesSnapshot(sessionId);
                    return null;
                })
                .toList();

        var executor = Executors.newFixedThreadPool(8);
        try {
            executor.invokeAll(tasks);
        } finally {
            executor.shutdown();
        }

        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(taskCount, stateManager.executionLog(sessionId).size());
        assertTrue(stateManager.getToolState(sessionId, toolName, "status").startsWith("value_"));
    }
}
