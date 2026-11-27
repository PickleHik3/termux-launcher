package com.termux.app.terminal.keybind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.leader.Leader;
import com.termux.app.leader.LeaderChord;
import com.termux.app.terminal.TerminalKeyBindingResolver.Hint;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The bindings page's decisions: how a binding reads, and what a captured key would do. */
public class ActionBindingsTest {

    private static final Map<String, Hint> NONE = Collections.emptyMap();

    private static Map<String, Hint> held(String stroke, String tool, String label) {
        Map<String, Hint> map = new LinkedHashMap<>();
        map.put(stroke, new Hint(tool, label));
        return map;
    }

    private static Leader custom(String spec) {
        return Leader.custom(LeaderChord.parse(spec).chord);
    }

    @Test
    public void aBindingReadsUnderTheTerlKey() {
        assertEquals("Terl key + P", ActionBindings.display(Leader.TERL_KEY, "ctrl+alt+p"));
        assertEquals("Terl key + Shift+P", ActionBindings.display(Leader.TERL_KEY, "ctrl+alt+shift+p"));
    }

    @Test
    public void aBindingReadsUnderCtrlAltAndCustom() {
        assertEquals("Ctrl+Alt + V", ActionBindings.display(Leader.CTRL_ALT, "ctrl+alt+v"));
        assertEquals("Ctrl+B + V", ActionBindings.display(custom("ctrl+b"), "ctrl+alt+v"));
        assertEquals("Ctrl+Shift + Left", ActionBindings.display(custom("ctrl+shift"), "ctrl+alt+left"));
    }

    @Test
    public void aStrokeOutsideTheLeaderReadsAsItself() {
        assertEquals("Ctrl+Z", ActionBindings.display(Leader.TERL_KEY, "ctrl+z"));
    }

    @Test
    public void aPressedKeyIsStoredAfterTheLeader() {
        assertEquals("ctrl+alt+p", ActionBindings.storedFor("p"));
        assertEquals("ctrl+alt+shift+p", ActionBindings.storedFor("shift+p"));
        assertNull(ActionBindings.storedFor("ctrl+p"));
        assertNull(ActionBindings.storedFor("alt+p"));
        assertNull(ActionBindings.storedFor(""));
        assertNull(ActionBindings.storedFor(null));
    }

    @Test
    public void aFreeKeyIsFree() {
        assertEquals(ActionBindings.Decision.Kind.FREE,
            ActionBindings.decide(Leader.TERL_KEY, "ctrl+alt+p", "a.tool", NONE, NONE).kind);
    }

    @Test
    public void theActionsOwnKeyIsNoChange() {
        assertEquals(ActionBindings.Decision.Kind.UNCHANGED,
            ActionBindings.decide(Leader.TERL_KEY, "ctrl+alt+p", "a.tool",
                held("ctrl+alt+p", "a.tool", null), NONE).kind);
    }

    @Test
    public void anotherActionsKeyIsAClashNamingIt() {
        ActionBindings.Decision decision = ActionBindings.decide(Leader.TERL_KEY, "ctrl+alt+p",
            "a.tool", held("ctrl+alt+p", "b.tool", null), NONE);
        assertEquals(ActionBindings.Decision.Kind.CLASH, decision.kind);
        assertEquals("b.tool", decision.holder.toolName);
    }

    @Test
    public void aLabelledBindingOfTheSameToolIsAClash() {
        // app.launch bound to an app is not the action "Launch app" with nothing to launch.
        assertEquals(ActionBindings.Decision.Kind.CLASH, ActionBindings.decide(Leader.TERL_KEY,
            "ctrl+alt+p", "a.tool", held("ctrl+alt+p", "a.tool", "Notes"), NONE).kind);
    }

    @Test
    public void aStrokeTheLeaderTypesAsAnotherBindingIsAClash() {
        // Under Ctrl+Shift, leader+X is typed Ctrl+Shift+X, which a plain binding may hold.
        ActionBindings.Decision decision = ActionBindings.decide(custom("ctrl+shift"),
            "ctrl+alt+x", "a.tool", NONE, held("ctrl+shift+x", "b.tool", null));
        assertEquals(ActionBindings.Decision.Kind.CLASH, decision.kind);
    }

    @Test
    public void keysAndroidTakesAreRefusedWithAReason() {
        ActionBindings.Decision tab = ActionBindings.decide(Leader.CTRL_ALT, "ctrl+alt+tab",
            "a.tool", NONE, NONE);
        assertEquals(ActionBindings.Decision.Kind.REFUSED, tab.kind);
        assertTrue(tab.reason.contains("Alt+Tab"));
        assertEquals(ActionBindings.Decision.Kind.REFUSED, ActionBindings.decide(Leader.TERL_KEY,
            "ctrl+alt+space", "a.tool", NONE, NONE).kind);
        // Under a prefix leader the key is typed bare, so Space is fine.
        assertEquals(ActionBindings.Decision.Kind.FREE, ActionBindings.decide(custom("ctrl+b"),
            "ctrl+alt+space", "a.tool", NONE, NONE).kind);
    }

    @Test
    public void rowsListEveryActionWithItsChords() {
        List<ActionBindings.Action> actions = Arrays.asList(
            new ActionBindings.Action("a.tool", "Alpha"), new ActionBindings.Action("b.tool", "Beta"));
        Map<String, Hint> holders = new LinkedHashMap<>();
        holders.put("ctrl+alt+a", new Hint("a.tool", null));
        holders.put("ctrl+alt+x", new Hint("a.tool", null));
        holders.put("ctrl+alt+n", new Hint("a.tool", "Notes"));
        List<ActionBindings.Row> rows = ActionBindings.rows(actions, holders);
        assertEquals(2, rows.size());
        assertEquals(Arrays.asList("ctrl+alt+a", "ctrl+alt+x"), rows.get(0).strokes);
        assertEquals("ctrl+alt+a", rows.get(0).stroke());
        assertNull(rows.get(1).stroke());
    }
}
