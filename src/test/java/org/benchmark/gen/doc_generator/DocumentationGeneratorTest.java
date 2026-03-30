package org.benchmark.gen.doc_generator;

import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.ToolObject;

import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.PreconditionObject;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.enums.ConditionOp;
import org.benchmark.model.enums.EffectOp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
                                List.of(new OptionEntity("--verbose", "Enable verbose logging")),
                                "Start the manufacturing process",
                                List.of(new PreconditionObject("system_status", ConditionOp.EQ, "RUNNING")),
                                List.of(new EffectObject("counter", EffectOp.INCREMENT, null))
                        ),
                        new CommandObject(
                                "stop_process",
                                List.of(),
                                "Stop the manufacturing process",
                                List.of(),
                                List.of(new EffectObject("system_status", EffectOp.ASSIGN, "SHUTDOWN"))
                        )
                ),
                Map.of("system_status", "string", "counter", "int")
        );
    }

    @ParameterizedTest
    @EnumSource(DocumentComplexity.class)
    void allComplexityModesProduceNonEmptyOutput(DocumentComplexity complexity) {
        String doc = generator.generateDocumentation(testTool, complexity);
        assertNotNull(doc);
        assertFalse(doc.isBlank(), "Documentation should not be blank for complexity: " + complexity);
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
    }

    @Test
    void cleanDocContainsPreconditions() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.CLEAN);
        assertTrue(doc.contains("system_status"));
        assertTrue(doc.contains("RUNNING"));
    }

    @Test
    void cleanDocContainsEffects() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.CLEAN);
        assertTrue(doc.contains("counter"));
        assertTrue(doc.contains("INCREMENT"));
    }

    @Test
    void gibberishNoiseIsLongerThanClean() {
        String clean = generator.generateDocumentation(testTool, DocumentComplexity.CLEAN);
        String gibberish = generator.generateDocumentation(testTool, DocumentComplexity.GIBBERISH_NOISE);
        assertTrue(gibberish.length() > clean.length());
    }

    @Test
    void contextualNoiseContainsWarnings() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.CONTEXTUAL_NOISE);
        assertTrue(doc.contains("WARNING") || doc.contains("NOISY"));
    }

    @Test
    void unstructuredRemovesMarkdownHeaders() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.UNSTRUCTURED);
        assertFalse(doc.contains("#"));
    }

    @Test
    void logicalConflictContainsConflictWarnings() {
        String doc = generator.generateDocumentation(testTool, DocumentComplexity.LOGICAL_CONFLICT);
        assertTrue(doc.contains("conflicting") || doc.contains("Conflicting") || doc.contains("contradict"));
    }

    @Test
    void incompleteDocIsNonEmpty() {
        String incomplete = generator.generateDocumentation(testTool, DocumentComplexity.INCOMPLETE);
        assertFalse(incomplete.isBlank());
    }
}
