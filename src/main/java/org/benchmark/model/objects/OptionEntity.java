package org.benchmark.model.objects;

/**
 * Immutable command option definition.
 *
 * @param optionName option flag (for example {@code --verbose})
 * @param description human-readable option description
 */
public record OptionEntity(
        String optionName,
        String description
) {}
