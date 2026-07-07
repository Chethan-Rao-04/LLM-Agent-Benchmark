package org.benchmark.gen.catalog;

import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.enums.StateScope;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.StateRequirement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hidden capability definition used to generate one concrete command.
 *
 * @param id stable catalog identifier, never shown to the model
 * @param role semantic stage label
 * @param verbSeeds candidate full-word verbs
 * @param nounSeeds candidate full-word nouns
 * @param preconditionTemplate legacy local state required before execution
 * @param effectTemplate legacy local state written after execution
 * @param preconditions scoped state required before execution
 * @param effects scoped state written after execution
 * @param optionProfile referenced option profile id, or blank when no profile applies
 */
public record ToolCapability(
        String id,
        String role,
        List<String> verbSeeds,
        List<String> nounSeeds,
        Map<String, String> preconditionTemplate,
        Map<String, String> effectTemplate,
        List<StateRequirement> preconditions,
        List<EffectObject> effects,
        String optionProfile
) {
    public ToolCapability(String id,
                          String role,
                          List<String> verbSeeds,
                          List<String> nounSeeds,
                          Map<String, String> preconditionTemplate,
                          Map<String, String> effectTemplate,
                          String optionProfile) {
        this(id, role, verbSeeds, nounSeeds, preconditionTemplate, effectTemplate,
                StateRequirement.fromToolMap(preconditionTemplate),
                toolEffectsFromMap(effectTemplate),
                optionProfile);
    }

    public ToolCapability {
        id = Objects.requireNonNull(id, "id must not be null");
        role = Objects.requireNonNull(role, "role must not be null");
        verbSeeds = List.copyOf(Objects.requireNonNull(verbSeeds, "verbSeeds must not be null"));
        nounSeeds = List.copyOf(Objects.requireNonNull(nounSeeds, "nounSeeds must not be null"));
        preconditions = preconditions == null
                ? StateRequirement.fromToolMap(preconditionTemplate)
                : List.copyOf(preconditions);
        effects = effects == null ? toolEffectsFromMap(effectTemplate) : List.copyOf(effects);
        preconditionTemplate = StateRequirement.toolMap(preconditions);
        effectTemplate = toolEffectMap(effects);
        optionProfile = optionProfile == null ? "" : optionProfile;
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
