package org.benchmark.model.objects;

import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.enums.StateScope;

import java.util.Objects;

/**
 * Immutable state effect definition for a command.
 *
 * @param scope state map to mutate
 * @param variable target state variable
 * @param operation mutation operation
 * @param valueRef reference value or literal value depending on operation
 */
public record EffectObject(
        StateScope scope,
        String variable,
        EffectOp operation,
        String valueRef
) {
    /** Special {@code valueRef} token meaning: assign selected command option. */
    public static final String OPTION_REF = "$OPTION";

    public EffectObject(String variable, EffectOp operation, String valueRef) {
        this(StateScope.TOOL, variable, operation, valueRef);
    }

    public EffectObject {
        scope = scope == null ? StateScope.TOOL : scope;
        variable = Objects.requireNonNull(variable, "variable must not be null");
        operation = Objects.requireNonNull(operation, "operation must not be null");
    }
}
