package org.benchmark.gen;

import java.util.Random;

/**
 * QUERY GENERATOR: Creates natural language inputs
 */
public class UserQueryGenerator {

    private final Random random;

    // --- VOCABULARY BANKS ---

    // User Intentions (The "I want to..." part)
    private final String[] INTENT_SIMPLE = {"Please", "I want to", "Can you", "Run", "Do a"};

    public UserQueryGenerator() {
        this.random = new Random(); // can pass seed as
    }

    public String generate(String action, String target, String optionHint) {
        String baseQuery = generateMedium(action, target);
        if (optionHint != null && !optionHint.isEmpty()) {
            return baseQuery + " " + optionHint;
        }
        return baseQuery;
    }

    // Output: "I want to Reset the virtual Router."
    private String generateMedium(String action, String target) {
        String start = pick(INTENT_SIMPLE);
        // Sometimes users add "virtual" or "my" to the target
        String modifier = random.nextBoolean() ? "virtual " : "my ";
        return String.format("%s %s the %s%s.", start, action.toLowerCase(), modifier, target);
    }

    private String pick(String[] list) {
        return list[random.nextInt(list.length)];
    }
}
