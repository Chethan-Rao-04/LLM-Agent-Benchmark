package org.benchmark.gen.tool_generator;
import java.util.Random;

/**
 * Generates brief natural-language descriptions for tools.
 */
public class ToolDescriptionGenerator {

    private final Random random;


    private final String[] ADJ_TARGET = {"primary", "redundant", "legacy", "upstream", "virtualized"};

    public ToolDescriptionGenerator() {
        this.random = new Random(); // can add seed for reproducibility
    }

    /**
     * Generates a tool description sentence.
     *
     * @param action primary action verb
     * @param target primary noun target
     * @return generated description text
     */
    public String generate(String action, String target) {
        return generateMedium(action, target);
    }

    // OExample- "Resets the virtualized Router configuration."
    private String generateMedium(String action, String target) {
        String adj = pick(ADJ_TARGET);
        return String.format("%s the %s %s configuration.", action, adj, target);
    }

    private String pick(String[] list) {
        return list[random.nextInt(list.length)];
    }
}
