package org.benchmark.model.documentation;

import java.util.List;

public record CommandObject(String commandName,
                            List<OptionSpec> commandOptions,
                            String description,
                            List<CommandPreconditions> commandPreConditions,
                            List<CommandEffect> commandEffects) {
}
