package com.thesis.benchmark.generator;

import com.thesis.benchmark.model.ComplexityLevel;
import org.benchmark.model.tool.ToolComplexity;

import java.util.Random;

/**
 * QUERY GENERATOR: Creates simulated user inputs/prompts.
 * These are the "questions" that an AI must map to the tool descriptions.
 */
public class QueryGenerator {

    private final Random random;

    // --- VOCABULARY BANKS ---

    // User Intentions (The "I want to..." part)
    private final String[] INTENT_SIMPLE = {"Please", "I want to", "Can you", "Run", "Do a"};
    private final String[] INTENT_COMPLEX = {"Execute", "Trigger", "Command", "Launch", "Initialize"};

    // User-side synonyms for technical terms (Simulates vague user requests)
    private final String[] NOUNS_VAGUE = {"thing", "box", "unit", "system", "setup"};

    // Enterprise Nouns (Matching the Description Generator's jargon)
    private final String[] NOUNS_ENTERPRISE = {"protocol", "sequence", "operation", "procedure"};

    public QueryGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generates a user query.
     * @param complexity The complexity of the USER'S language (not necessarily the tool's).
     * @param action The core action (e.g., "Reset")
     * @param target The core target (e.g., "Router")
     */
    public String generate(ToolComplexity complexity, String action, String target) {
        switch (complexity) {
            case SIMPLE:
                return generateSimple(action, target);
            case MODERATE:
                return generateMedium(action, target);
            case COMPLEX:
            default:
                return generateComplex(action, target);
        }
    }

    // Output: "Reset the Router."
    private String generateSimple(String action, String target) {
        // Simple imperative command
        return String.format("%s the %s.", action, target);
    }

    // Output: "I want to Reset the virtual Router."
    private String generateMedium(String action, String target) {
        String start = pick(INTENT_SIMPLE);
        // Sometimes users add "virtual" or "my" to the target
        String modifier = random.nextBoolean() ? "virtual " : "my ";
        return String.format("%s %s the %s%s.", start, action.toLowerCase(), modifier, target);
    }

    // Output: "Execute the hard Reset protocol for the primary Router."
    private String generateComplex(String action, String target) {
        String start = pick(INTENT_COMPLEX);
        String enterpriseNoun = pick(NOUNS_ENTERPRISE);

        // Complex queries often mirror the description's jargon
        // e.g., "Execute the [Action] [EnterpriseNoun]"
        return String.format("%s the %s %s for the %s.",
                start, action, enterpriseNoun, target);
    }

    private String pick(String[] list) {
        return list[random.nextInt(list.length)];
    }
}