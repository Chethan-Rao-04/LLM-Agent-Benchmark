package org.benchmark.scoring;

import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.spec.BenchmarkCaseSpec;
import org.benchmark.gen.spec.CapabilityStep;
import org.benchmark.gen.spec.DecoyPlan;
import org.benchmark.gen.spec.ScoringPolicy;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.enums.StateScope;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.ToolObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkScorerTest {

    private final SessionStateManager stateManager = new SessionStateManager();
    private final BenchmarkScorer scorer = new BenchmarkScorer(stateManager);

    @Test
    void emptyExpectedStateScoresPerfect() {
        assertEquals(1.0, scorer.scoreExpectedState(Map.of(), Map.of("key", "value")));
    }

    @Test
    void perfectMatchScoresPerfect() {
        Map<String, String> expected = Map.of("a", "1", "b", "2");
        Map<String, String> actual = Map.of("a", "1", "b", "2", "c", "3");
        assertEquals(1.0, scorer.scoreExpectedState(expected, actual));
    }

    @Test
    void noMatchScoresZero() {
        Map<String, String> expected = Map.of("a", "1", "b", "2");
        Map<String, String> actual = Map.of("a", "wrong", "b", "wrong");
        assertEquals(0.0, scorer.scoreExpectedState(expected, actual));
    }

    @Test
    void partialMatchScoresCorrectly() {
        Map<String, String> expected = Map.of("a", "1", "b", "2");
        Map<String, String> actual = Map.of("a", "1", "b", "wrong");
        assertEquals(0.5, scorer.scoreExpectedState(expected, actual));
    }

    @Test
    void missingActualKeyScoresZero() {
        assertEquals(0.0, scorer.scoreExpectedState(Map.of("a", "1"), Map.of()));
    }

    @Test
    void threeOfFourMatchScores075() {
        Map<String, String> expected = Map.of("a", "1", "b", "2", "c", "3", "d", "4");
        Map<String, String> actual = Map.of("a", "1", "b", "2", "c", "3", "d", "wrong");
        assertEquals(0.75, scorer.scoreExpectedState(expected, actual));
    }

    @Test
    void benchmarkScoreCompositeUsesSixPrimaryDimensions() {
        BenchmarkScorer.BenchmarkScore perfect = new BenchmarkScorer.BenchmarkScore(
                1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.2, true, true
        );
        assertEquals(1.0, perfect.composite());

        BenchmarkScorer.BenchmarkScore oneMiss = new BenchmarkScorer.BenchmarkScore(
                1.0, 1.0, 1.0, 1.0, 0.0, 1.0, 0.8, false, false
        );
        assertEquals(5.0 / 6.0, oneMiss.composite(), 1e-9);
    }

    @Test
    void attemptMetricsDoNotApplyTrapEfficiencyOverheadWhenTrapIsAbsent() {
        String sessionId = "no-trap";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildBenchmarkCase(false);
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        recordExecutions(sessionId, 5);

        BenchmarkScorer.AttemptMetrics metrics = scorer.computeAttemptMetrics(sessionId, benchmarkCase, false);

        assertEquals(0.4, metrics.efficiency(), 1e-9);
        assertEquals(1.0, metrics.decoyResistance(), 1e-9);
    }

    @Test
    void attemptMetricsApplyTrapEfficiencyOverheadWhenTrapIsPresent() {
        String sessionId = "with-trap";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildBenchmarkCase(true);
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        recordExecutions(sessionId, 5);

        BenchmarkScorer.AttemptMetrics metrics = scorer.computeAttemptMetrics(sessionId, benchmarkCase, false);

        assertEquals(0.8, metrics.efficiency(), 1e-9);
        assertEquals(1.0, metrics.decoyResistance(), 1e-9);
    }

    @Test
    void targetLikeWrongToolProbeLowersDecoyResistanceSlightly() {
        String sessionId = "target-like-wrong-tool-probe";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildBenchmarkCase(false);
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-DECOY-201",
                "probe_cfg",
                "",
                false,
                "Unknown command"
        ));

        BenchmarkScorer.AttemptMetrics metrics = scorer.computeAttemptMetrics(sessionId, benchmarkCase, false);

        assertEquals(0.75, metrics.decoyResistance(), 1e-9);
    }

    @Test
    void targetLikeWrongToolSelectionDropsDecoyResistance() {
        String sessionId = "target-like-wrong-tool-selection";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildBenchmarkCase(false);
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-TEST-101",
                "auth_srv",
                "",
                true,
                "OK: auth_srv"
        ));

        BenchmarkScorer.AttemptMetrics targetOnlyMetrics =
                scorer.computeAttemptMetrics(sessionId, benchmarkCase, false);

        assertEquals(1.0, targetOnlyMetrics.decoyResistance(), 1e-9);

        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-DECOY-201",
                "decoy_mutate",
                "",
                true,
                "OK: decoy_mutate"
        ));

        BenchmarkScorer.AttemptMetrics metrics = scorer.computeAttemptMetrics(sessionId, benchmarkCase, false);

        assertEquals(0.4, metrics.decoyResistance(), 1e-9);
    }

    @Test
    void caseScoreReportsTargetLikeWrongToolAvoidanceWithoutChangingPassRequirement() {
        String sessionId = "case-score-decoy";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildBenchmarkCase(false);
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-DECOY-201",
                "decoy_mutate",
                "",
                true,
                "OK: decoy_mutate"
        ));

        BenchmarkScorer.BenchmarkScore score = scorer.computeCaseScore(sessionId, benchmarkCase, 1, false, false);

        assertEquals(0.4, score.decoyResistance(), 1e-9);
        assertFalse(score.passed());
    }

    @Test
    void allSuccessfulExecutionsDoNotCountAsRecovery() {
        String sessionId = "all-success-no-recovery";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildBenchmarkCase(true);
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-TEST-101",
                "auth_srv",
                "",
                true,
                "OK: auth_srv"
        ));

        BenchmarkScorer.BenchmarkScore score = scorer.computeCaseScore(sessionId, benchmarkCase, 2, true, true);

        assertFalse(score.recovery());
    }

    @Test
    void failedExecutionThenGoalAchievementCountsAsRecovery() {
        String sessionId = "failed-then-recovery";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildBenchmarkCase(false);
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-TEST-101",
                "dpl_svc",
                "",
                false,
                "Precondition not met"
        ));

        BenchmarkScorer.BenchmarkScore score = scorer.computeCaseScore(sessionId, benchmarkCase, 2, true, true);

        assertTrue(score.recovery());
    }

    @Test
    void failedRequiredCommandDoesNotCountAsCommandPrecision() {
        String sessionId = "failed-required-command";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildBenchmarkCase(false);
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-TEST-101",
                "auth_srv",
                "--wrong",
                false,
                "Unknown option --wrong for command auth_srv"
        ));

        assertEquals(0.0, scorer.scoreCommandPrecision(
                stateManager.executionLog(sessionId), benchmarkCase), 1e-9);
    }

    @Test
    void scorerUsesCapabilityCommandNamesAsExpectedSteps() {
        String sessionId = "spec-command-source";
        ResolvedStep legacyStep = new ResolvedStep(
                "authenticate",
                "server",
                Map.of(),
                Map.of("auth_token", "valid")
        );
        ResolvedScenario scenario = new ResolvedScenario(
                "custom_command",
                "Use custom command identity",
                Domain.NETWORK_INFRA,
                List.of(legacyStep),
                Map.of("auth_token", "valid"),
                Map.of()
        );
        BenchmarkCaseSpec spec = new BenchmarkCaseSpec(
                "Use custom command identity",
                Domain.NETWORK_INFRA,
                List.of(new CapabilityStep(
                        "authenticate",
                        "authenticate",
                        "server",
                        "custom_auth",
                        Map.of(),
                        Map.of("auth_token", "valid")
                )),
                Map.of("auth_token", "valid"),
                DecoyPlan.currentDefault(0, 0),
                ScoringPolicy.currentDefault()
        );
        ToolObject targetTool = new ToolObject(
                "NET-TEST-101",
                "Test tool",
                Domain.NETWORK_INFRA,
                List.of(new CommandObject(
                        "custom_auth",
                        List.of(),
                        "Authenticate",
                        List.of(new EffectObject("auth_token", EffectOp.ASSIGN, "valid")),
                        Map.of()
                )),
                Map.of("auth_token", "string")
        );
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = new BenchmarkCaseGenerator.BenchmarkCase(
                scenario,
                spec,
                targetTool,
                scenario.steps(),
                "docs",
                Map.of("auth_token", "valid"),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "Use the documented tool.",
                false,
                null,
                null
        );
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-TEST-101",
                "custom_auth",
                "",
                true,
                "OK: custom_auth"
        ));

        assertEquals(1.0, scorer.scoreStepCompletion(stateManager.executionLog(sessionId), benchmarkCase), 1e-9);
        assertEquals(1.0, scorer.scoreOrdering(stateManager.executionLog(sessionId), benchmarkCase), 1e-9);
        assertEquals(1.0, scorer.scoreCommandPrecision(stateManager.executionLog(sessionId), benchmarkCase), 1e-9);
    }

    @Test
    void scorerMatchesTargetStepsByToolAndCommandPair() {
        String sessionId = "target-pair-matching";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildMultiTargetBenchmarkCase();
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-BACKUP-202",
                "sync_state",
                "",
                true,
                "OK: sync_state"
        ));

        assertEquals(0.5, scorer.scoreStepCompletion(stateManager.executionLog(sessionId), benchmarkCase), 1e-9);
        assertEquals(1.0, scorer.scoreCommandPrecision(stateManager.executionLog(sessionId), benchmarkCase), 1e-9);
    }

    @Test
    void scorerOrdersTargetPathPairsAcrossTools() {
        String sessionId = "target-pair-ordering";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildMultiTargetBenchmarkCase();
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-BACKUP-202",
                "sync_state",
                "",
                true,
                "OK: sync_state"
        ));
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-PRIMARY-101",
                "sync_state",
                "",
                true,
                "OK: sync_state"
        ));

        assertEquals(1.0, scorer.scoreStepCompletion(stateManager.executionLog(sessionId), benchmarkCase), 1e-9);
        assertEquals(0.5, scorer.scoreOrdering(stateManager.executionLog(sessionId), benchmarkCase), 1e-9);
    }

    @Test
    void scenarioCompletionEvaluatesExpectedStatePerTargetTool() {
        String sessionId = "per-target-state";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildMultiTargetBenchmarkCase();
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-PRIMARY-101",
                "sync_state",
                "",
                true,
                "OK: sync_state"
        ));
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-BACKUP-202",
                "sync_state",
                "",
                true,
                "OK: sync_state"
        ));
        stateManager.updateToolState(sessionId, "NET-PRIMARY-101", "status", "primary_ready");

        assertEquals(0.5, scorer.computeAttemptMetrics(sessionId, benchmarkCase, false).stateAccuracy(), 1e-9);
        assertFalse(scorer.hasSuccessfulScenarioCompletion(
                sessionId, stateManager.executionLog(sessionId), benchmarkCase));

        stateManager.updateToolState(sessionId, "NET-BACKUP-202", "status", "backup_ready");

        assertEquals(1.0, scorer.computeAttemptMetrics(sessionId, benchmarkCase, false).stateAccuracy(), 1e-9);
        assertTrue(scorer.hasSuccessfulScenarioCompletion(
                sessionId, stateManager.executionLog(sessionId), benchmarkCase));
    }

    @Test
    void scenarioCompletionUsesExpectedSharedStateWhenPresent() {
        String sessionId = "shared-state-completion";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildSharedStateBenchmarkCase();
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "MAN-SHAPE-101",
                "stabilize_profile",
                "",
                true,
                "OK: stabilize_profile"
        ));
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "MAN-WELDER-202",
                "weld_joint",
                "",
                true,
                "OK: weld_joint"
        ));
        stateManager.updateToolState(sessionId, "MAN-WELDER-202", "weld_state", "joined");

        assertEquals(0.0, scorer.computeAttemptMetrics(sessionId, benchmarkCase, false).stateAccuracy(), 1e-9);
        assertFalse(scorer.hasSuccessfulScenarioCompletion(
                sessionId, stateManager.executionLog(sessionId), benchmarkCase));

        stateManager.updateSharedState(sessionId, "joint_state", "joined");

        assertEquals(1.0, scorer.computeAttemptMetrics(sessionId, benchmarkCase, false).stateAccuracy(), 1e-9);
        assertTrue(scorer.hasSuccessfulScenarioCompletion(
                sessionId, stateManager.executionLog(sessionId), benchmarkCase));
    }

    @Test
    void decoyResistancePenalizesAnyNonTargetToolExecution() {
        String sessionId = "random-distractor-decoy";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildMultiTargetBenchmarkCase();
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
        stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                "NET-RANDOM-303",
                "probe_noise",
                "",
                false,
                "Wrong tool selected"
        ));

        assertEquals(0.75, scorer.computeAttemptMetrics(sessionId, benchmarkCase, false).decoyResistance(), 1e-9);
    }

    private void recordExecutions(String sessionId, int count) {
        for (int i = 0; i < count; i++) {
            stateManager.recordExecution(sessionId, new SessionStateManager.ExecutionRecord(
                    "NET-TEST-101",
                    "auth_srv",
                    "",
                    true,
                    "ok"
            ));
        }
    }

    private BenchmarkCaseGenerator.BenchmarkCase buildBenchmarkCase(boolean withTrap) {
        CommandObject authenticate = new CommandObject(
                "auth_srv",
                List.of(),
                "Authenticate",
                List.of(new EffectObject("auth_token", EffectOp.ASSIGN, "valid")),
                Map.of(),
                withTrap ? List.of(new EffectObject("auth_token", EffectOp.ASSIGN, "valid")) : null
        );
        CommandObject deploy = new CommandObject(
                "dpl_svc",
                List.of(),
                "Deploy",
                List.of(new EffectObject("service_status", EffectOp.ASSIGN, "ready")),
                Map.of("auth_token", "valid")
        );
        CommandObject recovery = new CommandObject(
                "restore_server",
                List.of(),
                "Restore",
                List.of(new EffectObject("auth_token", EffectOp.ASSIGN, "valid")),
                Map.of()
        );

        List<CommandObject> commands = withTrap ? List.of(authenticate, deploy, recovery) : List.of(authenticate, deploy);
        ToolObject tool = new ToolObject(
                "NET-TEST-101",
                "Test tool",
                Domain.NETWORK_INFRA,
                commands,
                Map.of("auth_token", "string", "service_status", "string")
        );
        ToolObject semanticDecoy = new ToolObject(
                "NET-DECOY-201",
                "Semantic decoy",
                Domain.NETWORK_INFRA,
                List.of(
                        new CommandObject(
                                "decoy_mutate",
                                List.of(),
                                "Mutates decoy state",
                                List.of(new EffectObject("decoy_status", EffectOp.ASSIGN, "shifted")),
                                Map.of()
                        ),
                        new CommandObject(
                                "probe_cfg",
                                List.of(),
                                "Harmless probe",
                                List.of(),
                                Map.of()
                        )
                ),
                Map.of("decoy_status", "string")
        );
        ResolvedScenario scenario = new ResolvedScenario(
                "auth_deploy",
                "Authenticate then deploy",
                Domain.NETWORK_INFRA,
                List.of(
                        new ResolvedStep("authenticate", "server", Map.of(), Map.of("auth_token", "valid")),
                        new ResolvedStep("deploy", "service", Map.of("auth_token", "valid"), Map.of("service_status", "ready"))
                ),
                Map.of("service_status", "ready"),
                Map.of()
        );

        return new BenchmarkCaseGenerator.BenchmarkCase(
                scenario,
                tool,
                scenario.steps(),
                "docs",
                Map.of(),
                List.of(semanticDecoy),
                List.of(semanticDecoy),
                List.of(),
                "Use the documented tool to complete the work.",
                withTrap,
                withTrap ? authenticate.name() : null,
                withTrap ? recovery.name() : null
        );
    }

    private BenchmarkCaseGenerator.BenchmarkCase buildMultiTargetBenchmarkCase() {
        CommandObject primarySync = new CommandObject(
                "sync_state",
                List.of(),
                "Sync primary state",
                List.of(new EffectObject("status", EffectOp.ASSIGN, "primary_ready")),
                Map.of()
        );
        CommandObject backupSync = new CommandObject(
                "sync_state",
                List.of(),
                "Sync backup state",
                List.of(new EffectObject("status", EffectOp.ASSIGN, "backup_ready")),
                Map.of()
        );
        ToolObject primaryTool = new ToolObject(
                "NET-PRIMARY-101",
                "Primary target",
                Domain.NETWORK_INFRA,
                List.of(primarySync),
                Map.of("status", "string")
        );
        ToolObject backupTool = new ToolObject(
                "NET-BACKUP-202",
                "Backup target",
                Domain.NETWORK_INFRA,
                List.of(backupSync),
                Map.of("status", "string")
        );
        ToolObject randomDistractor = new ToolObject(
                "NET-RANDOM-303",
                "Random distractor",
                Domain.NETWORK_INFRA,
                List.of(new CommandObject(
                        "probe_noise",
                        List.of(),
                        "Probe random state",
                        List.of(),
                        Map.of()
                )),
                Map.of("status", "string")
        );
        List<ResolvedStep> scenarioSteps = List.of(
                new ResolvedStep("sync", "primary", Map.of(), Map.of("status", "primary_ready")),
                new ResolvedStep("sync", "backup", Map.of(), Map.of("status", "backup_ready"))
        );
        ResolvedScenario scenario = new ResolvedScenario(
                "multi_target_sync",
                "Sync primary and backup",
                Domain.NETWORK_INFRA,
                scenarioSteps,
                Map.of("status", "backup_ready"),
                Map.of()
        );
        BenchmarkCaseSpec spec = new BenchmarkCaseSpec(
                "Sync primary and backup",
                Domain.NETWORK_INFRA,
                List.of(
                        new CapabilityStep(
                                "sync primary",
                                "primary",
                                "sync",
                                "primary",
                                "sync_state",
                                Map.of(),
                                Map.of("status", "primary_ready")
                        ),
                        new CapabilityStep(
                                "sync backup",
                                "backup",
                                "sync",
                                "backup",
                                "sync_state",
                                Map.of(),
                                Map.of("status", "backup_ready")
                        )
                ),
                Map.of("status", "backup_ready"),
                DecoyPlan.currentDefault(0, 1),
                ScoringPolicy.currentDefault()
        );

        return new BenchmarkCaseGenerator.BenchmarkCase(
                scenario,
                spec,
                primaryTool,
                List.of(primaryTool, backupTool),
                List.of(
                        new BenchmarkCaseGenerator.TargetStep(primaryTool.name(), "sync_state"),
                        new BenchmarkCaseGenerator.TargetStep(backupTool.name(), "sync_state")
                ),
                scenarioSteps,
                "docs",
                spec.expectedFinalState(),
                Map.of(
                        primaryTool.name(), Map.of("status", "primary_ready"),
                        backupTool.name(), Map.of("status", "backup_ready")
                ),
                List.of(randomDistractor),
                List.of(),
                List.of(randomDistractor),
                Map.of(),
                "Use the documented tools.",
                false,
                null,
                null
        );
    }

    private BenchmarkCaseGenerator.BenchmarkCase buildSharedStateBenchmarkCase() {
        CommandObject stabilize = new CommandObject(
                "stabilize_profile",
                List.of(),
                "Stabilize profile",
                List.of(new EffectObject(StateScope.SHARED, "profile_state", EffectOp.ASSIGN, "stable")),
                Map.of()
        );
        CommandObject weld = new CommandObject(
                "weld_joint",
                List.of(),
                "Weld joint",
                List.of(
                        new EffectObject("weld_state", EffectOp.ASSIGN, "joined"),
                        new EffectObject(StateScope.SHARED, "joint_state", EffectOp.ASSIGN, "joined")
                ),
                Map.of()
        );
        ToolObject shapeTool = new ToolObject(
                "MAN-SHAPE-101",
                "Shape target",
                Domain.MANUFACTURING,
                List.of(stabilize),
                Map.of()
        );
        ToolObject welderTool = new ToolObject(
                "MAN-WELDER-202",
                "Welder target",
                Domain.MANUFACTURING,
                List.of(weld),
                Map.of("weld_state", "string")
        );
        List<ResolvedStep> scenarioSteps = List.of(
                new ResolvedStep("stabilize", "profile", Map.of(), Map.of()),
                new ResolvedStep("weld", "joint", Map.of(), Map.of("weld_state", "joined"))
        );
        ResolvedScenario scenario = new ResolvedScenario(
                "shared_state_flow",
                "Stabilize and weld",
                Domain.MANUFACTURING,
                scenarioSteps,
                Map.of("joint_state", "joined"),
                Map.of()
        );
        BenchmarkCaseSpec spec = new BenchmarkCaseSpec(
                "Stabilize and weld",
                Domain.MANUFACTURING,
                List.of(
                        new CapabilityStep(
                                "stabilize profile",
                                "shape",
                                "stabilize",
                                "profile",
                                "stabilize_profile",
                                Map.of(),
                                Map.of()
                        ),
                        new CapabilityStep(
                                "weld joint",
                                "welder",
                                "weld",
                                "joint",
                                "weld_joint",
                                Map.of(),
                                Map.of("weld_state", "joined")
                        )
                ),
                Map.of("joint_state", "joined"),
                Map.of("joint_state", "joined"),
                DecoyPlan.currentDefault(0, 0),
                ScoringPolicy.currentDefault()
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
                scenarioSteps,
                "docs",
                spec.expectedFinalState(),
                Map.of(welderTool.name(), Map.of("weld_state", "joined")),
                spec.expectedSharedState(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "Use the documented tools.",
                false,
                null,
                null
        );
    }
}
