package org.benchmark.scoring;

import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.query_generator.UserQueryGenerator;
import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.ToolObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void semanticDecoyProbeLowersDecoyResistanceSlightly() {
        String sessionId = "semantic-decoy-probe";
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
    void semanticDecoyMutationPenalizesDecoyResistanceMoreThanHarmlessProbe() {
        String sessionId = "semantic-decoy-mutation";
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = buildBenchmarkCase(false);
        stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools());
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
    void caseScoreReportsDecoyResistanceWithoutChangingPassRequirement() {
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
                new UserQueryGenerator(new Random(42L)),
                withTrap,
                withTrap ? authenticate.name() : null,
                withTrap ? recovery.name() : null
        );
    }
}
