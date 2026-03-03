package org.benchmark.model.spec;

import org.benchmark.model.enums.Domain;
import java.util.List;

public record ToolSpec(String name,
                       String description,
                       Domain domain,
                       List<CommandSpec> commands,
                       ToolState toolState

) {
}
