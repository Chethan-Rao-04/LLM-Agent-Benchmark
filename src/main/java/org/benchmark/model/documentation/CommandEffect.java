package org.benchmark.model;

// TODO - See if u can implement this , otherwise temove

public record CommandEffect(
        String variable,      // e.g., "temperature"
        String operation,     // e.g., "ASSIGN"
        String valueRef       // e.g., "target" (argument name) or "50" (constant)
) {
}
