package org.benchmark.model.documentation;

import java.util.List;

public record CommandSpec(String commandName,
                          List<ArgumentSpec> commandArgs,
                          String description,
                          List<CommandPreconditions> preConditions,
                          List<CommandEffect> commandEffects) {
}
