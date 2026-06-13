package org.benchmark.gen;

import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.ToolObject;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        BenchmarkCaseGenerator first = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 123L);
        BenchmarkCaseGenerator second = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 123L);

        List<BenchmarkCaseGenerator.BenchmarkCase> firstCases = first.generateCases(3, 2, Domain.MANUFACTURING);
        List<BenchmarkCaseGenerator.BenchmarkCase> secondCases = second.generateCases(3, 2, Domain.MANUFACTURING);

        assertEquals(firstCases.size(), secondCases.size());
        for (int index = 0; index < firstCases.size(); index++) {
            assertEquals(firstCases.get(index).targetToolObject().name(), secondCases.get(index).targetToolObject().name());
            assertEquals(firstCases.get(index).scenario().patternName(), secondCases.get(index).scenario().patternName());
            assertEquals(firstCases.get(index).targetSteps().size(), secondCases.get(index).targetSteps().size());
        }
    }

    @Test
    void differentSeedsProduceDifferentCases() {
        BenchmarkCaseGenerator first = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 1L);
        BenchmarkCaseGenerator second = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 999L);

        List<BenchmarkCaseGenerator.BenchmarkCase> firstCases = first.generateCases(5, 2, null);
        List<BenchmarkCaseGenerator.BenchmarkCase> secondCases = second.generateCases(5, 2, null);

        Set<String> firstNames = firstCases.stream().map(caseItem -> caseItem.targetToolObject().name()).collect(Collectors.toSet());
        Set<String> secondNames = secondCases.stream().map(caseItem -> caseItem.targetToolObject().name()).collect(Collectors.toSet());
        assertNotEquals(firstNames, secondNames);
    }

    @Test
    void fixedDomainGeneratesOnlyThatDomain() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(5, 2, Domain.NETWORK_INFRA);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            assertEquals(Domain.NETWORK_INFRA, benchmarkCase.targetToolObject().domain());
            for (ToolObject distractor : benchmarkCase.allTools()) {
                assertEquals(Domain.NETWORK_INFRA, distractor.domain(),
                        "All tools including distractors should be same domain as target");
            }
        }
    }

    @Test
    void eachCaseHasTargetSteps() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(10, 2, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            assertNotNull(benchmarkCase.targetSteps());
            assertFalse(benchmarkCase.targetSteps().isEmpty(), "Each case must have at least one target step");
            assertTrue(benchmarkCase.targetSteps().size() >= 3,
                    "Each scenario should require at least three ordered steps");
        }
    }

    @Test
    void scenarioSetIncludesLongerStatefulCases() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(50, 2, null);

        boolean hasLongerScenario = cases.stream()
                .anyMatch(benchmarkCase -> benchmarkCase.targetSteps().size() > 3);

        assertTrue(hasLongerScenario, "Scenario set should include cases with more than three steps");
    }

    @Test
    void documentationExistsForAllTools() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(3, 3, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            for (var tool : benchmarkCase.allTools()) {
                String documentation = benchmarkCase.documentationForTool(tool.name());
                assertNotNull(documentation, "Documentation missing for tool: " + tool.name());
                assertFalse(documentation.isBlank());
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
    void expectedStateMatchesCumulativeScenarioEffects() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = generator.generateCases(1, 1, null).get(0);

        assertNotNull(benchmarkCase.expectedState());
        assertFalse(benchmarkCase.expectedState().isEmpty(),
                "Multi-step scenarios should produce cumulative expected state");
        assertEquals(benchmarkCase.scenario().cumulativeExpectedState(), benchmarkCase.expectedState());
    }

    @Test
    void scenarioAndStepsAreConsistent() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(5, 2, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            assertNotNull(benchmarkCase.scenario());
            assertEquals(benchmarkCase.scenario().steps(), benchmarkCase.targetSteps());
        }
    }

    @Test
    void goalQueryIsNotEmpty() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(5, 2, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            String query = benchmarkCase.generateUserQuery();
            assertNotNull(query);
            assertFalse(query.isBlank());
        }
    }

    @Test
    void trapEmbedsTrapInRequiredStepWithRecoveryCommand() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L, true);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(10, 2, null);

        for (int i = 0; i < cases.size(); i++) {
            BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = cases.get(i);
            boolean shouldHaveTrap = (i + 1) % 5 == 0;

            assertEquals(shouldHaveTrap, benchmarkCase.hasTrap(),
                    "Case " + (i + 1) + " hasTrap should be " + shouldHaveTrap);

            ToolObject target = benchmarkCase.targetToolObject();

            if (shouldHaveTrap) {
                // The trapped command is a required step (step 1)
                CommandObject trapCmd = benchmarkCase.trapCommand();
                assertNotNull(trapCmd, "Trap case should have a trapped command");

                // The trapped command should be step 1's abbreviated name
                String step1Name = CommandAbbreviator.commandName(
                        benchmarkCase.targetSteps().get(0).verb(),
                        benchmarkCase.targetSteps().get(0).noun());
                assertEquals(step1Name, trapCmd.name(),
                        "Trap should be embedded in step 1");

                // Documented effects should show ASSIGN with correct value
                assertNotNull(trapCmd.documentedEffects());
                assertTrue(trapCmd.documentedEffects().stream()
                                .allMatch(e -> e.operation() == EffectOp.ASSIGN),
                        "Documented effects should all be ASSIGN");

                // Real effects should also be ASSIGN but with a wrong value
                assertTrue(trapCmd.commandEffectObjects().stream()
                                .allMatch(e -> e.operation() == EffectOp.ASSIGN),
                        "Real effects should be ASSIGN (not DELETE)");

                // At least one real effect value should differ from documented
                boolean hasDifference = false;
                for (int e = 0; e < trapCmd.documentedEffects().size(); e++) {
                    String docValue = trapCmd.documentedEffects().get(e).valueRef();
                    String realValue = trapCmd.commandEffectObjects().get(e).valueRef();
                    if (!docValue.equals(realValue)) {
                        hasDifference = true;
                        break;
                    }
                }
                assertTrue(hasDifference, "Real and documented effects should differ");

                // Recovery command should exist
                assertNotNull(benchmarkCase.recoveryCommandName(),
                        "Trap case should have a recovery command");
                CommandObject recoveryCmd = target.commands().stream()
                        .filter(c -> c.name().equals(benchmarkCase.recoveryCommandName()))
                        .findFirst()
                        .orElse(null);
                assertNotNull(recoveryCmd, "Recovery command should be in the tool's commands");

                // Recovery command should fix the corrupted variable
                assertTrue(recoveryCmd.commandEffectObjects().stream()
                                .anyMatch(e -> e.operation() == EffectOp.ASSIGN),
                        "Recovery command should ASSIGN the correct value");

                // Recovery command should have no preconditions
                assertTrue(recoveryCmd.preconditions().isEmpty(),
                        "Recovery command should have no preconditions");

                // Tool description should NOT contain obvious recovery hints
                assertFalse(target.description().contains("stabilization"),
                        "Tool description should not contain obvious recovery hints");

                // Documentation should show the documented (fake) effects for the trapped step
                String doc = benchmarkCase.documentationForTool(target.name());
                assertNotNull(doc);
            } else {
                // Non-trap cases should have no commands with documentedEffects
                List<CommandObject> traps = target.commands().stream()
                        .filter(cmd -> cmd.documentedEffects() != null)
                        .toList();
                assertTrue(traps.isEmpty(), "Non-trap case should have no trap commands");
                assertNull(benchmarkCase.recoveryCommandName());
            }
        }
    }

    @Test
    void noTrapCommandWhenDisabled() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L, false);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(5, 2, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            boolean hasTrap = benchmarkCase.targetToolObject().commands().stream()
                    .anyMatch(cmd -> cmd.documentedEffects() != null);
            assertFalse(hasTrap, "No trap command should exist when disabled");
            assertNull(benchmarkCase.recoveryCommandName());
        }
    }
}
