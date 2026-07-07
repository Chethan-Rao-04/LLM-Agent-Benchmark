package org.benchmark.gen;

import org.benchmark.gen.catalog.ToolCatalogLoader;
import org.benchmark.gen.catalog.ToolFamily;
import org.benchmark.gen.doc_generator.DocumentationGenerator;
import org.benchmark.gen.query_generator.UserQueryGenerator;
import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.spec.BenchmarkCaseSpec;
import org.benchmark.gen.spec.CapabilityStep;
import org.benchmark.gen.spec.DecoyKind;
import org.benchmark.gen.tool_generator.CommandDict;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.ToolObject;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.benchmark.gen.tool_generator.ScenarioToolGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            assertEquals(benchmarkCase.targetTools().size() + benchmarkCase.distractors().size(),
                    benchmarkCase.allTools().size());
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
    void caseManualExistsForAllTools() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(3, 3, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            for (var tool : benchmarkCase.allTools()) {
                assertTrue(benchmarkCase.caseManual().contains(tool.name()),
                        "Manual missing tool: " + tool.name());
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
    void semanticSpecPreservesScenarioStepsAndExpectedState() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = generator.generateCases(1, 3, Domain.MANUFACTURING).getFirst();
        BenchmarkCaseSpec spec = benchmarkCase.spec();

        assertNotNull(spec);
        assertEquals(benchmarkCase.scenario().description(), spec.intentDescription());
        assertEquals(benchmarkCase.scenario().domain(), spec.domain());
        assertEquals(benchmarkCase.expectedState(), spec.expectedFinalState());
        assertFalse(spec.toolFamilyId().isBlank());
        assertFalse(spec.workflowId().isBlank());
        assertTrue(benchmarkCase.targetTools().size() >= 2);
        assertEquals(spec.capabilitySteps().size(), benchmarkCase.targetPath().size());
        assertFalse(benchmarkCase.expectedStateByTool().isEmpty());
        ToolFamily family = new ToolCatalogLoader().getCatalog().family(spec.toolFamilyId());
        assertEquals(family.tools().size(), benchmarkCase.targetTools().size() + benchmarkCase.semanticDecoys().size());
        assertEquals(benchmarkCase.semanticDecoys().size(), spec.decoyPlan().semanticDecoyCount());
        assertEquals(benchmarkCase.randomDistractors().size(), spec.decoyPlan().randomDistractorCount());
        assertTrue(spec.scoringPolicy().penalizeSemanticDecoyUse());

        List<ResolvedStep> scenarioSteps = benchmarkCase.scenario().steps();
        List<CapabilityStep> capabilitySteps = spec.capabilitySteps();
        assertEquals(scenarioSteps.size(), capabilitySteps.size());
        for (int index = 0; index < scenarioSteps.size(); index++) {
            ResolvedStep scenarioStep = scenarioSteps.get(index);
            CapabilityStep capabilityStep = capabilitySteps.get(index);
            assertEquals(scenarioStep.verb(), capabilityStep.verb());
            assertEquals(scenarioStep.noun(), capabilityStep.noun());
            assertEquals(CommandAbbreviator.commandName(scenarioStep.verb(), scenarioStep.noun()),
                    capabilityStep.commandName());
            assertEquals(scenarioStep.precondition(), capabilityStep.precondition());
            assertEquals(scenarioStep.effect(), capabilityStep.effect());
            assertFalse(capabilityStep.toolId().isBlank());
        }
    }

    @Test
    void compatibilityConstructorBuildsSpecFromCasePayload() {
        ResolvedStep scenarioStep = new ResolvedStep("deploy", "service", Map.of(), Map.of("service_status", "ready"));
        ResolvedScenario scenario = new ResolvedScenario(
                "test_pattern",
                "Test scenario",
                Domain.NETWORK_INFRA,
                List.of(scenarioStep),
                Map.of("service_status", "ready"),
                Map.of()
        );
        ResolvedStep payloadStep = new ResolvedStep("authenticate", "server", Map.of(), Map.of("auth_token", "valid"));
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = new BenchmarkCaseGenerator.BenchmarkCase(
                scenario,
                new ToolObject("NET-TEST-101", "Test tool", Domain.NETWORK_INFRA, List.of(), Map.of()),
                List.of(payloadStep),
                "docs",
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                "Use the documented tool to complete the work.",
                false,
                null,
                null
        );

        assertEquals(benchmarkCase.targetSteps().size(), benchmarkCase.spec().capabilitySteps().size());
        assertEquals(CommandAbbreviator.commandName(payloadStep.verb(), payloadStep.noun()),
                benchmarkCase.spec().capabilitySteps().getFirst().commandName());
        assertEquals(benchmarkCase.expectedState(), benchmarkCase.spec().expectedFinalState());
    }

    @Test
    void targetToolObjectAliasesFirstTargetDuringMigration() {
        ToolObject firstTarget = new ToolObject(
                "NET-FIRST-101",
                "First target",
                Domain.NETWORK_INFRA,
                List.of(),
                Map.of("primary_status", "string")
        );
        ToolObject secondTarget = new ToolObject(
                "NET-SECOND-202",
                "Second target",
                Domain.NETWORK_INFRA,
                List.of(),
                Map.of("secondary_status", "string")
        );
        ResolvedStep firstStep = new ResolvedStep(
                "prepare",
                "primary",
                Map.of(),
                Map.of("primary_status", "ready")
        );
        ResolvedStep secondStep = new ResolvedStep(
                "verify",
                "secondary",
                Map.of(),
                Map.of("secondary_status", "ready")
        );
        ResolvedScenario scenario = new ResolvedScenario(
                "multi_target",
                "Coordinate two target tools",
                Domain.NETWORK_INFRA,
                List.of(firstStep, secondStep),
                Map.of("primary_status", "ready", "secondary_status", "ready"),
                Map.of()
        );

        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = new BenchmarkCaseGenerator.BenchmarkCase(
                scenario,
                BenchmarkCaseSpec.fromScenario(scenario, 0, 0),
                secondTarget,
                List.of(firstTarget, secondTarget),
                List.of(
                        new BenchmarkCaseGenerator.TargetStep(
                                firstTarget.name(),
                                CommandAbbreviator.commandName(firstStep.verb(), firstStep.noun())),
                        new BenchmarkCaseGenerator.TargetStep(
                                secondTarget.name(),
                                CommandAbbreviator.commandName(secondStep.verb(), secondStep.noun()))
                ),
                scenario.steps(),
                "docs",
                scenario.cumulativeExpectedState(),
                Map.of(
                        firstTarget.name(), firstStep.effect(),
                        secondTarget.name(), secondStep.effect()
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "Use the documented tools.",
                false,
                null,
                null
        );

        assertEquals(firstTarget, benchmarkCase.targetToolObject());
        assertEquals(List.of(firstTarget, secondTarget), benchmarkCase.targetTools());
    }

    @Test
    void targetCommandEffectsComeFromCapabilitySteps() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = generator.generateCases(1, 2, Domain.NETWORK_INFRA).getFirst();

        for (int index = 0; index < benchmarkCase.targetPath().size(); index++) {
            BenchmarkCaseGenerator.TargetStep targetStep = benchmarkCase.targetPath().get(index);
            ToolObject targetTool = benchmarkCase.findTool(targetStep.toolName());
            assertNotNull(targetTool);
            CommandObject command = targetTool.commands().stream()
                    .filter(candidate -> candidate.name().equals(targetStep.commandName()))
                    .findFirst()
                    .orElseThrow();
            CapabilityStep step = benchmarkCase.spec().capabilitySteps().get(index);
            Map<String, String> actualEffects = command.commandEffectObjects().stream()
                    .collect(Collectors.toMap(
                            effect -> effect.variable(),
                            effect -> effect.valueRef() == null ? "" : effect.valueRef()
                    ));
            assertEquals(step.effect(), actualEffects);
        }
    }

    @Test
    void targetToolDescriptionsDoNotEchoScenarioIntent() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(10, 3, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            for (ToolObject targetTool : benchmarkCase.targetTools()) {
                String description = targetTool.description();

                assertTrue(containsIgnoreCase(description, "documented procedures"));

                for (CapabilityStep step : benchmarkCase.spec().capabilitySteps()) {
                    assertFalse(containsIgnoreCase(description, step.commandName()));
                }
            }
        }
    }

    @Test
    void semanticDecoysUseSameFamilyNonTargetTools() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(10, 3, Domain.MANUFACTURING);

        boolean foundSemanticDecoy = false;
        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            assertEquals(benchmarkCase.semanticDecoys().size(),
                    benchmarkCase.semanticDecoyKindsByToolName().size());
            Set<String> targetToolNames = benchmarkCase.targetTools().stream()
                    .map(ToolObject::name)
                    .collect(Collectors.toSet());

            for (ToolObject decoy : benchmarkCase.semanticDecoys()) {
                assertEquals(DecoyKind.SIMILAR_INTENT_WRONG_RESOURCE,
                        benchmarkCase.semanticDecoyKindsByToolName().get(decoy.name()));
                assertFalse(targetToolNames.contains(decoy.name()));
                assertEquals(benchmarkCase.targetToolObject().domain(), decoy.domain());
                foundSemanticDecoy = true;
            }
        }

        assertTrue(foundSemanticDecoy, "Generated cases should include semantic decoys");
    }

    @Test
    void semanticDecoysDoNotShareTargetExpectedStateExactly() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(10, 3, Domain.MANUFACTURING);

        boolean checkedSemanticDecoy = false;
        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            for (ToolObject decoy : benchmarkCase.semanticDecoys()) {
                checkedSemanticDecoy = true;
                boolean replaysTargetFinalState = decoy.commands().stream()
                        .map(command -> command.commandEffectObjects().stream()
                                .collect(Collectors.toMap(
                                        effect -> effect.variable(),
                                        effect -> effect.valueRef() == null ? "" : effect.valueRef()
                                )))
                        .anyMatch(commandEffects -> commandEffects.equals(benchmarkCase.expectedState()));
                assertFalse(replaysTargetFinalState,
                        "Semantic decoy commands should not reproduce the exact target final state");
            }
        }

        assertTrue(checkedSemanticDecoy, "Generated cases should include semantic decoys");
    }

    @Test
    void semanticDecoyDescriptionsUseNeutralPolicy() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(10, 3, Domain.MANUFACTURING);

        boolean checkedSemanticDecoy = false;
        boolean foundDistinctDescription = false;
        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            String targetDescription = benchmarkCase.targetToolObject().description();
            for (ToolObject decoy : benchmarkCase.semanticDecoys()) {
                checkedSemanticDecoy = true;
                assertTrue(containsIgnoreCase(decoy.description(), "documented procedures"));
                if (!targetDescription.equals(decoy.description())) {
                    foundDistinctDescription = true;
                }

                for (CommandObject command : decoy.commands()) {
                    assertFalse(containsIgnoreCase(command.description(), command.name()));
                    assertFalse(command.description().startsWith("Executes the "));
                    assertTrue(containsIgnoreCase(command.description(), "documented"));
                    for (var effect : command.commandEffectObjects()) {
                        assertFalse(containsIgnoreCase(command.description(), effect.variable()));
                        if (effect.valueRef() != null) {
                            assertFalse(containsIgnoreCase(command.description(), effect.valueRef()));
                            assertFalse(containsIgnoreCase(command.description(), effect.valueRef().replace('_', ' ')));
                        }
                    }
                }
            }
        }

        assertTrue(checkedSemanticDecoy, "Generated cases should include semantic decoys");
        assertTrue(foundDistinctDescription, "Semantic decoys should carry family-specific descriptions");
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
    void goalQueryComesFromSemanticSpecWithoutCommandNameLeakage() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        ToolCatalogLoader catalogLoader = new ToolCatalogLoader();
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(5, 2, Domain.MANUFACTURING);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            String query = benchmarkCase.generateUserQuery();
            assertTrue(query.length() > 45, "Query should be a realistic intent request");
            for (CapabilityStep step : benchmarkCase.spec().capabilitySteps()) {
                assertFalse(query.contains(step.commandName()), "Query should not leak proprietary command names");
            }
            assertFalse(containsIgnoreCase(query, benchmarkCase.spec().toolFamilyId()));
            ToolFamily family = catalogLoader.getCatalog().family(benchmarkCase.spec().toolFamilyId());
            for (var tool : family.tools()) {
                assertFalse(containsIgnoreCase(query, tool.id()));
                for (var capability : tool.capabilities()) {
                    assertFalse(containsIgnoreCase(query, capability.id()));
                }
            }
        }
    }

    @Test
    void trapEmbedsTrapInRequiredStepWithRecoveryCommand() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L, true);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(25, 2, null);

        boolean foundTrap = false;
        boolean foundTrapOutsideFirstStep = false;

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            assertEquals(benchmarkCase.trapCommandName() != null, benchmarkCase.hasTrap(),
                    "hasTrap should reflect explicit trap metadata");

            if (benchmarkCase.hasTrap()) {
                foundTrap = true;
                CommandObject trapCmd = benchmarkCase.trapCommand();
                assertNotNull(trapCmd, "Trap case should have a trapped command");
                assertEquals(benchmarkCase.trapCommandName(), trapCmd.name(),
                        "Trap lookup should use explicit trap command metadata");

                List<String> effectfulStepNames = benchmarkCase.spec().capabilitySteps().stream()
                        .filter(step -> !step.effect().isEmpty())
                        .map(CapabilityStep::commandName)
                        .toList();
                assertTrue(effectfulStepNames.contains(trapCmd.name()),
                        "Trap should be embedded in one of the effectful scenario steps");
                if (!effectfulStepNames.isEmpty() && !trapCmd.name().equals(effectfulStepNames.get(0))) {
                    foundTrapOutsideFirstStep = true;
                }

                assertNotNull(trapCmd.documentedEffects());
                assertTrue(trapCmd.documentedEffects().stream()
                                .allMatch(e -> e.operation() == EffectOp.ASSIGN),
                        "Documented effects should all be ASSIGN");

                assertTrue(trapCmd.commandEffectObjects().stream()
                                .allMatch(e -> e.operation() == EffectOp.ASSIGN),
                        "Real effects should be ASSIGN (not DELETE)");

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

                assertNotNull(benchmarkCase.recoveryCommandName(),
                        "Trap case should have a recovery command");
                CommandObject recoveryCmd = benchmarkCase.targetTools().stream()
                        .flatMap(tool -> tool.commands().stream())
                        .filter(c -> c.name().equals(benchmarkCase.recoveryCommandName()))
                        .findFirst()
                        .orElse(null);
                assertNotNull(recoveryCmd, "Recovery command should be in the tool's commands");

                assertTrue(recoveryCmd.commandEffectObjects().stream()
                                .anyMatch(e -> e.operation() == EffectOp.ASSIGN),
                        "Recovery command should ASSIGN the correct value");

                assertEquals(1, recoveryCmd.preconditions().size(),
                        "Recovery command should require the corrupted state");
                String corruptedVar = null;
                String wrongValue = null;
                for (int e = 0; e < trapCmd.documentedEffects().size(); e++) {
                    String docValue = trapCmd.documentedEffects().get(e).valueRef();
                    String realValue = trapCmd.commandEffectObjects().get(e).valueRef();
                    if (!docValue.equals(realValue)) {
                        corruptedVar = trapCmd.documentedEffects().get(e).variable();
                        wrongValue = realValue;
                        break;
                    }
                }
                assertNotNull(corruptedVar);
                assertEquals(wrongValue, recoveryCmd.preconditions().get(corruptedVar),
                        "Recovery command should only be available after the trap corrupts state");

                assertFalse(benchmarkCase.targetToolObject().description().contains("stabilization"),
                        "Tool description should not contain obvious recovery hints");

                assertTrue(benchmarkCase.caseManual().contains(benchmarkCase.targetToolObject().name()));
            } else {
                List<CommandObject> traps = benchmarkCase.targetTools().stream()
                        .flatMap(tool -> tool.commands().stream())
                        .filter(cmd -> cmd.documentedEffects() != null)
                        .toList();
                assertTrue(traps.isEmpty(), "Non-trap case should have no trap commands");
                assertNull(benchmarkCase.trapCommandName());
                assertNull(benchmarkCase.trapCommand());
                assertNull(benchmarkCase.recoveryCommandName());
            }
        }

        assertTrue(foundTrap, "Trap-enabled generation should produce at least one actual trap");
        assertTrue(foundTrapOutsideFirstStep, "Trap selection should not be hardcoded to the first effectful step");
    }

    @Test
    void noTrapCommandWhenDisabled() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L, false);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(5, 2, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            boolean hasTrap = benchmarkCase.targetTools().stream()
                    .flatMap(tool -> tool.commands().stream())
                    .anyMatch(cmd -> cmd.documentedEffects() != null);
            assertFalse(hasTrap, "No trap command should exist when disabled");
            assertNull(benchmarkCase.trapCommandName());
            assertNull(benchmarkCase.trapCommand());
            assertNull(benchmarkCase.recoveryCommandName());
        }
    }

    @Test
    void explicitTrapLookupReturnsOnlyNamedCommand() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L, true);
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = generator.generateCases(25, 2, null).stream()
                .filter(BenchmarkCaseGenerator.BenchmarkCase::hasTrap)
                .findFirst()
                .orElseThrow();

        CommandObject trapCommand = benchmarkCase.trapCommand();
        assertNotNull(trapCommand);
        assertEquals(benchmarkCase.trapCommandName(), trapCommand.name());
    }

    @Test
    void scenarioToolGeneratorSkipsTrapWhenRecoveryNamesAllCollide() {
        CommandDict collidingDict = new CommandDict(new Random(7L)) {
            @Override
            public String getRandomVerb(Domain domain) {
                return "configure";
            }

            @Override
            public String getRandomNoun(Domain domain) {
                return "router";
            }
        };
        ScenarioToolGenerator generator = new ScenarioToolGenerator(new Random(7L), collidingDict);
        ResolvedScenario scenario = new ResolvedScenario(
                "collision_case",
                "Generated recovery names always collide",
                Domain.NETWORK_INFRA,
                List.of(
                        new ResolvedStep("configure", "router", Map.of(), Map.of("router_status", "configured")),
                        new ResolvedStep("authenticate", "server", Map.of(), Map.of("auth_token", "valid"))
                ),
                Map.of("router_status", "configured"),
                Map.of()
        );

        ScenarioToolGenerator.ToolGenerationResult result = generator.generateTool(scenario, true);

        assertNull(result.trapCommandName(), "Trap should be skipped when no unique recovery name exists");
        assertNull(result.recoveryCommandName(), "Recovery command should be absent when trap generation is skipped");
        assertTrue(result.tool().commands().stream().noneMatch(command -> command.documentedEffects() != null),
                "Skipped trap generation should not leave partial trap metadata behind");
    }

    @Test
    void scenarioToolGeneratorCanTrapNonFirstEffectfulStep() {
        List<ResolvedStep> steps = List.of(
                new ResolvedStep("authenticate", "server", Map.of(), Map.of("auth_token", "valid")),
                new ResolvedStep("inspect", "router", Map.of("auth_token", "valid"), Map.of("router_status", "inspected")),
                new ResolvedStep("configure", "router", Map.of("router_status", "inspected"), Map.of("router_status", "configured"))
        );
        ResolvedScenario scenario = new ResolvedScenario(
                "trap_selection_case",
                "Multiple effectful steps",
                Domain.NETWORK_INFRA,
                steps,
                Map.of("router_status", "configured"),
                Map.of()
        );

        List<String> trapNames = new ArrayList<>();
        for (long seed = 1; seed <= 25; seed++) {
            ScenarioToolGenerator generator = new ScenarioToolGenerator(new Random(seed), new CommandDict(new Random(seed)));
            ScenarioToolGenerator.ToolGenerationResult result = generator.generateTool(scenario, true);
            if (result.trapCommandName() != null) {
                trapNames.add(result.trapCommandName());
            }
        }

        assertTrue(trapNames.contains(CommandAbbreviator.commandName("inspect", "router"))
                        || trapNames.contains(CommandAbbreviator.commandName("configure", "router")),
                "Trap selection should be able to land on a non-first effectful step");
    }

    @Test
    void generatedCommandsStayWithinSingleOptionRuntimeContract() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(10, 3, null);

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            for (ToolObject tool : benchmarkCase.allTools()) {
                for (CommandObject command : tool.commands()) {
                    assertTrue(command.commandOptions().size() <= 1,
                            "Runtime supports only one selected option per command");
                }
            }
        }
    }

    @Test
    void recoveryCommandsHaveNoOptions() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L, true);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(25, 3, null);

        boolean checkedRecoveryCommand = false;
        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            if (benchmarkCase.recoveryCommandName() == null) {
                continue;
            }
            for (CommandObject command : benchmarkCase.targetTools().stream()
                    .flatMap(tool -> tool.commands().stream())
                    .toList()) {
                if (command.name().equals(benchmarkCase.recoveryCommandName())
                        && command.preconditions().size() == 1) {
                    assertTrue(command.commandOptions().isEmpty(),
                            "Recovery commands should not carry generic options");
                    checkedRecoveryCommand = true;
                }
            }
        }

        assertTrue(checkedRecoveryCommand, "Trap-enabled generation should include a recovery command to verify");
    }

    @Test
    void generatedOptionsAvoidFlagsWithUnsupportedRuntimeSemantics() {
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 42L);
        List<BenchmarkCaseGenerator.BenchmarkCase> cases = generator.generateCases(10, 3, null);
        Set<String> unsupportedOptions = Set.of(
                "--help", "--version", "--dry-run", "--simulate", "--output", "--timeout", "--retry", "--interactive"
        );

        for (BenchmarkCaseGenerator.BenchmarkCase benchmarkCase : cases) {
            for (ToolObject tool : benchmarkCase.allTools()) {
                for (CommandObject command : tool.commands()) {
                    assertTrue(command.commandOptions().stream()
                                    .noneMatch(option -> unsupportedOptions.contains(option.optionName())),
                            "Generated options should not imply behavior the simulator does not implement");
                }
            }
        }
    }

    @Test
    void generationFailsWhenCatalogCannotSupplyUnrelatedDistractors() {
        Random random = new Random(42L);
        BenchmarkCaseGenerator generator = new BenchmarkCaseGenerator(
                random,
                new ToolCatalogLoader("test-tool-catalog-single-family.yaml"),
                new ScenarioToolGenerator(random, new CommandDict(random)),
                new DocumentationGenerator(),
                new UserQueryGenerator(random),
                DocumentComplexity.CLEAN,
                false
        );

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> generator.generateCases(1, 1, Domain.NETWORK_INFRA));

        assertTrue(error.getMessage().contains("No unrelated catalog families available"));
    }

    private boolean containsIgnoreCase(String text, String value) {
        return text.toLowerCase().contains(value.toLowerCase());
    }

}
