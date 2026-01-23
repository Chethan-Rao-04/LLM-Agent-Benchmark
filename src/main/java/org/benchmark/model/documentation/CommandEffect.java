package org.benchmark.model.documentation;

// TODO - Idea not yet concrete, might change
// eq usage - ( VOLTAGE - ASSIGN - RESET )
public record CommandEffect(
        String variable,
        String operation,
        String valueRef
) {
}
