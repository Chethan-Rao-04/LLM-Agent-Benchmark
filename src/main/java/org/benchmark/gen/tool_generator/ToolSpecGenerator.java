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
     * Includes numeric measurement vars plus 1-2 boolean capability vars.
     */
    private Map<String, String> generateState(Domain domain) {
        Map<String, String> variables = new HashMap<>();
        int varCount = 2 + random.nextInt(4);
        for (int i = 0; i < varCount; i++) {
            variables.put(commandDict.getRandomStateVariable(domain), "int");
        }
        // Add 1-2 capability flags (boolean-style: enabled/disabled)
        int capCount = 1 + random.nextInt(2);
        for (int i = 0; i < capCount; i++) {
            variables.put(commandDict.getRandomCapabilityVariable(domain), "boolean");
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

        // Separate numeric vars from boolean capability vars
        List<String> numericVars = new ArrayList<>();
        List<String> capabilityVars = new ArrayList<>();
        for (Map.Entry<String, String> entry : stateVars.entrySet()) {
            if (entry.getKey().equals(ToolEnvironment.SYSTEM_STATUS_KEY)) continue;
            if ("boolean".equals(entry.getValue())) {
                capabilityVars.add(entry.getKey());
            } else {
                numericVars.add(entry.getKey());
            }
        }

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

            // ~30% of commands require a capability flag to be enabled first
            // e.g. "encryption == enabled" or "safety_interlock == enabled"
            if (!capabilityVars.isEmpty() && random.nextInt(10) < 3) {
                String capVar = capabilityVars.get(random.nextInt(capabilityVars.size()));
                preconditionObjects.add(new PreconditionObject(capVar, ConditionOp.EQ, "enabled"));
            }

            // Effects target numeric vars (INCREMENT or ASSIGN literal)
            List<EffectObject> commandEffectObjects = new ArrayList<>();
            if (!numericVars.isEmpty() && random.nextBoolean()) {
                String targetVar = numericVars.get(random.nextInt(numericVars.size()));
                if (random.nextBoolean()) {
                    commandEffectObjects.add(new EffectObject(targetVar, EffectOp.INCREMENT, null));
                } else {
                    commandEffectObjects.add(new EffectObject(targetVar, EffectOp.ASSIGN, "0"));
                }
            }

            commands.add(new CommandObject(cmdName, args, desc, preconditionObjects, commandEffectObjects));
        }

        // Guarantee at least one command has a domain precondition (for multi-step viability)
        boolean hasDomainPrecond = commands.stream().anyMatch(cmd ->
            cmd.commandPreConditions() != null && cmd.commandPreConditions().stream()
                .anyMatch(p -> !p.variable().equals(ToolEnvironment.SYSTEM_STATUS_KEY)));

        if (!hasDomainPrecond && !capabilityVars.isEmpty() && !commands.isEmpty()) {
            // Pick a random command and rebuild it with an added capability precondition
            int idx = random.nextInt(commands.size());
            CommandObject original = commands.get(idx);
            List<PreconditionObject> newPreconds = new ArrayList<>(original.commandPreConditions());
            newPreconds.add(new PreconditionObject(
                capabilityVars.get(random.nextInt(capabilityVars.size())),
                ConditionOp.EQ, "enabled"));
            commands.set(idx, new CommandObject(
                original.name(), original.commandOptions(), original.description(),
                newPreconds, original.commandEffectObjects()));
        }

        // Generate configure_* commands for each capability var used as a precondition
        Set<String> domainPrecondVars = new HashSet<>();
        for (CommandObject cmd : commands) {
            if (cmd.commandPreConditions() != null) {
                for (PreconditionObject pre : cmd.commandPreConditions()) {
                    if (!pre.variable().equals(ToolEnvironment.SYSTEM_STATUS_KEY)) {
                        domainPrecondVars.add(pre.variable());
                    }
                }
            }
        }
        for (String varName : domainPrecondVars) {
            String configureCmdName = "configure_" + varName;
            if (!usedCommandNames.contains(configureCmdName)) {
                usedCommandNames.add(configureCmdName);
                commands.add(new CommandObject(
                    configureCmdName, List.of(),
                    "Enables " + varName + ". Sets " + varName + " to enabled.",
                    List.of(new PreconditionObject(
                        ToolEnvironment.SYSTEM_STATUS_KEY, ConditionOp.EQ,
                        ToolEnvironment.SYSTEM_STATUS_RUNNING)),
                    List.of(new EffectObject(varName, EffectOp.ASSIGN, "enabled"))
                ));
            }
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
