package org.benchmark;

import lombok.extern.slf4j.Slf4j;
import org.benchmark.factory.ToolFactory;
import org.benchmark.factory.DocFactory;
import org.benchmark.model.tool.*;
import org.benchmark.model.documentation.*;
import org.benchmark.task.NLQueryGenerator;

import java.util.*;


@Slf4j
public class ToolKitGenerator {
    private final ToolFactory toolFactory;
    private final DocFactory docFactory;
    private final Random random;
    private final NLQueryGenerator queryGenerator;

    public ToolKitGenerator() {
        int seed = 45;
        this.random = new Random(seed);
        this.toolFactory = new ToolFactory(seed);
        this.docFactory = new DocFactory();
        this.queryGenerator = new NLQueryGenerator(seed);
    }

    public List<ToolKitObjectModel> generateToolKit(ToolComplexity complexity, int count, int distractorCount, Domain specificDomain) {
        List<ToolKitObjectModel> toolKitList = new ArrayList<>();
        List<Domain> domains = (specificDomain != null) ? List.of(specificDomain) : Arrays.asList(Domain.values());

        for (int i = 0; i < count; i++) {
            Domain domain = domains.get(random.nextInt(domains.size()));

            // Generate Target Tool
            ToolSpecification targetTool = toolFactory.generateTool(complexity, domain);

            //  Pick a Random Command from the selected target tool
            int noOfCommands = targetTool.commands().size();
            CommandObject targetCommand = targetTool.commands().get(random.nextInt(noOfCommands));




            // 1. Pick Random Target Options (Ground Truth)
            // TODO enable option description as well only for generating documentation, not as ground truth
            String targetOptionName = "" ;
            if (targetCommand.commandOptions() != null && !(targetCommand.commandOptions().isEmpty())){
                int randomIndex = random.nextInt(targetCommand.commandOptions().size());
                OptionSpec selectedOption = targetCommand.commandOptions().get(randomIndex);
                targetOptionName = selectedOption.optionName();
            }

            // CALCULATE GROUND TRUTH (Expected State)
            // This reads the 'CommandEffect' logic to see what SHOULD happen
            Map<String, String> expectedState = new HashMap<>();
            if (targetCommand.commandEffects() != null) {


                for (CommandEffect effect : targetCommand.commandEffects()) {
                    switch (effect.operation().toUpperCase()) {
                        case "ASSIGN":
                            expectedState.put(effect.variable(), effect.valueRef());
                            break;
                        // Note: We assume initial state is 0 for numeric ops if not previously set
                        case "INCREMENT":
                            expectedState.put(effect.variable(), "1"); // 0 + 1
                            break;
                        case "DECREMENT":
                            expectedState.put(effect.variable(), "-1"); // 0 - 1
                            break;
                        case "DELETE":
                            expectedState.put(effect.variable(), null);
                            break;
                            
                        default:
                            log.debug("Operation {} not yet implemented", effect.operation());
                            break;
                    }
                }
            }
            
            else{
                log.info("The target command has no effects(expected state) set");
            }

            // 4. Distractors
            List<ToolSpecification> distractors = new ArrayList<>();
            for (int d = 0; d < distractorCount; d++) {
                distractors.add(toolFactory.generateTool(complexity, domains.get(random.nextInt(domains.size()))));
            }

            // 5. Combined Docs ( TODO: Add Different documentation levels later)
            StringBuilder combinedDoc = new StringBuilder(docFactory.generateDocumentation(targetTool, DocumentComplexity.CLEAN));
            for (ToolSpecification dist : distractors) {
                combinedDoc.append("\n").append(docFactory.generateDocumentation(dist, DocumentComplexity.CLEAN));
            }

            toolKitList.add(new ToolKitObjectModel(
                    targetTool,
                    targetCommand,
                    combinedDoc.toString(),
                    targetOptionName,
                    expectedState,
                    distractors,
                    complexity,
                    queryGenerator

            ));
        }
        return toolKitList;
    }


    public record ToolKitObjectModel(
            ToolSpecification targetToolObject,
            CommandObject targetCommand,
            String combinedToolDesc,
            String targetOptionName,
            Map<String, String> expectedState,
            List<ToolSpecification> distractors,
            ToolComplexity complexity,
            NLQueryGenerator queryGenerator
    ) {
        public String generateUserQuery(ToolSpecification targetTool) {

            String rawCmdName = targetCommand.commandName();
            String[] parts = rawCmdName.split("_");

            String action = parts.length > 0 ? capitalize(parts[0]) : "Execute";
            String target = parts.length > 1 ? capitalize(parts[1]) : targetTool.name();

            // Generate natural language query
            return queryGenerator.generate(complexity, action, target);
        }


        // small helper function for generating user query
        private String capitalize(String word) {
        if (word == null || word.isEmpty()) return word;
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }

}
}