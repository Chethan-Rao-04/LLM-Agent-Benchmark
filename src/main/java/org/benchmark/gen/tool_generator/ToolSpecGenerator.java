package org.benchmark.gen.tool_generator;

import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates one synthetic tool specification for the benchmark.
 *
 * <p>Distractor tools stay simple: each tool has a small state
 * schema, a set of commands, at most one runtime-selectable option, and direct
 * state effects.</p>
 */
public class ToolSpecGenerator {

    private static final String[] DESC_ADJECTIVES = {"primary", "redundant", "legacy", "upstream", "virtualized"};
    private static final String[] STRING_EFFECT_VALUES = {"ready", "paused", "completed", "queued", "stable", "synced"};

    private final Random random;
    private final CommandDict commandDict;
    private final CommandOptionGenerator optionGenerator;

    /**
     * Creates a tool generator that uses the shared random source.
     *
     * @param random shared random source for reproducibility
     */
    public ToolSpecGenerator(Random random) {
        this.random = random;
        this.commandDict = new CommandDict(random);
        this.optionGenerator = new CommandOptionGenerator(random, commandDict);
    }

    /**
     * Generates one synthetic tool for the given domain.
     *
     * @param domain source domain
     * @return generated tool specification
     */
    public ToolObject generateTool(Domain domain) {
        Map<String, String> stateVariables = generateStateVariables(domain);
        List<CommandObject> commands = generateCommands(stateVariables, domain);
        String toolName = commandDict.generateToolName(domain);
        String description = generateDescription(commandDict.getRandomVerb(domain), commandDict.getRandomNoun(domain));
        return new ToolObject(toolName, description, domain, commands, stateVariables);
    }

    private String generateDescription(String action, String target) {
        String adjective = DESC_ADJECTIVES[random.nextInt(DESC_ADJECTIVES.length)];
        return String.format("%s the %s %s configuration.", action, adjective, target);
    }

    /**
     * Builds a small schema of domain-specific state variables.
     *
     * <p>The generator keeps both numeric and text fields so command effects can
     * update different kinds of state while still staying easy to reason about.</p>
     */
    private Map<String, String> generateStateVariables(Domain domain) {
        Map<String, String> variables = new LinkedHashMap<>();
        Set<String> usedNames = new HashSet<>();

        addUniqueVariables(variables, usedNames, domain, 2 + random.nextInt(3), "int");
        addUniqueVariables(variables, usedNames, domain, 1 + random.nextInt(2), "string");
        return variables;
    }

    private void addUniqueVariables(Map<String, String> variables,
                                    Set<String> usedNames,
                                    Domain domain,
                                    int count,
                                    String type) {
        int attempts = 0;
        while (count > 0 && attempts < 50) {
            attempts++;
            String candidate = commandDict.getRandomStateVariable(domain);
            if (usedNames.add(candidate)) {
                variables.put(candidate, type);
                count--;
            }
        }
    }

    /**
     * Generates executable commands for a tool.
     *
     * <p>Each command gets a unique name, at most one selectable option, and a direct effect on
     * the tool state. Random distractors do not require setup chains.</p>
     */
    private List<CommandObject> generateCommands(Map<String, String> stateVariables, Domain domain) {
        List<CommandObject> commands = new ArrayList<>();
        List<String> numericVariables = filterVariablesByType(stateVariables, "int");
        List<String> stringVariables = filterVariablesByType(stateVariables, "string");
        Set<String> usedCommandNames = new HashSet<>();

        int targetCommandCount = 7 + random.nextInt(3);
        int attempts = 0;
        while (commands.size() < targetCommandCount && attempts < targetCommandCount * 10) {
            attempts++;

            String verb = commandDict.getRandomVerb(domain);
            String noun = commandDict.getRandomNoun(domain);
            String commandName = CommandAbbreviator.commandName(verb, noun);
            if (!usedCommandNames.add(commandName)) {
                continue;
            }

            List<OptionEntity> commandOptions = optionGenerator.generateOptions();
            List<EffectObject> effects = generateEffects(numericVariables, stringVariables);
            String description = "Executes the " + commandName + " operation.";
            commands.add(new CommandObject(commandName, commandOptions, description, effects, Map.of()));
        }

        return commands;
    }

    private List<String> filterVariablesByType(Map<String, String> stateVariables, String type) {
        return stateVariables.entrySet().stream()
                .filter(entry -> type.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Builds one small effect list for a command.
     *
     * <p>The generator prefers to give each command one direct effect so state-based
     * scoring remains meaningful without introducing planning logic.</p>
     */
    private List<EffectObject> generateEffects(List<String> numericVariables,
                                               List<String> stringVariables) {
        if (numericVariables.isEmpty() && stringVariables.isEmpty()) {
            return List.of();
        }

        List<EffectObject> effects = new ArrayList<>();
        boolean useNumericEffect = !numericVariables.isEmpty() && (stringVariables.isEmpty() || random.nextBoolean());

        if (useNumericEffect) {
            String variable = numericVariables.get(random.nextInt(numericVariables.size()));
            EffectOp operation = random.nextBoolean() ? EffectOp.INCREMENT : EffectOp.ASSIGN;
            String valueRef = operation == EffectOp.ASSIGN ? String.valueOf(random.nextInt(10)) : null;
            effects.add(new EffectObject(variable, operation, valueRef));
            return effects;
        }

        String variable = stringVariables.get(random.nextInt(stringVariables.size()));
        String valueRef = STRING_EFFECT_VALUES[random.nextInt(STRING_EFFECT_VALUES.length)];
        effects.add(new EffectObject(variable, EffectOp.ASSIGN, valueRef));
        return effects;
    }

}
