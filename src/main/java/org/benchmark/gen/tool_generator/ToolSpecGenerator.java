package org.benchmark.gen.tool_generator;

import org.benchmark.exec.ToolEnvironment;
import org.benchmark.model.enums.ConditionOp;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.PreconditionObject;
import org.benchmark.model.objects.ToolObject;

import java.util.*;


/**
 * Generates synthetic {@link ToolObject} instances including commands, options,
 * preconditions, effects, and initial state schema.
 */
public class ToolSpecGenerator {
    /** Command name used to move control state from SHUTDOWN to RUNNING. */
    public static final String PREP_COMMAND_NAME = "initialize_system";

    private static final String[] DESC_ADJECTIVES = {"primary", "redundant", "legacy", "upstream", "virtualized"};

    private final Random random;
    private final CommandDict commandDict;

    /**
     * Creates a tool spec generator that delegates to shared sub-generators.
     *
     * @param random shared random source for reproducibility
     */
    public ToolSpecGenerator(Random random) {
        this.random = random;
        this.commandDict = new CommandDict(random);
    }

    /**
     * Generates one synthetic tool for the given domain.
     *
     * @param domain domain to sample vocabulary/state from
     * @return generated tool specification
     */
    public ToolObject generateTool(Domain domain) {
        String toolName = commandDict.generateToolName(domain);
        Map<String, String> stateVars = generateState(domain);
        List<CommandObject> commands = generateCommands(stateVars, domain);

        String mainAction = commandDict.getRandomVerb(domain);
        String mainNoun = commandDict.getRandomNoun(domain);
        String description = generateDescription(mainAction, mainNoun);

        return new ToolObject(toolName, description, domain, commands, stateVars);
    }

    /**
     * Generates a brief tool description sentence.
     */
    private String generateDescription(String action, String target) {
        String adj = DESC_ADJECTIVES[random.nextInt(DESC_ADJECTIVES.length)];
        return String.format("%s the %s %s configuration.", action, adj, target);
    }

    /**
     * Generates state variable schema for a tool.
     */
    private Map<String, String> generateState(Domain domain) {
        Map<String, String> variables = new HashMap<>();
        int varCount = 2 + random.nextInt(4);
        for (int i = 0; i < varCount; i++) {
            variables.put(commandDict.getRandomStateVariable(domain), "int");
        }
        variables.put(ToolEnvironment.SYSTEM_STATUS_KEY, "string");
        return variables;
    }

    /**
     * Generates executable commands for a tool.
     */
    private List<CommandObject> generateCommands(Map<String, String> stateVars, Domain domain) {
        List<CommandObject> commands = new ArrayList<>();
        int cmdCount = 7 + random.nextInt(3);

        List<String> mutableVars = new ArrayList<>(stateVars.keySet());
        mutableVars.remove(ToolEnvironment.SYSTEM_STATUS_KEY);

        Set<String> usedCommandNames = new HashSet<>();
        usedCommandNames.add(PREP_COMMAND_NAME);

        int maxAttempts = cmdCount * 10;
        int attempts = 0;

        while (commands.size() < cmdCount && attempts < maxAttempts) {
            attempts++;
            String verb = commandDict.getRandomVerb(domain);
            String noun = commandDict.getRandomNoun(domain);
            String cmdName = verb + "_" + noun;

            if (usedCommandNames.contains(cmdName)) {
                continue;
            }

            usedCommandNames.add(cmdName);
            String desc = "Executes " + verb + " operation on " + noun + ".";

            List<OptionEntity> args = generateOptionSpecs();

            List<PreconditionObject> preconditionObjects = new ArrayList<>();
            preconditionObjects.add(new PreconditionObject(
                    ToolEnvironment.SYSTEM_STATUS_KEY,
                    ConditionOp.EQ,
                    ToolEnvironment.SYSTEM_STATUS_RUNNING
            ));

            List<EffectObject> commandEffectObjects = new ArrayList<>();
            if (!mutableVars.isEmpty() && random.nextBoolean()) {
                String targetVar = mutableVars.get(random.nextInt(mutableVars.size()));
                if (!args.isEmpty()) {
                    commandEffectObjects.add(new EffectObject(targetVar, EffectOp.ASSIGN, EffectObject.OPTION_REF));
                } else {
                    commandEffectObjects.add(new EffectObject(targetVar, EffectOp.ASSIGN, "RESET"));
                }
            }

            commands.add(new CommandObject(cmdName, args, desc, preconditionObjects, commandEffectObjects));
        }

        commands.add(new CommandObject(
                PREP_COMMAND_NAME,
                List.of(),
                "Initializes the tool and sets system_status to RUNNING.",
                List.of(),
                List.of(new EffectObject(
                        ToolEnvironment.SYSTEM_STATUS_KEY,
                        EffectOp.ASSIGN,
                        ToolEnvironment.SYSTEM_STATUS_RUNNING
                ))
        ));

        return commands;
    }

    /**
     * Generates a de-duplicated list of random option specs for a command.
     */
    private List<OptionEntity> generateOptionSpecs() {
        int numFlags = Math.min(random.nextInt(6), CommandDict.COMMON_OPTS.size());
        List<OptionEntity> options = new ArrayList<>();
        int maxAttempts = numFlags * 10;
        int attempts = 0;

        while (options.size() < numFlags && attempts < maxAttempts) {
            attempts++;
            OptionEntity candidate = commandDict.getRandomCommonOptionSpec();
            boolean alreadyExists = options.stream()
                    .anyMatch(opt -> opt.optionName().equals(candidate.optionName()));
            if (!alreadyExists) {
                options.add(candidate);
            }
        }
        return options;
    }
}
