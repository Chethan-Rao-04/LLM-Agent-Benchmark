package org.benchmark.model.objects;

import org.benchmark.model.enums.EffectOp;

/**
 * Immutable state effect definition for a command.
 *
 * @param variable target state variable
 * @param operation mutation operation
 * @param valueRef reference value or literal value depending on operation
 */
public record EffectObject(
        String variable,
        EffectOp operation,
        String valueRef
) {
    /** Special {@code valueRef} token meaning: assign selected command option. */
    public static final String OPTION_REF = "$OPTION";
}
