package org.benchmark.gen.query_generator;

import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;

import java.util.List;
import java.util.Random;

/**
 * Generates goal-oriented user queries that describe only the desired OUTCOME.
 *
 * <p>The query mentions the final action and target noun — nothing about
 * intermediate steps, capabilities, or systems the LLM must figure out
 * by reading documentation.</p>
 */
public class UserQueryGenerator {

    private static final String[] GOAL_TEMPLATES = {
            "I need to %s the %s.",
            "Please %s the %s.",
            "Help me %s the %s.",
            "Can you %s the %s?",
    };

    private final Random random;

    public UserQueryGenerator(Random random) {
        this.random = random;
    }

    /**
     * Generates a query that describes only the end goal — the final step's
     * action and target — giving away nothing about intermediate steps.
     */
    public String generateGoalQuery(ResolvedScenario scenario) {
        List<ResolvedStep> steps = scenario.steps();
        ResolvedStep lastStep = steps.get(steps.size() - 1);
        String verb = lastStep.verb().replace('_', ' ');
        String noun = lastStep.noun().replace('_', ' ');
        return String.format(pick(GOAL_TEMPLATES), verb, noun);
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }
}
