package com.termux.app.terminal.io;

import android.app.Application;
import android.os.Build;

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

    @Test
    public void everyPageHasAPropertyAndADefault() {
        assertEquals(TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS.length,
            TermuxTerminalExtraKeys.PAGE_DEFAULT_VALUES.length);
        for (String value : TermuxTerminalExtraKeys.PAGE_DEFAULT_VALUES) {
            assertFalse("a shipped page default must parse",
                ExtraKeysLayoutModel.parse(value).isEmpty());
        }
    }
}
