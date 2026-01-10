package org.benchmark.model.tool;

import org.benchmark.model.documentation.CommandSpec;

import java.util.List;

public record ToolSpecification(String name,
                                String description,
                                Domain domain,
                                ToolComplexity complexity,
                                List<CommandSpec> commands,
                                ToolStateMemory toolState

) {
}
