package org.benchmark.gen.tool_generator;

import org.benchmark.gen.catalog.OptionProfile;
import org.benchmark.gen.catalog.OptionProfileStyle;
import org.benchmark.gen.catalog.OptionValueMode;
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

    private static final String[] LETTER_SUFFIXES = {"a", "b", "c", "d"};
    private static final String[] NUMERIC_SUFFIXES = {"1", "2", "3", "4"};
    private static final String[] PROFILE_SUFFIXES = {"safe", "balanced", "rapid", "standard"};
    private static final String[] PATH_SUFFIXES = {"primary", "secondary", "east", "west"};

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

        return List.of(commandDict.getRandomCommonOptionSpec());
    }

    List<OptionEntity> generateOptions(OptionProfile optionProfile) {
        if (optionProfile == null || optionProfile.style() == OptionProfileStyle.NONE) {
            return List.of();
        }
        if (optionProfile.allowedFlags().isEmpty()) {
            return List.of();
        }

        String flagBase = optionProfile.allowedFlags().get(random.nextInt(optionProfile.allowedFlags().size()));
        String optionName = renderOptionName(flagBase, optionProfile.valueMode());
        return List.of(new OptionEntity(optionName, describeOption(flagBase, optionProfile.valueMode())));
    }

    private String renderOptionName(String flagBase, OptionValueMode valueMode) {
        return switch (valueMode) {
            case NONE -> "--" + flagBase;
            case LETTER_SUFFIX -> "--" + flagBase + "-" + LETTER_SUFFIXES[random.nextInt(LETTER_SUFFIXES.length)];
            case NUMERIC_SUFFIX -> "--" + flagBase + "-" + NUMERIC_SUFFIXES[random.nextInt(NUMERIC_SUFFIXES.length)];
            case PROFILE_SUFFIX -> "--" + flagBase + "-" + PROFILE_SUFFIXES[random.nextInt(PROFILE_SUFFIXES.length)];
            case PATH_SUFFIX -> "--" + flagBase + "-" + PATH_SUFFIXES[random.nextInt(PATH_SUFFIXES.length)];
        };
    }

    private String describeOption(String flagBase, OptionValueMode valueMode) {
        if (valueMode == OptionValueMode.NONE) {
            return "Applies the documented " + flagBase + " mode.";
        }
        return "Selects the documented " + flagBase + " path for this procedure.";
    }
}
