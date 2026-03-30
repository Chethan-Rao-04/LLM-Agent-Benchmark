package org.benchmark.app;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScoringTest {

    /**
     * We test the scoring logic via a standalone instance since
     * {@code scoreExpectedState} is now package-visible on {@link BenchmarkCaseExecutor}.
     */
    private final BenchmarkCaseExecutor executor = new BenchmarkCaseExecutor(null, null, null, null);

    @Test
    void emptyExpectedStateScoresPerfect() {
        double score = executor.scoreExpectedState(Map.of(), Map.of("key", "value"));
        assertEquals(1.0, score);
    }

    @Test
    void perfectMatchScoresPerfect() {
        Map<String, String> expected = Map.of("a", "1", "b", "2");
        Map<String, String> actual = Map.of("a", "1", "b", "2", "c", "3");
        assertEquals(1.0, executor.scoreExpectedState(expected, actual));
    }

    @Test
    void noMatchScoresZero() {
        Map<String, String> expected = Map.of("a", "1", "b", "2");
        Map<String, String> actual = Map.of("a", "wrong", "b", "wrong");
        assertEquals(0.0, executor.scoreExpectedState(expected, actual));
    }

    @Test
    void partialMatchScoresCorrectly() {
        Map<String, String> expected = Map.of("a", "1", "b", "2");
        Map<String, String> actual = Map.of("a", "1", "b", "wrong");
        assertEquals(0.5, executor.scoreExpectedState(expected, actual));
    }

    @Test
    void missingActualKeyScoresZero() {
        Map<String, String> expected = Map.of("a", "1");
        Map<String, String> actual = Map.of();
        assertEquals(0.0, executor.scoreExpectedState(expected, actual));
    }

    @Test
    void threeOfFourMatchScores075() {
        Map<String, String> expected = Map.of("a", "1", "b", "2", "c", "3", "d", "4");
        Map<String, String> actual = Map.of("a", "1", "b", "2", "c", "3", "d", "wrong");
        assertEquals(0.75, executor.scoreExpectedState(expected, actual));
    }

    @Test
    void benchmarkScoreCompositeFormulaIncludesPrerequisiteHandling() {
        BenchmarkCaseExecutor.BenchmarkScore score = new BenchmarkCaseExecutor.BenchmarkScore(
                1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 1.0, 1.0, false, true
        );
        assertEquals(1.0, score.composite());

        BenchmarkCaseExecutor.BenchmarkScore scoreWithoutPrereq = new BenchmarkCaseExecutor.BenchmarkScore(
                1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0, false, false
        );
        assertEquals(6.0 / 7.0, scoreWithoutPrereq.composite());
    }
}
