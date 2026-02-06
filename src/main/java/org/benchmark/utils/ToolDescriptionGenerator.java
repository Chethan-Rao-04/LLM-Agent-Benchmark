package org.benchmark.model.tool;



import java.util.Random;
//TODO: Think about how to improve this, and avoiding redudndancies, when multiple tools get some descriptions
/**
 * Creates natural language descriptions(diff complexities) for tools
 */
public class ToolDescriptionGenerator {

    private final Random random;


    // High-tech verbs
    private final String[] VERBS_COMPLEX = {"Initiates", "Dispatches", "Orchestrates", "Synchronizes", "Provision","Resets", "Updates", "Checks", "Ping", "List"};

    // Technical adjectives
    private final String[] ADJ_STATE = {"hard", "soft", "asynchronous", "blocking", "recursive", "atomic"};
    private final String[] ADJ_TARGET = {"primary", "redundant", "legacy", "upstream", "virtualized"};

    // Industr related nouns (as the action object)
    private final String[] NOUNS_ACTION = {"sequence", "protocol", "handshake", "daemon", "transaction"};

    public ToolDescriptionGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generates a description based on the ComplexityLevel.
     */
    public String generate(ToolComplexity complexity, String action, String target) {
        switch (complexity) {
            case SIMPLE:
                return generateSimple(action, target);
            case MEDIUM:
                return generateMedium(action, target);
            case COMPLEX:
            default:
                return generateComplex(action, target);
        }
    }

    // Example Output: "Resets the Router."
    private String generateSimple(String action, String target) {
        return String.format("%s the %s.", action, target);
    }

    // OExample- "Resets the virtualized Router configuration."
    private String generateMedium(String action, String target) {
        String adj = pick(ADJ_TARGET);
        return String.format("%s the %s %s configuration.", action, adj, target);
    }

    // Example: "Initiates a hard reset sequence for the primary  Router."
    private String generateComplex(String action, String target) {

        String verb = pick(VERBS_COMPLEX);


        String state = pick(ADJ_STATE);       // like, "hard"
        String actionNoun = pick(NOUNS_ACTION); // like "sequence"
        String targetAdj = pick(ADJ_TARGET);    // like "primary"

        // indea is: [some Fancy Verb] a [State] [Action Noun] for the [Target Adj] [Target].

        return String.format("%s a %s %s %s for the %s %s.",
                verb, state, action, actionNoun, targetAdj, target);
    }

    private String pick(String[] list) {
        return list[random.nextInt(list.length)];
    }
}