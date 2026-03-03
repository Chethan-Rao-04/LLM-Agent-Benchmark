package org.benchmark.model.spec;

import java.util.List;

public record CommandSpec(String commandName,
                            List<OptionSpec> commandOptions,
                            String description,
                            List<Precondition> commandPreConditions,
                            List<Effect> commandEffects) {
}
