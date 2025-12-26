package org.benchmark.model.documentation;

// TODO - Idea not yet concrete, might change

public record CommandEffect(
        String variable,      // like "temperature"
        String operation,     // like "assign" or something
        String valueRef       // like "target" or some constant
) {
}
