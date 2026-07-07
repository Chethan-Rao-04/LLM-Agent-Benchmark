package org.benchmark.gen.doc_generator;

import org.benchmark.gen.description.GeneratedDescriptionPolicy;
import org.benchmark.gen.spec.BenchmarkCaseSpec;
import org.benchmark.gen.spec.CapabilityStep;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.StateScope;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.StateRequirement;
import org.benchmark.model.objects.ToolObject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Produces tool documentation ranging from clean manuals to degraded references.
 */
public class DocumentationGenerator {
    /**
     * Generates documentation for one tool using the requested profile.
     *
     * @param tool tool whose documentation should be generated
     * @param quality degradation profile to apply
     * @return generated documentation text
     */
    public String generateDocumentation(ToolObject tool, DocumentComplexity quality) {
        return switch (quality) {
            case CLEAN -> generateCleanDoc(tool);
            case INCOMPLETE -> generateIncompleteDoc(tool);
            case UNSTRUCTURED -> generateUnstructured(tool);
            case LOGICAL_CONFLICT -> generateLogicalConflict(tool);
        };
    }

    /**
     * Generates target-tool documentation from the semantic case specification.
     */
    public String generateDocumentation(ToolObject tool, DocumentComplexity quality, BenchmarkCaseSpec spec) {
        return generateDocumentation(applySemanticDescriptions(tool, spec), quality);
    }

    private ToolObject applySemanticDescriptions(ToolObject tool, BenchmarkCaseSpec spec) {
        if (spec == null || spec.capabilitySteps().isEmpty()) {
            return tool;
        }

        Map<String, CapabilityStep> stepsByCommandName = spec.capabilitySteps().stream()
                .collect(Collectors.toMap(
                        CapabilityStep::commandName,
                        step -> step,
                        (first, ignored) -> first
                ));
        List<CommandObject> commands = tool.commands().stream()
                .map(command -> {
                    CapabilityStep step = stepsByCommandName.get(command.name());
                    if (step == null) {
                        return command;
                    }
                    return new CommandObject(
                            command.name(),
                            command.commandOptions(),
                            GeneratedDescriptionPolicy.commandDescription(
                                    step.intent(),
                                    command.scopedPreconditions(),
                                    documentedEffects(command)
                            ),
                            command.commandEffectObjects(),
                            command.preconditions(),
                            command.documentedEffects(),
                            command.scopedPreconditions()
                    );
                })
                .toList();
        return new ToolObject(
                tool.name(),
                tool.description(),
                tool.domain(),
                commands,
                tool.stateVariables()
        );
    }

    private List<EffectObject> documentedEffects(CommandObject command) {
        return command.documentedEffects() != null
                ? command.documentedEffects()
                : command.commandEffectObjects();
    }

    /**
     * Builds the clean, structured documentation used as the baseline profile.
     */
    private String generateCleanDoc(ToolObject tool) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Tool Documentation: ").append(tool.name()).append("\n\n");
        builder.append("Domain: ").append(tool.domain()).append("\n");
        builder.append("Description: ").append(tool.description()).append("\n\n");
        builder.append("Supported Commands:\n");

        for (CommandObject command : tool.commands()) {
            builder.append("\n## ").append(command.name()).append("\n");
            builder.append("Description: ").append(command.description()).append("\n");
            appendPreconditionsSection(builder, "Requires local state", command.scopedPreconditions(), StateScope.TOOL);
            appendPreconditionsSection(builder, "Requires shared state", command.scopedPreconditions(), StateScope.SHARED);
            appendOptionsSection(builder, command.commandOptions());
            appendEffectsSection(builder, "Produces local state", command, StateScope.TOOL);
            appendEffectsSection(builder, "Produces shared state", command, StateScope.SHARED);
        }

        builder.append("\n--- End of documentation for ").append(tool.name()).append(" ---");
        return builder.toString();
    }

    private void appendPreconditionsSection(StringBuilder builder,
                                            String title,
                                            List<StateRequirement> preconditions,
                                            StateScope scope) {
        builder.append(title).append(":\n");
        List<StateRequirement> scopedPreconditions = preconditions == null
                ? List.of()
                : preconditions.stream()
                .filter(precondition -> precondition.scope() == scope)
                .toList();
        if (scopedPreconditions.isEmpty()) {
            builder.append("- none\n");
            return;
        }

        for (StateRequirement precondition : scopedPreconditions) {
            builder.append("- requires `")
                    .append(precondition.variable())
                    .append("` to equal `")
                    .append(precondition.value())
                    .append("`\n");
        }
    }

    private void appendOptionsSection(StringBuilder builder, List<OptionEntity> options) {
        builder.append("Options:\n");
        if (options == null || options.isEmpty()) {
            builder.append("- none\n");
            return;
        }

        for (OptionEntity option : options) {
            builder.append("- `")
                    .append(option.optionName())
                    .append("`: ")
                    .append(option.description())
                    .append("\n");
        }
    }

    private void appendEffectsSection(StringBuilder builder, String title, CommandObject command, StateScope scope) {
        List<EffectObject> effects = command.documentedEffects() != null
                ? command.documentedEffects()
                : command.commandEffectObjects();
        appendEffectsSection(builder, title, effects, scope);
    }

    private void appendEffectsSection(StringBuilder builder, String title, List<EffectObject> effects, StateScope scope) {
        builder.append(title).append(":\n");
        List<EffectObject> scopedEffects = effects == null
                ? List.of()
                : effects.stream()
                .filter(effect -> effect.scope() == scope)
                .toList();
        if (scopedEffects.isEmpty()) {
            builder.append("- no state change documented\n");
            return;
        }

        for (EffectObject effect : scopedEffects) {
            builder.append("- updates `")
                    .append(effect.variable())
                    .append("` with `")
                    .append(effect.operation())
                    .append("`");

            String valueRef = formatValueRef(effect.valueRef());
            if (valueRef != null) {
                builder.append(" using `").append(valueRef).append("`");
            }
            builder.append("\n");
        }
    }

    private String formatValueRef(String valueRef) {
        if (valueRef == null) {
            return null;
        }
        if (EffectObject.OPTION_REF.equals(valueRef)) {
            return "selected option";
        }
        return valueRef;
    }

    /**
     * Drops parts of the clean documentation to simulate an incomplete manual.
     */
    private String generateIncompleteDoc(ToolObject tool) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Tool Documentation: ").append(tool.name()).append("\n");
        builder.append("Domain: ").append(tool.domain()).append("\n");
        builder.append("Description: ").append(tool.description()).append("\n");
        builder.append("Note: this reference is partial and may omit some command details.\n\n");

        int commandIndex = 0;
        for (CommandObject command : tool.commands()) {
            builder.append("Command: ").append(command.name()).append("\n");
            builder.append("Description: ").append(command.description()).append("\n");

            int detailVariant = commandIndex++ % 3;
            if (detailVariant == 0) {
                appendIncompleteEffects(builder, command);
            } else if (detailVariant == 1) {
                appendIncompleteOptions(builder, command.commandOptions());
            } else {
                appendIncompletePreconditions(builder, command.preconditions());
            }

            builder.append("\n");
        }
        return builder.toString();
    }

    private void appendIncompleteEffects(StringBuilder builder, CommandObject command) {
        List<EffectObject> effects = command.documentedEffects() != null
                ? command.documentedEffects()
                : command.commandEffectObjects();
        if (effects == null || effects.isEmpty()) {
            builder.append("Effects: not documented.\n");
            return;
        }

        builder.append("Effects: updates ")
                .append(effects.get(0).variable())
                .append(".\n");
    }

    private void appendIncompleteOptions(StringBuilder builder, List<OptionEntity> options) {
        if (options == null || options.isEmpty()) {
            builder.append("Options: not documented.\n");
            return;
        }

        builder.append("Options: available flags ");
        for (int index = 0; index < options.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append('`').append(options.get(index).optionName()).append('`');
        }
        builder.append(". Details are partial.\n");
    }

    private void appendIncompletePreconditions(StringBuilder builder, Map<String, String> preconditions) {
        if (preconditions == null || preconditions.isEmpty()) {
            builder.append("Preconditions: not documented.\n");
            return;
        }

        Map.Entry<String, String> firstEntry = preconditions.entrySet().iterator().next();
        builder.append("Preconditions: requires `")
                .append(firstEntry.getKey())
                .append("` = `")
                .append(firstEntry.getValue())
                .append("`.\n");
    }

    /**
     * Removes most structure and formatting from the clean documentation.
     */
    private String generateUnstructured(ToolObject tool) {
        return generateCleanDoc(tool)
                .replaceAll("#+", "")
                .replace("`", "")
                .replaceAll("\\n+", " ")
                .trim();
    }

    /**
     * Builds documentation with real contradictions: correct details interleaved
     * with plausible-but-wrong option values, effect descriptions, and preconditions.
     * The model must resolve conflicts using the structured sections, not the prose notes.
     */
    private String generateLogicalConflict(ToolObject tool) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Tool Documentation: ").append(tool.name()).append("\n\n");
        builder.append("Domain: ").append(tool.domain()).append("\n");
        builder.append("Description: ").append(tool.description()).append("\n\n");
        builder.append("Supported Commands:\n");

        for (CommandObject command : tool.commands()) {
            builder.append("\n## ").append(command.name()).append("\n");
            builder.append("Description: ").append(command.description()).append("\n");

            appendPreconditionsSection(builder, "Requires local state", command.scopedPreconditions(), StateScope.TOOL);
            appendPreconditionsSection(builder, "Requires shared state", command.scopedPreconditions(), StateScope.SHARED);
            appendOptionsSection(builder, command.commandOptions());
            appendEffectsSection(builder, "Produces local state", command, StateScope.TOOL);
            appendEffectsSection(builder, "Produces shared state", command, StateScope.SHARED);

            // Inject contradictory prose that conflicts with the structured sections above
            builder.append("\n> Note (v2.1 migration guide): `").append(command.name())
                    .append("` no longer modifies any state variables. Effects listed above are outdated.\n");

            if (command.commandOptions() != null && !command.commandOptions().isEmpty()) {
                OptionEntity firstOpt = command.commandOptions().get(0);
                builder.append("> Errata: The option `").append(firstOpt.optionName())
                        .append("` was removed in the latest release. Do not use it.\n");
            }

            if (command.scopedPreconditions() != null && !command.scopedPreconditions().isEmpty()) {
                builder.append("> Correction: this command has no preconditions; ")
                        .append("the requirements listed above are from a deprecated version.\n");
            }
        }

        builder.append("\n--- End of documentation for ").append(tool.name()).append(" ---");
        return builder.toString();
    }

}
