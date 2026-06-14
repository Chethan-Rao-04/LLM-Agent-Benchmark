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
 * @param commandName {@code verb + "_" + noun} — logical identity for scoring
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
    public ResolvedStep {
        commandName = verb + "_" + noun;
        precondition = precondition == null ? Map.of() : Map.copyOf(precondition);
        effect = effect == null ? Map.of() : Map.copyOf(effect);
    }

    public ResolvedStep(String verb, String noun, Map<String, String> precondition, Map<String, String> effect) {
        this(verb, noun, verb + "_" + noun, precondition, effect);
    }
}
