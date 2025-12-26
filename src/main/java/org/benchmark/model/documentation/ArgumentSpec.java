package org.benchmark.model.documentation;

public record ArgumentSpec(
    String optionName,        // for example, "--target"
    String optionType,
    boolean isRequired,
    String optionDescription){
}
