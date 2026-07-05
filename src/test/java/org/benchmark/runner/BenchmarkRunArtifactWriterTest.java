package org.benchmark.runner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkRunArtifactWriterTest {

    private final BenchmarkRunArtifactWriter writer = new BenchmarkRunArtifactWriter();

    @Test
    void rendersMarkdownAndCsvFromCaseReports() {
        BenchmarkRunSummary summary = new BenchmarkRunSummary();
        summary.caseReports.add(new BenchmarkRunCaseReport(
                1,
                "abc",
                "MAN-VALVE-123",
                List.of("MAN-VALVE-123 -> MAN-VALVE-555"),
                true,
                false,
                2,
                4,
                0.86,
                1.0,
                1.0,
                0.75,
                1.0,
                0.80,
                0.90,
                0.75,
                List.of("diag_vlv", "rpr_vlv --retry", "vfy_vlv"),
                List.of(
                        "SUCCESS MAN-VALVE-123 diag_vlv",
                        "ERROR MAN-PUMP-555 rpr_pmp",
                        "SUCCESS MAN-VALVE-123 rpr_vlv --retry",
                        "SUCCESS MAN-VALVE-123 vfy_vlv"
                ),
                List.of("MAN-VALVE-123 diag_vlv: attempt already consumed")
        ));

        Map<String, Number> metrics = Map.ofEntries(
                Map.entry("successes", 1),
                Map.entry("autonomousRecoveries", 0),
                Map.entry("benchmarkExecutions", 4),
                Map.entry("averageComposite", 0.86),
                Map.entry("averageToolSelection", 1.0),
                Map.entry("averageStepCompletion", 1.0),
                Map.entry("averageOrdering", 0.75),
                Map.entry("averageStateAccuracy", 1.0),
                Map.entry("averageEfficiency", 0.80),
                Map.entry("averageCommandPrecision", 0.90),
                Map.entry("averageDecoyResistance", 0.75),
                Map.entry("averageTargetLikeWrongToolAvoidance", 0.75)
        );

        String markdown = writer.toMarkdown(summary, metrics, "run-123");
        String csv = writer.toCsv(summary);

        assertTrue(markdown.contains("# Benchmark Run Report"));
        assertTrue(markdown.contains("## Case 1"));
        assertTrue(markdown.contains("Session: abc"));
        assertTrue(markdown.contains("- MAN-VALVE-123 -> MAN-VALVE-555"));
        assertTrue(markdown.contains("Target-like wrong tool avoidance: 0.75"));
        assertTrue(markdown.contains("- SUCCESS MAN-VALVE-123 rpr_vlv --retry"));
        assertTrue(markdown.contains("- MAN-VALVE-123 diag_vlv: attempt already consumed"));
        assertTrue(markdown.contains("- Average target-like wrong tool avoidance: 0.75"));

        assertTrue(csv.contains("case,session,passed,score,attempts,executions,toolSelection,stepCompletion,orderingAccuracy,stateAccuracy,efficiency,commandPrecision,targetLikeWrongToolAvoidance,recovery"));
        assertTrue(csv.contains("1,abc,true,0.860,2,4,1.00,1.00,0.75,1.00,0.80,0.90,0.75,false"));
    }
}
