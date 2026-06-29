package org.benchmark.gen.doc_generator;

import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationGeneratorTest {

    private DocumentationGenerator generator;
    private ToolObject testTool;

    @BeforeEach
    void setUp() {
        generator = new DocumentationGenerator();
        testTool = new ToolObject(
                "TEST-TOOL-001",
                "A test tool for unit testing",
                Domain.MANUFACTURING,
                List.of(
                        new CommandObject(
                                "start_process",
                                List.of(new OptionEntity("--verbose", "Enable verbose logging", true)),
                                "Start the manufacturing process",
                                List.of(new EffectObject("counter", EffectOp.INCREMENT, null)),
                                Map.of()
                        ),
                        new CommandObject(
                                "stop_process",
                                List.of(),
                                "Stop the manufacturing process",
                                List.of(new EffectObject("status", EffectOp.ASSIGN, "stopped")),
                                Map.of()
                        )
                ),
                Map.of("counter", "int", "status", "string")
        );
    }

    @ParameterizedTest
    @EnumSource(DocumentComplexity.class)
    void allComplexityModesProduceNonEmptyOutput(DocumentComplexity complexity) {
        String doc = generator.generateDocumentation(testTool, complexity);
        assertNotNull(doc);
        assertFalse(doc.isBlank());
    }

    @Test
    void cleanDocContainsToolName() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.CLEAN);
        assertTrue(doc.contains("TEST-TOOL-001"));
    }

    @Test
    void cleanDocContainsCommandNames() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.CLEAN);
        assertTrue(doc.contains("start_process"));
        assertTrue(doc.contains("stop_process"));
    }

    @Test
    void cleanDocContainsOptions() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.CLEAN);
        assertTrue(doc.contains("--verbose"));
        assertTrue(doc.contains("required"));
    }

    @Test
    void cleanDocContainsEffects() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.CLEAN);
        assertTrue(doc.contains("counter"));
        assertTrue(doc.contains("INCREMENT"));
    }

    @Test
    void unstructuredRemovesMarkdownHeaders() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.UNSTRUCTURED);
        assertFalse(doc.contains("#"));
    }

    @Test
    void logicalConflictContainsStructuredDetailsAndContradictions() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.LOGICAL_CONFLICT);
        // Should contain the real structured sections
        assertTrue(doc.contains("start_process"));
        assertTrue(doc.contains("--verbose"));
        assertTrue(doc.contains("counter"));
        // Should also contain contradictory prose
        assertTrue(doc.contains("no longer modifies any state"));
        assertTrue(doc.contains("removed in the latest release"));
    }

    @Test
    void incompleteDocIsNonEmpty() {
        String incomplete = generator.generateDocumentation(testTool, DocumentComplexity.INCOMPLETE);
        assertFalse(incomplete.isBlank());
    }

    @Test
    void incompleteDocRetainsAllCommandNames() {
        String incomplete = generator.generateDocumentation(testTool, DocumentComplexity.INCOMPLETE);
        assertTrue(incomplete.contains("start_process"));
        assertTrue(incomplete.contains("stop_process"));
    }

    @Test
    void incompleteDocMarksThatDetailsAreOmitted() {
        String incomplete = generator.generateDocumentation(testTool, DocumentComplexity.INCOMPLETE);
        assertTrue(incomplete.contains("partial") || incomplete.contains("not documented"));
    }

    @Test
    void targetDocumentationUsesSemanticDescriptions() {
        BenchmarkCaseGenerator benchmarkGenerator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase =
                benchmarkGenerator.generateCases(1, 2, Domain.MANUFACTURING).getFirst();

        for (var step : benchmarkCase.spec().capabilitySteps()) {
            String commandName = CommandAbbreviator.commandName(step.verb(), step.noun());
            assertTrue(benchmarkCase.caseManual().contains("## " + commandName));
            assertFalse(benchmarkCase.caseManual().contains(
                    "Description: Executes the " + commandName + " operation."));
        }
    }

}
