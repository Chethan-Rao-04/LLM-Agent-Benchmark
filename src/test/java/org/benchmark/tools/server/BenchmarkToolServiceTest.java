package org.benchmark.tools.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.CliSimulator;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.query_generator.UserQueryGenerator;
import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.spec.BenchmarkCaseSpec;
import org.benchmark.gen.spec.DecoyKind;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.benchmark.logging.BenchmarkEventLogger;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.enums.StateScope;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.StateRequirement;
import org.benchmark.model.objects.ToolObject;
import org.benchmark.scoring.BenchmarkScorer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the benchmark tool service entry points used by the agent runtime.
 */
class BenchmarkToolServiceTest {

    private static final String SESSION_ID = "session-1";
    private static final String TOOL_NAME = "TEST-TOOL-123";

    @Test
    void getCurrentStateReturnsCurrentToolStates() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());

        BenchmarkToolService.StateResponse response = server.getCurrentState(SESSION_ID);

        assertTrue(response.toolStates().containsKey(TOOL_NAME));
    }

    @Test
    void executeToolRecordsUnknownToolCallbackAttempt() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        BenchmarkToolService.CommandExecutionResponse response = server.executeCommand(
                SESSION_ID,
                "UNKNOWN-TOOL",
                "activate_router",
                "--dry-run"
        );

        assertEquals(BenchmarkToolService.CommandOutcomeType.REJECTED, response.outcomeType());
        assertFalse(response.success());
        assertTrue(stateManager.executionLog(SESSION_ID).isEmpty());
        assertEquals(1, stateManager.commandRejectionLog(SESSION_ID).size());
        assertTrue(stateManager.commandRejectionLog(SESSION_ID).get(0).message().contains("Unknown tool"));

        BenchmarkToolService.CommandExecutionResponse followUp = server.executeCommand(
                SESSION_ID,
                TOOL_NAME,
                "activate_router",
                "--dry-run"
        );

        assertEquals(BenchmarkToolService.CommandOutcomeType.EXECUTED, followUp.outcomeType());
        assertTrue(followUp.success());
        assertEquals(1, stateManager.executionLog(SESSION_ID).size());
    }

    @Test
    void unknownSessionDoesNotCreatePlaceholderState() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> server.executeCommand(SESSION_ID, TOOL_NAME, "activate_router", "--dry-run"));

        assertTrue(exception.getMessage().contains("Unknown benchmark session"));
        assertNull(stateManager.getBenchmarkCase(SESSION_ID));
        assertTrue(stateManager.executionLog(SESSION_ID).isEmpty());
    }

    @Test
    void unknownCommandIsRejectedWithoutRecordingExecution() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        BenchmarkToolService.CommandExecutionResponse response = server.executeCommand(
                SESSION_ID,
                TOOL_NAME,
                "list_tools",
                TOOL_NAME
        );

        assertEquals(BenchmarkToolService.CommandOutcomeType.REJECTED, response.outcomeType());
        assertFalse(response.success());
        assertTrue(response.message().contains("Unknown command"));
        assertTrue(response.message().contains("activate_router"));
        assertTrue(stateManager.executionLog(SESSION_ID).isEmpty());
        assertEquals(1, stateManager.commandRejectionLog(SESSION_ID).size());
    }

    @Test
    void wrongToolCommandRecordsFailedExecution() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCaseWithWrongTool();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        BenchmarkToolService.CommandExecutionResponse response = server.executeCommand(
                SESSION_ID,
                "WRONG-TOOL-456",
                "activate_router",
                "--dry-run"
        );

        assertEquals(BenchmarkToolService.CommandOutcomeType.EXECUTED, response.outcomeType());
        assertFalse(response.success());
        assertTrue(response.message().contains("Wrong tool selected"));
        assertEquals(1, stateManager.executionLog(SESSION_ID).size());
        assertFalse(stateManager.executionLog(SESSION_ID).get(0).success());
        assertTrue(stateManager.commandRejectionLog(SESSION_ID).isEmpty());
        assertNull(stateManager.getToolState(SESSION_ID, "WRONG-TOOL-456", "status"));
    }

    @Test
    void commandOnAnyTargetToolCanExecuteWithLocalState() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createMultiTargetBenchmarkCase();
        ToolObject secondTarget = benchmarkCase.targetTools().get(1);

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        BenchmarkToolService.CommandExecutionResponse response = server.executeCommand(
                SESSION_ID,
                secondTarget.name(),
                "activate_backup",
                ""
        );

        assertEquals(BenchmarkToolService.CommandOutcomeType.EXECUTED, response.outcomeType());
        assertTrue(response.success());
        assertEquals("ready", stateManager.getToolState(SESSION_ID, secondTarget.name(), "backup_status"));
        assertNull(stateManager.getToolState(SESSION_ID, benchmarkCase.targetToolObject().name(), "primary_status"));
        Map<String, String> familyState = stateManager.getTargetFamilyStateSnapshot(SESSION_ID, benchmarkCase);
        assertNull(familyState.get("primary_status"));
        assertEquals("ready", familyState.get("backup_status"));
        assertEquals(1, stateManager.executionLog(SESSION_ID).size());
    }

    @Test
    void commandWithMissingSharedPreconditionFailsBeforeMutation() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createSharedStateBenchmarkCase();
        ToolObject welder = benchmarkCase.targetTools().get(1);

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        BenchmarkToolService.CommandExecutionResponse response = server.executeCommand(
                SESSION_ID,
                welder.name(),
                "weld_joint",
                ""
        );

        assertEquals(BenchmarkToolService.CommandOutcomeType.EXECUTED, response.outcomeType());
        assertFalse(response.success());
        assertTrue(response.message().contains("Required shared state"));
        assertNull(stateManager.getToolState(SESSION_ID, welder.name(), "weld_state"));
        assertNull(stateManager.getSharedState(SESSION_ID, "joint_state"));
    }

    @Test
    void wrongToolStillFailsAndConsumesAttemptForMultiTargetCase() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createMultiTargetBenchmarkCaseWithWrongTool();
        ToolObject wrongTool = benchmarkCase.distractors().getFirst();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        BenchmarkToolService.CommandExecutionResponse wrongToolResponse = server.executeCommand(
                SESSION_ID,
                wrongTool.name(),
                "activate_backup",
                ""
        );
        BenchmarkToolService.CommandExecutionResponse followUp = server.executeCommand(
                SESSION_ID,
                benchmarkCase.targetTools().get(1).name(),
                "activate_backup",
                ""
        );

        assertEquals(BenchmarkToolService.CommandOutcomeType.EXECUTED, wrongToolResponse.outcomeType());
        assertFalse(wrongToolResponse.success());
        assertTrue(wrongToolResponse.message().contains("Wrong tool selected"));
        assertEquals(BenchmarkToolService.CommandOutcomeType.REJECTED, followUp.outcomeType());
        assertTrue(followUp.message().contains("single execution"));
        assertEquals(1, stateManager.executionLog(SESSION_ID).size());
        assertFalse(stateManager.executionLog(SESSION_ID).getFirst().success());
        assertNull(stateManager.getToolState(SESSION_ID, wrongTool.name(), "backup_status"));
    }

    @Test
    void executeToolRejectsSecondCommandInSameAttemptWhenBudgetIsOne() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkProperties properties = new BenchmarkProperties();
        properties.setMaxExecutionsPerAttempt(1);
        BenchmarkToolService server = createServer(stateManager, properties);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        BenchmarkToolService.CommandExecutionResponse first = server.executeCommand(
                SESSION_ID,
                TOOL_NAME,
                "activate_router",
                "--dry-run"
        );
        BenchmarkToolService.CommandExecutionResponse second = server.executeCommand(
                SESSION_ID,
                TOOL_NAME,
                "activate_router",
                "--dry-run"
        );

        assertEquals(BenchmarkToolService.CommandOutcomeType.EXECUTED, first.outcomeType());
        assertEquals(BenchmarkToolService.CommandOutcomeType.REJECTED, second.outcomeType());
        assertTrue(first.success());
        assertFalse(second.success());
        assertTrue(second.message().contains("single execution"));
        assertEquals(1, stateManager.executionLog(SESSION_ID).size());
        assertEquals(1, stateManager.commandRejectionLog(SESSION_ID).size());
    }

    @Test
    void concurrentCommandsDoNotBypassExecutionBudget() throws Exception {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkProperties properties = new BenchmarkProperties();
        properties.setMaxExecutionsPerAttempt(1);
        BlockingCliSimulator cliSimulator = BlockingCliSimulator.success();
        BenchmarkToolService server = createServer(stateManager, properties, cliSimulator);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        withExecutor(executor -> {
            Future<BenchmarkToolService.CommandExecutionResponse> first = executor.submit(() ->
                    server.executeCommand(SESSION_ID, TOOL_NAME, "activate_router", "--dry-run"));
            assertTrue(cliSimulator.firstExecutionStarted.await(1, TimeUnit.SECONDS));

            Future<BenchmarkToolService.CommandExecutionResponse> second = executor.submit(() ->
                    server.executeCommand(SESSION_ID, TOOL_NAME, "activate_router", "--dry-run"));
            Thread.sleep(150);
            assertEquals(1, cliSimulator.invocationCount.get());

            cliSimulator.releaseExecution.countDown();

            BenchmarkToolService.CommandExecutionResponse firstResponse = first.get(1, TimeUnit.SECONDS);
            BenchmarkToolService.CommandExecutionResponse secondResponse = second.get(1, TimeUnit.SECONDS);

            assertEquals(BenchmarkToolService.CommandOutcomeType.EXECUTED, firstResponse.outcomeType());
            assertEquals(BenchmarkToolService.CommandOutcomeType.REJECTED, secondResponse.outcomeType());
            assertTrue(firstResponse.success());
            assertFalse(secondResponse.success());
            assertTrue(secondResponse.message().contains("single execution"));
            assertEquals(1, stateManager.executionLog(SESSION_ID).size());
            assertEquals(1, stateManager.commandRejectionLog(SESSION_ID).size());
        });
    }

    @Test
    void realExecutionFailureStillConsumesAttemptSlot() throws Exception {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkProperties properties = new BenchmarkProperties();
        properties.setMaxExecutionsPerAttempt(5);
        properties.setMaxRepeatedCommandFailuresPerAttempt(1);
        BlockingCliSimulator cliSimulator = BlockingCliSimulator.failure("activate_router", "Precondition not met");
        BenchmarkToolService server = createServer(stateManager, properties, cliSimulator);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        withExecutor(executor -> {
            Future<BenchmarkToolService.CommandExecutionResponse> first = executor.submit(() ->
                    server.executeCommand(SESSION_ID, TOOL_NAME, "activate_router", "--dry-run"));
            assertTrue(cliSimulator.firstExecutionStarted.await(1, TimeUnit.SECONDS));

            Future<BenchmarkToolService.CommandExecutionResponse> second = executor.submit(() ->
                    server.executeCommand(SESSION_ID, TOOL_NAME, "activate_router", "--dry-run"));
            Thread.sleep(150);
            assertEquals(1, cliSimulator.invocationCount.get());

            cliSimulator.releaseExecution.countDown();

            BenchmarkToolService.CommandExecutionResponse firstResponse = first.get(1, TimeUnit.SECONDS);
            BenchmarkToolService.CommandExecutionResponse secondResponse = second.get(1, TimeUnit.SECONDS);

            assertEquals(BenchmarkToolService.CommandOutcomeType.EXECUTED, firstResponse.outcomeType());
            assertEquals(BenchmarkToolService.CommandOutcomeType.REJECTED, secondResponse.outcomeType());
            assertFalse(firstResponse.success());
            assertFalse(secondResponse.success());
            assertTrue(secondResponse.message().contains("single execution"));
            assertEquals(1, cliSimulator.invocationCount.get());
            assertEquals(1, stateManager.executionLog(SESSION_ID).size());
            assertEquals(1, stateManager.commandRejectionLog(SESSION_ID).size());
        });
    }

    @Test
    void alreadyCompletedScenarioRejectsWithoutRecordingExecution() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createAlreadyCompletedBenchmarkCase();

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        BenchmarkToolService.CommandExecutionResponse response = server.executeCommand(
                SESSION_ID,
                TOOL_NAME,
                "inspect_router",
                "--dry-run"
        );

        assertEquals(BenchmarkToolService.CommandOutcomeType.REJECTED, response.outcomeType());
        assertFalse(response.success());
        assertTrue(response.message().contains("Scenario already completed"));
        assertTrue(stateManager.executionLog(SESSION_ID).isEmpty());
        assertEquals(1, stateManager.commandRejectionLog(SESSION_ID).size());
    }

    @Test
    void rejectedCommandsDoNotAffectScoringInputs() {
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkToolService server = createServer(stateManager);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();
        BenchmarkScorer scorer = new BenchmarkScorer(stateManager);

        stateManager.initializeSession(SESSION_ID, benchmarkCase, benchmarkCase.allTools());
        stateManager.startAttempt(SESSION_ID);

        BenchmarkToolService.CommandExecutionResponse executed = server.executeCommand(
                SESSION_ID,
                TOOL_NAME,
                "activate_router",
                "--dry-run"
        );
        BenchmarkToolService.CommandExecutionResponse rejected = server.executeCommand(
                SESSION_ID,
                TOOL_NAME,
                "activate_router",
                "--dry-run"
        );

        assertEquals(BenchmarkToolService.CommandOutcomeType.EXECUTED, executed.outcomeType());
        assertEquals(BenchmarkToolService.CommandOutcomeType.REJECTED, rejected.outcomeType());
        assertEquals(1, stateManager.executionLog(SESSION_ID).size());
        assertEquals(1, stateManager.commandRejectionLog(SESSION_ID).size());
        assertEquals(1, scorer.computeAttemptMetrics(SESSION_ID, benchmarkCase, true).executionsTotal());
    }

    private BenchmarkToolService createServer(SessionStateManager stateManager) {
        return createServer(stateManager, new BenchmarkProperties(), new CliSimulator());
    }

    private BenchmarkToolService createServer(SessionStateManager stateManager, BenchmarkProperties properties) {
        return createServer(stateManager, properties, new CliSimulator());
    }

    private BenchmarkToolService createServer(SessionStateManager stateManager,
                                              BenchmarkProperties properties,
                                              CliSimulator cliSimulator) {
        BenchmarkEventLogger eventLogger = new BenchmarkEventLogger(new ObjectMapper());
        BenchmarkScorer benchmarkScorer = new BenchmarkScorer(stateManager);
        BenchmarkToolEventPublisher eventPublisher = new BenchmarkToolEventPublisher(stateManager, eventLogger);
        BenchmarkToolExecutionPolicy executionPolicy = new BenchmarkToolExecutionPolicy(
                properties,
                stateManager,
                benchmarkScorer);
        BenchmarkToolExecutionService executionService = new BenchmarkToolExecutionService(
                stateManager,
                cliSimulator,
                executionPolicy,
                eventPublisher);
        return new BenchmarkToolService(stateManager, executionService);
    }

    private void withExecutor(ExecutorWork work) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            work.run(executor);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
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
                "Tool documentation",
                Map.of("status", "ready"),
                List.of(),
                List.of(),
                List.of(),
                "Use the documented tool to complete the work.",
                false,
                null,
                null
        );
    }

    private BenchmarkCaseGenerator.BenchmarkCase createBenchmarkCaseWithWrongTool() {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createBenchmarkCase();
        CommandObject wrongCommand = new CommandObject(
                "activate_router",
                List.of(new OptionEntity("--dry-run", "Simulate execution")),
                "Wrong target command",
                List.of(new EffectObject("status", EffectOp.ASSIGN, "ready")),
                Map.of()
        );
        ToolObject wrongTool = new ToolObject(
                "WRONG-TOOL-456",
                "Wrong tool",
                Domain.NETWORK_INFRA,
                List.of(wrongCommand),
                Map.of("status", "string")
        );

        return new BenchmarkCaseGenerator.BenchmarkCase(
                benchmarkCase.scenario(),
                benchmarkCase.targetToolObject(),
                benchmarkCase.targetSteps(),
                benchmarkCase.caseManual(),
                benchmarkCase.expectedState(),
                List.of(wrongTool),
                List.of(wrongTool),
                List.of(),
                benchmarkCase.userQuery(),
                benchmarkCase.hasTrap(),
                benchmarkCase.trapCommandName(),
                benchmarkCase.recoveryCommandName()
        );
    }

    private BenchmarkCaseGenerator.BenchmarkCase createMultiTargetBenchmarkCase() {
        CommandObject primaryCommand = new CommandObject(
                "activate_primary",
                List.of(),
                "Activate primary",
                List.of(new EffectObject("primary_status", EffectOp.ASSIGN, "ready")),
                Map.of()
        );
        CommandObject backupCommand = new CommandObject(
                "activate_backup",
                List.of(),
                "Activate backup",
                List.of(new EffectObject("backup_status", EffectOp.ASSIGN, "ready")),
                Map.of()
        );
        ToolObject primaryTool = new ToolObject(
                "TARGET-PRIMARY-123",
                "Primary target",
                Domain.NETWORK_INFRA,
                List.of(primaryCommand),
                Map.of("primary_status", "string")
        );
        ToolObject backupTool = new ToolObject(
                "TARGET-BACKUP-456",
                "Backup target",
                Domain.NETWORK_INFRA,
                List.of(backupCommand),
                Map.of("backup_status", "string")
        );
        ResolvedStep primaryStep = new ResolvedStep("activate", "primary",
                Map.of(), Map.of("primary_status", "ready"));
        ResolvedStep backupStep = new ResolvedStep("activate", "backup",
                Map.of(), Map.of("backup_status", "ready"));
        ResolvedScenario scenario = new ResolvedScenario(
                "multi_target",
                "Activate primary and backup",
                Domain.NETWORK_INFRA,
                List.of(primaryStep, backupStep),
                Map.of("primary_status", "ready", "backup_status", "ready"),
                Map.of()
        );

        return new BenchmarkCaseGenerator.BenchmarkCase(
                scenario,
                BenchmarkCaseSpec.fromScenario(scenario, 0, 0),
                primaryTool,
                List.of(primaryTool, backupTool),
                List.of(
                        new BenchmarkCaseGenerator.TargetStep(primaryTool.name(), "activate_primary"),
                        new BenchmarkCaseGenerator.TargetStep(backupTool.name(), "activate_backup")
                ),
                scenario.steps(),
                "Tool documentation",
                scenario.cumulativeExpectedState(),
                Map.of(
                        primaryTool.name(), primaryStep.effect(),
                        backupTool.name(), backupStep.effect()
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "Use the documented tools to complete the work.",
                false,
                null,
                null
        );
    }

    private BenchmarkCaseGenerator.BenchmarkCase createMultiTargetBenchmarkCaseWithWrongTool() {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = createMultiTargetBenchmarkCase();
        CommandObject wrongCommand = new CommandObject(
                "activate_backup",
                List.of(),
                "Wrong backup command",
                List.of(new EffectObject("backup_status", EffectOp.ASSIGN, "ready")),
                Map.of()
        );
        ToolObject wrongTool = new ToolObject(
                "WRONG-BACKUP-789",
                "Wrong backup",
                Domain.NETWORK_INFRA,
                List.of(wrongCommand),
                Map.of("backup_status", "string")
        );

        return new BenchmarkCaseGenerator.BenchmarkCase(
                benchmarkCase.scenario(),
                benchmarkCase.spec(),
                benchmarkCase.targetToolObject(),
                benchmarkCase.targetTools(),
                benchmarkCase.targetPath(),
                benchmarkCase.targetSteps(),
                benchmarkCase.caseManual(),
                benchmarkCase.expectedState(),
                benchmarkCase.expectedStateByTool(),
                List.of(wrongTool),
                List.of(wrongTool),
                List.of(),
                Map.of(wrongTool.name(), DecoyKind.SIMILAR_INTENT_WRONG_RESOURCE),
                benchmarkCase.userQuery(),
                benchmarkCase.hasTrap(),
                benchmarkCase.trapCommandName(),
                benchmarkCase.recoveryCommandName()
        );
    }

    private BenchmarkCaseGenerator.BenchmarkCase createSharedStateBenchmarkCase() {
        CommandObject shapeCommand = new CommandObject(
                "stabilize_profile",
                List.of(),
                "Stabilize profile",
                List.of(new EffectObject(StateScope.SHARED, "profile_state", EffectOp.ASSIGN, "stable")),
                List.of()
        );
        CommandObject weldCommand = new CommandObject(
                "weld_joint",
                List.of(),
                "Weld joint",
                List.of(
                        new EffectObject("weld_state", EffectOp.ASSIGN, "joined"),
                        new EffectObject(StateScope.SHARED, "joint_state", EffectOp.ASSIGN, "joined")
                ),
                List.of(new StateRequirement(StateScope.SHARED, "profile_state", "stable"))
        );
        ToolObject shapeTool = new ToolObject(
                "MAN-SHAPE-123",
                "Shape target",
                Domain.MANUFACTURING,
                List.of(shapeCommand),
                Map.of()
        );
        ToolObject welderTool = new ToolObject(
                "MAN-WELDER-456",
                "Welder target",
                Domain.MANUFACTURING,
                List.of(weldCommand),
                Map.of("weld_state", "string")
        );
        ResolvedStep shapeStep = new ResolvedStep("stabilize", "profile", Map.of(), Map.of());
        ResolvedStep weldStep = new ResolvedStep("weld", "joint", Map.of(), Map.of("weld_state", "joined"));
        ResolvedScenario scenario = new ResolvedScenario(
                "shared_state",
                "Stabilize profile then weld joint",
                Domain.MANUFACTURING,
                List.of(shapeStep, weldStep),
                Map.of("joint_state", "joined"),
                Map.of()
        );
        BenchmarkCaseSpec spec = new BenchmarkCaseSpec(
                "Stabilize profile then weld joint",
                Domain.MANUFACTURING,
                List.of(),
                Map.of("joint_state", "joined"),
                Map.of("joint_state", "joined"),
                org.benchmark.gen.spec.DecoyPlan.currentDefault(0, 0),
                org.benchmark.gen.spec.ScoringPolicy.currentDefault()
        );
        return new BenchmarkCaseGenerator.BenchmarkCase(
                scenario,
                spec,
                shapeTool,
                List.of(shapeTool, welderTool),
                List.of(
                        new BenchmarkCaseGenerator.TargetStep(shapeTool.name(), "stabilize_profile"),
                        new BenchmarkCaseGenerator.TargetStep(welderTool.name(), "weld_joint")
                ),
                scenario.steps(),
                "Tool documentation",
                spec.expectedFinalState(),
                Map.of(welderTool.name(), Map.of("weld_state", "joined")),
                spec.expectedSharedState(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "Use the documented tools to complete the work.",
                false,
                null,
                null
        );
    }

    private BenchmarkCaseGenerator.BenchmarkCase createScenarioCompletionBenchmarkCase() {
        String commandName = CommandAbbreviator.commandName("activate", "router");
        CommandObject command = new CommandObject(
                commandName,
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
        ResolvedStep step = new ResolvedStep("activate", "router", Map.of(), Map.of("status", "ready"));
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
                "Tool documentation",
                Map.of("status", "ready"),
                List.of(),
                List.of(),
                List.of(),
                "Use the documented tool to complete the work.",
                false,
                null,
                null
        );
    }

    private BenchmarkCaseGenerator.BenchmarkCase createAlreadyCompletedBenchmarkCase() {
        CommandObject command = new CommandObject(
                "inspect_router",
                List.of(new OptionEntity("--dry-run", "Simulate execution")),
                "Inspect a router",
                List.of(),
                Map.of()
        );
        ToolObject tool = new ToolObject(
                TOOL_NAME,
                "Test tool",
                Domain.NETWORK_INFRA,
                List.of(command),
                Map.of()
        );
        ResolvedScenario scenario = new ResolvedScenario(
                "already_done",
                "Already completed scenario",
                Domain.NETWORK_INFRA,
                List.of(),
                Map.of(),
                Map.of()
        );

        return new BenchmarkCaseGenerator.BenchmarkCase(
                scenario,
                tool,
                List.of(),
                "Tool documentation",
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                "Use the documented tool to complete the work.",
                false,
                null,
                null
        );
    }

    @FunctionalInterface
    private interface ExecutorWork {
        void run(ExecutorService executor) throws Exception;
    }

    private static final class BlockingCliSimulator extends CliSimulator {
        private final AtomicInteger invocationCount = new AtomicInteger();
        private final CountDownLatch firstExecutionStarted = new CountDownLatch(1);
        private final CountDownLatch releaseExecution = new CountDownLatch(1);
        private final String failureMessage;
        private final String resolvedCommandName;

        private BlockingCliSimulator(String resolvedCommandName, String failureMessage) {
            this.resolvedCommandName = resolvedCommandName;
            this.failureMessage = failureMessage;
        }

        private static BlockingCliSimulator success() {
            return new BlockingCliSimulator(null, null);
        }

        private static BlockingCliSimulator failure(String resolvedCommandName, String failureMessage) {
            return new BlockingCliSimulator(resolvedCommandName, failureMessage);
        }

        @Override
        public ExecutionResult execute(ToolObject tool,
                                       String commandName,
                                       String option,
                                       SessionStateManager stateManager,
                                       String sessionId) {
            if (invocationCount.incrementAndGet() == 1) {
                firstExecutionStarted.countDown();
            }
            awaitRelease();
            if (failureMessage != null) {
                return new ExecutionResult(false, 2, "", failureMessage, resolvedCommandName);
            }
            return super.execute(tool, commandName, option, stateManager, sessionId);
        }

        private void awaitRelease() {
            try {
                assertTrue(releaseExecution.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
