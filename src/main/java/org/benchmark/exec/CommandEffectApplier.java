package org.benchmark.exec;

import org.benchmark.model.objects.EffectObject;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Utility for applying command effects to tool state.
 *
 * <p>Both {@link SessionStateManager}-backed execution and plain {@link Map}-backed
 * expected-state computation share a single code path.</p>
 */
@Slf4j
public final class CommandEffectApplier {

    private CommandEffectApplier() {}

    /**
     * Applies effects to a session state managed by {@link SessionStateManager}.
     */
    public static void applyEffects(List<EffectObject> effectObjects, String option,
                                    SessionStateManager stateManager, String sessionId, String toolName) {
        applyAll(effectObjects, option,
                effect -> stateManager.getState(sessionId, toolName, effect.scope(), effect.variable()),
                (effect, val) -> stateManager.updateState(sessionId, toolName, effect.scope(), effect.variable(), val),
                toolName);
    }

    /**
     * Applies effects to a plain map (used for expected-state computation).
     */
    public static void applyEffectsToMap(List<EffectObject> effectObjects, String option,
                                         Map<String, String> state) {
        applyAll(effectObjects, option,
                effect -> state.get(effect.variable()),
                (effect, val) -> {
                    if (val == null) {
                        state.remove(effect.variable());
                    } else {
                        state.put(effect.variable(), val);
                    }
                },
                null);
    }

    /**
     * Applies effects to separate local and shared maps (used for expected-state computation).
     */
    public static void applyEffectsToMaps(List<EffectObject> effectObjects, String option,
                                          Map<String, String> toolState,
                                          Map<String, String> sharedState) {
        Map<String, String> safeToolState = toolState == null ? new LinkedHashMap<>() : toolState;
        Map<String, String> safeSharedState = sharedState == null ? new LinkedHashMap<>() : sharedState;
        applyAll(effectObjects, option,
                effect -> stateFor(effect, safeToolState, safeSharedState).get(effect.variable()),
                (effect, val) -> {
                    Map<String, String> state = stateFor(effect, safeToolState, safeSharedState);
                    if (val == null) {
                        state.remove(effect.variable());
                    } else {
                        state.put(effect.variable(), val);
                    }
                },
                null);
    }

    /**
     * Unified effect application logic.
     *
     * @param effectObjects effects to apply
     * @param option        selected option for $OPTION resolution
     * @param getter        reads current value of an effect variable
     * @param setter        writes a new value (null = delete)
     * @param toolName      tool name for logging (null to suppress logging)
     */
    private static void applyAll(List<EffectObject> effectObjects, String option,
                                  Function<EffectObject, String> getter,
                                  BiConsumer<EffectObject, String> setter,
                                  String toolName) {
        if (effectObjects == null || effectObjects.isEmpty()) {
            return;
        }
        for (EffectObject effect : effectObjects) {
            String before = getter.apply(effect);
            switch (effect.operation()) {
                case ASSIGN -> setter.accept(effect, resolveValue(effect.valueRef(), option));
                case INCREMENT -> setter.accept(effect, String.valueOf(parseIntOrZero(getter.apply(effect)) + 1));
                case DECREMENT -> setter.accept(effect, String.valueOf(parseIntOrZero(getter.apply(effect)) - 1));
                case DELETE -> setter.accept(effect, null);
            }
            if (toolName != null) {
                String after = getter.apply(effect);
                log.debug("Effect applied tool={} scope={} op={} var={} before={} after={}",
                        toolName, effect.scope(), effect.operation(), effect.variable(), before, after);
            }
        }
    }

    private static Map<String, String> stateFor(EffectObject effect,
                                                Map<String, String> toolState,
                                                Map<String, String> sharedState) {
        return switch (effect.scope()) {
            case TOOL -> toolState;
            case SHARED -> sharedState;
        };
    }

    private static String resolveValue(String valueRef, String option) {
        if (valueRef == null) {
            return null;
        }
        if (EffectObject.OPTION_REF.equals(valueRef)) {
            return (option == null || option.isBlank()) ? null : option;
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
            log.warn("Invalid integer state value '{}'; defaulting to 0 before applying numeric effect", value);
            return 0;
        }
    }
}
