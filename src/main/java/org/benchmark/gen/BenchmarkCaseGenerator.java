package org.benchmark.gen;

import org.benchmark.exec.CommandEffectApplier;
import org.benchmark.gen.doc_generator.DocumentationGenerator;
import org.benchmark.gen.query_generator.UserQueryGenerator;
import org.benchmark.gen.tool_generator.ToolSpecGenerator;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;
import org.benchmark.gen.tool_generator.CommandDict;

import java.util.*;

/**
 * Generates synthetic benchmark cases consisting of one target tool and a set
 * of distractor tools plus degraded documentation.
 */
public class BenchmarkCaseGenerator {
    private final ToolSpecGenerator toolSpecGenerator;
    private final DocumentationGenerator documentationGenerator;
    private final Random random;
    private final UserQueryGenerator queryGenerator;
    private final DocumentComplexity documentationComplexity;

    public BenchmarkCaseGenerator() {
        this(DocumentComplexity.CLEAN);
    }

    /**
     * Creates a generator configured for the given documentation complexity.
     *
     * @param documentationComplexity complexity profile applied to generated docs
     */
    public BenchmarkCaseGenerator(DocumentComplexity documentationComplexity) {
        this.random = new Random();
        this.toolSpecGenerator = new ToolSpecGenerator();
        this.documentationGenerator = new DocumentationGenerator();
        this.queryGenerator = new UserQueryGenerator();
        this.documentationComplexity = documentationComplexity == null ? DocumentComplexity.CLEAN : documentationComplexity;
    }

    /**
     * Generates a batch of benchmark cases.
     *
     * @param count number of cases to create
     * @param distractorCount number of distractor tools per case
     * @param specificDomain optional fixed domain; {@code null} samples all domains
     * @return generated benchmark cases
     */
    public List<BenchmarkCase> generateCases(int count, int distractorCount, Domain specificDomain) {
        List<BenchmarkCase> cases = new ArrayList<>();
        List<Domain> domains = (specificDomain != null) ? List.of(specificDomain) : Arrays.asList(Domain.values());

        for (int i = 0; i < count; i++) {
            Domain domain = domains.get(random.nextInt(domains.size()));

            ToolObject targetTool = toolSpecGenerator.generateTool(domain);

            List<CommandObject> eligibleTargetCommands = targetTool.commands().stream()
                    .filter(cmd -> !cmd.name().equalsIgnoreCase(ToolSpecGenerator.PREP_COMMAND_NAME))
                    .toList();

            List<CommandObject> commandPool = eligibleTargetCommands.isEmpty()
                    ? targetTool.commands()
                    : eligibleTargetCommands;
            CommandObject targetCommand = commandPool.get(random.nextInt(commandPool.size()));

            String targetOptionName = "";
            if (targetCommand.commandOptions() != null && !targetCommand.commandOptions().isEmpty()) {
                int randomIndex = random.nextInt(targetCommand.commandOptions().size());
                OptionEntity selectedOption = targetCommand.commandOptions().get(randomIndex);
                targetOptionName = selectedOption.optionName();
            }

            Map<String, String> expectedState = new HashMap<>();
            String optionForExpected = targetOptionName == null ? "" : targetOptionName;
            CommandEffectApplier.applyEffectsToMap(targetCommand.commandEffectObjects(), optionForExpected, expectedState);

            List<ToolObject> distractors = new ArrayList<>();
            for (int d = 0; d < distractorCount; d++) {
                distractors.add(toolSpecGenerator.generateTool(domains.get(random.nextInt(domains.size()))));
            }

            Map<String, String> docsByToolName = new LinkedHashMap<>();
            docsByToolName.put(targetTool.name(), documentationGenerator.generateDocumentation(targetTool, documentationComplexity));

            StringBuilder combinedDoc = new StringBuilder(docsByToolName.get(targetTool.name()));
            for (ToolObject dist : distractors) {
                String doc = documentationGenerator.generateDocumentation(dist, documentationComplexity);
                docsByToolName.put(dist.name(), doc);
                combinedDoc.append("\n").append(doc);
            }

            cases.add(new BenchmarkCase(
                    targetTool,
                    targetCommand,
                    combinedDoc.toString(),
                    docsByToolName,
                    targetOptionName,
                    expectedState,
                    distractors,
                    queryGenerator

            ));
        }
        return cases;
    }

    /**
     * Immutable benchmark-case representation consumed by the runner and MCP server.
     *
     * @param targetToolObject tool that should satisfy the user request
     * @param targetCommand target command to execute
     * @param combinedToolDesc concatenated documentation across target and distractors
     * @param docsByToolName per-tool documentation lookup map
     * @param targetOptionName expected option for the target command
     * @param expectedState expected state delta after successful execution
     * @param distractors distractor tools included in the case
     * @param queryGenerator helper used to convert command metadata into user requests
     */
    public record BenchmarkCase(
            ToolObject targetToolObject,
            CommandObject targetCommand,
            String combinedToolDesc,
            Map<String, String> docsByToolName,
            String targetOptionName,
            Map<String, String> expectedState,
            List<ToolObject> distractors,
            UserQueryGenerator queryGenerator
    ) {
        /**
         * Generates the user-facing request corresponding to this benchmark case.
         *
         * @param targetTool target tool used for fallback phrasing
         * @return synthetic natural-language user request
         */
        public String generateUserQuery(ToolObject targetTool) {
            String rawCmdName = targetCommand.name();
            String[] parts = rawCmdName.split("_");

            String action = parts.length > 0 ? capitalize(parts[0]) : "Execute";
            String target = parts.length > 1 ? capitalize(parts[1]) : targetTool.name();

            String optionHint = null;
            if (targetOptionName != null && !targetOptionName.isEmpty() && targetCommand.commandOptions() != null) {
                optionHint = targetCommand.commandOptions().stream()
                        .filter(opt -> opt.optionName().equals(targetOptionName))
                        .findFirst()
                        .map(CommandDict::hintFromOptionSpec)
                        .orElse(null);
            }

            return queryGenerator.generate(action, target, optionHint);
        }

        /**
         * Returns the target tool followed by all distractor tools.
         *
         * @return ordered list of all tools visible in this case
         */
        public List<ToolObject> allTools() {
            List<ToolObject> tools = new ArrayList<>();
            tools.add(targetToolObject);
            tools.addAll(distractors);
            return tools;
        }

        /**
         * Finds a tool in this case by case-insensitive name.
         *
         * @param toolName tool name to search for
         * @return matching tool or {@code null}
         */
        public ToolObject findTool(String toolName) {
            if (toolName == null || toolName.isBlank()) {
                return null;
            }
            return allTools().stream()
                    .filter(tool -> tool.name().equalsIgnoreCase(toolName))
                    .findFirst()
                    .orElse(null);
        }

        /**
         * Returns documentation for a tool in this case by case-insensitive name.
         *
         * @param toolName tool name to search for
         * @return matching documentation text or {@code null}
         */
        public String documentationForTool(String toolName) {
            if (toolName == null || toolName.isBlank()) {
                return null;
            }
            return docsByToolName.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(toolName))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        private String capitalize(String word) {
            if (word == null || word.isEmpty()) {
                return word;
            }
            return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
        }
    }
}
