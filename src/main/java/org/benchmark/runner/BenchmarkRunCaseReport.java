package org.benchmark.runner;

import java.util.List;

/**
 * Immutable case-level snapshot used to build human-readable benchmark artifacts.
 */
record BenchmarkRunCaseReport(
        int caseIndex,
        String sessionId,
        List<String> targetTools,
        List<String> targetPath,
        List<String> targetLikeWrongTools,
        List<String> expectedSharedState,
        List<String> actualSharedState,
        boolean passed,
        boolean recovery,
        int attemptsUsed,
        int executionCount,
        double compositeScore,
        double toolSelection,
        double stepCompletionRate,
        double orderingAccuracy,
        double stateAccuracy,
        double efficiency,
        double commandPrecision,
        double decoyResistance,
        List<String> executions
) {
}
