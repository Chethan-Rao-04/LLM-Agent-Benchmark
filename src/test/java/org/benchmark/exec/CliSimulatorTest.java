package org.benchmark.exec;

import org.benchmark.model.enums.ConditionOp;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.PreconditionObject;
import org.benchmark.model.objects.ToolObject;

import org.benchmark.model.enums.Domain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CliSimulatorTest {

    private CliSimulator simulator;
    private SessionStateManager stateManager;
    private static final String SESSION_ID = "test-session";
    private static final String TOOL_NAME = "TEST-TOOL-001";

    @BeforeEach
    void setUp() {
        simulator = new CliSimulator();
        stateManager = new SessionStateManager();

        // Initialize a tool with system_status = RUNNING
        ToolObject tool = new ToolObject(
                TOOL_NAME, "Test tool", Domain.MANUFACTURING,
                List.of(),
                Map.of("system_status", "string", "counter", "int")
        );
        stateManager.initializeSession(SESSION_ID, null, List.of(tool), new Random(42));
        stateManager.updateToolState(SESSION_ID, TOOL_NAME, "system_status", "RUNNING");
        stateManager.updateToolState(SESSION_ID, TOOL_NAME, "counter", "0");
    }

    @Test
    void executeSucceedsWhenPreconditionsMet() {
        CommandObject cmd = new CommandObject(
                "start_process",
                List.of(new OptionEntity("--verbose", "Enable verbose")),
                "Start the process",
                List.of(new PreconditionObject("system_status", ConditionOp.EQ, "RUNNING")),
                List.of(new EffectObject("counter", EffectOp.INCREMENT, null))
        );

        CliSimulator.ExecutionResult result = simulator.execute(cmd, "--verbose", stateManager, SESSION_ID, TOOL_NAME);

        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertEquals("1", stateManager.getToolState(SESSION_ID, TOOL_NAME, "counter"));
    }

    @Test
    void executeFailsWhenPreconditionsNotMet() {
        stateManager.updateToolState(SESSION_ID, TOOL_NAME, "system_status", "SHUTDOWN");

        CommandObject cmd = new CommandObject(
                "start_process",
                List.of(),
                "Start the process",
                List.of(new PreconditionObject("system_status", ConditionOp.EQ, "RUNNING")),
                List.of()
        );

        CliSimulator.ExecutionResult result = simulator.execute(cmd, "", stateManager, SESSION_ID, TOOL_NAME);

        assertFalse(result.success());
        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Preconditions not met"));
    }

    @Test
    void executeFailsWithInvalidOption() {
        CommandObject cmd = new CommandObject(
                "start_process",
                List.of(new OptionEntity("--verbose", "Enable verbose")),
                "Start the process",
                List.of(),
                List.of()
        );

        CliSimulator.ExecutionResult result = simulator.execute(cmd, "--invalid", stateManager, SESSION_ID, TOOL_NAME);

        assertFalse(result.success());
        assertTrue(result.stderr().contains("Unknown option"));
    }

    @Test
    void executeSucceedsWithNullOption() {
        CommandObject cmd = new CommandObject(
                "start_process",
                List.of(new OptionEntity("--verbose", "Enable verbose")),
                "Start the process",
                List.of(),
                List.of()
        );

        CliSimulator.ExecutionResult result = simulator.execute(cmd, null, stateManager, SESSION_ID, TOOL_NAME);

        assertTrue(result.success());
    }

    @Test
    void executeSucceedsWithEmptyOption() {
        CommandObject cmd = new CommandObject(
                "start_process",
                List.of(new OptionEntity("--verbose", "Enable verbose")),
                "Start the process",
                List.of(),
                List.of()
        );

        CliSimulator.ExecutionResult result = simulator.execute(cmd, "", stateManager, SESSION_ID, TOOL_NAME);

        assertTrue(result.success());
    }

    @Test
    void preconditionNEOperatorWorks() {
        CommandObject cmd = new CommandObject(
                "check_process",
                List.of(),
                "Check process",
                List.of(new PreconditionObject("system_status", ConditionOp.NE, "SHUTDOWN")),
                List.of()
        );

        CliSimulator.ExecutionResult result = simulator.execute(cmd, null, stateManager, SESSION_ID, TOOL_NAME);
        assertTrue(result.success());
    }
}
