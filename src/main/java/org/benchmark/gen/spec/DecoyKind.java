package org.benchmark.gen.spec;

/**
 * Semantic categories for tools that are plausible but should not satisfy the case goal.
 */
public enum DecoyKind {
    SIMILAR_INTENT_WRONG_RESOURCE,
    SIMILAR_COMMANDS_WRONG_STATE_PATH,
    SAME_DOMAIN_WRONG_LIFECYCLE,
    VALID_TOOL_IRRELEVANT_GOAL,
    RANDOM_DISTRACTOR
}
