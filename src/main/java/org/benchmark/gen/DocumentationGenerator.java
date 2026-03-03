package org.benchmark.gen;

import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.spec.CommandSpec;
import org.benchmark.model.spec.Effect;
import org.benchmark.model.spec.OptionSpec;
import org.benchmark.model.spec.Precondition;
import org.benchmark.model.spec.ToolSpec;

import java.util.Arrays;
import java.util.List;



public class DocumentationGenerator {

    public String generateDocumentation(ToolSpec tool, DocumentComplexity quality) {
        if(quality == DocumentComplexity.CLEAN) {
            return this.generateCleanDoc(tool);
        }else if(quality == DocumentComplexity.GIBBERISH_NOISE){
            return this.genGibberishNoise(tool);
        }
        else if(quality == DocumentComplexity.CONTEXTUAL_NOISE){
            return this.genNoise(tool);
        }

        else if(quality == DocumentComplexity.INCOMPLETE){
            return this.generateIncompleteDoc(tool);
        }
        else if(quality == DocumentComplexity.UNSTRUCTURED){
            return this.generateUnstructured(tool);
        }
        else if(quality == DocumentComplexity.LOGICAL_CONFLICT){
            return this.genLogicalConflict(tool);
        }
        return "ERROR IN GENERATING DOCUMENTATION";

    }
    // Documentation Generation for Complexity level - Clean
    private String generateCleanDoc(ToolSpec tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Documentation for the tool :  ").append(tool.name());
        sb.append("## This tool belongs to the ").append(tool.domain()).append(" industry").append("\n\n");
        sb.append("## Tool Description : ").append(tool.description()).append("\n\n");

        sb.append("## List of commands supported by the tool : ");
        for(CommandSpec command : tool.commands()){
            sb.append("### Command Name : ").append(command.commandName()).append("\n");;
            sb.append("Command Description : ").append(command.description()).append("\n");

            sb.append("#### Command Arguments : ").append("\n");
            sb.append("Name | " + "Type | " + "Description | ");
            sb.append("| :--- | :--- | :--- |\n");
            for (OptionSpec arg : command.commandOptions()) {
                sb.append("`" + arg.optionName()).append(" | ").append(arg.description()).append("\n");
            }



            if (command.commandPreConditions() != null) {
                sb.append("#### Requirements before executing a command : ");
                sb.append("Pre-Re variable | ` " + "Pre-condition operator | " + "Pre-condition value | ");
                for (Precondition preconditions : command.commandPreConditions()) {
                    sb.append("- Ensure that the system variable `").append(preconditions.variable())
                            .append("` is currently ").append(preconditions.operator())
                            .append(" ").append(preconditions.value()).append(".\n");
                }
            }        sb.append("\n");

            if (command.commandEffects() != null) {
                sb.append("#### Command Effects : ");
                sb.append("Effect variable  | ` " + "Effect operation |  " + "Effect valueRef  ");
                for (Effect effect : command.commandEffects()) {
                    String valueRef = formatValueRef(effect.valueRef());
                    sb.append("- Successfully running this will update `").append(effect.variable())
                            .append("` by applying a `").append(effect.operation())
                            .append("` operation against value `").append(valueRef).append("`.\n");
                }
            }        sb.append("\n");
        }
        sb.append("\n");
        sb.append("-------------------------END OF DOCUMENT FOR THIS TOOL " + tool.name() +" -------------------------------");
        return sb.toString();
    }

    private String formatValueRef(String valueRef) {
        if (Effect.OPTION_REF.equals(valueRef)) {
            return "selected option";
        }
        return valueRef;
    }

    // Adds noise (SEMANTIC),  not gibberish
    //tests the model's Reasoning & Filtering.
    private String genNoise(ToolSpec tool) {
        String cleanDoc = generateCleanDoc(tool);
        List<String> splitLines = Arrays.asList(cleanDoc.split("\\R"));
        int sizeOfList = splitLines.size();
        for(int n=1;n< sizeOfList; n ++){ // adds noise fr every 5th line
            if(n%5==0) {
                String line = splitLines.get(n) + "\n\n" + "========================================================"
                        + "This is a higly important task which needs to be carefully undertaken" + "\n" + "This is a simulation of a proprietary tool, please make sure you are aware of this.";
                splitLines.set(n,line);
            }
        }
        // Join the altered lines
        String noisyDoc = String.join("\n",splitLines);
        return "NOISY DOCUMENTATION - WARNING: PROPRIETARY CLI CONTROLLING THE INDUSTRIAL PROCESS. AUTHORIZED USE ONLY.\n" +
               "\n\n" +
                noisyDoc +
                "\n\nNOTE: THE SYSTEM MAY GIVE RISKY SUGGESTIONS OR OUTPUTS, A ENGINEER MUST VERIFY";
    }

    // Adds noise (GIBBERISH)
    // tests the model's the Attention Mechanism
    private String genGibberishNoise(ToolSpec tool) {
        String cleanDoc = generateCleanDoc(tool);
        List<String> splitLines = Arrays.asList(cleanDoc.split("\\R"));
        int sizeOfList = splitLines.size();
        for(int n=1;n< sizeOfList; n ++){ // adds noise fr every 5th line
            if(n%3==0) {
                String line = splitLines.get(n) + "This dawd a adad awdad  which awda to be carefully 12312312";
                splitLines.set(n,line);
            }
        }
        // Join the altered lines
        String noisyDoc = String.join("\n",splitLines);
        return "NOISY DOCdaw121UMENTATION d- WA121RNING: PROPRI3232ETARY CLI 32CONTROLL232ING THE INDUSTRIAL PROCESS. AUTHORIZED USE ONLY" +
                "FIRMWAasdRE VEdsdsRSION 1.12.1.\n" +
                noisyDoc +
                "\nNOTE: THE dasdSYSTEM MsdAYadasda asE RISKY SasdUGaIONS OR OUTPUTaassd, AdENGINEER MUSasdasdT VERIFY";
    }


    private String generateIncompleteDoc(ToolSpec tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("# Documentation for the tool :  ").append(tool.name());
        sb.append("belonging to the ").append(tool.domain()).append(" industry").append("\n\n");
        sb.append("# Tool Description : ").append(tool.description()).append("\n");
        int count =1;
        for (CommandSpec command : tool.commands()) {
            if(!(count++ %4==0)) { // skip every fourth command
                sb.append("Command Name : ").append(command.commandName()).append("\n");;
                sb.append("Command Description : ").append(command.description()).append("\n");
            }
            for (OptionSpec arg : command.commandOptions()) {
                if(!(count++ %3==0)) {//skip every third argument
                    sb.append(arg.optionName()).append(" ; ").append(arg.description()).append("\n");
                }
            }
            if(command.commandPreConditions()!=null && !command.commandPreConditions().isEmpty()){
                sb.append("\n").append("Note: There are certain pre-conditions that needs to be followed \n");
            }
            if(command.commandEffects()!=null && !command.commandEffects().isEmpty()){
                sb.append("\n").append("Warning: this command may change internal state.\n");
            }

        }
        return sb.toString();
    }

    //  STRUCTURAL_LOSS:
    // This tests if the model depends on Markdown formatters
    private String generateUnstructured(ToolSpec tool) {
        String clean = generateCleanDoc(tool);

        // makes it unstructured by removing line breaks , back ticks for code bits etc.
        return clean.replaceAll("#+", "")
                .replaceAll("`", "")
                .replaceAll("- ", "")
                .replaceAll("\n+", " ")
                .trim();
    }

    //  LOGICAL_CONFLICT: Contradictory instructions to confuse model, checks if it listens to warnings or something similar
    private String genLogicalConflict(ToolSpec tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Documentation for the tool (with known inconsistencies)\n\n");
        sb.append("WARNING: The following manual has conflicting statements due to outdated revisions.\n");
        sb.append("When in doubt, you must prioritize the *most recent* and *explicit* safety instructions.\n\n");

        for (CommandSpec command : tool.commands()) {
            String cmdName = command.commandName();
            String humanName = cmdName.replace("_", " ");

            sb.append("### Command: `").append(cmdName).append("`\n\n");
            sb.append("Official description: ").append(command.description()).append("\n\n");


            // Conflicting statement about behavior
            sb.append("-WARNING: Legacy note (possibly outdated): Several older operators claim that `")
                    .append(cmdName)
                    .append("` never changes any state and might cause a system  failure.\n");

            // Conflicting statement about selecting command
            sb.append("- Conflicting manual entry: For safety reasons, some internal docs suggest always calling ")
                    .append("`")
                    .append("diagnostic_")
                    .append(tool.name().toLowerCase())
                    .append("` instead of `")
                    .append(cmdName)
                    .append("`, regardless of the requested operation.\n");


            sb.append("\n\n");
        }

        sb.append("NOTE: Different sections above may contradict each other .\n");
        sb.append("Your job is to resolve conflicts logically and choose the command that best matches the user request.\n");

        return sb.toString();
    }
}
