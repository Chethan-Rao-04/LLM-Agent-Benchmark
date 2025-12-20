package org.benchmark.model;

import java.util.List;

public record ToolSpecification(String name,
                                String domain,
                                ToolComplexity complexity,
                                List<CommandSpec>  command,
                                ToolStateMemory toolState

) {
}
