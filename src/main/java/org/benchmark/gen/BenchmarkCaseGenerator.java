package org.benchmark.gen;

import org.benchmark.gen.catalog.ToolCatalog;
import org.benchmark.gen.catalog.ToolCatalogLoader;
import org.benchmark.gen.catalog.ToolFamily;
import org.benchmark.gen.catalog.WorkflowStepTemplate;
import org.benchmark.gen.catalog.WorkflowTemplate;
import org.benchmark.gen.doc_generator.DocumentationGenerator;
import org.benchmark.gen.query_generator.UserQueryGenerator;
import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.spec.BenchmarkCaseSpec;
import org.benchmark.gen.spec.CapabilityStep;
import org.benchmark.gen.spec.DecoyKind;
import org.benchmark.gen.spec.DecoyPlan;
import org.benchmark.gen.spec.ScoringPolicy;
import org.benchmark.gen.tool_generator.CommandDict;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.benchmark.gen.tool_generator.ScenarioToolGenerator;
import org.benchmark.gen.tool_generator.ToolSpecGenerator;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.objects.ToolObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Generates multi-step benchmark cases from a hidden static family/workflow catalog.
 *
 * <p>The runtime still receives only the sampled per-case manual, while the hidden
 * catalog keeps target tools, queries, commands, and decoys semantically aligned.</p>
 */
public class BenchmarkCaseGenerator {

    private final Random random;
    private final ToolCatalog catalog;
    private final ScenarioToolGenerator scenarioToolGenerator;
    private final ToolSpecGenerator toolSpecGenerator;
    private final DocumentationGenerator documentationGenerator;
    private final UserQueryGenerator queryGenerator;
    private final DocumentComplexity documentationComplexity;
    private final boolean trapCommand;

    /**
     * Creates the benchmark case generator from explicit collaborators.
     */
    public BenchmarkCaseGenerator(Random random,
                                  ToolCatalogLoader toolCatalogLoader,
                                  ScenarioToolGenerator scenarioToolGenerator,
                                  ToolSpecGenerator toolSpecGenerator,
                                  DocumentationGenerator documentationGenerator,
                                  UserQueryGenerator queryGenerator,
                                  DocumentComplexity documentationComplexity,
                                  boolean trapCommand) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.catalog = Objects.requireNonNull(toolCatalogLoader, "toolCatalogLoader must not be null").getCatalog();
        this.scenarioToolGenerator = Objects.requireNonNull(scenarioToolGenerator, "scenarioToolGenerator must not be null");
        this.toolSpecGenerator = Objects.requireNonNull(toolSpecGenerator, "toolSpecGenerator must not be null");
        this.documentationGenerator = Objects.requireNonNull(documentationGenerator, "documentationGenerator must not be null");
        this.queryGenerator = Objects.requireNonNull(queryGenerator, "queryGenerator must not be null");
        this.documentationComplexity = documentationComplexity == null ? DocumentComplexity.CLEAN : documentationComplexity;
        this.trapCommand = trapCommand;
    }

    public BenchmarkCaseGenerator(DocumentComplexity documentationComplexity, Long seed) {
        this(documentationComplexity, seed, false);
    }

    public BenchmarkCaseGenerator(DocumentComplexity documentationComplexity, Long seed, boolean trapCommand) {
        this(createRandom(seed), documentationComplexity, trapCommand);
    }

    private BenchmarkCaseGenerator(Random random, DocumentComplexity documentationComplexity, boolean trapCommand) {
        this(
                random,
                new ToolCatalogLoader(),
                new ScenarioToolGenerator(random, new CommandDict(random)),
                new ToolSpecGenerator(random),
                new DocumentationGenerator(),
                new UserQueryGenerator(random),
                documentationComplexity,
                trapCommand
        );
    }

    private static Random createRandom(Long seed) {
        return seed == null ? new Random() : new Random(seed);
    }

    /**
     * Generates a batch of multi-step benchmark cases.
     */
    public List<BenchmarkCase> generateCases(int count, int distractorCount, Domain specificDomain) {
        List<BenchmarkCase> cases = new ArrayList<>();
        List<Domain> availableDomains = specificDomain == null
                ? Arrays.asList(Domain.values())
                : List.of(specificDomain);

        for (int index = 0; index < count; index++) {
            Domain domain = pickOne(availableDomains);
            ToolFamily targetFamily = pickOne(nonEmptyFamilies(domain));
            WorkflowTemplate targetWorkflow = pickWorkflow(targetFamily);
            int semanticDecoyCount = Math.min(1, distractorCount);

            CatalogCaseDefinition targetDefinition = buildCaseDefinition(targetFamily, targetWorkflow, semanticDecoyCount, 0);
            boolean enableTrapForCase = trapCommand && random.nextInt(5) == 0;
            ScenarioToolGenerator.ToolGenerationResult targetResult =
                    scenarioToolGenerator.generateTool(
                            targetDefinition.spec(),
                            targetFamily,
                            targetWorkflow,
                            catalog,
                            enableTrapForCase
                    );
            ToolObject targetTool = targetResult.tool();
            Set<String> usedToolNames = new LinkedHashSet<>();
            usedToolNames.add(targetTool.name());

            List<ToolObject> semanticDecoys = generateSemanticDecoys(targetFamily, targetTool, semanticDecoyCount, usedToolNames);
            int remainingDistractors = Math.max(0, distractorCount - semanticDecoys.size());
            List<ToolObject> randomDistractors = generateRandomDistractors(domain, targetFamily, remainingDistractors, usedToolNames);

            BenchmarkCaseSpec spec = buildSpec(
                    targetFamily,
                    targetWorkflow,
                    targetDefinition.scenario().steps(),
                    targetDefinition.scenario().cumulativeExpectedState(),
                    semanticDecoys.size(),
                    randomDistractors.size()
            );
            Map<String, DecoyKind> semanticDecoyKindsByToolName =
                    buildSemanticDecoyKindMap(semanticDecoys);

            List<ToolObject> allDistractors = new ArrayList<>(semanticDecoys);
            allDistractors.addAll(randomDistractors);
            String caseManual = buildCaseManual(targetTool, allDistractors, spec);
            String userQuery = queryGenerator.generateGoalQuery(spec, targetFamily, targetWorkflow);

            cases.add(new BenchmarkCase(
                    targetDefinition.scenario(),
                    spec,
                    targetTool,
                    targetDefinition.scenario().steps(),
                    caseManual,
                    targetDefinition.scenario().cumulativeExpectedState(),
                    allDistractors,
                    semanticDecoys,
                    randomDistractors,
                    semanticDecoyKindsByToolName,
                    userQuery,
                    targetResult.trapCommandName() != null,
                    targetResult.trapCommandName(),
                    targetResult.recoveryCommandName()
            ));
        }

        return cases;
    }

    private String buildCaseManual(ToolObject targetTool, List<ToolObject> distractors, BenchmarkCaseSpec spec) {
        List<ToolObject> tools = new ArrayList<>(distractors.size() + 1);
        tools.add(targetTool);
        tools.addAll(distractors);
        tools.sort(Comparator.comparing(ToolObject::name, String.CASE_INSENSITIVE_ORDER));

        StringBuilder manual = new StringBuilder("# Case Manual\n\n");
        for (int index = 0; index < tools.size(); index++) {
            if (index > 0) {
                manual.append("\n\n");
            }
            ToolObject tool = tools.get(index);
            if (tool.name().equals(targetTool.name())) {
                manual.append(documentationGenerator.generateDocumentation(tool, documentationComplexity, spec));
            } else {
                manual.append(documentationGenerator.generateDocumentation(tool, documentationComplexity));
            }
        }
        return manual.toString();
    }

    private List<ToolObject> generateSemanticDecoys(ToolFamily targetFamily,
                                                    ToolObject targetTool,
                                                    int semanticDecoyCount,
                                                    Set<String> usedToolNames) {
        if (semanticDecoyCount <= 0 || targetFamily.decoyFamilyIds().isEmpty()) {
            return List.of();
        }

        ToolObject decoy = generateDecoyTool(targetFamily, targetTool, usedToolNames);
        return decoy == null ? List.of() : List.of(decoy);
    }

    private ToolObject generateDecoyTool(ToolFamily targetFamily,
                                         ToolObject targetTool,
                                         Set<String> usedToolNames) {
        List<ToolFamily> candidateFamilies = targetFamily.decoyFamilyIds().stream()
                .map(catalog::family)
                .toList();
        if (candidateFamilies.isEmpty()) {
            return null;
        }

        for (int attempts = 0; attempts < 20; attempts++) {
            ToolFamily decoyFamily = pickOne(candidateFamilies);
            WorkflowTemplate decoyWorkflow = pickWorkflow(decoyFamily);
            CatalogCaseDefinition decoyDefinition = buildCaseDefinition(decoyFamily, decoyWorkflow, 0, 0);
            ToolObject baseTool = scenarioToolGenerator.generateTool(
                    decoyDefinition.spec(),
                    decoyFamily,
                    decoyWorkflow,
                    catalog,
                    false
            ).tool();
            ToolObject decoyTool = renameToolLikeTarget(baseTool, targetTool.name());
            if (usedToolNames.add(decoyTool.name())) {
                return decoyTool;
            }
        }
        return null;
    }

    private ToolObject renameToolLikeTarget(ToolObject tool, String targetToolName) {
        return new ToolObject(
                similarToolName(targetToolName),
                tool.description(),
                tool.domain(),
                tool.commands(),
                tool.stateVariables()
        );
    }

    private String similarToolName(String targetToolName) {
        int suffixStart = targetToolName.lastIndexOf('-');
        String prefix = suffixStart > 0 ? targetToolName.substring(0, suffixStart) : targetToolName;
        return prefix + "-" + (100 + random.nextInt(900));
    }

    private List<ToolObject> generateRandomDistractors(Domain domain,
                                                       ToolFamily targetFamily,
                                                       int count,
                                                       Set<String> usedToolNames) {
        if (count <= 0) {
            return List.of();
        }

        List<ToolFamily> unrelatedFamilies = nonEmptyFamilies(domain).stream()
                .filter(family -> !family.id().equals(targetFamily.id()))
                .filter(family -> !targetFamily.decoyFamilyIds().contains(family.id()))
                .toList();
        List<ToolObject> distractors = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            ToolObject distractor = unrelatedFamilies.isEmpty()
                    ? generateFallbackDistractor(domain, usedToolNames)
                    : generateCatalogDistractor(unrelatedFamilies, usedToolNames);
            if (distractor == null) {
                distractor = generateFallbackDistractor(domain, usedToolNames);
            }
            if (distractor != null) {
                distractors.add(distractor);
            }
        }
        return distractors;
    }

    private ToolObject generateCatalogDistractor(List<ToolFamily> candidateFamilies, Set<String> usedToolNames) {
        for (int attempts = 0; attempts < 20; attempts++) {
            ToolFamily family = pickOne(candidateFamilies);
            WorkflowTemplate workflow = pickWorkflow(family);
            CatalogCaseDefinition definition = buildCaseDefinition(family, workflow, 0, 0);
            ToolObject tool = scenarioToolGenerator.generateTool(
                    definition.spec(),
                    family,
                    workflow,
                    catalog,
                    false
            ).tool();
            if (usedToolNames.add(tool.name())) {
                return tool;
            }
        }
        return null;
    }

    private ToolObject generateFallbackDistractor(Domain domain, Set<String> usedToolNames) {
        for (int attempts = 0; attempts < 20; attempts++) {
            ToolObject tool = toolSpecGenerator.generateTool(domain);
            if (usedToolNames.add(tool.name())) {
                return tool;
            }
        }
        return null;
    }

    private CatalogCaseDefinition buildCaseDefinition(ToolFamily family,
                                                      WorkflowTemplate workflow,
                                                      int semanticDecoyCount,
                                                      int randomDistractorCount) {
        List<ResolvedStep> steps = new ArrayList<>(workflow.steps().size());
        List<CapabilityStep> capabilities = new ArrayList<>(workflow.steps().size());

        for (WorkflowStepTemplate stepTemplate : workflow.steps()) {
            ResolvedStep step = new ResolvedStep(
                    pickOne(stepTemplate.verbSeeds()),
                    pickOne(stepTemplate.nounSeeds()),
                    stepTemplate.preconditionTemplate(),
                    stepTemplate.effectTemplate()
            );
            steps.add(step);
            capabilities.add(new CapabilityStep(
                    stepTemplate.role(),
                    step.verb(),
                    step.noun(),
                    CommandAbbreviator.commandName(step.verb(), step.noun()),
                    step.precondition(),
                    step.effect()
            ));
        }

        Map<String, String> cumulativeExpectedState = computeCumulativeExpectedState(steps);
        if (!cumulativeExpectedState.equals(workflow.expectedFinalState())) {
            throw new IllegalStateException("Workflow '" + workflow.id()
                    + "' expectedFinalState does not match generated cumulative state");
        }

        ResolvedScenario scenario = new ResolvedScenario(
                workflow.id(),
                workflow.intent(),
                family.domain(),
                steps,
                cumulativeExpectedState,
                Map.of(
                        "toolFamilyId", family.id(),
                        "workflowId", workflow.id()
                )
        );
        BenchmarkCaseSpec spec = new BenchmarkCaseSpec(
                workflow.intent(),
                family.domain(),
                capabilities,
                cumulativeExpectedState,
                DecoyPlan.currentDefault(semanticDecoyCount, randomDistractorCount),
                ScoringPolicy.currentDefault(),
                family.id(),
                workflow.id()
        );
        return new CatalogCaseDefinition(family, workflow, scenario, spec);
    }

    private BenchmarkCaseSpec buildSpec(ToolFamily family,
                                        WorkflowTemplate workflow,
                                        List<ResolvedStep> steps,
                                        Map<String, String> expectedState,
                                        int semanticDecoyCount,
                                        int randomDistractorCount) {
        List<CapabilityStep> capabilities = new ArrayList<>(steps.size());
        for (int index = 0; index < steps.size(); index++) {
            ResolvedStep step = steps.get(index);
            String role = workflow.steps().get(index).role();
            capabilities.add(new CapabilityStep(
                    role,
                    step.verb(),
                    step.noun(),
                    CommandAbbreviator.commandName(step.verb(), step.noun()),
                    step.precondition(),
                    step.effect()
            ));
        }

        return new BenchmarkCaseSpec(
                workflow.intent(),
                family.domain(),
                capabilities,
                expectedState,
                DecoyPlan.currentDefault(semanticDecoyCount, randomDistractorCount),
                ScoringPolicy.currentDefault(),
                family.id(),
                workflow.id()
        );
    }

    private Map<String, String> computeCumulativeExpectedState(List<ResolvedStep> steps) {
        Map<String, String> cumulative = new LinkedHashMap<>();
        for (ResolvedStep step : steps) {
            cumulative.putAll(step.effect());
        }
        return Map.copyOf(cumulative);
    }

    private Map<String, DecoyKind> buildSemanticDecoyKindMap(List<ToolObject> semanticDecoys) {
        Map<String, DecoyKind> decoyKinds = new LinkedHashMap<>();
        for (ToolObject semanticDecoy : semanticDecoys) {
            decoyKinds.put(semanticDecoy.name(), DecoyKind.SIMILAR_INTENT_WRONG_RESOURCE);
        }
        return decoyKinds;
    }

    private WorkflowTemplate pickWorkflow(ToolFamily family) {
        return catalog.workflow(pickOne(family.workflowIds()));
    }

    private List<ToolFamily> nonEmptyFamilies(Domain domain) {
        List<ToolFamily> families = catalog.familiesForDomain(domain);
        if (families.isEmpty()) {
            throw new IllegalStateException("No tool families available for domain " + domain);
        }
        return families;
    }

    private <T> T pickOne(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    /**
     * Immutable multi-step benchmark case consumed by the runner and tool callbacks.
     */
    public record BenchmarkCase(
            ResolvedScenario scenario,
            BenchmarkCaseSpec spec,
            ToolObject targetToolObject,
            List<ResolvedStep> targetSteps,
            String caseManual,
            Map<String, String> expectedState,
            List<ToolObject> distractors,
            List<ToolObject> semanticDecoys,
            List<ToolObject> randomDistractors,
            Map<String, DecoyKind> semanticDecoyKindsByToolName,
            String userQuery,
            boolean hasTrap,
            String trapCommandName,
            String recoveryCommandName
    ) {
        public BenchmarkCase(ResolvedScenario scenario,
                             ToolObject targetToolObject,
                             List<ResolvedStep> targetSteps,
                             String caseManual,
                             Map<String, String> expectedState,
                             List<ToolObject> distractors,
                             List<ToolObject> semanticDecoys,
                             List<ToolObject> randomDistractors,
                             String userQuery,
                             boolean hasTrap,
                             String trapCommandName,
                             String recoveryCommandName) {
            this(
                    scenario,
                    buildSpecFromPayload(
                            scenario,
                            targetSteps,
                            expectedState,
                            semanticDecoys == null ? 0 : semanticDecoys.size(),
                            randomDistractors == null ? 0 : randomDistractors.size()
                    ),
                    targetToolObject,
                    targetSteps,
                    caseManual,
                    expectedState,
                    distractors,
                    semanticDecoys,
                    randomDistractors,
                    buildSemanticDecoyKindMapStatic(semanticDecoys),
                    userQuery,
                    hasTrap,
                    trapCommandName,
                    recoveryCommandName
            );
        }

        private static Map<String, DecoyKind> buildSemanticDecoyKindMapStatic(List<ToolObject> semanticDecoys) {
            if (semanticDecoys == null || semanticDecoys.isEmpty()) {
                return Map.of();
            }
            Map<String, DecoyKind> decoyKinds = new LinkedHashMap<>();
            for (ToolObject semanticDecoy : semanticDecoys) {
                decoyKinds.put(semanticDecoy.name(), DecoyKind.SIMILAR_INTENT_WRONG_RESOURCE);
            }
            return decoyKinds;
        }

        private static BenchmarkCaseSpec buildSpecFromPayload(ResolvedScenario scenario,
                                                              List<ResolvedStep> targetSteps,
                                                              Map<String, String> expectedState,
                                                              int semanticDecoyCount,
                                                              int randomDistractorCount) {
            Objects.requireNonNull(scenario, "scenario must not be null");
            Objects.requireNonNull(targetSteps, "targetSteps must not be null");
            Objects.requireNonNull(expectedState, "expectedState must not be null");
            return new BenchmarkCaseSpec(
                    scenario.description(),
                    scenario.domain(),
                    targetSteps.stream()
                            .map(CapabilityStep::fromResolvedStep)
                            .toList(),
                    expectedState,
                    DecoyPlan.currentDefault(semanticDecoyCount, randomDistractorCount),
                    ScoringPolicy.currentDefault()
            );
        }

        public BenchmarkCase {
            scenario = Objects.requireNonNull(scenario, "scenario must not be null");
            spec = Objects.requireNonNull(spec, "spec must not be null");
            targetToolObject = Objects.requireNonNull(targetToolObject, "targetToolObject must not be null");
            targetSteps = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(targetSteps, "targetSteps must not be null")));
            caseManual = Objects.requireNonNull(caseManual, "caseManual must not be null");
            expectedState = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(expectedState, "expectedState must not be null")));
            distractors = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(distractors, "distractors must not be null")));
            semanticDecoys = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(semanticDecoys, "semanticDecoys must not be null")));
            randomDistractors = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(randomDistractors, "randomDistractors must not be null")));
            semanticDecoyKindsByToolName = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(semanticDecoyKindsByToolName,
                            "semanticDecoyKindsByToolName must not be null")));
            userQuery = Objects.requireNonNullElse(userQuery, "");
        }

        public String generateUserQuery() {
            return userQuery;
        }

        public List<ToolObject> allTools() {
            List<ToolObject> tools = new ArrayList<>(distractors.size() + 1);
            tools.add(targetToolObject);
            tools.addAll(distractors);
            return Collections.unmodifiableList(tools);
        }

        public ToolObject findTool(String toolName) {
            if (toolName == null || toolName.isBlank()) return null;
            return allTools().stream()
                    .filter(tool -> tool.name().equalsIgnoreCase(toolName))
                    .findFirst()
                    .orElse(null);
        }

        public org.benchmark.model.objects.CommandObject trapCommand() {
            if (!hasTrap || trapCommandName == null || trapCommandName.isBlank()) return null;
            return targetToolObject.commands().stream()
                    .filter(c -> c.name().equalsIgnoreCase(trapCommandName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record CatalogCaseDefinition(
            ToolFamily family,
            WorkflowTemplate workflow,
            ResolvedScenario scenario,
            BenchmarkCaseSpec spec
    ) {
    }
}
