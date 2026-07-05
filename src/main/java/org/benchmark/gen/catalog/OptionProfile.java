package org.benchmark.gen.catalog;

import java.util.List;
import java.util.Objects;

/**
 * Reusable option generation profile referenced by workflow steps.
 *
 * @param id unique profile identifier
 * @param style option emission style
 * @param allowedFlags candidate flag bases
 * @param valueMode suffix/value rendering mode for the selected flag
 */
public record OptionProfile(
        String id,
        OptionProfileStyle style,
        List<String> allowedFlags,
        OptionValueMode valueMode
) {
    public OptionProfile {
        id = Objects.requireNonNull(id, "id must not be null");
        style = Objects.requireNonNull(style, "style must not be null");
        allowedFlags = List.copyOf(Objects.requireNonNull(allowedFlags, "allowedFlags must not be null"));
        valueMode = Objects.requireNonNull(valueMode, "valueMode must not be null");
    }
}
