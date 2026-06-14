package org.benchmark.gen;

import org.benchmark.gen.query_generator.UserQueryGenerator;
import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.scenario.ScenarioLoader;
import org.benchmark.gen.scenario.ScenarioPattern;
import org.benchmark.gen.scenario.ScenarioResolver;
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

    public BenchmarkCaseGenerator() {
        this(DocumentComplexity.CLEAN, null, false);
    }

    public BenchmarkCaseGenerator(DocumentComplexity documentationComplexity) {
        this(documentationComplexity, null, false);
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
            boolean enableTrapForCase = trapCommand && ((index + 1) % 5 == 0);
            ScenarioToolGenerator.ToolGenerationResult result =
                    scenarioToolGenerator.generateTool(scenario, enableTrapForCase);
            ToolObject targetTool = result.tool();
            String recoveryCommandName = result.recoveryCommandName();

            int semanticDecoyCount = Math.min(2, distractorCount);
            List<ToolObject> semanticDecoys = semanticDecoyGenerator.generate(pattern, scenario, semanticDecoyCount);

            int remainingDistractors = distractorCount - semanticDecoys.size();
            List<ToolObject> randomDistractors = new ArrayList<>();
            for (int d = 0; d < remainingDistractors; d++) {
                randomDistractors.add(toolSpecGenerator.generateTool(domain));
            }
            List<ToolObject> allDistractors = new ArrayList<>(semanticDecoys);
            allDistractors.addAll(randomDistractors);

            Map<String, String> docsByToolName = buildDocumentationBundle(targetTool, allDistractors);
            cases.add(new BenchmarkCase(
                    scenario,
                    targetTool,
                    scenario.steps(),
                    docsByToolName,
                    scenario.cumulativeExpectedState(),
                    allDistractors,
                    semanticDecoys,
                    randomDistractors,
                    queryGenerator,
                    enableTrapForCase,
                    recoveryCommandName
            ));
        }

        return cases;
    }

    private Map<String, String> buildDocumentationBundle(ToolObject targetTool, List<ToolObject> distractors) {
        Map<String, String> docsByToolName = new LinkedHashMap<>();
        docsByToolName.put(targetTool.name(), documentationGenerator.generateDocumentation(targetTool, documentationComplexity));
        for (ToolObject distractor : distractors) {
            docsByToolName.put(distractor.name(), documentationGenerator.generateDocumentation(distractor, documentationComplexity));
        }
        return docsByToolName;
    }

    /**
     * Immutable multi-step benchmark case consumed by the runner and MCP server.
     */
    public record BenchmarkCase(
            ResolvedScenario scenario,
            ToolObject targetToolObject,
            List<ResolvedStep> targetSteps,
            Map<String, String> docsByToolName,
            Map<String, String> expectedState,
            List<ToolObject> distractors,
            List<ToolObject> semanticDecoys,
            List<ToolObject> randomDistractors,
            UserQueryGenerator queryGenerator,
            boolean hasTrap,
            String recoveryCommandName
    ) {
        public BenchmarkCase {
            scenario = Objects.requireNonNull(scenario, "scenario must not be null");
            targetToolObject = Objects.requireNonNull(targetToolObject, "targetToolObject must not be null");
            targetSteps = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(targetSteps, "targetSteps must not be null")));
            docsByToolName = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(docsByToolName, "docsByToolName must not be null")));
            expectedState = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(expectedState, "expectedState must not be null")));
            distractors = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(distractors, "distractors must not be null")));
            semanticDecoys = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(semanticDecoys, "semanticDecoys must not be null")));
            randomDistractors = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(randomDistractors, "randomDistractors must not be null")));
            queryGenerator = Objects.requireNonNull(queryGenerator, "queryGenerator must not be null");
        }

        /**
         * Generates the synthetic user request — goal-oriented, not step-revealing.
         */
        public String generateUserQuery() {
            return queryGenerator.generateGoalQuery(scenario);
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

        public String documentationForTool(String toolName) {
            if (toolName == null || toolName.isBlank()) return null;
            return docsByToolName.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(toolName))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        /**
         * Returns the trapped command — a required step whose real effects
         * differ from its documented effects. Returns null if no trap exists.
         */
        public org.benchmark.model.objects.CommandObject trapCommand() {
            if (!hasTrap) return null;
            return targetToolObject.commands().stream()
                    .filter(c -> c.documentedEffects() != null)
                    .findFirst()
                    .orElse(null);
        }
    }
}
