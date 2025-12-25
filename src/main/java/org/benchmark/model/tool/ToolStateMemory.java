package org.benchmark.model;

//TODO - check if needed

import java.util.Map;

// This acts as the toolSpec's memory, keeps track of the toolSpec states ( experimental)
public record ToolStateMemory( Map<String, String> toolStateVariables) {
    public Map<String, String> variables() {
        return toolStateVariables;
    }
}
