package org.benchmark.factory;


import org.benchmark.model.*;
import java.util.*;

public class ToolFactory {

    private final Random random;

    public ToolFactory(long seed) {
        this.random = new Random(seed);
    }

    public ToolSpecification generateTool(ToolComplexity toolComplexity) {
        return generateTool(toolComplexity);
    }


}