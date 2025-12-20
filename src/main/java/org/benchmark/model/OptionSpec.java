package org.benchmark.model;

public record OptionSpec (
    String optionName,        // for example, "--target"
    String optionType,
    boolean isRequired,
    String optionDescription){
}
