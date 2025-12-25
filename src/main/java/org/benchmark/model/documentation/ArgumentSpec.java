package org.benchmark.model.documentation;

public record OptionSpec (
    String optionName,        // for example, "--target"
    String optionType,
    boolean isRequired,
    String optionDescription){
}
