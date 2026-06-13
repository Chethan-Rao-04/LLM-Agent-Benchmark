package org.benchmark.gen.tool_generator;

import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates a tool whose commands align with the steps in a resolved scenario.
 *
 * <p>All command names (scenario steps AND fillers) go through
 * {@link CommandAbbreviator} so every name in the tool is uniformly abbreviated.</p>
 */
public class ScenarioToolGenerator {

    private static final String[] WRONG_VALUE_SUFFIXES = {
            "DEGRADED", "PENDING", "PARTIAL", "UNSTABLE", "LIMITED"
    };

    private static final String[] RECOVERY_VERBS = {"reset", "stabilize", "restore", "recalibrate"};

    private final Random random;
    private final CommandDict commandDict;

    public ScenarioToolGenerator(Random random, CommandDict commandDict) {
        this.random = random;
        this.commandDict = commandDict;
    }

    /**
     * Result of tool generation, carrying the tool and optional recovery command name.
     */
    public record ToolGenerationResult(ToolObject tool, String recoveryCommandName) {}

    public ToolObject generateTool(ResolvedScenario scenario) {
        return generateTool(scenario, false).tool();
    }

    public ToolGenerationResult generateTool(ResolvedScenario scenario, boolean includeTrapCommand) {
        Map<String, String> stateVariables = buildStateSchema(scenario);
        List<CommandObject> commands = new ArrayList<>();
        Set<String> usedCommandNames = new HashSet<>();

        String corruptedVar = null;
        String correctValue = null;
        String trapNoun = null;

        // Scenario step commands
        for (int i = 0; i < scenario.steps().size(); i++) {
            ResolvedStep step = scenario.steps().get(i);
            String name = CommandAbbreviator.commandName(step.verb(), step.noun());
            usedCommandNames.add(name);

            if (includeTrapCommand && i == 0 && !step.effect().isEmpty()) {
                // Step 1 becomes the embedded trap
                Map.Entry<String, String> targetEntry = step.effect().entrySet().iterator().next();
                corruptedVar = targetEntry.getKey();
                correctValue = targetEntry.getValue();
                trapNoun = step.noun();
                commands.add(buildTrappedStepCommand(name, step, corruptedVar, correctValue));
            } else {
                commands.add(buildStepCommand(name, step));
            }
        }

        // Recovery command (if trap is active)
        String recoveryCommandName = null;
        if (includeTrapCommand && corruptedVar != null) {
            CommandObject recovery = buildRecoveryCommand(
                    trapNoun, corruptedVar, correctValue, usedCommandNames);
            if (recovery != null) {
                recoveryCommandName = recovery.name();
                // Will be inserted among fillers below
                commands.add(recovery);
            }
        }

        // Filler commands (4-6)
        int fillerCount = 4 + random.nextInt(3);
        int attempts = 0;
        int targetSize = scenario.steps().size() + fillerCount
                + (recoveryCommandName != null ? 1 : 0);
        while (commands.size() < targetSize && attempts < fillerCount * 10) {
            attempts++;
            String verb = commandDict.getRandomVerb(scenario.domain());
            String noun = commandDict.getRandomNoun(scenario.domain());
            String name = CommandAbbreviator.commandName(verb, noun);
            if (!usedCommandNames.add(name)) continue;

            List<OptionEntity> options = generateOptions();
            List<EffectObject> effects = generateFillerEffect(stateVariables);
            commands.add(new CommandObject(name, options,
                    "Executes the " + name + " operation.", effects, Map.of()));
        }

        // Shuffle the recovery command among fillers so it's not predictably placed
        if (recoveryCommandName != null) {
            shuffleRecoveryAmongFillers(commands, scenario.steps().size(), recoveryCommandName);
        }

        String toolName = commandDict.generateToolName(scenario.domain());
        String description = generateDescription(scenario, includeTrapCommand && corruptedVar != null);
        ToolObject tool = new ToolObject(toolName, description, scenario.domain(), commands, stateVariables);
        return new ToolGenerationResult(tool, recoveryCommandName);
    }

    /**
     * Builds a trapped version of a step command. The documentation shows the
     * correct effects, but the real effects assign a wrong value to the target
     * variable, causing the next step's precondition to fail.
     */
    private CommandObject buildTrappedStepCommand(String name, ResolvedStep step,
                                                   String corruptedVar, String correctValue) {
        // Documented effects: the correct ones (what the LLM expects)
        List<EffectObject> documentedEffects = new ArrayList<>();
        for (Map.Entry<String, String> entry : step.effect().entrySet()) {
            documentedEffects.add(new EffectObject(entry.getKey(), EffectOp.ASSIGN, entry.getValue()));
        }

        // Real effects: same as documented, but the corrupted variable gets a wrong value
        String wrongValue = correctValue + "_" + WRONG_VALUE_SUFFIXES[random.nextInt(WRONG_VALUE_SUFFIXES.length)];
        List<EffectObject> realEffects = new ArrayList<>();
        for (Map.Entry<String, String> entry : step.effect().entrySet()) {
            if (entry.getKey().equals(corruptedVar)) {
                realEffects.add(new EffectObject(entry.getKey(), EffectOp.ASSIGN, wrongValue));
            } else {
                realEffects.add(new EffectObject(entry.getKey(), EffectOp.ASSIGN, entry.getValue()));
            }
        }

        List<OptionEntity> options = generateOptions();
        return new CommandObject(name, options, "Executes the " + name + " operation.",
                realEffects, step.precondition(), documentedEffects);
    }

    /**
     * Builds a recovery command that fixes the corrupted state variable.
     * Has no preconditions and a subtly descriptive name/description.
     */
    private CommandObject buildRecoveryCommand(String noun, String corruptedVar,
                                               String correctValue, Set<String> usedCommandNames) {
        String recoveryVerb = RECOVERY_VERBS[random.nextInt(RECOVERY_VERBS.length)];
        String recoveryName = CommandAbbreviator.commandName(recoveryVerb, noun);
        if (!usedCommandNames.add(recoveryName)) {
            // Try another verb
            for (String verb : RECOVERY_VERBS) {
                recoveryName = CommandAbbreviator.commandName(verb, noun);
                if (usedCommandNames.add(recoveryName)) break;
            }
            if (!usedCommandNames.contains(recoveryName)) return null;
        }

        List<EffectObject> effects = List.of(
                new EffectObject(corruptedVar, EffectOp.ASSIGN, correctValue)
        );

        String[] descriptions = {
                "Executes the " + recoveryName + " operation.",
                "Applies " + noun + " parameter adjustments.",
                "Performs " + noun + " state synchronization.",
                "Runs auxiliary " + noun + " routine."
        };
        String description = descriptions[random.nextInt(descriptions.length)];

        return new CommandObject(recoveryName, generateOptions(), description,
                effects, Map.of());
    }

    /**
     * Moves the recovery command to a random position among the filler commands
     * (i.e., after all scenario step commands).
     */
    private void shuffleRecoveryAmongFillers(List<CommandObject> commands,
                                              int scenarioStepCount,
                                              String recoveryCommandName) {
        int recoveryIdx = -1;
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).name().equals(recoveryCommandName)) {
                recoveryIdx = i;
                break;
            }
        }
        if (recoveryIdx < 0 || commands.size() <= scenarioStepCount + 1) return;

        CommandObject recovery = commands.remove(recoveryIdx);
        // Insert at a random position among fillers (after scenario steps)
        int minPos = scenarioStepCount;
        int maxPos = commands.size();
        int newPos = minPos + random.nextInt(maxPos - minPos + 1);
        commands.add(newPos, recovery);
    }

    private CommandObject buildStepCommand(String abbreviatedName, ResolvedStep step) {
        List<OptionEntity> options = generateOptions();
        List<EffectObject> effects = new ArrayList<>();
        for (Map.Entry<String, String> entry : step.effect().entrySet()) {
            effects.add(new EffectObject(entry.getKey(), EffectOp.ASSIGN, entry.getValue()));
        }
        return new CommandObject(abbreviatedName, options,
                "Executes the " + abbreviatedName + " operation.", effects, step.precondition());
    }

    private Map<String, String> buildStateSchema(ResolvedScenario scenario) {
        Map<String, String> schema = new LinkedHashMap<>();
        for (ResolvedStep step : scenario.steps()) {
            step.precondition().keySet().forEach(k -> schema.put(k, "string"));
            step.effect().keySet().forEach(k -> schema.put(k, "string"));
        }
        for (int i = 0; i < 2; i++) {
            schema.putIfAbsent(commandDict.getRandomStateVariable(scenario.domain()), "int");
        }
        return schema;
    }

    private List<OptionEntity> generateOptions() {
        int count = random.nextInt(4);
        List<OptionEntity> options = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            OptionEntity opt = commandDict.getRandomCommonOptionSpec();
            if (seen.add(opt.optionName())) options.add(opt);
        }
        return options;
    }

    private List<EffectObject> generateFillerEffect(Map<String, String> stateVariables) {
        List<String> intVars = stateVariables.entrySet().stream()
                .filter(e -> "int".equals(e.getValue()))
                .map(Map.Entry::getKey).toList();
        if (!intVars.isEmpty()) {
            String var = intVars.get(random.nextInt(intVars.size()));
            return List.of(new EffectObject(var, EffectOp.INCREMENT, null));
        }
        return List.of();
    }

    private String generateDescription(ResolvedScenario scenario, boolean hasTrap) {
        return String.format("Manages %s operations for %s domain.",
                scenario.patternName().replace('_', ' '),
                scenario.domain().name().toLowerCase().replace('_', ' '));
    }
}
