package org.benchmark.gen;

import org.benchmark.exec.CommandEffectApplier;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.spec.CommandSpec;
import org.benchmark.model.spec.OptionSpec;
import org.benchmark.model.spec.ToolSpec;

import java.util.*;


public class BenchmarkCaseGenerator {
    private final ToolSpecGenerator toolSpecGenerator;
    private final DocumentationGenerator documentationGenerator;
    private final Random random;
    private final UserQueryGenerator queryGenerator;

    public BenchmarkCaseGenerator() {
        this.random = new Random();
        this.toolSpecGenerator = new ToolSpecGenerator();  //can add seed for reproducibility
        this.documentationGenerator = new DocumentationGenerator();
        this.queryGenerator = new UserQueryGenerator(); // can add seed for reproducibility
    }

    public List<BenchmarkCase> generateCases(int count, int distractorCount, Domain specificDomain) {
        List<BenchmarkCase> cases = new ArrayList<>();
        List<Domain> domains = (specificDomain != null) ? List.of(specificDomain) : Arrays.asList(Domain.values());

        for (int i = 0; i < count; i++) {
            Domain domain = domains.get(random.nextInt(domains.size()));

            // Generate Target Tool
            ToolSpec targetTool = toolSpecGenerator.generateTool(domain);

            //  Pick a Random Command from the selected target tool
            List<CommandSpec> eligibleTargetCommands = targetTool.commands().stream()
                    .filter(cmd -> !cmd.commandName().equalsIgnoreCase(ToolSpecGenerator.PREP_COMMAND_NAME))
                    .toList();

            List<CommandSpec> commandPool = eligibleTargetCommands.isEmpty()
                    ? targetTool.commands()
                    : eligibleTargetCommands;
            CommandSpec targetCommand = commandPool.get(random.nextInt(commandPool.size()));




            // 1. Pick Random Target Options (Ground Truth)
    
            String targetOptionName = "" ;
            if (targetCommand.commandOptions() != null && !(targetCommand.commandOptions().isEmpty())){
                int randomIndex = random.nextInt(targetCommand.commandOptions().size());
                OptionSpec selectedOption = targetCommand.commandOptions().get(randomIndex);
                targetOptionName = selectedOption.optionName();
            }

            // CALCULATE GROUND TRUTH (Expected State)
            // Expected state delta: only keys touched by effects
            Map<String, String> expectedState = new HashMap<>();
            String optionForExpected = targetOptionName == null ? "" : targetOptionName;
            CommandEffectApplier.applyEffectsToMap(targetCommand.commandEffects(), optionForExpected, expectedState);

            // 4. Distractors
            List<ToolSpec> distractors = new ArrayList<>();
            for (int d = 0; d < distractorCount; d++) {
                distractors.add(toolSpecGenerator.generateTool(domains.get(random.nextInt(domains.size()))));
            }

            // 5. Combined Docs
            StringBuilder combinedDoc = new StringBuilder(documentationGenerator.generateDocumentation(targetTool, DocumentComplexity.CLEAN));
            for (ToolSpec dist : distractors) {
                combinedDoc.append("\n").append(documentationGenerator.generateDocumentation(dist, DocumentComplexity.CLEAN));
            }

            cases.add(new BenchmarkCase(
                    targetTool,
                    targetCommand,
                    combinedDoc.toString(),
                    targetOptionName,
                    expectedState,
                    distractors,
                    queryGenerator

            ));
        }
        return cases;
    }


    public record BenchmarkCase(
            ToolSpec targetToolObject,
            CommandSpec targetCommand,
            String combinedToolDesc,
            String targetOptionName,
            Map<String, String> expectedState,
            List<ToolSpec> distractors,
            UserQueryGenerator queryGenerator
    ) {
        public String generateUserQuery(ToolSpec targetTool) {

            // Splits the target command and assigns it as action and target, 
            // Example - Run the tool , action = run & target = tool

            String rawCmdName = targetCommand.commandName();
            String[] parts = rawCmdName.split("_");

            String action = parts.length > 0 ? capitalize(parts[0]) : "Execute";
            String target = parts.length > 1 ? capitalize(parts[1]) : targetTool.name();

            // Connects the targetOptionName -> real OptionSpec -> Natural Language Hint
            String optionHint = null;
            if (targetOptionName != null && !targetOptionName.isEmpty() && targetCommand.commandOptions() != null) {
                optionHint = targetCommand.commandOptions().stream()
                        .filter(opt -> opt.optionName().equals(targetOptionName))
                        .findFirst()
                        .map(CommandDict::hintFromOptionSpec) // Uses the utility in Command Dictionary
                        .orElse(null);
            }

            // Generate natural language query with the hint
            return queryGenerator.generate(action, target, optionHint);
        }



        // helper to capitalize
        private String capitalize(String word) {
        if (word == null || word.isEmpty()) return word;
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }

}
}
