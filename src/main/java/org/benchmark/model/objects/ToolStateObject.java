package org.benchmark.model.objects;

import java.util.Map;

/**
 * Immutable tool state schema.
 *
 * @param toolStateVariables state variable definitions (name -> type/value kind)
 */
public record ToolStateObject(Map<String, String> toolStateVariables) {
    /**
     * Convenience alias for {@link #toolStateVariables()}.
     *
     * @return state variable map
     */
    public Map<String, String> variables() {
        return toolStateVariables;
    }
}
