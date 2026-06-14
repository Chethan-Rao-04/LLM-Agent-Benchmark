package org.benchmark.model.objects;

import org.benchmark.model.enums.Domain;
import java.util.List;
import java.util.Map;

/**
 * Immutable tool specification containing commands and state schema.
 *
 * @param name unique tool name
 * @param description natural-language tool description
 * @param domain tool domain
 * @param commands commands supported by this tool
 * @param stateVariables state variable schema (name → type)
 */
public record ToolObject(String name,
                         String description,
                         Domain domain,
                         List<CommandObject> commands,
                         Map<String, String> stateVariables
) {
}
