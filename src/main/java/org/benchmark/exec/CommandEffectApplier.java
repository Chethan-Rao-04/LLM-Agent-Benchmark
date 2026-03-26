package org.benchmark.exec;

import org.benchmark.model.objects.EffectObject;

import java.util.List;
import java.util.Map;

/**
 * Utility for applying command effects to session state.
 */
public final class CommandEffectApplier {

    private CommandEffectApplier() {}

    /**
     * Applies effects to a session state managed by {@link SessionStateManager}.
     *
     * @param effectObjects list of effects to apply
     * @param option selected option used for {@link EffectObject#OPTION_REF} resolution
     * @param stateManager mutable session state storage
     * @param sessionId target session
     * @param toolName tool whose local environment should be mutated
     */
    public static void applyEffects(List<EffectObject> effectObjects, String option,
                                    SessionStateManager stateManager, String sessionId, String toolName) {
        if (effectObjects == null || effectObjects.isEmpty()) {
            return;
        }
        for (EffectObject effectObject : effectObjects) {
            applyEffect(effectObject, option, stateManager, sessionId, toolName);
        }
    }

    /**
     * Applies effects to a plain map representation of state.
     *
     * <p>This variant is used for expected-state computation during benchmark generation.</p>
     *
     * @param effectObjects list of effects to apply
     * @param option selected option used for {@link EffectObject#OPTION_REF} resolution
     * @param state mutable map to update
     */
    public static void applyEffectsToMap(List<EffectObject> effectObjects, String option,
                                         Map<String, String> state) {
        if (effectObjects == null || effectObjects.isEmpty()) {
            return;
        }
        for (EffectObject effectObject : effectObjects) {
            applyEffectToMap(effectObject, option, state);
        }
    }

    private static void applyEffect(EffectObject effectObject, String option,
                                    SessionStateManager stateManager, String sessionId, String toolName) {
        String before = stateManager.getToolState(sessionId, toolName, effectObject.variable());
        switch (effectObject.operation()) {
            case ASSIGN:
                stateManager.updateToolState(
                        sessionId,
                        toolName,
                        effectObject.variable(),
                        resolveValue(effectObject.valueRef(), option)
                );
                break;
            case INCREMENT:
                String currentVal = stateManager.getToolState(sessionId, toolName, effectObject.variable());
                int current = parseIntOrZero(currentVal);
                stateManager.updateToolState(sessionId, toolName, effectObject.variable(), String.valueOf(current + 1));
                break;
            case DECREMENT:
                String currentDec = stateManager.getToolState(sessionId, toolName, effectObject.variable());
                int currentDecVal = parseIntOrZero(currentDec);
                stateManager.updateToolState(sessionId, toolName, effectObject.variable(), String.valueOf(currentDecVal - 1));
                break;
            case DELETE:
                stateManager.updateToolState(sessionId, toolName, effectObject.variable(), null);
                break;
            default:
                break;
        }
        String after = stateManager.getToolState(sessionId, toolName, effectObject.variable());
        String resolvedValue = resolveValue(effectObject.valueRef(), option);
        System.out.printf(
                "[EFFECT] tool=%s var=%s op=%s valueRef=%s resolved=%s before=%s after=%s%n",
                toolName,
                effectObject.variable(),
                effectObject.operation(),
                effectObject.valueRef(),
                resolvedValue,
                before,
                after
        );
    }

    private static void applyEffectToMap(EffectObject effectObject, String option, Map<String, String> state) {
        switch (effectObject.operation()) {
            case ASSIGN:
                String resolved = resolveValue(effectObject.valueRef(), option);
                if (resolved == null) {
                    state.remove(effectObject.variable());
                } else {
                    state.put(effectObject.variable(), resolved);
                }
                break;
            case INCREMENT:
                int current = parseIntOrZero(state.get(effectObject.variable()));
                state.put(effectObject.variable(), String.valueOf(current + 1));
                break;
            case DECREMENT:
                int currentDec = parseIntOrZero(state.get(effectObject.variable()));
                state.put(effectObject.variable(), String.valueOf(currentDec - 1));
                break;
            case DELETE:
                state.remove(effectObject.variable());
                break;
            default:
                break;
        }
    }

    private static String resolveValue(String valueRef, String option) {
        if (valueRef == null) {
            return null;
        }
        if (EffectObject.OPTION_REF.equals(valueRef)) {
            if (option == null || option.isBlank()) {
                return null;
            }
            return option;
        }
        return valueRef;
    }

    /**
     * Best-effort numeric parsing used by increment/decrement effects.
     *
     * <p>Non-numeric and missing values are treated as zero so effect application
     * stays deterministic even when prior tool state is uninitialized.</p>
     */
    private static int parseIntOrZero(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
