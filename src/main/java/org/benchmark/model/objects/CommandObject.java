package org.benchmark.model.objects;

import java.util.List;

/**
 * Immutable command specification used by tool generation and execution.
 *
 * @param name command identifier
 * @param commandOptions supported options for this command
 * @param description natural-language command description
 * @param commandPreConditions required state checks before execution
 * @param commandEffectObjects state mutations applied after successful execution
 */
public record CommandObject(String name,
                            List<OptionEntity> commandOptions,
                            String description,
                            List<PreconditionObject> commandPreConditions,
                            List<EffectObject> commandEffectObjects) {
}
