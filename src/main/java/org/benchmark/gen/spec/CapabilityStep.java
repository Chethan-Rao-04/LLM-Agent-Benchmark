package org.benchmark.gen.spec;

import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.enums.StateScope;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.StateRequirement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Semantic representation of one required capability in a benchmark case.
 *
 * @param intent human-readable action target used by future query and documentation generators
 * @param toolId hidden abstract tool id that owns this capability, or blank for legacy single-tool cases
 * @param verb resolved action verb before proprietary command naming is applied
 * @param noun resolved action noun before proprietary command naming is applied
 * @param commandName executable command identity for the resolved action
 * @param precondition state required before the capability can be applied
 * @param effect state change produced by the capability
 * @param scopedPreconditions scoped state required before the capability can be applied
 * @param scopedEffects scoped state changes produced by the capability
 * @param optionProfile catalog option profile id, or blank when no profile applies
 */
public record CapabilityStep(
        String intent,
        String toolId,
        String verb,
        String noun,
        String commandName,
        Map<String, String> precondition,
        Map<String, String> effect,
        List<StateRequirement> scopedPreconditions,
        List<EffectObject> scopedEffects,
        String optionProfile
) {
    public CapabilityStep(String intent,
                          String verb,
                          String noun,
                          String commandName,
                          Map<String, String> precondition,
                          Map<String, String> effect) {
        this(intent, "", verb, noun, commandName, precondition, effect, "");
    }

    public CapabilityStep(String intent,
                          String toolId,
                          String verb,
                          String noun,
                          String commandName,
                          Map<String, String> precondition,
                          Map<String, String> effect) {
        this(intent, toolId, verb, noun, commandName, precondition, effect, "");
    }

    public CapabilityStep(String intent,
                          String toolId,
                          String verb,
                          String noun,
                          String commandName,
                          List<StateRequirement> scopedPreconditions,
                          List<EffectObject> scopedEffects,
                          String optionProfile) {
        this(intent, toolId, verb, noun, commandName,
                StateRequirement.toolMap(scopedPreconditions),
                toolEffectMap(scopedEffects),
                scopedPreconditions,
                scopedEffects,
                optionProfile);
    }

    public CapabilityStep(String intent,
                          String toolId,
                          String verb,
                          String noun,
                          String commandName,
                          Map<String, String> precondition,
                          Map<String, String> effect,
                          String optionProfile) {
        this(intent, toolId, verb, noun, commandName, precondition, effect,
                StateRequirement.fromToolMap(precondition),
                toolEffectsFromMap(effect),
                optionProfile);
    }

    public CapabilityStep {
        toolId = toolId == null ? "" : toolId;
        verb = Objects.requireNonNull(verb, "verb must not be null");
        noun = Objects.requireNonNull(noun, "noun must not be null");
        commandName = Objects.requireNonNull(commandName, "commandName must not be null");
        intent = intent == null || intent.isBlank()
                ? verb.replace('_', ' ') + " " + noun.replace('_', ' ')
                : intent;
        scopedPreconditions = scopedPreconditions == null
                ? StateRequirement.fromToolMap(precondition)
                : List.copyOf(scopedPreconditions);
        scopedEffects = scopedEffects == null ? toolEffectsFromMap(effect) : List.copyOf(scopedEffects);
        precondition = StateRequirement.toolMap(scopedPreconditions);
        effect = toolEffectMap(scopedEffects);
        optionProfile = optionProfile == null ? "" : optionProfile;
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

    private static List<EffectObject> toolEffectsFromMap(Map<String, String> effects) {
        if (effects == null || effects.isEmpty()) {
            return List.of();
        }
        return effects.entrySet().stream()
                .map(entry -> new EffectObject(StateScope.TOOL, entry.getKey(), EffectOp.ASSIGN, entry.getValue()))
                .toList();
    }

    private static Map<String, String> toolEffectMap(List<EffectObject> effects) {
        if (effects == null || effects.isEmpty()) {
            return Map.of();
        }
        Map<String, String> toolEffects = new LinkedHashMap<>();
        for (EffectObject effect : effects) {
            if (effect.scope() == StateScope.TOOL && effect.operation() == EffectOp.ASSIGN) {
                toolEffects.put(effect.variable(), effect.valueRef());
            }
        }
        return Map.copyOf(toolEffects);
    }
}
