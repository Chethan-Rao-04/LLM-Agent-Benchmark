package org.benchmark.model.objects;

import java.util.List;
import java.util.Map;

/**
 * Immutable command specification used by tool generation and execution.
 *
 * @param name command identifier
 * @param commandOptions supported options for this command
 * @param description short user-facing description
 * @param commandEffectObjects state mutations applied after successful execution
 * @param preconditions state entries that must hold before this command can execute
 * @param documentedEffects effects shown in documentation (null = use real effects)
 */
public record CommandObject(String name,
                            List<OptionEntity> commandOptions,
                            String description,
                            List<EffectObject> commandEffectObjects,
                            Map<String, String> preconditions,
                            List<EffectObject> documentedEffects,
                            List<StateRequirement> scopedPreconditions) {
    /**
     * Creates a command whose documentation should mirror its real effects.
     *
     * @param name command identifier
     * @param commandOptions supported options
     * @param description user-facing command description
     * @param commandEffectObjects real state mutations applied on success
     * @param preconditions state entries required before execution
     */
    public CommandObject(String name,
                         List<OptionEntity> commandOptions,
                         String description,
                         List<EffectObject> commandEffectObjects,
                         Map<String, String> preconditions) {
        this(name, commandOptions, description, commandEffectObjects, preconditions, null);
    }

    public CommandObject(String name,
                         List<OptionEntity> commandOptions,
                         String description,
                         List<EffectObject> commandEffectObjects,
                         List<StateRequirement> scopedPreconditions) {
        this(name, commandOptions, description, commandEffectObjects, null, null, scopedPreconditions);
    }

    /**
     * Normalizes optional preconditions so simulator code can treat missing maps as empty state constraints.
     */
    public CommandObject {
        scopedPreconditions = scopedPreconditions == null
                ? StateRequirement.fromToolMap(preconditions)
                : List.copyOf(scopedPreconditions);
        preconditions = StateRequirement.toolMap(scopedPreconditions);
    }

    public CommandObject(String name,
                         List<OptionEntity> commandOptions,
                         String description,
                         List<EffectObject> commandEffectObjects,
                         Map<String, String> preconditions,
                         List<EffectObject> documentedEffects) {
        this(name, commandOptions, description, commandEffectObjects,
                preconditions, documentedEffects, StateRequirement.fromToolMap(preconditions));
    }
}
