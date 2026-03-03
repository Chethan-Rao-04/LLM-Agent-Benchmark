package org.benchmark.model.spec;

import java.util.Map;

// This acts as the tool's memory, keeps track of the tool states
public record ToolState( Map<String, String> toolStateVariables) {
    public Map<String, String> variables() {
        return toolStateVariables;
    }
}
