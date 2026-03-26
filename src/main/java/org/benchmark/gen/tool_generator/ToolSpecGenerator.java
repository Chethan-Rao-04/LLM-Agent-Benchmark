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
import org.benchmark.model.objects.ToolStateObject;

import java.util.*;


/**
 * Generates synthetic {@link ToolObject} instances including commands, options,
 * preconditions, effects, and initial state schema.
 */
public class ToolSpecGenerator {
    /** Command name used to move control state from SHUTDOWN to RUNNING. */
    public static final String PREP_COMMAND_NAME = "initialize_system";

    private final Random random;
    private final CommandDict commandDict;
    private final ToolDescriptionGenerator descGenerator;

    public ToolSpecGenerator() {
        this.random = new Random();
        this.descGenerator = new ToolDescriptionGenerator();
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
        ToolStateObject toolStateObject = generateState(domain);
        List<CommandObject> commands = generateCommands(toolStateObject, domain);

        String mainAction = commandDict.getRandomVerb(domain);
        String mainNoun = commandDict.getRandomNoun(domain);

        String description = descGenerator.generate(mainAction, mainNoun);

        return new ToolObject(
                toolName,
                description,
                domain,
                commands,
                toolStateObject
        );
    }

    /**
     * Generates initial state schema for a tool.
     *
     * @param domain source domain for state variable naming
     * @return generated state schema
     */
    private ToolStateObject generateState(Domain domain) {
        Map<String, String> variables = new HashMap<>();

        // assign random number of vars
        int varCount = 2 + random.nextInt(4);
        for (int i = 0; i < varCount; i++) {
            variables.put(commandDict.getRandomStateVariable(domain), "int");
        }
        // common var for sys state ( eq., for generic preconditions)
        variables.put(ToolEnvironment.SYSTEM_STATUS_KEY, "string");
        return new ToolStateObject(variables);
    }


    /**
     * Generates executable commands for a tool.
     *
     * @param state generated tool state schema
     * @param domain source domain for naming
     * @return command list including prep command
     */
    private List<CommandObject> generateCommands(ToolStateObject state, Domain domain) {
        List<CommandObject> commands = new ArrayList<>();

        // Number of commands between 8 and 15
        int cmdCount = 7 + random.nextInt(3);

        // List of state variable names, used to attach effects
        List<String> stateVars = new ArrayList<>(state.variables().keySet());
        // Keep system_status as control state only. Regular commands should not mutate it.
        stateVars.remove(ToolEnvironment.SYSTEM_STATUS_KEY);
        // Track used command names to prevent duplicates
        Set<String> usedCommandNames = new HashSet<>();
        usedCommandNames.add(PREP_COMMAND_NAME); // reserve prep command name upfront


        int maxAttempts = cmdCount * 10; // safety cap to avoid infinite loop
        int attempts = 0;


        while (commands.size() < cmdCount && attempts < maxAttempts) {
            attempts++;
            // Build a commands name like "update_user"
            String verb = commandDict.getRandomVerb(domain);
            String noun = commandDict.getRandomNoun(domain);
            String cmdName = verb + "_" + noun;

            // Skip if name already used
            if (usedCommandNames.contains(cmdName)){
                continue;
            }

            // If unique, store it to avoid reuse in the next loop cycle
            usedCommandNames.add(cmdName);
            String desc = "Executes " + verb + " operation on " + noun + ".";

            List<OptionEntity> args = generateOptionSpecs();

            // Pre-checks
            List<PreconditionObject> preconditionObjects = new ArrayList<>();
            preconditionObjects.add(new PreconditionObject(
                    ToolEnvironment.SYSTEM_STATUS_KEY,
                    ConditionOp.EQ,
                    ToolEnvironment.SYSTEM_STATUS_RUNNING
            ));


            // Effects that modify the toolSpec state after executing the commands
            List<EffectObject> commandEffectObjects = new ArrayList<>();

            if (!stateVars.isEmpty()) {
                // With some probability, attach an effect that assigns a value to a state variable
                if (random.nextBoolean()) {
                    String targetVar = stateVars.get(random.nextInt(stateVars.size()));

                    // If there is at least one option, assign the value from that option
                    if (!args.isEmpty()) {
                        commandEffectObjects.add(new EffectObject(targetVar, EffectOp.ASSIGN, EffectObject.OPTION_REF));
                    } else {
                        // Otherwise, assign a fixed "RESET" value
                        commandEffectObjects.add(new EffectObject(targetVar, EffectOp.ASSIGN, "RESET"));
                    }
                }

            }

            // Create the commands spec with its description, preconditions and effects
            commands.add(new CommandObject(
                    cmdName,
                    args,
                    desc,
                    preconditionObjects,
                    commandEffectObjects
            ));
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
     *
     * @return command option list
     */
    private List<OptionEntity> generateOptionSpecs() {
        // Generates 0-5 options
        int numFlags = random.nextInt(6); 
        List<OptionEntity> options = new ArrayList<>();

        // to avoid duplicate options, we check if the option name already exists
        while (options.size() < numFlags) {
            OptionEntity candidate = commandDict.getRandomCommonOptionSpec();

            // Simple check to avoid duplicate option names
            boolean alreadyExists = options.stream()
                    .anyMatch(opt -> opt.optionName().equals(candidate.optionName()));

            if (!alreadyExists) {
                options.add(candidate);
            }
        }
        return options;
    }
}
