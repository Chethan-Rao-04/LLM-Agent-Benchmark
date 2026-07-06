package org.benchmark.gen.tool_generator;

import org.benchmark.gen.catalog.OptionProfile;
import org.benchmark.gen.catalog.ToolCatalog;
import org.benchmark.gen.catalog.ToolFamily;
import org.benchmark.gen.catalog.WorkflowStepTemplate;
import org.benchmark.gen.catalog.WorkflowTemplate;
import org.benchmark.gen.description.GeneratedDescriptionPolicy;
import org.benchmark.gen.scenario.ResolvedScenario;
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
        return generateTool(BenchmarkCaseSpec.fromScenario(scenario, 0, 0), null, null, null, includeTrapCommand);
    }

    /**
     * Generates the target tool from the semantic benchmark case specification.
     */
    public ToolGenerationResult generateTool(BenchmarkCaseSpec spec, boolean includeTrapCommand) {
        return generateTool(spec, null, null, null, includeTrapCommand);
    }

    public ToolGenerationResult generateTool(BenchmarkCaseSpec spec,
                                             ToolFamily family,
                                             WorkflowTemplate workflow,
                                             ToolCatalog catalog,
                                             boolean includeTrapCommand) {
        boolean catalogBacked = family != null || workflow != null || catalog != null;
        if (catalogBacked && (family == null || workflow == null || catalog == null)) {
            throw new IllegalStateException("Catalog-backed tool generation requires family, workflow, and catalog");
        }

        Map<String, String> stateVariables = buildStateSchema(spec, family);
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
            WorkflowStepTemplate stepTemplate = workflowStep(workflow, i);
            String name = step.commandName();
            usedCommandNames.add(name);

            if (trappedStepIndex != null && i == trappedStepIndex) {
                commands.add(buildTrappedStepCommand(name, step, stepTemplate, catalog, corruptedVar, wrongValue));
                trapCommandName = name;
            } else {
                commands.add(buildStepCommand(name, step, stepTemplate, catalog));
            }
        }

        if (trapCommandName != null) {
            CommandObject recovery = buildRecoveryCommand(
                    spec, family, corruptedVar, wrongValue, correctValue, usedCommandNames);
            if (recovery == null) {
                trapCommandName = null;
                commands.clear();
                usedCommandNames.clear();
                for (int i = 0; i < spec.capabilitySteps().size(); i++) {
                    CapabilityStep step = spec.capabilitySteps().get(i);
                    WorkflowStepTemplate stepTemplate = workflowStep(workflow, i);
                    String name = step.commandName();
                    usedCommandNames.add(name);
                    commands.add(buildStepCommand(name, step, stepTemplate, catalog));
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
            String[] fillerRole = fillerRole(family, spec.domain());
            String verb = fillerRole[0];
            String noun = fillerRole[1];
            String name = CommandAbbreviator.commandName(verb, noun);
            if (!usedCommandNames.add(name)) continue;

            List<EffectObject> effects = generateFillerEffect(stateVariables);
            commands.add(new CommandObject(name, List.of(),
                    GeneratedDescriptionPolicy.commandDescription("support", Map.of(), effects), effects, Map.of()));
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

        String toolName = family == null
                ? commandDict.generateToolName(spec.domain())
                : commandDict.generateToolName(spec.domain(), family.nameFragments());
        String description = family == null
                ? GeneratedDescriptionPolicy.toolDescription(spec.domain())
                : GeneratedDescriptionPolicy.toolDescription(family.purpose());
        ToolObject tool = new ToolObject(toolName, description, spec.domain(), commands, stateVariables);
        return new ToolGenerationResult(tool, trapCommandName, recoveryCommandName);
    }

    /**
     * Builds a trapped version of a step command. The documentation shows the
     * correct effects, but the real effects assign a wrong value to the target
     * variable, causing the next step's precondition to fail.
     */
    private CommandObject buildTrappedStepCommand(String name,
                                                  CapabilityStep step,
                                                  WorkflowStepTemplate stepTemplate,
                                                  ToolCatalog catalog,
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

        List<OptionEntity> options = resolveOptions(stepTemplate, catalog);
        return new CommandObject(name, options,
                GeneratedDescriptionPolicy.commandDescription(step.intent(), step.precondition(), documentedEffects),
                realEffects, step.precondition(), documentedEffects);
    }

    /**
     * Builds a neutral-looking command that restores the corrupted state only
     * when the bad value is actually present.
     */
    private CommandObject buildRecoveryCommand(BenchmarkCaseSpec spec,
                                               ToolFamily family,
                                               String corruptedVar,
                                               String wrongValue,
                                               String correctValue,
                                               Set<String> usedCommandNames) {
        String recoveryName = null;
        for (int attempts = 0; attempts < 20; attempts++) {
            String[] fillerRole = fillerRole(family, spec.domain());
            String candidate = CommandAbbreviator.commandName(fillerRole[0], fillerRole[1]);
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
        Map<String, String> recoveryPreconditions = Map.of(corruptedVar, wrongValue);
        return new CommandObject(recoveryName, List.of(),
                GeneratedDescriptionPolicy.commandDescription("recovery", recoveryPreconditions, effects),
                effects, recoveryPreconditions);
    }

    private String wrongValueFor(String correctValue) {
        return correctValue + "_" + WRONG_VALUE_SUFFIXES[random.nextInt(WRONG_VALUE_SUFFIXES.length)];
    }

    private CommandObject buildStepCommand(String abbreviatedName,
                                           CapabilityStep step,
                                           WorkflowStepTemplate stepTemplate,
                                           ToolCatalog catalog) {
        List<OptionEntity> options = resolveOptions(stepTemplate, catalog);
        List<EffectObject> effects = new ArrayList<>();
        for (Map.Entry<String, String> entry : step.effect().entrySet()) {
            effects.add(new EffectObject(entry.getKey(), EffectOp.ASSIGN, entry.getValue()));
        }
        return new CommandObject(abbreviatedName, options,
                GeneratedDescriptionPolicy.commandDescription(step.intent(), step.precondition(), effects),
                effects, step.precondition());
    }

    private Map<String, String> buildStateSchema(BenchmarkCaseSpec spec, ToolFamily family) {
        Map<String, String> schema = new LinkedHashMap<>();
        if (family != null) {
            family.stateVariables().forEach(variable -> schema.put(variable, "string"));
        }
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

    private WorkflowStepTemplate workflowStep(WorkflowTemplate workflow, int index) {
        if (workflow == null) {
            return null;
        }
        if (index < 0 || index >= workflow.steps().size()) {
            throw new IllegalStateException("Workflow '" + workflow.id()
                    + "' is missing step metadata at index " + index);
        }
        return workflow.steps().get(index);
    }

    private List<OptionEntity> resolveOptions(WorkflowStepTemplate stepTemplate, ToolCatalog catalog) {
        if (stepTemplate == null || stepTemplate.optionProfile().isBlank()) {
            return List.of();
        }
        OptionProfile optionProfile = catalog.optionProfile(stepTemplate.optionProfile());
        return optionGenerator.generateOptions(optionProfile);
    }

    private String[] fillerRole(ToolFamily family, org.benchmark.model.enums.Domain domain) {
        if (family == null) {
            return new String[]{commandDict.getRandomVerb(domain), commandDict.getRandomNoun(domain)};
        }
        if (family.fillerCommandRoles().isEmpty()) {
            throw new IllegalStateException("Tool family '" + family.id()
                    + "' must declare at least one filler command role");
        }
        String role = family.fillerCommandRoles().get(random.nextInt(family.fillerCommandRoles().size())).trim();
        int split = role.indexOf(' ');
        return new String[]{role.substring(0, split), role.substring(split + 1)};
    }

}
