package org.benchmark.model.spec;

import org.benchmark.model.enums.EffectOp;

// eq usage - ( VOLTAGE - ASSIGN - RESET )
public record Effect(
        String variable,
        EffectOp operation,
        String valueRef
) {
    public static final String OPTION_REF = "$OPTION";
}
