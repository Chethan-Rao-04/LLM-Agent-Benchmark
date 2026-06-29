package org.benchmark.gen;

import org.benchmark.gen.query_generator.UserQueryGenerator;
import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.scenario.ScenarioLoader;
import org.benchmark.gen.scenario.ScenarioPattern;
import org.benchmark.gen.scenario.ScenarioResolver;
import org.benchmark.gen.spec.BenchmarkCaseSpec;
import org.benchmark.gen.spec.CapabilityStep;
import org.benchmark.gen.spec.DecoyKind;
import org.benchmark.gen.spec.DecoyPlan;
import org.benchmark.gen.spec.ScoringPolicy;
import org.benchmark.gen.tool_generator.CommandDict;
import org.benchmark.gen.tool_generator.ScenarioToolGenerator;
import org.benchmark.gen.tool_generator.SemanticDecoyGenerator;
import org.benchmark.gen.tool_generator.ToolSpecGenerator;
import org.benchmark.gen.doc_generator.DocumentationGenerator;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.objects.ToolObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Comparator;

/**
 * Generates multi-step scenario-driven benchmark cases.
 *
 * <p>Each case is backed by a scenario pattern loaded from YAML. The generator
 * resolves template variables, builds a target tool with scenario-aligned commands,
 * creates semantic decoys from the same vocabulary pool, and adds random distractors.</p>
 */
public class BenchmarkCaseGenerator {

    private final Random random;
    private final ScenarioLoader scenarioLoader;
    private final ScenarioResolver scenarioResolver;
    private final ScenarioToolGenerator scenarioToolGenerator;
    private final SemanticDecoyGenerator semanticDecoyGenerator;
    private final ToolSpecGenerator toolSpecGenerator;
    private final DocumentationGenerator documentationGenerator;
    private final UserQueryGenerator queryGenerator;
    private final DocumentComplexity documentationComplexity;
    private final boolean trapCommand;

    /**
     * Creates the benchmark case generator from explicit collaborators.
     *
     * @param random shared random source
     * @param scenarioLoader scenario template loader
     * @param scenarioResolver template resolver
     * @param scenarioToolGenerator target tool generator
     * @param semanticDecoyGenerator semantic distractor generator
     * @param toolSpecGenerator random distractor generator
     * @param documentationGenerator documentation renderer
     * @param queryGenerator user query generator
     * @param documentationComplexity documentation degradation profile
     * @param trapCommand whether trap commands may be injected into generated target tools
     */
    public BenchmarkCaseGenerator(Random random,
                                  ScenarioLoader scenarioLoader,
                                  ScenarioResolver scenarioResolver,
                                  ScenarioToolGenerator scenarioToolGenerator,
                                  SemanticDecoyGenerator semanticDecoyGenerator,
                                  ToolSpecGenerator toolSpecGenerator,
                                  DocumentationGenerator documentationGenerator,
                                  UserQueryGenerator queryGenerator,
                                  DocumentComplexity documentationComplexity,
                                  boolean trapCommand) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.scenarioLoader = Objects.requireNonNull(scenarioLoader, "scenarioLoader must not be null");
        this.scenarioResolver = Objects.requireNonNull(scenarioResolver, "scenarioResolver must not be null");
        this.scenarioToolGenerator = Objects.requireNonNull(scenarioToolGenerator, "scenarioToolGenerator must not be null");
        this.semanticDecoyGenerator = Objects.requireNonNull(semanticDecoyGenerator, "semanticDecoyGenerator must not be null");
        this.toolSpecGenerator = Objects.requireNonNull(toolSpecGenerator, "toolSpecGenerator must not be null");
        this.documentationGenerator = Objects.requireNonNull(documentationGenerator, "documentationGenerator must not be null");
        this.queryGenerator = Objects.requireNonNull(queryGenerator, "queryGenerator must not be null");
        this.documentationComplexity = documentationComplexity == null ? DocumentComplexity.CLEAN : documentationComplexity;
        this.trapCommand = trapCommand;
    }

    /**
     * Creates a self-contained generator with default collaborators and no trap commands.
     *
     * @param documentationComplexity documentation degradation profile
     * @param seed optional seed for deterministic generation
     */
    public BenchmarkCaseGenerator(DocumentComplexity documentationComplexity, Long seed) {
        this(documentationComplexity, seed, false);
    }

    /**
     * Creates a self-contained generator with default collaborators.
     *
     * @param documentationComplexity documentation degradation profile
     * @param seed optional seed for deterministic generation
     * @param trapCommand whether generated cases may include a trap and recovery path
     */
    public BenchmarkCaseGenerator(DocumentComplexity documentationComplexity, Long seed, boolean trapCommand) {
        this(createRandom(seed), documentationComplexity, trapCommand);
    }

    private BenchmarkCaseGenerator(Random random, DocumentComplexity documentationComplexity, boolean trapCommand) {
        this(
                random,
                new ScenarioLoader(),
                new ScenarioResolver(random),
                new ScenarioToolGenerator(random, new CommandDict(random)),
                new SemanticDecoyGenerator(random, new CommandDict(random)),
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
        List<ScenarioPattern> patterns = scenarioLoader.getPatterns();

        for (int index = 0; index < count; index++) {
            Domain domain = availableDomains.get(random.nextInt(availableDomains.size()));
            ScenarioPattern pattern = patterns.get(random.nextInt(patterns.size()));

            ResolvedScenario scenario = scenarioResolver.resolve(pattern, domain);
            int semanticDecoyCount = Math.min(2, distractorCount);
            BenchmarkCaseSpec generationSpec = BenchmarkCaseSpec.fromScenario(scenario, semanticDecoyCount, 0);
            boolean enableTrapForCase = trapCommand && random.nextInt(5) == 0;
            ScenarioToolGenerator.ToolGenerationResult result =
                    scenarioToolGenerator.generateTool(generationSpec, enableTrapForCase);
            ToolObject targetTool = result.tool();
            String trapCommandName = result.trapCommandName();
            String recoveryCommandName = result.recoveryCommandName();
            boolean hasTrap = trapCommandName != null;

            List<ToolObject> semanticDecoys = semanticDecoyGenerator.generate(pattern, scenario, generationSpec.decoyPlan());

            int remainingDistractors = distractorCount - semanticDecoys.size();
            List<ToolObject> randomDistractors = new ArrayList<>();
            for (int d = 0; d < remainingDistractors; d++) {
                randomDistractors.add(toolSpecGenerator.generateTool(domain));
            }
            List<ToolObject> allDistractors = new ArrayList<>(semanticDecoys);
            allDistractors.addAll(randomDistractors);

            BenchmarkCaseSpec spec = BenchmarkCaseSpec.fromScenario(
                    scenario, semanticDecoys.size(), randomDistractors.size());
            Map<String, DecoyKind> semanticDecoyKindsByToolName =
                    buildSemanticDecoyKindMap(semanticDecoys, spec.decoyPlan());
            String caseManual = buildCaseManual(targetTool, allDistractors, spec);
            cases.add(new BenchmarkCase(
                    scenario,
                    spec,
                    targetTool,
                    scenario.steps(),
                    caseManual,
                    scenario.cumulativeExpectedState(),
                    allDistractors,
                    semanticDecoys,
                    randomDistractors,
                    semanticDecoyKindsByToolName,
                    queryGenerator,
                    hasTrap,
                    trapCommandName,
                    recoveryCommandName
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

    private Map<String, DecoyKind> buildSemanticDecoyKindMap(List<ToolObject> semanticDecoys, DecoyPlan decoyPlan) {
        Map<String, DecoyKind> decoyKinds = new LinkedHashMap<>();
        List<DecoyKind> plannedKinds = decoyPlan.semanticDecoyKinds();
        for (int index = 0; index < semanticDecoys.size(); index++) {
            DecoyKind kind = plannedKinds.isEmpty()
                    ? DecoyKind.SIMILAR_INTENT_WRONG_RESOURCE
                    : plannedKinds.get(Math.min(index, plannedKinds.size() - 1));
            decoyKinds.put(semanticDecoys.get(index).name(), kind);
        }
        return decoyKinds;
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
            UserQueryGenerator queryGenerator,
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
                             UserQueryGenerator queryGenerator,
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
                            randomDistractors == null ? 0 : randomDistractors.size()),
                    targetToolObject,
                    targetSteps,
                    caseManual,
                    expectedState,
                    distractors,
                    semanticDecoys,
                    randomDistractors,
                    buildSemanticDecoyKindMapStatic(semanticDecoys),
                    queryGenerator,
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
            DecoyPlan plan = DecoyPlan.currentDefault(semanticDecoys.size(), 0);
            List<DecoyKind> plannedKinds = plan.semanticDecoyKinds();
            for (int index = 0; index < semanticDecoys.size(); index++) {
                DecoyKind kind = plannedKinds.isEmpty()
                        ? DecoyKind.SIMILAR_INTENT_WRONG_RESOURCE
                        : plannedKinds.get(Math.min(index, plannedKinds.size() - 1));
                decoyKinds.put(semanticDecoys.get(index).name(), kind);
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

        /**
         * Normalizes benchmark case collections into immutable snapshots so downstream
         * runtime code can treat each generated case as stable metadata.
         */
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
            queryGenerator = Objects.requireNonNull(queryGenerator, "queryGenerator must not be null");
        }

        /**
         * Generates the synthetic user request. It is goal-oriented, not step-revealing.
         */
        public String generateUserQuery() {
            return queryGenerator.generateGoalQuery(spec);
        }

        /**
         * Returns the target tool together with all distractor tools in execution order.
         *
         * @return immutable list of all tools exposed to the agent
         */
        public List<ToolObject> allTools() {
            List<ToolObject> tools = new ArrayList<>(distractors.size() + 1);
            tools.add(targetToolObject);
            tools.addAll(distractors);
            return Collections.unmodifiableList(tools);
        }

        /**
         * Finds a tool by name using the same case-insensitive lookup semantics as the runtime tool layer.
         *
         * @param toolName requested tool name
         * @return matching tool, or {@code null} when the case does not expose that tool
         */
        public ToolObject findTool(String toolName) {
            if (toolName == null || toolName.isBlank()) return null;
            return allTools().stream()
                    .filter(tool -> tool.name().equalsIgnoreCase(toolName))
                    .findFirst()
                    .orElse(null);
        }

        /**
         * Returns the trapped command. Its real effects
         * differ from its documented effects. Returns null if no trap exists.
         */
        public org.benchmark.model.objects.CommandObject trapCommand() {
            if (!hasTrap || trapCommandName == null || trapCommandName.isBlank()) return null;
            return targetToolObject.commands().stream()
                    .filter(c -> c.name().equalsIgnoreCase(trapCommandName))
                    .findFirst()
                    .orElse(null);
        }
    }
}
