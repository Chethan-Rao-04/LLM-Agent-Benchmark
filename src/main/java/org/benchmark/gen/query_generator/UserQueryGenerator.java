package org.benchmark.gen.query_generator;

import java.util.Random;
import org.benchmark.model.objects.WorkflowStep;

/**
 * Generates synthetic natural-language user requests.
 */
public class UserQueryGenerator {

    private final Random random;

    private static final String[] OPENERS = {
            "Please %s %s.",
            "Can you %s %s?",
            "I need to %s %s.",
            "Could you %s %s?",
            "Help me %s %s."
    };

    /**
     * Creates a query generator using the provided random source.
     *
     * @param random shared random source for reproducibility
     */
    public UserQueryGenerator(Random random) {
        this.random = random;
    }

    /**
     * Generates one user query from action/target tokens and optional option hint.
     *
     * @param action  command action token (for example "Start")
     * @param target  command target token (for example "Router")
     * @param optionHint optional hint derived from the expected option
     * @return user-facing query sentence
     */
    public String generate(String action,
                           String target,
                           String optionHint) {
        String baseQuery = generateNatural(action, target);
        StringBuilder query = new StringBuilder(baseQuery);

        if (optionHint != null && !optionHint.isEmpty()) {
            query.append(" ").append(normalizeSentence(optionHint));
        }
        return query.toString();
    }

    /**
     * Generates a multi-step user query that encapsulates the goal of a sequence of commands.
     *
     * @param steps        the ordered sequence of workflow steps
     * @param targetTool   the target tool's name
     * @return user-facing query describing the overall goal
     */
    public String generateMultiStep(java.util.List<WorkflowStep> steps, String targetTool) {
        if (steps == null || steps.isEmpty()) {
            return generate("Execute", targetTool, null);
        }
        
        WorkflowStep finalStep = steps.get(steps.size() - 1);
        String baseQuery = generate(finalStep.commandName().split("_")[0], targetTool, null);
        
        return baseQuery + " Please ensure all necessary prerequisites and configurations are correctly set up beforehand.";
    }

    /**
     * Creates the base user request sentence from action and target tokens.
     *
     * @param action command action token
     * @param target command target token
     * @return natural-language request sentence
     */
    private String generateNatural(String action, String target) {
        String verb = normalizeVerb(action);
        String object = buildObjectPhrase(target);
        String template = pick(OPENERS);
        return String.format(template, verb, object);
    }

    /**
     * Normalizes the action token into a lower-case command verb.
     *
     * @param action action token from command name
     * @return normalized verb text
     */
    private String normalizeVerb(String action) {
        if (action == null || action.isBlank()) {
            return "execute";
        }
        return action.trim().toLowerCase();
    }

    /**
     * Produces a short object phrase with varied determiners for linguistic diversity.
     *
     * @param target command target token
     * @return object phrase used in the generated query
     */
    private String buildObjectPhrase(String target) {
        String normalizedTarget = normalizeTarget(target);
        return switch (random.nextInt(3)) {
            case 0 -> "the " + normalizedTarget;
            case 1 -> "my " + normalizedTarget;
            default -> "the virtual " + normalizedTarget;
        };
    }

    /**
     * Normalizes target token content for user-facing query text.
     *
     * @param target command target token
     * @return normalized target label
     */
    private String normalizeTarget(String target) {
        if (target == null || target.isBlank()) {
            return "resource";
        }
        return target.trim().toLowerCase();
    }

    /**
     * Ensures option hints are emitted as valid standalone sentences.
     *
     * @param text hint text
     * @return sentence-normalized hint
     */
    private String normalizeSentence(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return trimmed;
        }
        return trimmed + ".";
    }

    /**
     * Picks one random element from the provided string array.
     *
     * @param list source array
     * @return randomly selected element
     */
    private String pick(String[] list) {
        return list[random.nextInt(list.length)];
    }
}
