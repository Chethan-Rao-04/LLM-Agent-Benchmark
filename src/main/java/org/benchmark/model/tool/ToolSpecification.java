package org.benchmark.model.tool;

import java.util.List;

public record ToolSpecification(String name,
                                String description,
                                Domain domain,
                                ToolComplexity complexity,
                                List<CommandObject> commands,
                                ToolStateMemory toolState

) {
}
