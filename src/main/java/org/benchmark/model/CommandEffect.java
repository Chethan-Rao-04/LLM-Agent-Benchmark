package org.benchmark.model;

public record CommandEffect(
        String variable,      // e.g., "temperature"
        String operation,     // e.g., "ASSIGN"
        String valueRef       // e.g., "target" (argument name) or "50" (constant)
) {
}
