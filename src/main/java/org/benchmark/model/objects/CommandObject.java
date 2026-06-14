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
                            List<EffectObject> documentedEffects) {
    public CommandObject(String name,
                         List<OptionEntity> commandOptions,
                         String description,
                         List<EffectObject> commandEffectObjects,
                         Map<String, String> preconditions) {
        this(name, commandOptions, description, commandEffectObjects, preconditions, null);
    }

    public CommandObject {
        preconditions = preconditions == null ? Map.of() : Map.copyOf(preconditions);
    }
}
