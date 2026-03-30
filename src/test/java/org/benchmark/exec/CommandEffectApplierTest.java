package org.benchmark.exec;

import org.benchmark.model.enums.EffectOp;
import org.benchmark.model.objects.EffectObject;
import org.benchmark.model.objects.ToolObject;

import org.benchmark.model.enums.Domain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CommandEffectApplierTest {

    private SessionStateManager stateManager;
    private static final String SESSION_ID = "test-session";
    private static final String TOOL_NAME = "TEST-TOOL-001";

    @BeforeEach
    void setUp() {
        stateManager = new SessionStateManager();
        ToolObject tool = new ToolObject(
                TOOL_NAME, "Test tool", Domain.MANUFACTURING,
                List.of(),
                Map.of("counter", "int", "label", "string")
        );
        stateManager.initializeSession(SESSION_ID, null, List.of(tool), new Random(42));
        stateManager.updateToolState(SESSION_ID, TOOL_NAME, "counter", "5");
        stateManager.updateToolState(SESSION_ID, TOOL_NAME, "label", "old-value");
    }

    @Test
    void assignSetsValue() {
        List<EffectObject> effects = List.of(
                new EffectObject("label", EffectOp.ASSIGN, "new-value")
        );
        CommandEffectApplier.applyEffects(effects, "", stateManager, SESSION_ID, TOOL_NAME);

        assertEquals("new-value", stateManager.getToolState(SESSION_ID, TOOL_NAME, "label"));
    }

    @Test
    void assignWithOptionRefResolvesOption() {
        List<EffectObject> effects = List.of(
                new EffectObject("label", EffectOp.ASSIGN, EffectObject.OPTION_REF)
        );
        CommandEffectApplier.applyEffects(effects, "--verbose", stateManager, SESSION_ID, TOOL_NAME);

        assertEquals("--verbose", stateManager.getToolState(SESSION_ID, TOOL_NAME, "label"));
    }

    @Test
    void assignWithOptionRefNullOptionSetsNull() {
        List<EffectObject> effects = List.of(
                new EffectObject("label", EffectOp.ASSIGN, EffectObject.OPTION_REF)
        );
        CommandEffectApplier.applyEffects(effects, null, stateManager, SESSION_ID, TOOL_NAME);

        assertNull(stateManager.getToolState(SESSION_ID, TOOL_NAME, "label"));
    }

    @Test
    void incrementAddsOne() {
        List<EffectObject> effects = List.of(
                new EffectObject("counter", EffectOp.INCREMENT, null)
        );
        CommandEffectApplier.applyEffects(effects, "", stateManager, SESSION_ID, TOOL_NAME);

        assertEquals("6", stateManager.getToolState(SESSION_ID, TOOL_NAME, "counter"));
    }

    @Test
    void decrementSubtractsOne() {
        List<EffectObject> effects = List.of(
                new EffectObject("counter", EffectOp.DECREMENT, null)
        );
        CommandEffectApplier.applyEffects(effects, "", stateManager, SESSION_ID, TOOL_NAME);

        assertEquals("4", stateManager.getToolState(SESSION_ID, TOOL_NAME, "counter"));
    }

    @Test
    void deleteRemovesVariable() {
        List<EffectObject> effects = List.of(
                new EffectObject("label", EffectOp.DELETE, null)
        );
        CommandEffectApplier.applyEffects(effects, "", stateManager, SESSION_ID, TOOL_NAME);

        assertNull(stateManager.getToolState(SESSION_ID, TOOL_NAME, "label"));
    }

    @Test
    void incrementOnNullTreatsAsZero() {
        stateManager.updateToolState(SESSION_ID, TOOL_NAME, "counter", null);

        List<EffectObject> effects = List.of(
                new EffectObject("counter", EffectOp.INCREMENT, null)
        );
        CommandEffectApplier.applyEffects(effects, "", stateManager, SESSION_ID, TOOL_NAME);

        assertEquals("1", stateManager.getToolState(SESSION_ID, TOOL_NAME, "counter"));
    }

    @Test
    void applyEffectsToMapAssign() {
        Map<String, String> state = new HashMap<>();
        List<EffectObject> effects = List.of(
                new EffectObject("key", EffectOp.ASSIGN, "value")
        );
        CommandEffectApplier.applyEffectsToMap(effects, "", state);

        assertEquals("value", state.get("key"));
    }

    @Test
    void applyEffectsToMapIncrement() {
        Map<String, String> state = new HashMap<>();
        state.put("count", "10");
        List<EffectObject> effects = List.of(
                new EffectObject("count", EffectOp.INCREMENT, null)
        );
        CommandEffectApplier.applyEffectsToMap(effects, "", state);

        assertEquals("11", state.get("count"));
    }

    @Test
    void applyEffectsToMapDelete() {
        Map<String, String> state = new HashMap<>();
        state.put("key", "value");
        List<EffectObject> effects = List.of(
                new EffectObject("key", EffectOp.DELETE, null)
        );
        CommandEffectApplier.applyEffectsToMap(effects, "", state);

        assertFalse(state.containsKey("key"));
    }

    @Test
    void nullEffectsListIsNoOp() {
        CommandEffectApplier.applyEffects(null, "", stateManager, SESSION_ID, TOOL_NAME);
        assertEquals("5", stateManager.getToolState(SESSION_ID, TOOL_NAME, "counter"));
    }

    @Test
    void emptyEffectsListIsNoOp() {
        CommandEffectApplier.applyEffects(List.of(), "", stateManager, SESSION_ID, TOOL_NAME);
        assertEquals("5", stateManager.getToolState(SESSION_ID, TOOL_NAME, "counter"));
    }
}
