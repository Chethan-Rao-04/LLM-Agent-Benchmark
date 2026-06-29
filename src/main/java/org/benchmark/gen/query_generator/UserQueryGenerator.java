package org.benchmark.gen.query_generator;

import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.spec.BenchmarkCaseSpec;
import org.benchmark.gen.spec.CapabilityStep;

import java.util.List;
import java.util.Random;

/**
 * Generates goal-oriented user queries that describe only the desired OUTCOME.
 *
 * <p>The query describes the user intent without exposing proprietary command
 * names or the full ordered answer path.</p>
 */
public class UserQueryGenerator {

    private static final String[] GOAL_TEMPLATES = {
            "Bring the %s to a %s state using the available maintenance tools.",
            "The %s needs to end in a %s state. Use the documented tools to complete the work.",
            "Resolve the %s workflow so the final service state is %s.",
            "Use the available tools to move the %s from its current state to %s."
    };

    private final Random random;

    /**
     * Creates the user query generator.
     *
     * @param random shared random source used to vary user-facing phrasing
     */
    public UserQueryGenerator(Random random) {
        this.random = random;
    }

    /**
     * Generates a query that describes only the end goal. This avoids leaking
     * intermediate steps into the model prompt.
     */
    public String generateGoalQuery(ResolvedScenario scenario) {
        return generateGoalQuery(BenchmarkCaseSpec.fromScenario(scenario, 0, 0));
    }

    /**
     * Generates a user request from the semantic case specification instead of
     * command-shaped scenario vocabulary.
     */
    public String generateGoalQuery(BenchmarkCaseSpec spec) {
        List<CapabilityStep> steps = spec.capabilitySteps();
        CapabilityStep lastStep = steps.get(steps.size() - 1);
        String target = readableTarget(lastStep);
        String finalState = readableFinalState(spec, lastStep);
        return String.format(pick(GOAL_TEMPLATES), target, finalState);
    }

    private String readableTarget(CapabilityStep lastStep) {
        return lastStep.noun().replace('_', ' ');
    }

    private String readableFinalState(BenchmarkCaseSpec spec, CapabilityStep lastStep) {
        if (!lastStep.effect().isEmpty()) {
            return lastStep.effect().values().iterator().next().replace('_', ' ');
        }
        if (!spec.expectedFinalState().isEmpty()) {
            return spec.expectedFinalState().values().iterator().next().replace('_', ' ');
        }
        return "complete";
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }
}
