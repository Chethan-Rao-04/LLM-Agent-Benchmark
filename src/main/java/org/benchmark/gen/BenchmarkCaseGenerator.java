package org.benchmark.gen;

import lombok.extern.slf4j.Slf4j;
import org.benchmark.gen.catalog.CatalogTool;
import org.benchmark.gen.catalog.ToolCatalog;
import org.benchmark.gen.catalog.ToolCatalogLoader;
import org.benchmark.gen.catalog.ToolCapability;
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
@Slf4j
public class BenchmarkCaseGenerator {

    private final Random random;
    private final ToolCatalog catalog;
    private final ScenarioToolGenerator scenarioToolGenerator;
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
                                  DocumentationGenerator documentationGenerator,
                                  UserQueryGenerator queryGenerator,
                                  DocumentComplexity documentationComplexity,
                                  boolean trapCommand) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.catalog = Objects.requireNonNull(toolCatalogLoader, "toolCatalogLoader must not be null").getCatalog();
        this.scenarioToolGenerator = Objects.requireNonNull(scenarioToolGenerator, "scenarioToolGenerator must not be null");
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
            CatalogCaseDefinition targetDefinition = buildCaseDefinition(targetFamily, targetWorkflow, 0, 0);
            boolean enableTrapForCase = trapCommand && random.nextInt(5) == 0;

            FamilyToolGeneration targetGeneration = generateFamilyTools(
                    targetFamily,
                    targetDefinition,
                    enableTrapForCase
            );
            List<ToolObject> targetTools = targetGeneration.targetTools();
            ToolObject targetTool = targetTools.getFirst();
            Set<String> usedToolNames = new LinkedHashSet<>();
            targetGeneration.allFamilyTools().forEach(tool -> usedToolNames.add(tool.name()));

            List<ToolObject> semanticDecoys = targetGeneration.sameFamilyDistractors();
            if (semanticDecoys.size() > distractorCount) {
                log.warn("Selected family '{}' exposes {} same-family distractors, exceeding requested distractor-count {}",
                        targetFamily.id(), semanticDecoys.size(), distractorCount);
            }
            int remainingDistractors = Math.max(0, distractorCount - semanticDecoys.size());
            List<ToolObject> randomDistractors = generateRandomDistractors(domain, targetFamily, remainingDistractors, usedToolNames);

            BenchmarkCaseSpec spec = withDecoyCounts(targetDefinition.spec(), semanticDecoys.size(), randomDistractors.size());
            Map<String, DecoyKind> semanticDecoyKindsByToolName =
                    buildSemanticDecoyKindMap(semanticDecoys);

            List<ToolObject> allDistractors = new ArrayList<>(semanticDecoys);
            allDistractors.addAll(randomDistractors);
            String caseManual = buildCaseManual(targetTools, allDistractors, spec);
            String userQuery = queryGenerator.generateGoalQuery(spec, targetFamily, targetWorkflow);

            cases.add(new BenchmarkCase(
                    targetDefinition.scenario(),
                    spec,
                    targetTool,
                    targetTools,
                    targetGeneration.targetPath(),
                    targetDefinition.scenario().steps(),
                    caseManual,
                    targetDefinition.scenario().cumulativeExpectedState(),
                    targetDefinition.expectedStateByToolName(targetGeneration.toolNamesByCatalogId()),
                    allDistractors,
                    semanticDecoys,
                    randomDistractors,
                    semanticDecoyKindsByToolName,
                    userQuery,
                    targetGeneration.trapCommandName() != null,
                    targetGeneration.trapCommandName(),
                    targetGeneration.recoveryCommandName()
            ));
        }

        return cases;
    }

    private String buildCaseManual(List<ToolObject> targetTools, List<ToolObject> distractors, BenchmarkCaseSpec spec) {
        Set<String> targetToolNames = targetTools.stream()
                .map(ToolObject::name)
                .collect(java.util.stream.Collectors.toSet());
        List<ToolObject> tools = new ArrayList<>(distractors.size() + targetTools.size());
        tools.addAll(targetTools);
        tools.addAll(distractors);
        tools.sort(Comparator.comparing(ToolObject::name, String.CASE_INSENSITIVE_ORDER));

        StringBuilder manual = new StringBuilder("# Case Manual\n\n");
        for (int index = 0; index < tools.size(); index++) {
            if (index > 0) {
                manual.append("\n\n");
            }
            ToolObject tool = tools.get(index);
            if (targetToolNames.contains(tool.name())) {
                manual.append(documentationGenerator.generateDocumentation(tool, documentationComplexity, spec));
            } else {
                manual.append(documentationGenerator.generateDocumentation(tool, documentationComplexity));
            }
        }
        return manual.toString();
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
                .toList();
        if (unrelatedFamilies.isEmpty()) {
            throw new IllegalStateException("No unrelated catalog families available for domain "
                    + domain + " and family '" + targetFamily.id() + "'");
        }
        List<ToolObject> distractors = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            ToolObject distractor = generateCatalogDistractor(unrelatedFamilies, usedToolNames);
            if (distractor == null) {
                throw new IllegalStateException("Unable to generate " + count
                        + " unique catalog distractors for family '" + targetFamily.id() + "'");
            }
            distractors.add(distractor);
        }
        return distractors;
    }

    private ToolObject generateCatalogDistractor(List<ToolFamily> candidateFamilies, Set<String> usedToolNames) {
        for (int attempts = 0; attempts < 20; attempts++) {
            ToolFamily family = pickOne(candidateFamilies);
            CatalogTool catalogTool = pickOne(family.tools());
            ResolvedCatalogTool resolvedTool = resolveTool(catalogTool);
            BenchmarkCaseSpec spec = new BenchmarkCaseSpec(
                    family.purpose(),
                    family.domain(),
                    resolvedTool.capabilities(),
                    computeExpectedState(resolvedTool.capabilities()),
                    DecoyPlan.currentDefault(0, 0),
                    ScoringPolicy.currentDefault(),
                    family.id(),
                    ""
            );
            ToolObject tool = scenarioToolGenerator.generateCatalogTool(
                    spec,
                    family,
                    catalogTool,
                    resolvedTool.capabilities(),
                    List.of(),
                    catalog,
                    false
            ).tool();
            if (usedToolNames.add(tool.name())) {
                return tool;
            }
        }
        return null;
    }

    private FamilyToolGeneration generateFamilyTools(ToolFamily family,
                                                     CatalogCaseDefinition definition,
                                                     boolean includeTrapCommand) {
        Set<String> targetToolIds = definition.targetToolIds();
        String trapToolId = includeTrapCommand ? pickTrapToolId(definition) : "";
        Map<String, ToolObject> toolsByCatalogId = new LinkedHashMap<>();
        List<ToolObject> targetTools = new ArrayList<>();
        List<ToolObject> sameFamilyDistractors = new ArrayList<>();
        String trapCommandName = null;
        String recoveryCommandName = null;
        Set<String> usedFamilyToolNames = new LinkedHashSet<>();

        for (ResolvedCatalogTool resolvedTool : definition.resolvedTools().values()) {
            boolean targetTool = targetToolIds.contains(resolvedTool.catalogTool().id());
            List<CapabilityStep> requiredSteps = targetTool
                    ? definition.requiredStepsForTool(resolvedTool.catalogTool().id())
                    : List.of();
            ScenarioToolGenerator.ToolGenerationResult result = generateUniqueFamilyTool(
                    family,
                    definition,
                    resolvedTool,
                    requiredSteps,
                    includeTrapCommand && resolvedTool.catalogTool().id().equals(trapToolId),
                    usedFamilyToolNames
            );
            ToolObject generatedTool = result.tool();
            toolsByCatalogId.put(resolvedTool.catalogTool().id(), generatedTool);
            if (targetTool) {
                targetTools.add(generatedTool);
            } else {
                sameFamilyDistractors.add(generatedTool);
            }
            if (result.trapCommandName() != null) {
                trapCommandName = result.trapCommandName();
                recoveryCommandName = result.recoveryCommandName();
            }
        }

        Map<String, String> toolNamesByCatalogId = new LinkedHashMap<>();
        toolsByCatalogId.forEach((toolId, tool) -> toolNamesByCatalogId.put(toolId, tool.name()));
        List<TargetStep> targetPath = definition.catalogTargetPath().stream()
                .map(step -> new TargetStep(toolNamesByCatalogId.get(step.toolId()), step.commandName()))
                .toList();

        List<ToolObject> allFamilyTools = new ArrayList<>();
        allFamilyTools.addAll(targetTools);
        allFamilyTools.addAll(sameFamilyDistractors);
        return new FamilyToolGeneration(
                List.copyOf(targetTools),
                List.copyOf(sameFamilyDistractors),
                List.copyOf(allFamilyTools),
                targetPath,
                Map.copyOf(toolNamesByCatalogId),
                trapCommandName,
                recoveryCommandName
        );
    }

    private ScenarioToolGenerator.ToolGenerationResult generateUniqueFamilyTool(ToolFamily family,
                                                                                CatalogCaseDefinition definition,
                                                                                ResolvedCatalogTool resolvedTool,
                                                                                List<CapabilityStep> requiredSteps,
                                                                                boolean includeTrapCommand,
                                                                                Set<String> usedFamilyToolNames) {
        for (int attempts = 0; attempts < 20; attempts++) {
            ScenarioToolGenerator.ToolGenerationResult result = scenarioToolGenerator.generateCatalogTool(
                    definition.spec(),
                    family,
                    resolvedTool.catalogTool(),
                    resolvedTool.capabilities(),
                    requiredSteps,
                    catalog,
                    includeTrapCommand
            );
            if (usedFamilyToolNames.add(result.tool().name())) {
                return result;
            }
        }
        throw new IllegalStateException("Unable to generate unique concrete name for tool '"
                + resolvedTool.catalogTool().id() + "' in family '" + family.id() + "'");
    }

    private String pickTrapToolId(CatalogCaseDefinition definition) {
        List<String> eligibleToolIds = definition.catalogTargetPath().stream()
                .filter(step -> !definition.resolvedTools()
                        .get(step.toolId())
                        .capabilityByCommandName(step.commandName())
                        .effect()
                        .isEmpty())
                .map(CatalogTargetStep::toolId)
                .distinct()
                .toList();
        return eligibleToolIds.isEmpty() ? "" : pickOne(eligibleToolIds);
    }

    private CatalogCaseDefinition buildCaseDefinition(ToolFamily family,
                                                      WorkflowTemplate workflow,
                                                      int semanticDecoyCount,
                                                      int randomDistractorCount) {
        Map<String, ResolvedCatalogTool> resolvedTools = resolveTools(family);
        List<ResolvedStep> steps = new ArrayList<>(workflow.steps().size());
        List<CapabilityStep> capabilities = new ArrayList<>(workflow.steps().size());
        List<CatalogTargetStep> catalogTargetPath = new ArrayList<>(workflow.steps().size());
        Map<String, Map<String, String>> expectedStateByToolId = new LinkedHashMap<>();

        for (WorkflowStepTemplate stepTemplate : workflow.steps()) {
            CapabilityStep capability = resolvedTools.get(stepTemplate.toolId())
                    .capability(stepTemplate.capabilityId());
            ResolvedStep step = new ResolvedStep(
                    capability.verb(),
                    capability.noun(),
                    capability.precondition(),
                    capability.effect()
            );
            steps.add(step);
            capabilities.add(capability);
            catalogTargetPath.add(new CatalogTargetStep(stepTemplate.toolId(), capability.commandName()));
            expectedStateByToolId.computeIfAbsent(stepTemplate.toolId(), ignored -> new LinkedHashMap<>())
                    .putAll(capability.effect());
        }

        Map<String, String> cumulativeExpectedState = computeExpectedState(capabilities);

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
        return new CatalogCaseDefinition(
                family,
                workflow,
                scenario,
                spec,
                resolvedTools,
                List.copyOf(catalogTargetPath),
                copyNestedStringMap(expectedStateByToolId)
        );
    }

    private Map<String, String> computeExpectedState(List<CapabilityStep> steps) {
        Map<String, String> cumulative = new LinkedHashMap<>();
        for (CapabilityStep step : steps) {
            cumulative.putAll(step.effect());
        }
        return Map.copyOf(cumulative);
    }

    private BenchmarkCaseSpec withDecoyCounts(BenchmarkCaseSpec spec,
                                              int semanticDecoyCount,
                                              int randomDistractorCount) {
        return new BenchmarkCaseSpec(
                spec.intentDescription(),
                spec.domain(),
                spec.capabilitySteps(),
                spec.expectedFinalState(),
                DecoyPlan.currentDefault(semanticDecoyCount, randomDistractorCount),
                spec.scoringPolicy(),
                spec.toolFamilyId(),
                spec.workflowId()
        );
    }

    private Map<String, ResolvedCatalogTool> resolveTools(ToolFamily family) {
        Map<String, ResolvedCatalogTool> resolvedTools = new LinkedHashMap<>();
        for (CatalogTool tool : family.tools()) {
            resolvedTools.put(tool.id(), resolveTool(tool));
        }
        return Map.copyOf(resolvedTools);
    }

    private ResolvedCatalogTool resolveTool(CatalogTool tool) {
        Map<String, CapabilityStep> capabilitiesById = new LinkedHashMap<>();
        Set<String> usedCommandNames = new LinkedHashSet<>();
        for (ToolCapability capability : tool.capabilities()) {
            CapabilityStep step = resolveCapability(tool, capability, usedCommandNames);
            capabilitiesById.put(capability.id(), step);
        }
        return new ResolvedCatalogTool(tool, capabilitiesById);
    }

    private CapabilityStep resolveCapability(CatalogTool tool,
                                             ToolCapability capability,
                                             Set<String> usedCommandNames) {
        for (int attempts = 0; attempts < 20; attempts++) {
            String verb = pickOne(capability.verbSeeds());
            String noun = pickOne(capability.nounSeeds());
            String commandName = CommandAbbreviator.commandName(verb, noun);
            if (usedCommandNames.add(commandName)) {
                return new CapabilityStep(
                        capability.role(),
                        tool.id(),
                        verb,
                        noun,
                        commandName,
                        capability.preconditionTemplate(),
                        capability.effectTemplate(),
                        capability.optionProfile()
                );
            }
        }
        throw new IllegalStateException("Unable to generate unique command for capability '"
                + capability.id() + "' on tool '" + tool.id() + "'");
    }

    private Map<String, Map<String, String>> copyNestedStringMap(Map<String, Map<String, String>> value) {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : value.entrySet()) {
            copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private Map<String, DecoyKind> buildSemanticDecoyKindMap(List<ToolObject> semanticDecoys) {
        Map<String, DecoyKind> decoyKinds = new LinkedHashMap<>();
        for (ToolObject semanticDecoy : semanticDecoys) {
            decoyKinds.put(semanticDecoy.name(), DecoyKind.SIMILAR_INTENT_WRONG_RESOURCE);
        }
        return decoyKinds;
    }

    private WorkflowTemplate pickWorkflow(ToolFamily family) {
        List<WorkflowTemplate> workflows = catalog.workflowsForFamily(family.id());
        if (workflows.isEmpty()) {
            throw new IllegalStateException("No workflows defined for family '" + family.id() + "'");
        }
        return pickOne(workflows);
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
    public record TargetStep(String toolName, String commandName) {
        public TargetStep {
            toolName = Objects.requireNonNull(toolName, "toolName must not be null");
            commandName = Objects.requireNonNull(commandName, "commandName must not be null");
        }
    }

    public record BenchmarkCase(
            ResolvedScenario scenario,
            BenchmarkCaseSpec spec,
            ToolObject targetToolObject,
            List<ToolObject> targetTools,
            List<TargetStep> targetPath,
            List<ResolvedStep> targetSteps,
            String caseManual,
            Map<String, String> expectedState,
            Map<String, Map<String, String>> expectedStateByTool,
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
                    List.of(targetToolObject),
                    buildTargetPath(targetToolObject, targetSteps),
                    targetSteps,
                    caseManual,
                    expectedState,
                    Map.of(targetToolObject.name(), expectedState),
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

        public BenchmarkCase(ResolvedScenario scenario,
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
                             String recoveryCommandName) {
            this(
                    scenario,
                    spec,
                    targetToolObject,
                    List.of(targetToolObject),
                    buildTargetPath(targetToolObject, spec),
                    targetSteps,
                    caseManual,
                    expectedState,
                    Map.of(targetToolObject.name(), expectedState),
                    distractors,
                    semanticDecoys,
                    randomDistractors,
                    semanticDecoyKindsByToolName,
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

        private static List<TargetStep> buildTargetPath(ToolObject targetToolObject, List<ResolvedStep> targetSteps) {
            return targetSteps.stream()
                    .map(step -> new TargetStep(
                            targetToolObject.name(),
                            CommandAbbreviator.commandName(step.verb(), step.noun())))
                    .toList();
        }

        private static List<TargetStep> buildTargetPath(ToolObject targetToolObject, BenchmarkCaseSpec spec) {
            return spec.capabilitySteps().stream()
                    .map(step -> new TargetStep(targetToolObject.name(), step.commandName()))
                    .toList();
        }

        public BenchmarkCase {
            scenario = Objects.requireNonNull(scenario, "scenario must not be null");
            spec = Objects.requireNonNull(spec, "spec must not be null");
            targetTools = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(targetTools, "targetTools must not be null")));
            if (targetTools.isEmpty()) {
                throw new IllegalArgumentException("targetTools must not be empty");
            }
            targetToolObject = targetTools.getFirst();
            targetPath = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(targetPath, "targetPath must not be null")));
            targetSteps = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(targetSteps, "targetSteps must not be null")));
            caseManual = Objects.requireNonNull(caseManual, "caseManual must not be null");
            expectedState = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(expectedState, "expectedState must not be null")));
            Map<String, Map<String, String>> expectedStateCopy = new LinkedHashMap<>();
            Objects.requireNonNull(expectedStateByTool, "expectedStateByTool must not be null")
                    .forEach((toolName, state) -> expectedStateCopy.put(
                            toolName,
                            Collections.unmodifiableMap(new LinkedHashMap<>(state))));
            expectedStateByTool = Collections.unmodifiableMap(expectedStateCopy);
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
            List<ToolObject> tools = new ArrayList<>(distractors.size() + targetTools.size());
            tools.addAll(targetTools);
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
            return targetTools.stream()
                    .flatMap(tool -> tool.commands().stream())
                    .filter(command -> command.name().equalsIgnoreCase(trapCommandName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record CatalogCaseDefinition(
            ToolFamily family,
            WorkflowTemplate workflow,
            ResolvedScenario scenario,
            BenchmarkCaseSpec spec,
            Map<String, ResolvedCatalogTool> resolvedTools,
            List<CatalogTargetStep> catalogTargetPath,
            Map<String, Map<String, String>> expectedStateByToolId
    ) {
        private Set<String> targetToolIds() {
            return catalogTargetPath.stream()
                    .map(CatalogTargetStep::toolId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        private List<CapabilityStep> requiredStepsForTool(String toolId) {
            return catalogTargetPath.stream()
                    .filter(step -> step.toolId().equals(toolId))
                    .map(step -> resolvedTools.get(toolId).capabilityByCommandName(step.commandName()))
                    .toList();
        }

        private Map<String, Map<String, String>> expectedStateByToolName(Map<String, String> toolNamesByCatalogId) {
            Map<String, Map<String, String>> expectedByToolName = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, String>> entry : expectedStateByToolId.entrySet()) {
                expectedByToolName.put(toolNamesByCatalogId.get(entry.getKey()), entry.getValue());
            }
            return Map.copyOf(expectedByToolName);
        }
    }

    private record ResolvedCatalogTool(
            CatalogTool catalogTool,
            Map<String, CapabilityStep> capabilitiesById
    ) {
        private List<CapabilityStep> capabilities() {
            return List.copyOf(capabilitiesById.values());
        }

        private CapabilityStep capability(String capabilityId) {
            CapabilityStep capability = capabilitiesById.get(capabilityId);
            if (capability == null) {
                throw new IllegalStateException("Unknown capability '" + capabilityId
                        + "' for tool '" + catalogTool.id() + "'");
            }
            return capability;
        }

        private CapabilityStep capabilityByCommandName(String commandName) {
            return capabilitiesById.values().stream()
                    .filter(capability -> capability.commandName().equals(commandName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unknown command '" + commandName
                            + "' for tool '" + catalogTool.id() + "'"));
        }
    }

    private record CatalogTargetStep(String toolId, String commandName) {
    }

    private record FamilyToolGeneration(
            List<ToolObject> targetTools,
            List<ToolObject> sameFamilyDistractors,
            List<ToolObject> allFamilyTools,
            List<TargetStep> targetPath,
            Map<String, String> toolNamesByCatalogId,
            String trapCommandName,
            String recoveryCommandName
    ) {
    }
}
