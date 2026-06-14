package org.benchmark.exec;

import org.benchmark.model.objects.EffectObject;
import lombok.extern.slf4j.Slf4j;

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
                var -> stateManager.getToolState(sessionId, toolName, var),
                (var, val) -> stateManager.updateToolState(sessionId, toolName, var, val),
                toolName);
    }

    /**
     * Applies effects to a plain map (used for expected-state computation).
     */
    public static void applyEffectsToMap(List<EffectObject> effectObjects, String option,
                                         Map<String, String> state) {
        applyAll(effectObjects, option,
                state::get,
                (var, val) -> {
                    if (val == null) {
                        state.remove(var);
                    } else {
                        state.put(var, val);
                    }
                },
                null);
    }

    /**
     * Unified effect application logic.
     *
     * @param effectObjects effects to apply
     * @param option        selected option for $OPTION resolution
     * @param getter        reads current value of a variable
     * @param setter        writes a new value (null = delete)
     * @param toolName      tool name for logging (null to suppress logging)
     */
    private static void applyAll(List<EffectObject> effectObjects, String option,
                                  Function<String, String> getter,
                                  BiConsumer<String, String> setter,
                                  String toolName) {
        if (effectObjects == null || effectObjects.isEmpty()) {
            return;
        }
        for (EffectObject effect : effectObjects) {
            String before = getter.apply(effect.variable());
            switch (effect.operation()) {
                case ASSIGN -> setter.accept(effect.variable(), resolveValue(effect.valueRef(), option));
                case INCREMENT -> setter.accept(effect.variable(), String.valueOf(parseIntOrZero(getter.apply(effect.variable())) + 1));
                case DECREMENT -> setter.accept(effect.variable(), String.valueOf(parseIntOrZero(getter.apply(effect.variable())) - 1));
                case DELETE -> setter.accept(effect.variable(), null);
            }
            if (toolName != null) {
                String after = getter.apply(effect.variable());
                log.debug("Effect applied tool={} op={} var={} before={} after={}",
                        toolName, effect.operation(), effect.variable(), before, after);
            }
        }
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
