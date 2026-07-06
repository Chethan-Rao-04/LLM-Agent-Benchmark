package org.benchmark.gen.spec;

import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.tool_generator.CommandAbbreviator;

import java.util.Map;
import java.util.Objects;

/**
 * Semantic representation of one required capability in a benchmark case.
 *
 * @param intent human-readable action target used by future query and documentation generators
 * @param verb resolved action verb before proprietary command naming is applied
 * @param noun resolved action noun before proprietary command naming is applied
 * @param commandName executable command identity for the resolved action
 * @param precondition state required before the capability can be applied
 * @param effect state change produced by the capability
 */
public record CapabilityStep(
        String intent,
        String verb,
        String noun,
        String commandName,
        Map<String, String> precondition,
        Map<String, String> effect
) {
    public CapabilityStep {
        verb = Objects.requireNonNull(verb, "verb must not be null");
        noun = Objects.requireNonNull(noun, "noun must not be null");
        commandName = Objects.requireNonNull(commandName, "commandName must not be null");
        intent = intent == null || intent.isBlank()
                ? verb.replace('_', ' ') + " " + noun.replace('_', ' ')
                : intent;
        precondition = precondition == null ? Map.of() : Map.copyOf(precondition);
        effect = effect == null ? Map.of() : Map.copyOf(effect);
    }

    /**
     * Builds a semantic capability from the current resolved scenario step format.
     */
    public static CapabilityStep fromResolvedStep(ResolvedStep step) {
        Objects.requireNonNull(step, "step must not be null");
        return new CapabilityStep(
                step.verb().replace('_', ' ') + " " + step.noun().replace('_', ' '),
                step.verb(),
                step.noun(),
                CommandAbbreviator.commandName(step.verb(), step.noun()),
                step.precondition(),
                step.effect()
        );
    }
}
