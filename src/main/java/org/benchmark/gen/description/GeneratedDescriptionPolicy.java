package org.benchmark.gen.description;

import org.benchmark.model.enums.Domain;
import org.benchmark.model.objects.EffectObject;

import java.util.List;
import java.util.Map;

/**
 * Central wording policy for generated benchmark manuals.
 *
 * <p>The descriptions stay truthful at the operational level while leaving exact
 * state requirements and effects to the structured manual sections.</p>
 */
public final class GeneratedDescriptionPolicy {

    private GeneratedDescriptionPolicy() {
    }

    public static String toolDescription(Domain domain) {
        return switch (domain) {
            case MANUFACTURING ->
                    "Provides documented maintenance state management procedures for manufacturing equipment.";
            case NETWORK_INFRA ->
                    "Provides documented operational state management procedures for network infrastructure.";
        };
    }

    public static String toolDescription(String purpose) {
        String normalized = purpose == null ? "" : purpose.trim();
        if (normalized.isBlank()) {
            return "Provides documented operating procedures for the generated tool.";
        }
        if (normalized.endsWith(".")) {
            return "Provides documented procedures for " + normalized;
        }
        return "Provides documented procedures for " + normalized + ".";
    }

    public static String commandDescription(Map<String, String> preconditions, List<EffectObject> documentedEffects) {
        return commandDescription(null, preconditions, documentedEffects);
    }

    public static String commandDescription(String intent,
                                            Map<String, String> preconditions,
                                            List<EffectObject> documentedEffects) {
        boolean hasPreconditions = preconditions != null && !preconditions.isEmpty();
        boolean hasDocumentedEffects = documentedEffects != null && !documentedEffects.isEmpty();
        String stage = normalizedIntent(intent);

        if (!hasDocumentedEffects) {
            return "Runs the documented " + stage + " check with no documented state update.";
        }
        if (hasPreconditions) {
            return "Runs the documented " + stage + " transition after required state checks pass.";
        }
        return "Runs the documented " + stage + " transition from the current tool state.";
    }

    private static String normalizedIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            return "procedure";
        }
        return intent.replace('_', ' ').toLowerCase();
    }
}
