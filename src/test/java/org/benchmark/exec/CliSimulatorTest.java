package org.benchmark.exec;

import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliSimulatorTest {

    private static final String SESSION_ID = "test-session";
    private static final String TOOL_NAME = "TEST-TOOL-001";

    private CliSimulator simulator;
    private SessionStateManager stateManager;

    @BeforeEach
    void setUp() {
        simulator = new CliSimulator();
        stateManager = new SessionStateManager();

        ToolObject tool = new ToolObject(
                TOOL_NAME,
                "Test tool",
                Domain.MANUFACTURING,
                List.of(),
                Map.of("counter", "int", "label", "string")
        );
        stateManager.initializeSession(SESSION_ID, null, List.of(tool));
        stateManager.updateToolState(SESSION_ID, TOOL_NAME, "counter", "0");
        stateManager.updateToolState(SESSION_ID, TOOL_NAME, "label", "old-value");
    }

    @Test
    void executeAppliesEffectsWhenOptionIsValid() {
        ToolObject tool = new ToolObject(
                TOOL_NAME,
                "Test tool",
                Domain.MANUFACTURING,
                List.of(new CommandObject(
                        "start_process",
                        List.of(new OptionEntity("--verbose", "Enable verbose")),
                        "Start the process",
                        List.of(new EffectObject("counter", EffectOp.INCREMENT, null)),
                        Map.of()
                )),
                Map.of("counter", "int", "label", "string")
        );

        CliSimulator.ExecutionResult result = simulator.execute(tool, "start_process", "--verbose", stateManager, SESSION_ID);

        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertEquals("1", stateManager.getToolState(SESSION_ID, TOOL_NAME, "counter"));
    }

    @Test
    void executeFailsWithInvalidOption() {
        ToolObject tool = new ToolObject(
                TOOL_NAME,
                "Test tool",
                Domain.MANUFACTURING,
                List.of(new CommandObject(
                        "start_process",
                        List.of(new OptionEntity("--verbose", "Enable verbose")),
                        "Start the process",
                        List.of(),
                        Map.of()
                )),
                Map.of("counter", "int", "label", "string")
        );

        CliSimulator.ExecutionResult result = simulator.execute(tool, "start_process", "--invalid", stateManager, SESSION_ID);

        assertFalse(result.success());
        assertTrue(result.stderr().contains("Unknown option"));
    }

    @Test
    void executeSucceedsWithNullOption() {
        ToolObject tool = new ToolObject(
                TOOL_NAME,
                "Test tool",
                Domain.MANUFACTURING,
                List.of(new CommandObject(
                        "start_process",
                        List.of(new OptionEntity("--verbose", "Enable verbose")),
                        "Start the process",
                        List.of(new EffectObject("label", EffectOp.ASSIGN, "done")),
                        Map.of()
                )),
                Map.of("counter", "int", "label", "string")
        );

        CliSimulator.ExecutionResult result = simulator.execute(tool, "start_process", null, stateManager, SESSION_ID);

        assertTrue(result.success());
        assertEquals("done", stateManager.getToolState(SESSION_ID, TOOL_NAME, "label"));
    }

    @Test
    void executeSucceedsWithEmptyOption() {
        ToolObject tool = new ToolObject(
                TOOL_NAME,
                "Test tool",
                Domain.MANUFACTURING,
                List.of(new CommandObject(
                        "start_process",
                        List.of(new OptionEntity("--verbose", "Enable verbose")),
                        "Start the process",
                        List.of(),
                        Map.of()
                )),
                Map.of("counter", "int", "label", "string")
        );

        CliSimulator.ExecutionResult result = simulator.execute(tool, "start_process", "", stateManager, SESSION_ID);

        assertTrue(result.success());
    }

    @Test
    void executeFailsWhenCommandIsUnknown() {
        ToolObject tool = new ToolObject(
                TOOL_NAME,
                "Test tool",
                Domain.MANUFACTURING,
                List.of(),
                Map.of("counter", "int", "label", "string")
        );

        CliSimulator.ExecutionResult result = simulator.execute(tool, "missing_command", "", stateManager, SESSION_ID);

        assertFalse(result.success());
        assertTrue(result.stderr().contains("Unknown command"));
    }
}
