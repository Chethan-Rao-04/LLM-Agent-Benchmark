package org.benchmark.gen.tool_generator;

import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.scenario.ScenarioPattern;
import org.benchmark.gen.spec.DecoyKind;
import org.benchmark.gen.spec.DecoyPlan;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.ToolObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates distractor tools that use vocabulary from the same semantic pool as the
 * target scenario, preventing the LLM from filtering by domain vocabulary alone.
 *
 * <p>All command names go through {@link CommandAbbreviator}.</p>
 */
public class SemanticDecoyGenerator {

    private final Random random;
    private final CommandDict commandDict;
    private final CommandOptionGenerator optionGenerator;

    /**
     * Creates the generator that builds semantically plausible distractor tools.
     *
     * @param random shared random source
     * @param commandDict domain vocabulary dictionary
     */
    public SemanticDecoyGenerator(Random random, CommandDict commandDict) {
        this.random = random;
        this.commandDict = commandDict;
        this.optionGenerator = new CommandOptionGenerator(random, commandDict);
    }

    /**
     * Generates distractor tools that stay close to the target scenario's vocabulary without sharing its exact state path.
     *
     * @param sourcePattern original scenario template
     * @param targetScenario resolved target scenario
     * @param count number of semantic decoys to attempt to build
     * @return generated semantic distractors
     */
    public List<ToolObject> generate(ScenarioPattern sourcePattern,
                                      ResolvedScenario targetScenario,
                                      int count) {
        return generate(sourcePattern, targetScenario, DecoyPlan.currentDefault(count, 0));
    }

    /**
     * Generates semantic distractor tools according to the case decoy plan.
     */
    public List<ToolObject> generate(ScenarioPattern sourcePattern,
                                      ResolvedScenario targetScenario,
                                      DecoyPlan decoyPlan) {
        List<ToolObject> decoys = new ArrayList<>();
        Set<String> usedToolNames = new HashSet<>();

        for (int i = 0; i < decoyPlan.semanticDecoyCount(); i++) {
            DecoyKind decoyKind = decoyKindFor(decoyPlan, i);
            Map<String, String> altBindings = pickAlternativeBindings(
                    sourcePattern, targetScenario.domain(), targetScenario.resolvedPoolValues());
            if (altBindings.isEmpty()) break;

            ToolObject decoy = buildDecoyTool(targetScenario, altBindings, usedToolNames, decoyKind);
            if (decoy != null) {
                decoys.add(decoy);
                usedToolNames.add(decoy.name());
            }
        }
        return decoys;
    }

    private DecoyKind decoyKindFor(DecoyPlan decoyPlan, int index) {
        List<DecoyKind> plannedKinds = decoyPlan.semanticDecoyKinds();
        if (plannedKinds.isEmpty()) {
            return DecoyKind.SIMILAR_INTENT_WRONG_RESOURCE;
        }
        return plannedKinds.get(Math.min(index, plannedKinds.size() - 1));
    }

    private Map<String, String> pickAlternativeBindings(ScenarioPattern pattern,
                                                         Domain domain,
                                                         Map<String, String> targetBindings) {
        Map<String, String> alt = new LinkedHashMap<>();
        String domainKey = domain.name();

        for (Map.Entry<String, String> targetEntry : targetBindings.entrySet()) {
            String variable = targetEntry.getKey();
            String targetValue = targetEntry.getValue();

            Map<String, List<String>> domainMap = pattern.pools().get(variable);
            if (domainMap == null) continue;

            List<String> pool = domainMap.get(domainKey);
            if (pool == null || pool.size() <= 1) {
                alt.put(variable, targetValue);
                continue;
            }

            List<String> alternatives = pool.stream()
                    .filter(v -> !v.equals(targetValue))
                    .toList();
            alt.put(variable, alternatives.get(random.nextInt(alternatives.size())));
        }

        if (alt.equals(targetBindings)) return Map.of();
        return alt;
    }

    private ToolObject buildDecoyTool(ResolvedScenario targetScenario,
                                       Map<String, String> altBindings,
                                       Set<String> usedToolNames,
                                       DecoyKind decoyKind) {
        Map<String, String> stateSchema = new LinkedHashMap<>();
        List<CommandObject> commands = new ArrayList<>();
        Set<String> usedCommandNames = new HashSet<>();

        // Mirror scenario steps with alternative nouns
        for (ResolvedStep targetStep : targetScenario.steps()) {
            String verb = targetStep.verb();
            String altNoun = substituteNoun(targetStep.noun(), targetScenario.resolvedPoolValues(), altBindings);
            String name = CommandAbbreviator.commandName(verb, altNoun);
            if (!usedCommandNames.add(name)) continue;

            Map<String, String> altEffect = substituteMap(targetStep.effect(),
                    targetScenario.resolvedPoolValues(), altBindings);
            Map<String, String> altPrecondition = substituteMap(targetStep.precondition(),
                    targetScenario.resolvedPoolValues(), altBindings);
            if (decoyKind == DecoyKind.SIMILAR_COMMANDS_WRONG_STATE_PATH) {
                altEffect = shiftEffectValues(altEffect);
                altPrecondition = shiftPreconditionValues(altPrecondition, altEffect);
            }

            List<EffectObject> effects = new ArrayList<>();
            for (Map.Entry<String, String> entry : altEffect.entrySet()) {
                effects.add(new EffectObject(entry.getKey(), EffectOp.ASSIGN, entry.getValue()));
                stateSchema.put(entry.getKey(), "string");
            }
            altPrecondition.keySet().forEach(k -> stateSchema.put(k, "string"));

            var options = optionGenerator.generateOptions();
            commands.add(new CommandObject(name, options,
                    "Executes the " + name + " operation.", effects, altPrecondition));
        }

        // Filler commands
        int fillerCount = 3 + random.nextInt(3);
        int attempts = 0;
        while (fillerCount > 0 && attempts < fillerCount * 10) {
            attempts++;
            String verb = commandDict.getRandomVerb(targetScenario.domain());
            String noun = commandDict.getRandomNoun(targetScenario.domain());
            String name = CommandAbbreviator.commandName(verb, noun);
            if (!usedCommandNames.add(name)) continue;

            commands.add(new CommandObject(name, optionGenerator.generateOptions(),
                    "Executes the " + name + " operation.", List.of(), Map.of()));
            fillerCount--;
        }

        stateSchema.putIfAbsent(commandDict.getRandomStateVariable(targetScenario.domain()), "int");

        String toolName = commandDict.generateToolName(targetScenario.domain());
        if (usedToolNames.contains(toolName)) return null;

        return new ToolObject(toolName,
                "Manages " + targetScenario.patternName().replace('_', ' ') + " operations.",
                targetScenario.domain(), commands, stateSchema);
    }

    private String substituteNoun(String original, Map<String, String> targetBindings,
                                   Map<String, String> altBindings) {
        String result = original;
        for (Map.Entry<String, String> entry : targetBindings.entrySet()) {
            result = result.replace(entry.getValue(), altBindings.getOrDefault(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private Map<String, String> substituteMap(Map<String, String> original,
                                               Map<String, String> targetBindings,
                                               Map<String, String> altBindings) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : original.entrySet()) {
            result.put(substituteNoun(entry.getKey(), targetBindings, altBindings),
                    substituteNoun(entry.getValue(), targetBindings, altBindings));
        }
        return result;
    }

    private Map<String, String> shiftEffectValues(Map<String, String> original) {
        Map<String, String> shifted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : original.entrySet()) {
            shifted.put(entry.getKey(), entry.getValue() + "_alternate");
        }
        return shifted;
    }

    private Map<String, String> shiftPreconditionValues(Map<String, String> original,
                                                        Map<String, String> shiftedEffects) {
        Map<String, String> shifted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : original.entrySet()) {
            String matchingShiftedValue = shiftedEffects.get(entry.getKey());
            shifted.put(entry.getKey(), matchingShiftedValue != null ? matchingShiftedValue : entry.getValue());
        }
        return shifted;
    }

}
