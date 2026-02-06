package org.benchmark.model.documentation;


import java.util.List;

public record OptionSpec(
        String optionName,           // "--target"
        String description  //"Flag to set target"
) {}

