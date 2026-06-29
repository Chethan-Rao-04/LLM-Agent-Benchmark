package org.benchmark.model.objects;

/**
 * Immutable command option definition.
 *
 * @param optionName option flag (for example {@code --verbose})
 * @param description human-readable option description
 * @param required whether the runtime requires this option to execute the command
 */
public record OptionEntity(
        String optionName,
        String description,
        boolean required
) {

    /**
     * Creates an optional command option.
     *
     * @param optionName option flag (for example {@code --verbose})
     * @param description human-readable option description
     */
    public OptionEntity(String optionName, String description) {
        this(optionName, description, false);
    }
}
