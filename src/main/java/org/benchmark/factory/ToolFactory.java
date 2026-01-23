package org.benchmark.factory;

import org.benchmark.model.documentation.ArgumentSpec;
import org.benchmark.model.documentation.CommandObject;
import org.benchmark.model.documentation.CommandEffect;
import org.benchmark.model.documentation.CommandPreconditions;
import org.benchmark.model.tool.*;

import java.util.*;


// For a given complexity level, this class generates a toolSpec along with its commands, options and state.
public class ToolFactory {

    private final Random random;
    private final CommandDict commandDict;
    private final ToolDescriptionGenerator descGenerator;

    public ToolFactory(long seed) {
        this.random = new Random(seed);
        this.descGenerator = new ToolDescriptionGenerator(seed);
        this.commandDict = new CommandDict(random);
    }


    // Generate a toolSpec for a specific domain and complexity
    public ToolSpecification generateTool(ToolComplexity complexity, Domain domain) {
        String toolName = commandDict.generateToolName(domain);
        ToolStateMemory toolState = generateState(complexity, domain);
        List<CommandObject> commands = generateCommands(complexity, toolState, domain);

        String mainAction = commandDict.getRandomVerb(domain);
        String mainNoun = commandDict.getRandomNoun(domain);

        String description = descGenerator.generate(complexity, mainAction, mainNoun);

        return new ToolSpecification(
                toolName,
                description,
                domain,
                complexity,
                commands,
                toolState
        );
    }
    // Generate the toolSpec state variables
    private ToolStateMemory generateState(ToolComplexity complexity, Domain domain) {
        Map<String, String> variables = new HashMap<>();
        if (complexity == ToolComplexity.SIMPLE) return new ToolStateMemory(variables); // for simple tools, emptx map


        // assign random number of var for non-simple tools
        int varCount = 2 + random.nextInt(4);
        for (int i = 0; i < varCount; i++) {
            variables.put(commandDict.getRandomStateVariable(domain), "int");
        }
        // common var for sys state
        variables.put("system_status", "string");
        return new ToolStateMemory(variables);
    }


    // Generate commands for a toolSpec, including their options, preconditions, and effects
    private List<CommandObject> generateCommands(ToolComplexity complexity, ToolStateMemory state, Domain domain) {
        List<CommandObject> commands = new ArrayList<>();

        // Number of commands between 3 and 7
        int cmdCount = 3 + random.nextInt(5);

        // List of state variable names, used to attach effects
        List<String> stateVars = new ArrayList<>(state.variables().keySet());

        for (int i = 0; i < cmdCount; i++) {
            // Build a commands name like "update_user"
            String verb = commandDict.getRandomVerb(domain);
            String noun = commandDict.getRandomNoun(domain);
            String cmdName = verb + "_" + noun;
            String desc = verb + "the" + noun;

            List<ArgumentSpec> args = generateOptionSpecs(domain);

            // Pre-checks
            List<CommandPreconditions> preconditions = new ArrayList<>();
            // Effects that modify the toolSpec state after executing the commands
            List<CommandEffect> commandEffects = new ArrayList<>();

            // Only non-SIMPLE tools have stateful effects and preconditions
            if (complexity != ToolComplexity.SIMPLE && !stateVars.isEmpty()) {

                // With some probability, attach an effect that assigns a value to a state variable
                if (random.nextBoolean()) {
                    String targetVar = stateVars.get(random.nextInt(stateVars.size()));

                    // If there is at least one option, assign the value from that option
                    if (!args.isEmpty()) {
                        commandEffects.add(new CommandEffect(targetVar, "ASSIGN", args.get(0).optionName()));
                    } else {
                        // Otherwise, assign a fixed "RESET" value
                        commandEffects.add(new CommandEffect(targetVar, "ASSIGN", "RESET"));
                    }
                }

                // COMPLEX tools may additionally require system_status to be READY before running
                if (complexity == ToolComplexity.COMPLEX && random.nextBoolean()) {
                    preconditions.add(new CommandPreconditions("system_status", "==", "READY"));
                }
            }

            // Create the commands spec with its description, preconditions and effects
            commands.add(new CommandObject(
                    cmdName,
                    args,
                    "Executes " + verb + " operation on " + noun + ".",
                    preconditions,
                    commandEffects
            ));
        }

        // COMPLEX tools get a dedicated initialization commands at the beginning
        if (complexity == ToolComplexity.COMPLEX) {
            commands.add(0, new CommandObject(
                    "initialize_system",
                    List.of(), // no options
                    "Sets system_status to READY. Must be run first.",
                    List.of(), // no preconditions
                    List.of(new CommandEffect("system_status", "ASSIGN", "READY")) // effect: mark system as READY
            ));
        }
        return commands;
    }

    // Generates options for a commands ( right now, only max 1)
    private List<ArgumentSpec> generateOptionSpecs(Domain domain) {
        List<ArgumentSpec> args = new ArrayList<>();

        // Randomly decide whether this commands has an option
        if (random.nextBoolean()) {
            // Example: `--user`, type int, required, with a generic description
            args.add(new ArgumentSpec(
                    "--" + commandDict.getRandomNoun(domain),
                    "int",
                    true,
                    "Target value"
            ));
        }
        return args;
    }
}