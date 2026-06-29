package org.benchmark.prompt;

import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.objects.ToolObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkAttemptFeedbackBuilderTest {

    private static final String SESSION_ID = "feedback-session";

    private SessionStateManager stateManager;
    private BenchmarkAttemptFeedbackBuilder feedbackBuilder;

    @BeforeEach
    void setUp() {
        stateManager = new SessionStateManager();
        feedbackBuilder = new BenchmarkAttemptFeedbackBuilder(stateManager);

        ToolObject targetTool = new ToolObject(
                "TARGET-TOOL",
                "Target tool",
                Domain.MANUFACTURING,
                List.of(),
                Map.of("status", "string")
        );
        ToolObject distractorTool = new ToolObject(
                "DECOY-TOOL",
                "Decoy tool",
                Domain.MANUFACTURING,
                List.of(),
                Map.of("status", "string")
        );

        stateManager.initializeSession(SESSION_ID, null, List.of(targetTool, distractorTool));
        stateManager.updateToolState(SESSION_ID, "TARGET-TOOL", "status", "waiting");
        stateManager.updateToolState(SESSION_ID, "DECOY-TOOL", "status", "idle");
    }

    @Test
    void feedbackForIncompleteAttemptContainsOnlyObservableRuntimeEvidence() {
        List<ExecutionRecord> executions = List.of(
                new ExecutionRecord("DECOY-TOOL", "probe_valve", "", false, "Precondition not met")
        );

        String feedback = feedbackBuilder.build(executions, SESSION_ID, false);

        assertTrue(feedback.contains("DECOY-TOOL"));
        assertTrue(feedback.contains("probe_valve"));
        assertTrue(feedback.contains("Current session state"));
        assertTrue(feedback.contains("call executeCommand exactly once"));
        assertFalse(feedback.contains("state accuracy"));
        assertFalse(feedback.contains("Next required:"));
        assertFalse(feedback.contains("Precondition:"));
        assertFalse(feedback.contains("correct target tool"));
        assertFalse(feedback.contains("not part of the required scenario"));
    }

    @Test
    void feedbackForNoExecutionDoesNotInventHiddenBenchmarkHints() {
        String feedback = feedbackBuilder.build(List.of(), SESSION_ID, false);

        assertTrue(feedback.contains("No tool command executed"));
        assertTrue(feedback.contains("Current session state"));
        assertTrue(feedback.contains("call executeCommand exactly once"));
        assertFalse(feedback.contains("getToolDocumentation for the tools you haven't read yet"));
        assertFalse(feedback.contains("steps done"));
    }

    @Test
    void feedbackForSuccessfulAttemptKeepsObservableEvidenceWithoutBenchmarkHints() {
        stateManager.updateToolState(SESSION_ID, "TARGET-TOOL", "status", "ready");
        List<ExecutionRecord> executions = List.of(
                new ExecutionRecord("TARGET-TOOL", "activate_rtr", "--dry-run", true, "OK: activate_rtr")
        );

        String feedback = feedbackBuilder.build(executions, SESSION_ID, true);

        assertTrue(feedback.contains("SUCCESS"));
        assertTrue(feedback.contains("TARGET-TOOL"));
        assertTrue(feedback.contains("activate_rtr"));
        assertTrue(feedback.contains("Current session state"));
        assertFalse(feedback.contains("state accuracy"));
        assertFalse(feedback.contains("correct target tool"));
        assertFalse(feedback.contains("Next required:"));
        assertFalse(feedback.contains("recovery command"));
    }
}
