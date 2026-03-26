package org.benchmark.model.objects;

import org.benchmark.model.enums.Domain;
import java.util.List;

/**
 * Immutable tool specification containing commands and state schema.
 *
 * @param name unique tool name
 * @param description natural-language tool description
 * @param domain tool domain
 * @param commands commands supported by this tool
 * @param toolStateObject state schema associated with this tool
 */
public record ToolObject(String name,
                         String description,
                         Domain domain,
                         List<CommandObject> commands,
                         ToolStateObject toolStateObject

) {
}
