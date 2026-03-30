package org.benchmark.gen;

import org.benchmark.model.objects.WorkflowStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkflowStepTest {

    @Test
    void workflowStepInitializesCorrectly() {
        WorkflowStep step = new WorkflowStep("cmd_name", "opt_name", "A test step");
        assertNotNull(step);
        assertEquals("cmd_name", step.commandName());
        assertEquals("opt_name", step.optionName());
        assertEquals("A test step", step.description());
    }
}
