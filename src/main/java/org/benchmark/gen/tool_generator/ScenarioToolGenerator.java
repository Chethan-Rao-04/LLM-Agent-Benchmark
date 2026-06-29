package org.benchmark.gen.tool_generator;

import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.spec.BenchmarkCaseSpec;
import org.benchmark.gen.spec.CapabilityStep;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;

import java.util.ArrayList;
import java.util.Collections;
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

    private final Random random;
    private final CommandDict commandDict;
    private final CommandOptionGenerator optionGenerator;

    /**
     * Creates the generator that turns resolved scenarios into executable benchmark tools.
     *
     * @param random shared random source
     * @param commandDict domain vocabulary dictionary
     */
    public ScenarioToolGenerator(Random random, CommandDict commandDict) {
        this.random = random;
        this.commandDict = commandDict;
        this.optionGenerator = new CommandOptionGenerator(random, commandDict);
    }

    /**
     * Result of tool generation, carrying the tool and optional recovery command name.
     */
    public record ToolGenerationResult(ToolObject tool, String trapCommandName, String recoveryCommandName) {}


    /**
     * Generates the scenario-aligned target tool, optionally injecting a trap and recovery command.
     *
     * @param scenario resolved benchmark scenario
     * @param includeTrapCommand whether this generated tool should contain a misleading trap command
     * @return tool specification plus optional trap and recovery command names
     */
    public ToolGenerationResult generateTool(ResolvedScenario scenario, boolean includeTrapCommand) {
        return generateTool(BenchmarkCaseSpec.fromScenario(scenario, 0, 0), includeTrapCommand);
    }

    /**
     * Generates the target tool from the semantic benchmark case specification.
     */
    public ToolGenerationResult generateTool(BenchmarkCaseSpec spec, boolean includeTrapCommand) {
        Map<String, String> stateVariables = buildStateSchema(spec);
        List<CommandObject> commands = new ArrayList<>();
        Set<String> usedCommandNames = new HashSet<>();
        String trapCommandName = null;
        String recoveryCommandName = null;

        List<Integer> effectfulStepIndexes = new ArrayList<>();
        for (int i = 0; i < spec.capabilitySteps().size(); i++) {
            if (!spec.capabilitySteps().get(i).effect().isEmpty()) {
                effectfulStepIndexes.add(i);
            }
        }

        Integer trappedStepIndex = null;
        String corruptedVar = null;
        String correctValue = null;
        String wrongValue = null;
        if (includeTrapCommand && !effectfulStepIndexes.isEmpty()) {
            Collections.shuffle(effectfulStepIndexes, random);
            trappedStepIndex = effectfulStepIndexes.get(0);

            CapabilityStep trappedStep = spec.capabilitySteps().get(trappedStepIndex);
            Map.Entry<String, String> targetEntry = trappedStep.effect().entrySet().iterator().next();
            corruptedVar = targetEntry.getKey();
            correctValue = targetEntry.getValue();
            wrongValue = wrongValueFor(correctValue);
        }

        // Scenario step commands
        for (int i = 0; i < spec.capabilitySteps().size(); i++) {
            CapabilityStep step = spec.capabilitySteps().get(i);
            String name = CommandAbbreviator.commandName(step.verb(), step.noun());
            usedCommandNames.add(name);

            if (trappedStepIndex != null && i == trappedStepIndex) {
                commands.add(buildTrappedStepCommand(name, step, corruptedVar, wrongValue));
                trapCommandName = name;
            } else {
                commands.add(buildStepCommand(name, step));
            }
        }

        if (trapCommandName != null) {
            CommandObject recovery = buildRecoveryCommand(
                    spec, corruptedVar, wrongValue, correctValue, usedCommandNames);
            if (recovery == null) {
                trapCommandName = null;
                commands.clear();
                usedCommandNames.clear();
                for (CapabilityStep step : spec.capabilitySteps()) {
                    String name = CommandAbbreviator.commandName(step.verb(), step.noun());
                    usedCommandNames.add(name);
                    commands.add(buildStepCommand(name, step));
                }
            } else {
                recoveryCommandName = recovery.name();
                commands.add(recovery);
            }
        }

        // Filler commands (4-6)
        int fillerCount = 4 + random.nextInt(3);
        int attempts = 0;
        int targetSize = spec.capabilitySteps().size() + fillerCount
                + (recoveryCommandName != null ? 1 : 0);
        while (commands.size() < targetSize && attempts < fillerCount * 10) {
            attempts++;
            String verb = commandDict.getRandomVerb(spec.domain());
            String noun = commandDict.getRandomNoun(spec.domain());
            String name = CommandAbbreviator.commandName(verb, noun);
            if (!usedCommandNames.add(name)) continue;

            List<OptionEntity> options = optionGenerator.generateOptions();
            List<EffectObject> effects = generateFillerEffect(stateVariables);
            commands.add(new CommandObject(name, options,
                    "Executes the " + name + " operation.", effects, Map.of()));
        }

        if (recoveryCommandName != null) {
            int recoveryIndex = -1;
            for (int i = 0; i < commands.size(); i++) {
                if (commands.get(i).name().equals(recoveryCommandName)) {
                    recoveryIndex = i;
                    break;
                }
            }
            if (recoveryIndex >= 0 && commands.size() > spec.capabilitySteps().size() + 1) {
                CommandObject recovery = commands.remove(recoveryIndex);
                int insertIndex = spec.capabilitySteps().size()
                        + random.nextInt(commands.size() - spec.capabilitySteps().size() + 1);
                commands.add(insertIndex, recovery);
            }
        }

        String toolName = commandDict.generateToolName(spec.domain());
        String description = generateDescription(spec);
        ToolObject tool = new ToolObject(toolName, description, spec.domain(), commands, stateVariables);
        return new ToolGenerationResult(tool, trapCommandName, recoveryCommandName);
    }

    /**
     * Builds a trapped version of a step command. The documentation shows the
     * correct effects, but the real effects assign a wrong value to the target
     * variable, causing the next step's precondition to fail.
     */
    private CommandObject buildTrappedStepCommand(String name, CapabilityStep step,
                                                  String corruptedVar, String wrongValue) {
        // Documented effects: the correct ones (what the LLM expects)
        List<EffectObject> documentedEffects = new ArrayList<>();
        for (Map.Entry<String, String> entry : step.effect().entrySet()) {
            documentedEffects.add(new EffectObject(entry.getKey(), EffectOp.ASSIGN, entry.getValue()));
        }

        // Real effects: same as documented, but the corrupted variable gets a wrong value
        List<EffectObject> realEffects = new ArrayList<>();
        for (Map.Entry<String, String> entry : step.effect().entrySet()) {
            if (entry.getKey().equals(corruptedVar)) {
                realEffects.add(new EffectObject(entry.getKey(), EffectOp.ASSIGN, wrongValue));
            } else {
                realEffects.add(new EffectObject(entry.getKey(), EffectOp.ASSIGN, entry.getValue()));
            }
        }

        List<OptionEntity> options = optionGenerator.generateOptions();
        return new CommandObject(name, options, commandDescription(step),
                realEffects, step.precondition(), documentedEffects);
    }

    /**
     * Builds a neutral-looking command that restores the corrupted state only
     * when the bad value is actually present.
     */
    private CommandObject buildRecoveryCommand(BenchmarkCaseSpec spec,
                                               String corruptedVar,
                                               String wrongValue,
                                               String correctValue,
                                               Set<String> usedCommandNames) {
        String recoveryName = null;
        for (int attempts = 0; attempts < 20; attempts++) {
            String candidate = CommandAbbreviator.commandName(
                    commandDict.getRandomVerb(spec.domain()),
                    commandDict.getRandomNoun(spec.domain()));
            if (usedCommandNames.add(candidate)) {
                recoveryName = candidate;
                break;
            }
        }
        if (recoveryName == null) {
            return null;
        }

        List<EffectObject> effects = List.of(
                new EffectObject(corruptedVar, EffectOp.ASSIGN, correctValue)
        );
        return new CommandObject(recoveryName, optionGenerator.generateOptions(), "Executes the " + recoveryName + " operation.",
                effects, Map.of(corruptedVar, wrongValue));
    }

    private String wrongValueFor(String correctValue) {
        return correctValue + "_" + WRONG_VALUE_SUFFIXES[random.nextInt(WRONG_VALUE_SUFFIXES.length)];
    }

    private CommandObject buildStepCommand(String abbreviatedName, CapabilityStep step) {
        List<OptionEntity> options = optionGenerator.generateOptions();
        List<EffectObject> effects = new ArrayList<>();
        for (Map.Entry<String, String> entry : step.effect().entrySet()) {
            effects.add(new EffectObject(entry.getKey(), EffectOp.ASSIGN, entry.getValue()));
        }
        return new CommandObject(abbreviatedName, options, commandDescription(step), effects, step.precondition());
    }

    private Map<String, String> buildStateSchema(BenchmarkCaseSpec spec) {
        Map<String, String> schema = new LinkedHashMap<>();
        for (CapabilityStep step : spec.capabilitySteps()) {
            step.precondition().keySet().forEach(k -> schema.put(k, "string"));
            step.effect().keySet().forEach(k -> schema.put(k, "string"));
        }
        for (int i = 0; i < 2; i++) {
            schema.putIfAbsent(commandDict.getRandomStateVariable(spec.domain()), "int");
        }
        return schema;
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

    private String generateDescription(BenchmarkCaseSpec spec) {
        return String.format("Manages %s operations for %s domain.",
                spec.intentDescription().toLowerCase().replace('_', ' '),
                spec.domain().name().toLowerCase().replace('_', ' '));
    }

    private String commandDescription(CapabilityStep step) {
        String target = step.noun().replace('_', ' ');
        if (step.precondition().isEmpty()) {
            return "Prepares " + target + " for the next documented workflow state.";
        }
        if (step.effect().isEmpty()) {
            return "Checks the current " + target + " workflow state without changing target state.";
        }
        String stateValue = step.effect().values().iterator().next().replace('_', ' ');
        return "Moves " + target + " toward the documented " + stateValue + " workflow state.";
    }
}
