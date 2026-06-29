package org.benchmark.runner;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable accumulator for benchmark-wide summary totals.
 *
 * <p>The runner updates this object sequentially as each case completes, then the
 * publisher turns the totals into averages and final output.</p>
 */
final class BenchmarkRunSummary {
    int totalSuccess;
    int autonomousRecoveries;
    int totalExecutions;
    double totalComposite;
    double totalToolSelection;
    double totalStepCompletion;
    double totalOrdering;
    double totalStateAccuracy;
    double totalEfficiency;
    double totalCommandPrecision;
    double totalDecoyResistance;
    final List<BenchmarkRunCaseReport> caseReports = new ArrayList<>();
}
