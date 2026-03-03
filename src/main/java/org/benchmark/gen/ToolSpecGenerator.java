package org.benchmark.gen;

import org.benchmark.model.enums.ConditionOp;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.spec.CommandSpec;
import org.benchmark.model.spec.Effect;
import org.benchmark.model.spec.OptionSpec;
import org.benchmark.model.spec.Precondition;
import org.benchmark.model.spec.ToolSpec;
import org.benchmark.model.spec.ToolState;

import java.util.*;


// Generates a toolSpec along with its commands, options and state.
public class ToolSpecGenerator {
    public static final String PREP_COMMAND_NAME = "initialize_system";
    public static final String CONTROL_STATE_KEY = "system_status";
    public static final String CONTROL_STATE_READY = "READY";
    public static final String CONTROL_STATE_SHUTDOWN = "SHUTDOWN";

    private final Random random;
    private final CommandDict commandDict;
    private final ToolDescriptionGenerator descGenerator;

    public ToolSpecGenerator() {
        this.random = new Random();
        this.descGenerator = new ToolDescriptionGenerator();
        this.commandDict = new CommandDict(random);
    }


    // Generate a toolSpec for a specific domain
    public ToolSpec generateTool(Domain domain) {
        String toolName = commandDict.generateToolName(domain);
        ToolState toolState = generateState(domain);
        List<CommandSpec> commands = generateCommands(toolState, domain);

        String mainAction = commandDict.getRandomVerb(domain);
        String mainNoun = commandDict.getRandomNoun(domain);

        String description = descGenerator.generate(mainAction, mainNoun);

        return new ToolSpec(
                toolName,
                description,
                domain,
                commands,
                toolState
        );
    }

    // Generate the toolSpec state variables
    private ToolState generateState(Domain domain) {
        Map<String, String> variables = new HashMap<>();

        // assign random number of vars
        int varCount = 2 + random.nextInt(4);
        for (int i = 0; i < varCount; i++) {
            variables.put(commandDict.getRandomStateVariable(domain), "int");
        }
        // common var for sys state
        variables.put(CONTROL_STATE_KEY, "string");
        return new ToolState(variables);
    }


    // Generate commands for a toolSpec, including their options, preconditions, and effects
    private List<CommandSpec> generateCommands(ToolState state, Domain domain) {
        List<CommandSpec> commands = new ArrayList<>();

        // Number of commands between 8 and 15
        int cmdCount = 7 + random.nextInt(3);

        // List of state variable names, used to attach effects
        List<String> stateVars = new ArrayList<>(state.variables().keySet());
        // Keep system_status as control state only. Regular commands should not mutate it.
        stateVars.remove(CONTROL_STATE_KEY);

        for (int i = 0; i < cmdCount; i++) {
            // Build a commands name like "update_user"
            String verb = commandDict.getRandomVerb(domain);
            String noun = commandDict.getRandomNoun(domain);
            String cmdName = verb + "_" + noun;
            String desc = verb + "the" + noun;

            List<OptionSpec> args = generateOptionSpecs();

            // Pre-checks
            List<Precondition> preconditions = new ArrayList<>();
            preconditions.add(new Precondition(
                    CONTROL_STATE_KEY,
                    ConditionOp.EQ,
                    CONTROL_STATE_READY
            ));


            // Effects that modify the toolSpec state after executing the commands
            List<Effect> commandEffects = new ArrayList<>();

            if (!stateVars.isEmpty()) {
                // With some probability, attach an effect that assigns a value to a state variable
                if (random.nextBoolean()) {
                    String targetVar = stateVars.get(random.nextInt(stateVars.size()));

                    // If there is at least one option, assign the value from that option
                    if (!args.isEmpty()) {
                        commandEffects.add(new Effect(targetVar, EffectOp.ASSIGN, Effect.OPTION_REF));
                    } else {
                        // Otherwise, assign a fixed "RESET" value
                        commandEffects.add(new Effect(targetVar, EffectOp.ASSIGN, "RESET"));
                    }
                }

            }

            // Create the commands spec with its description, preconditions and effects
            commands.add(new CommandSpec(
                    cmdName,
                    args,
                    "Executes " + verb + " operation on " + noun + ".",
                    preconditions,
                    commandEffects
            ));
        }

        commands.add(new CommandSpec(
                PREP_COMMAND_NAME,
                List.of(),
                "Initializes the system and sets system_status to READY.",
                List.of(),
                List.of(new Effect(CONTROL_STATE_KEY, EffectOp.ASSIGN, CONTROL_STATE_READY))
        ));

        return commands;
    }
    // MOdify this, make it simpler, check if we need hashmap
    private List<OptionSpec> generateOptionSpecs() {
        // Generates 0-5 options
        int numFlags = random.nextInt(6); 
        List<OptionSpec> options = new ArrayList<>();

        // to avoid duplicate options, we check if the option name already exists
        while (options.size() < numFlags) {
            OptionSpec candidate = commandDict.getRandomCommonOptionSpec();

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
