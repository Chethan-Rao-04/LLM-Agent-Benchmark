package org.benchmark.app;

import org.benchmark.exec.SessionStateManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchmarkScorerTest {

    private final BenchmarkScorer scorer = new BenchmarkScorer(new SessionStateManager());

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
                1.0, 1.0, 1.0, 1.0, 1.0, 1.0, true, true
        );
        assertEquals(1.0, perfect.composite());

        BenchmarkScorer.BenchmarkScore oneMiss = new BenchmarkScorer.BenchmarkScore(
                1.0, 1.0, 1.0, 1.0, 0.0, 1.0, false, false
        );
        assertEquals(5.0 / 6.0, oneMiss.composite(), 1e-9);
    }
}
