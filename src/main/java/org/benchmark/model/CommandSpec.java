package org.benchmark.model;

import java.util.List;

public record CommandSpec(String commandName,
                          List<OptionSpec> commandArgs,
                          String description,
                          List<CommandEffect> commandEffects) {
}
