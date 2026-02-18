package org.benchmark.model.tool;

// TODO - Idea not yet concrete, might change
// eq usage - ( VOLTAGE - ASSIGN - RESET )
public record CommandEffect(
        String variable,
        String operation,
        String valueRef
) {
}
