package org.benchmark.gen.scenario;

import java.util.Map;

/**
 * A scenario step after all template variables have been substituted with concrete values.
 *
 * <p>{@code verb} and {@code noun} are always full, human-readable words
 * (used for NL query generation). {@code commandName} is {@code verb + "_" + noun}
 * and represents the logical step identity used for scoring.
 * The actual abbreviated command name that appears in {@code CommandObject}
 * is produced by {@code CommandAbbreviator}.</p>
 *
 * @param verb resolved action verb (full word)
 * @param noun resolved target noun (full word)
 * @param commandName logical identity for scoring, built from verb and noun
 * @param precondition resolved state preconditions, or empty
 * @param effect resolved state mutations, or empty
 */
public record ResolvedStep(
        String verb,
        String noun,
        String commandName,
        Map<String, String> precondition,
        Map<String, String> effect
) {
    /**
     * Normalizes a resolved step so scoring and tool generation see immutable state maps
     * and a stable logical command identity.
     */
    public ResolvedStep {
        commandName = verb + "_" + noun;
        precondition = precondition == null ? Map.of() : Map.copyOf(precondition);
        effect = effect == null ? Map.of() : Map.copyOf(effect);
    }

    /**
     * Creates a resolved step while deriving the logical command name from the full verb and noun.
     *
     * @param verb resolved action verb
     * @param noun resolved target noun
     * @param precondition resolved state preconditions
     * @param effect resolved state mutations
     */
    public ResolvedStep(String verb, String noun, Map<String, String> precondition, Map<String, String> effect) {
        this(verb, noun, verb + "_" + noun, precondition, effect);
    }
}
