package com.termux.app.terminal.io;

import android.app.Application;
import android.os.Build;

import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ExtraKeysLayoutModelTest {

    @Test
    public void parsesBareTokensAndKeepsThemBareOnTheWayOut() {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse("[[ESC, TAB, CTRL]]");

        assertEquals(1, model.rowCount());
        assertEquals(3, model.keyCount());
        assertEquals("ESC", model.row(0).get(0).key);
        assertEquals("[[\"ESC\",\"TAB\",\"CTRL\"]]", model.serialize());
    }

    @Test
    public void parsesUnquotedPropertyStyleJson() {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse(
            "[[{key: ESC, popup: {macro: 'CTRL f d', display: 'tmux exit'}}, TAB]]");

        ExtraKeysLayoutModel.Key esc = model.row(0).get(0);
        assertEquals("ESC", esc.key);
        assertFalse(esc.macro);
        assertEquals("CTRL f d", esc.popup.key);
        assertTrue(esc.popup.macro);
        assertEquals("tmux exit", esc.popup.display);
    }

    @Test
    public void roundTripsToolKeysWithArgumentsAndDisplayOverrides() {
        String source = "[[{key: 'tool:pane.move_to_edge:edge=left', display: '⇤',"
            + " popup: {key: 'tool:pane.next_layout', display: '⟳'}}]]";

        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse(source);
        ExtraKeysLayoutModel reparsed = ExtraKeysLayoutModel.parse(model.serialize());

        ExtraKeysLayoutModel.Key key = reparsed.row(0).get(0);
        assertEquals("tool:pane.move_to_edge:edge=left", key.key);
        assertEquals("⇤", key.display);
        assertEquals("tool:pane.next_layout", key.popup.key);
        assertEquals("⟳", key.popup.display);
    }

    @Test
    public void serializesMacrosAsMacrosNotKeys() {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.empty();
        model.addRow();
        model.row(0).add(new ExtraKeysLayoutModel.Key("ALT j", true, "A-j", null));

        String serialized = model.serialize();

        assertTrue(serialized, serialized.contains("\"macro\":\"ALT j\""));
        assertTrue(ExtraKeysLayoutModel.parse(serialized).row(0).get(0).macro);
    }

    @Test
    public void movesKeysWithinAndBetweenRows() {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse("[[A, B], [C]]");

        assertTrue(model.move(0, 0, 0, 1));
        assertEquals("B", model.row(0).get(0).key);

        assertTrue(model.move(0, 1, 1, 0));
        assertEquals(1, model.row(0).size());
        assertEquals("A", model.row(1).get(0).key);
        assertEquals("C", model.row(1).get(1).key);
    }

    @Test
    public void moveRejectsOutOfBoundsSourcesAndClampsTheTargetIndex() {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse("[[A, B], [C]]");

        assertFalse(model.move(2, 0, 0, 0));
        assertFalse(model.move(0, 5, 0, 0));
        assertFalse(model.move(0, 0, 5, 0));
        assertFalse(model.move(-1, 0, 0, 0));

        assertTrue(model.move(0, 0, 1, 99));
        assertEquals("A", model.row(1).get(1).key);
    }

    @Test
    public void insertRowRestoresARemovedRowAtItsOldIndex() {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse("[[A], [B, C], [D]]");
        java.util.List<ExtraKeysLayoutModel.Key> removed =
            new java.util.ArrayList<>(model.row(1));
        model.removeRow(1);
        assertEquals(2, model.rowCount());

        model.insertRow(1, removed);

        assertEquals(3, model.rowCount());
        assertEquals("B", model.row(1).get(0).key);
        assertEquals("C", model.row(1).get(1).key);

        // An index past the end is clamped rather than thrown.
        model.insertRow(99, removed);
        assertEquals(4, model.rowCount());
        assertEquals("B", model.row(3).get(0).key);
    }

    @Test
    public void emptyRowsAreDroppedOnSave() {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse("[[A]]");
        model.addRow();

        model.pruneEmptyRows();

        assertEquals(1, model.rowCount());
        assertEquals("[[\"A\"]]", model.serialize());
    }

    @Test
    public void emptyAndMalformedValuesYieldAnEmptyPageInsteadOfThrowing() {
        assertTrue(ExtraKeysLayoutModel.parse(null).isEmpty());
        assertTrue(ExtraKeysLayoutModel.parse("").isEmpty());
        assertTrue(ExtraKeysLayoutModel.parse("[]").isEmpty());
        assertTrue(ExtraKeysLayoutModel.parse("not json at all {[").isEmpty());
        assertEquals("[]", ExtraKeysLayoutModel.parse("[]").serialize());
    }

    @Test
    public void displayFallsBackToTheKeyName() {
        ExtraKeysLayoutModel.Key key = new ExtraKeysLayoutModel.Key("HOME");

        assertNull(key.display);
        assertEquals("HOME", key.label());
    }

    /**
     * The editor and the live toolbar have to arrive at the same cap text. They did not: the editor
     * fell back to the raw key name, so a cap that the row draws as {@code ⌨} read {@code KEYBOARD},
     * clipped over two lines inside a 56dp cap.
     */
    @Test
    public void theCapLabelResolvesThroughTheSameMapTheLiveRowUses() {
        ExtraKeysConstants.ExtraKeyDisplayMap map =
            ExtraKeysInfo.getCharDisplayMapForStyle("default");

        // nf-md-keyboard_outline, drawn with the bundled symbols face.
        assertEquals("\uDB82\uDD7B", ExtraKeyButton.resolveDisplay("KEYBOARD", null, map));
        assertEquals("←", ExtraKeyButton.resolveDisplay("LEFT", null, map));
        // An explicit display always wins, exactly as in the live row.
        assertEquals("exit", ExtraKeyButton.resolveDisplay("KEYBOARD", "exit", map));
        // Macros resolve token by token and stay joined by spaces.
        assertEquals("CTRL \uDB82\uDD7B", ExtraKeyButton.resolveDisplay("CTRL KEYBOARD", null, map));
        // Nothing in the map: the spec itself, which is what the edit panel is for.
        assertEquals("tool:workspace.picker",
            ExtraKeyButton.resolveDisplay("tool:workspace.picker", null, map));
    }

    /** The swipe-up label is a real field now, so it has to survive a write and a re-read. */
    @Test
    public void aSwipeUpDisplayRoundTripsThroughSerialization() {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse(
            "[[{key: ESC, popup: {key: \"tool:session.previous\", display: \"Previous session\"}}]]");

        model.row(0).get(0).popup.display = "⇤";
        String serialized = model.serialize();

        ExtraKeysLayoutModel reread = ExtraKeysLayoutModel.parse(serialized);
        ExtraKeysLayoutModel.Key popup = reread.row(0).get(0).popup;
        assertEquals("tool:session.previous", popup.key);
        assertEquals("⇤", popup.display);
    }

    /** Clearing the field writes no {@code display} at all rather than an empty one. */
    @Test
    public void anEmptySwipeUpDisplayIsDroppedRatherThanWrittenBlank() {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse(
            "[[{key: ESC, popup: {key: HOME, display: \"Start of line\"}}]]");

        model.row(0).get(0).popup.display = null;

        ExtraKeysLayoutModel reread = ExtraKeysLayoutModel.parse(model.serialize());
        assertNull(reread.row(0).get(0).popup.display);
    }

    @Test
    public void everyPageHasAPropertyAndADefault() {
        assertEquals(TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS.length,
            TermuxTerminalExtraKeys.PAGE_DEFAULT_VALUES.length);
        // The launcher's own row is the first page and always holds keys.
        assertFalse("the first page default must parse into keys",
            ExtraKeysLayoutModel.parse(TermuxTerminalExtraKeys.PAGE_DEFAULT_VALUES[0]).isEmpty());
        // A later page may ship empty — the pager drops an empty page rather than showing it
        // blank. parse() reports a malformed value as empty too, so a default that does not
        // literally declare an empty layout still has to produce keys.
        for (String value : TermuxTerminalExtraKeys.PAGE_DEFAULT_VALUES) {
            assertEquals("a shipped page default must parse: " + value,
                "[]".equals(value.trim()), ExtraKeysLayoutModel.parse(value).isEmpty());
        }
    }
}
