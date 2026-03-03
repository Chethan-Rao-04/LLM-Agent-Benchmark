package org.benchmark.exec;

import org.benchmark.model.spec.Effect;

import java.util.List;
import java.util.Map;

public final class CommandEffectApplier {

    private CommandEffectApplier() {}

    public static void applyEffects(List<Effect> effects, String option,
                                    SessionStateManager stateManager, String sessionId) {
        if (effects == null || effects.isEmpty()) {
            return;
        }
        for (Effect effect : effects) {
            applyEffect(effect, option, stateManager, sessionId);
        }
    }

    public static void applyEffectsToMap(List<Effect> effects, String option,
                                         Map<String, String> state) {
        if (effects == null || effects.isEmpty()) {
            return;
        }
        for (Effect effect : effects) {
            applyEffectToMap(effect, option, state);
        }
    }

    private static void applyEffect(Effect effect, String option,
                                    SessionStateManager stateManager, String sessionId) {
        switch (effect.operation()) {
            case ASSIGN:
                stateManager.updateState(sessionId, effect.variable(), resolveValue(effect.valueRef(), option));
                break;
            case INCREMENT:
                String currentVal = stateManager.getState(sessionId, effect.variable());
                int current = parseIntOrZero(currentVal);
                stateManager.updateState(sessionId, effect.variable(), String.valueOf(current + 1));
                break;
            case DECREMENT:
                String currentDec = stateManager.getState(sessionId, effect.variable());
                int currentDecVal = parseIntOrZero(currentDec);
                stateManager.updateState(sessionId, effect.variable(), String.valueOf(currentDecVal - 1));
                break;
            case DELETE:
                stateManager.updateState(sessionId, effect.variable(), null);
                break;
            default:
                break;
        }
    }

    private static void applyEffectToMap(Effect effect, String option, Map<String, String> state) {
        switch (effect.operation()) {
            case ASSIGN:
                String resolved = resolveValue(effect.valueRef(), option);
                if (resolved == null) {
                    state.remove(effect.variable());
                } else {
                    state.put(effect.variable(), resolved);
                }
                break;
            case INCREMENT:
                int current = parseIntOrZero(state.get(effect.variable()));
                state.put(effect.variable(), String.valueOf(current + 1));
                break;
            case DECREMENT:
                int currentDec = parseIntOrZero(state.get(effect.variable()));
                state.put(effect.variable(), String.valueOf(currentDec - 1));
                break;
            case DELETE:
                state.remove(effect.variable());
                break;
            default:
                break;
        }
    }

    private static String resolveValue(String valueRef, String option) {
        if (valueRef == null) {
            return null;
        }
        if (Effect.OPTION_REF.equals(valueRef)) {
            if (option == null || option.isBlank()) {
                return null;
            }
            return option;
        }
        return valueRef;
    }

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
