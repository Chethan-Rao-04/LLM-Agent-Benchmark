package org.benchmark.gen;

import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkCaseGeneratorTest {

    @Test
    void generatesCasesWithCorrectCount() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(5, 2, null);

        assertEquals(5, cases.size());
    }

    @Test
    void eachCaseHasCorrectDistractorCount() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(3, 4, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            assertEquals(4, benchmarkCase.distractors().size());
            // allTools = target + distractors
            assertEquals(5, benchmarkCase.allTools().size());
        }
    }

    @Test
    void targetToolIsFirstInAllTools() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(3, 2, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            assertEquals(benchmarkCase.targetToolObject(), benchmarkCase.allTools().get(0));
        }
    }

    @Test
    void seededGeneratorIsReproducible() {
        BenchmarkCaseGenerator gen1 = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 123L);
        BenchmarkCaseGenerator gen2 = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 123L);

        List<BenchmarkCaseGenerator.BenchmarkCase> cases1 = gen1.generateCases(3, 2, Domain.MANUFACTURING);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases2 = gen2.generateCases(3, 2, Domain.MANUFACTURING);

        assertEquals(cases1.size(), cases2.size());
        for (int i = 0; i < cases1.size(); i++) {
            assertEquals(cases1.get(i).targetToolObject().name(), cases2.get(i).targetToolObject().name());
            assertEquals(cases1.get(i).targetCommand().name(), cases2.get(i).targetCommand().name());
            assertEquals(cases1.get(i).targetOptionName(), cases2.get(i).targetOptionName());
        }
    }

    @Test
    void differentSeedsProduceDifferentCases() {
        BenchmarkCaseGenerator gen1 = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 1L);
        BenchmarkCaseGenerator gen2 = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 999L);

        List<BenchmarkCaseGenerator.BenchmarkCase> cases1 = gen1.generateCases(5, 2, null);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases2 = gen2.generateCases(5, 2, null);

        // At least one tool name should differ
        Set<String> names1 = cases1.stream().map(c -> c.targetToolObject().name()).collect(Collectors.toSet());
        Set<String> names2 = cases2.stream().map(c -> c.targetToolObject().name()).collect(Collectors.toSet());
        assertNotEquals(names1, names2);
    }

    @Test
    void fixedDomainGeneratesOnlyThatDomain() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(5, 2, Domain.NETWORK_INFRA);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            assertEquals(Domain.NETWORK_INFRA, benchmarkCase.targetToolObject().domain());
        }
    }

    @Test
    void targetCommandIsNotInitializeSystem() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(10, 2, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            assertNotEquals("initialize_system", benchmarkCase.targetCommand().name());
        }
    }

    @Test
    void documentationExistsForAllTools() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(3, 3, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            for (var tool : benchmarkCase.allTools()) {
                String doc = benchmarkCase.documentationForTool(tool.name());
                assertNotNull(doc, "Documentation missing for tool: " + tool.name());
                assertFalse(doc.isBlank());
            }
        }
    }

    @Test
    void findToolWorksWithCaseInsensitiveName() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(1, 1, null);

        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = cases.get(0);
        String targetName = benchmarkCase.targetToolObject().name();

        assertNotNull(benchmarkCase.findTool(targetName.toLowerCase()));
        assertNotNull(benchmarkCase.findTool(targetName.toUpperCase()));
        assertNull(benchmarkCase.findTool("NONEXISTENT-TOOL-999"));
        assertNull(benchmarkCase.findTool(null));
        assertNull(benchmarkCase.findTool(""));
    }

    @Test
    void multiStepCaseHasWorkflowSteps() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L, true);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(1, 1, null);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = cases.get(0);

        assertNotNull(benchmarkCase.workflowSteps());
        assertTrue(benchmarkCase.workflowSteps().size() > 1);
        assertEquals(benchmarkCase.targetCommand().name(), benchmarkCase.workflowSteps().get(benchmarkCase.workflowSteps().size() - 1).commandName());
    }

    @Test
    void workflowStepsStartWithInitializeSystem() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L, true);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(5, 1, null);
        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            assertNotNull(benchmarkCase.workflowSteps());
            assertEquals("initialize_system", benchmarkCase.workflowSteps().get(0).commandName());
        }
    }
}
