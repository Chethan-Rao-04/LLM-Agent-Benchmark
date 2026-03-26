package org.benchmark.gen.doc_generator;

import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.PreconditionObject;
import org.benchmark.model.objects.ToolObject;

import java.util.Arrays;
import java.util.List;

/**
 * Produces benchmark documentation variants ranging from clean manuals to
 * intentionally degraded or contradictory references.
 */
public class DocumentationGenerator {

    /**
     * Generates documentation for a tool according to the requested complexity profile.
     *
     * @param tool tool whose documentation should be produced
     * @param quality degradation profile to apply
     * @return generated documentation text
     */
    public String generateDocumentation(ToolObject tool, DocumentComplexity quality) {
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

    /**
     * Generates fully structured documentation.
     */
    private String generateCleanDoc(ToolObject tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Documentation for the tool :  ").append(tool.name());
        sb.append("## This tool belongs to the ").append(tool.domain()).append(" industry").append("\n\n");
        sb.append("## Tool Description : ").append(tool.description()).append("\n\n");

        sb.append("## List of commands supported by the tool : ");
        for(CommandObject command : tool.commands()){
            sb.append("### Command Name : ").append(command.name()).append("\n");;
            sb.append("Command Description : ").append(command.description()).append("\n");

            sb.append("#### Command Arguments : ").append("\n");
            sb.append("Name | " + "Type | " + "Description | ");
            sb.append("| :--- | :--- | :--- |\n");
            for (OptionEntity arg : command.commandOptions()) {
                sb.append("`" + arg.optionName()).append(" | ").append(arg.description()).append("\n");
            }



            if (command.commandPreConditions() != null) {
                sb.append("#### Requirements before executing a command : ");
                sb.append("Pre-Re variable | ` " + "Pre-condition operator | " + "Pre-condition value | ");
                for (PreconditionObject preconditions : command.commandPreConditions()) {
                    sb.append("- Ensure that the system variable `").append(preconditions.variable())
                            .append("` is currently ").append(preconditions.operator())
                            .append(" ").append(preconditions.value()).append(".\n");
                }
            }        sb.append("\n");

            if (command.commandEffectObjects() != null) {
                sb.append("#### Command Effects : ");
                sb.append("Effect variable  | ` " + "Effect operation |  " + "Effect valueRef  ");
                for (EffectObject effectObject : command.commandEffectObjects()) {
                    String valueRef = formatValueRef(effectObject.valueRef());
                    sb.append("- Successfully running this will update `").append(effectObject.variable())
                            .append("` by applying a `").append(effectObject.operation())
                            .append("` operation against value `").append(valueRef).append("`.\n");
                }
            }        sb.append("\n");
        }
        sb.append("\n");
        sb.append("-------------------------END OF DOCUMENT FOR THIS TOOL " + tool.name() +" -------------------------------");
        return sb.toString();
    }

    /**
     * Converts internal effect value references into user-facing text.
     */
    private String formatValueRef(String valueRef) {
        if (EffectObject.OPTION_REF.equals(valueRef)) {
            return "selected option";
        }
        return valueRef;
    }

    /**
     * Injects semantically irrelevant but coherent noise into the clean documentation.
     */
    private String genNoise(ToolObject tool) {
        String cleanDoc = generateCleanDoc(tool);
        List<String> splitLines = Arrays.asList(cleanDoc.split("\\R"));
        int sizeOfList = splitLines.size();
        for(int n=1;n< sizeOfList; n ++){
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

    /**
     * Injects random gibberish to test the model's ability to ignore token noise.
     */
    private String genGibberishNoise(ToolObject tool) {
        String cleanDoc = generateCleanDoc(tool);
        List<String> splitLines = Arrays.asList(cleanDoc.split("\\R"));
        int sizeOfList = splitLines.size();
        for(int n=1;n< sizeOfList; n ++){
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

    /**
     * Omits periodic command and option details to simulate incomplete manuals.
     */
    private String generateIncompleteDoc(ToolObject tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("# Documentation for the tool :  ").append(tool.name());
        sb.append("belonging to the ").append(tool.domain()).append(" industry").append("\n\n");
        sb.append("# Tool Description : ").append(tool.description()).append("\n");
        int count =1;
        for (CommandObject command : tool.commands()) {
            if(!(count++ %4==0)) { // skip every fourth command
                sb.append("Command Name : ").append(command.name()).append("\n");;
                sb.append("Command Description : ").append(command.description()).append("\n");
            }
            for (OptionEntity arg : command.commandOptions()) {
                if(!(count++ %3==0)) {//skip every third argument
                    sb.append(arg.optionName()).append(" ; ").append(arg.description()).append("\n");
                }
            }
            if(command.commandPreConditions()!=null && !command.commandPreConditions().isEmpty()){
                sb.append("\n").append("Note: There are certain pre-conditions that needs to be followed \n");
            }
            if(command.commandEffectObjects()!=null && !command.commandEffectObjects().isEmpty()){
                sb.append("\n").append("Warning: this command may change internal state.\n");
            }

        }
        return sb.toString();
    }

    /**
     * Removes structure and formatting from otherwise clean documentation.
     */
    private String generateUnstructured(ToolObject tool) {
        String clean = generateCleanDoc(tool);

        return clean.replaceAll("#+", "")
                .replaceAll("`", "")
                .replaceAll("- ", "")
                .replaceAll("\n+", " ")
                .trim();
    }

    /**
     * Injects contradictory guidance to test conflict resolution.
     */
    private String genLogicalConflict(ToolObject tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Documentation for the tool (with known inconsistencies)\n\n");
        sb.append("WARNING: The following manual has conflicting statements due to outdated revisions.\n");
        sb.append("When in doubt, you must prioritize the *most recent* and *explicit* safety instructions.\n\n");

        for (CommandObject command : tool.commands()) {
            String cmdName = command.name();

            sb.append("### Command: `").append(cmdName).append("`\n\n");
            sb.append("Official description: ").append(command.description()).append("\n\n");

            sb.append("-WARNING: Legacy note (possibly outdated): Several older operators claim that `")
                    .append(cmdName)
                    .append("` never changes any state and might cause a system  failure.\n");

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
