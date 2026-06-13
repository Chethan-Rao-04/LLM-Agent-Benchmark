package org.benchmark.tools.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.benchmark.app.BenchmarkEventLogger;
import org.benchmark.app.BenchmarkScorer;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.CliSimulator;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.query_generator.UserQueryGenerator;
import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the benchmark tool service entry points used by the agent runtime.
 */
class BenchmarkToolServiceTest {

    private static final String SESSION_ID = "session-1";
    private static final String TOOL_NAME = "TEST-TOOL-123";

    @Test
    void getCurrentStateCountsAsDiscoveryUse() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());

        BenchmarkToolService.StateResponse response = server.getCurrentState(SESSION_ID);

        assertEquals(1, stateManager.discoveryCount(SESSION_ID));
        assertTrue(stateManager.discoveryUsed(SESSION_ID));
        assertTrue(response.toolStates().containsKey(TOOL_NAME));
    }

    @Test
    void executeToolRecordsUnknownToolCallbackAttempt() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());

        BenchmarkToolService.CommandExecutionResponse response = server.executeBoundTool(
                SESSION_ID,
                "UNKNOWN-TOOL",
                "activate_router",
                "--dry-run"
        );

        assertFalse(response.success());
        assertEquals(1, stateManager.executionLog(SESSION_ID).size());
        assertTrue(stateManager.executionLog(SESSION_ID).get(0).message().contains("Unknown tool"));
    }

    @Test
    void executeToolRejectsSecondCommandInSameAttemptWhenBudgetIsOne() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkProperties properties = new BenchmarkProperties();
        properties.setMaxExecutionsPerAttempt(1);
        BenchmarkToolService server = createServer(stateManager, properties);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID, 1);

        BenchmarkToolService.CommandExecutionResponse first = server.executeBoundTool(
                SESSION_ID,
                TOOL_NAME,
                "activate_router",
                "--dry-run"
        );
        BenchmarkToolService.CommandExecutionResponse second = server.executeBoundTool(
                SESSION_ID,
                TOOL_NAME,
                "activate_router",
                "--dry-run"
        );

        assertTrue(first.success());
        assertFalse(second.success());
        assertTrue(second.message().contains("Execution budget exceeded"));
        assertEquals(2, stateManager.executionLog(SESSION_ID).size());
    }

    private BenchmarkToolService createServer(SessionStateManager stateManager) {
        return createServer(stateManager, new BenchmarkProperties());
    }

    private BenchmarkToolService createServer(SessionStateManager stateManager, BenchmarkProperties properties) {
        return new BenchmarkToolService(
                properties,
                stateManager,
                new CliSimulator(),
                new BenchmarkScorer(stateManager),
                new BenchmarkEventLogger(new ObjectMapper()));
    }

    private BenchmarkCaseGenerator.BenchmarkCase createBenchmarkCase() {
        CommandObject command = new CommandObject(
                "activate_router",
                List.of(new OptionEntity("--dry-run", "Simulate execution")),
                "Activate a router",
                List.of(new EffectObject("status", EffectOp.ASSIGN, "ready")),
                Map.of()
        );
        ToolObject tool = new ToolObject(
                TOOL_NAME,
                "Test tool",
                Domain.NETWORK_INFRA,
                List.of(command),
                Map.of("status", "string")
        );

        ResolvedStep step = new ResolvedStep("activate", "router",
                Map.of(), Map.of("status", "ready"));
        ResolvedScenario scenario = new ResolvedScenario(
                "test_pattern",
                "Test scenario",
                Domain.NETWORK_INFRA,
                List.of(step),
                Map.of("status", "ready"),
                Map.of()
        );

        return new BenchmarkCaseGenerator.BenchmarkCase(
                scenario,
                tool,
                scenario.steps(),
                Map.of(TOOL_NAME, "Tool documentation"),
                Map.of("status", "ready"),
                List.of(),
                List.of(),
                List.of(),
                new UserQueryGenerator(new Random(42)),
                false,
                null
        );
    }
}
