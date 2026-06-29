package org.benchmark.gen.tool_generator;

import org.benchmark.model.objects.OptionEntity;

import java.util.List;
import java.util.Random;

/**
 * Generates lightweight option sets for synthetic commands.
 *
 * <p>The generator intentionally keeps options sparse so the benchmark remains focused on
 * tool discovery and state reasoning instead of dense CLI parsing.</p>
 */
final class CommandOptionGenerator {

    private final Random random;
    private final CommandDict commandDict;

    CommandOptionGenerator(Random random, CommandDict commandDict) {
        this.random = random;
        this.commandDict = commandDict;
    }

    List<OptionEntity> generateOptions() {
        if (random.nextBoolean()) {
            return List.of();
        }

        boolean required = random.nextInt(4) == 0;
        return List.of(commandDict.getRandomCommonOptionSpec(required));
    }
}
