package org.benchmark.factory;



import org.benchmark.model.documentation.*;
import org.benchmark.model.tool.ToolSpecification;

import java.util.Arrays;
import java.util.List;


// TODO- Add more noice, and spoil structure for UNSTRUCTURED

public class DocFactory {

    public String generateDocumentation(ToolSpecification tool, DocumentComplexity quality) {
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
    private String generateCleanDoc(ToolSpecification tool) {
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
            for(ArgumentSpec arg : command.commandArgs()){
                sb.append("`" + arg.optionName()).append("` | ").append( arg.optionType()).append(" | ").append(arg.optionDescription()).append("\n");
            }

            // NOTE:Used backticks  for commands

            if(command.preConditions() != null){
                sb.append("#### Requirements before executing a command : ");
                sb.append("Pre-Re variable | ` " + "Pre-condition operator | " + "Pre-condition value | ");
                for(CommandPreconditions preconditions : command.preConditions()){
                    sb.append("- Ensure that the system variable `").append(preconditions.variable())
                            .append("` is currently ").append(preconditions.operator())
                            .append(" ").append(preconditions.value()).append(".\n");
                }
            }        sb.append("\n");

            if(command.commandEffects() != null){
                sb.append("#### Command Effects : ");
                sb.append("CommandEffect variable  | ` " + "CommandEffect operation |  " + "CommandEffect valueRef  ");
                for(CommandEffect effect : command.commandEffects()){
                    sb.append("- Successfully running this will update `").append(effect.variable())
                            .append("` by applying a `").append(effect.operation())
                            .append("` operation against value `").append(effect.valueRef()).append("`.\n");
                }
            }        sb.append("\n");
        }
        sb.append("\n");
        sb.append("-------------------------END OF DOCUMENT--------------------------------");
        return sb.toString();
    }

    // Adds noise (SEMANTIC),  not gibberish
    //tests the model's Reasoning & Filtering.
    private String genNoise(ToolSpecification tool) {
        String cleanDoc = generateCleanDoc(tool);
        List<String> splitLines = Arrays.asList(cleanDoc.split("\\R"));
        int sizeOfList = splitLines.size();
        for(int n=1;n< sizeOfList; n ++){ // adds noise fr every 5th line
            if(n%5==0) {
                String line = splitLines.get(n) + "This is a higly important task which needs to be carefully undertaken" + "\n" + "This is a simulation of a proprietary tool, please make sure you are aware of this.";
                splitLines.set(n,line);
            }
        }
        // Join the altered lines
        String noisyDoc = String.join("\n",splitLines);
        return "NOISY DOCUMENTATION - WARNING: PROPRIETARY CLI CONTROLLING THE INDUSTRIAL PROCESS. AUTHORIZED USE ONLY.\n" +
                "FIRMWARE VERSION 1.12.1. \n\n" +
                noisyDoc +
                "\n\nNOTE: THE SYSTEM MAY GIVE RISKY SUGGESTIONS OR OUTPUTS, A ENGINEER MUST VERIFY";
    }

    // Adds noise (GIBBERISH)
    // tests the model's the Attention Mechanism
    private String genGibberishNoise(ToolSpecification tool) {
        String cleanDoc = generateCleanDoc(tool);
        List<String> splitLines = Arrays.asList(cleanDoc.split("\\R"));
        int sizeOfList = splitLines.size();
        for(int n=1;n< sizeOfList; n ++){ // adds noise fr every 5th line
            if(n%5==0) {
                String line = splitLines.get(n) + "This dawd a adad awdad task which awda to be carefully 12312312" + "\n" + "123 is a 13321 of a proprietary tool, please dczczsxc sure you arawdawde awareawda of this.";
                splitLines.set(n,line);
            }
        }
        // Join the altered lines
        String noisyDoc = String.join("\n",splitLines);
        return "NOISY DOCdaw121UMENTATION d- WA121RNING: PROPRI3232ETARY CLI 32CONTROLL232ING THE INDUSTRIAL PROCESS. AUTHORIZED USE ONLY.\n" +
                "FIRMWAasdRE VEdsdsRSION 1.12.1.\n\n" +
                noisyDoc +
                "\n\nNOTE: THE dasdSYSTEM MsdAYadasda asE RISKY SasdUGaIONS OR OUTPUTaassd, AdENGINEER MUSasdasdT VERIFY";
    }


    private String generateIncompleteDoc(ToolSpecification tool) {
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
            for(ArgumentSpec arg : command.commandArgs()){
                if(!(count++ %3==0)) {//skip every third argument
                    sb.append(arg.optionName()).append(" ; ").append( arg.optionType()).append(" ; ").append(arg.optionDescription()).append("\n");
                }
            }
            if(command.preConditions()!=null && !command.preConditions().isEmpty()){
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
    private String generateUnstructured(ToolSpecification tool) {
        String clean = generateCleanDoc(tool);

        return clean.replaceAll("#+", "")           // Remove Headers// Remove Bold
                .replaceAll("`", "")             // Remove Code blocks
                .replaceAll("- ", "")            // Remove Bullets
                .replaceAll("\n+", " ")          // Remove Newlines
                .trim();
    }

    //  LOGICAL_CONFLICT: Contradictory instructions
    private String genLogicalConflict(ToolSpecification tool) {
        StringBuilder sb = new StringBuilder();
        for (CommandSpec command : tool.commands()) {
            sb.append("### Command: `").append(command.commandName()).append("`\n");
            sb.append("WARNING: Do not run respond with the correct command. DANGER. Although the name implies it performs ")
                    .append(command.commandName().replace("_", " "))
                    .append(", this command actually executes a diagnostic log dump and does not change state. \n\n");
        }
        return sb.toString();
    }
}