package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.PaneWallPolicy;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The shipped key row cut to the wall: a place switch stays only for a place the wall has, and
 * a wall of one place carries none. A row the user wrote is never cut.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ExtraKeysDefaultRowTest {

    private static final String SHIPPED = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS;
    private static final String CUSTOM = "[['ESC', {key: 'tool:wall.display', display: 'D'}, 'TAB']]";

    /** The tool names the row's keys run, in order, with the {@code tool:} prefix stripped. */
    private static List<String> tools(String row) {
        List<String> names = new ArrayList<>();
        for (List<ExtraKeysLayoutModel.Key> line : ExtraKeysLayoutModel.parse(row).rows())
            for (ExtraKeysLayoutModel.Key key : line)
                if (key.key.startsWith(TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX))
                    names.add(ExtraKeyEligibility.toolNameOf(key.key));
        return names;
    }

    @Test
    public void theShippedRowCarriesAllThreeSwitchesOnAWallOfThreePlaces() {
        String row = ExtraKeysDefaultRow.forWall(SHIPPED, PaneWallPolicy.availablePages(false, true, true));
        assertSame("nothing to cut, nothing rebuilt", SHIPPED, row);
        assertTrue(tools(row).contains(LauncherToolRegistry.TOOL_WALL_WIDGETS));
        assertTrue(tools(row).contains(LauncherToolRegistry.TOOL_WALL_TERMINAL));
        assertTrue(tools(row).contains(LauncherToolRegistry.TOOL_WALL_DISPLAY));
    }

    @Test
    public void aHomeScreenWithoutTheDisplayLosesTheDisplaySwitchOnly() {
        String row = ExtraKeysDefaultRow.forWall(SHIPPED, PaneWallPolicy.availablePages(false, true, false));
        List<String> tools = tools(row);
        assertTrue(tools.contains(LauncherToolRegistry.TOOL_WALL_WIDGETS));
        assertTrue(tools.contains(LauncherToolRegistry.TOOL_WALL_TERMINAL));
        assertFalse(tools.contains(LauncherToolRegistry.TOOL_WALL_DISPLAY));
        // Every other key of the row is still there, in its order.
        assertEquals(tools(SHIPPED).size() - 1, tools.size());
        assertEquals(tools(SHIPPED).get(0), tools.get(0));
    }

    @Test
    public void theDisplayWithoutTheWidgetsLosesTheWidgetsSwitchOnly() {
        String row = ExtraKeysDefaultRow.forWall(SHIPPED, PaneWallPolicy.availablePages(false, false, true));
        assertFalse(tools(row).contains(LauncherToolRegistry.TOOL_WALL_WIDGETS));
        assertTrue(tools(row).contains(LauncherToolRegistry.TOOL_WALL_DISPLAY));
    }

    @Test
    public void aWallOfOnePlaceCarriesNoSwitchAtAll() {
        String row = ExtraKeysDefaultRow.forWall(SHIPPED, PaneWallPolicy.availablePages(true, true, false));
        List<String> tools = tools(row);
        assertFalse(tools.contains(LauncherToolRegistry.TOOL_WALL_WIDGETS));
        assertFalse("a single place needs no switch for itself",
            tools.contains(LauncherToolRegistry.TOOL_WALL_TERMINAL));
        assertFalse(tools.contains(LauncherToolRegistry.TOOL_WALL_DISPLAY));
        // The keyboard, mouse, split and session keys are what is left, and the row is one row.
        assertEquals(tools(SHIPPED).size() - 3, tools.size());
        assertEquals(1, ExtraKeysLayoutModel.parse(row).rowCount());
    }

    @Test
    public void theCutRowStillParsesAsAKeyRow() throws Exception {
        String row = ExtraKeysDefaultRow.forWall(SHIPPED, PaneWallPolicy.availablePages(true, false, false));
        com.termux.shared.termux.extrakeys.ExtraKeysInfo info =
            new com.termux.shared.termux.extrakeys.ExtraKeysInfo(row,
                TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE,
                com.termux.shared.termux.extrakeys.ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals(1, info.getMatrix().length);
        assertEquals(tools(row).size() + 1, info.getMatrix()[0].length);
    }

    @Test
    public void aRowTheUserWroteIsNeverCut() {
        assertSame(CUSTOM, ExtraKeysDefaultRow.forWall(CUSTOM, PaneWallPolicy.availablePages(true, false, false)));
        assertFalse(ExtraKeysDefaultRow.isShippedRow(CUSTOM));
        assertTrue(ExtraKeysDefaultRow.isShippedRow(" " + SHIPPED + "\n"));
        assertEquals("", ExtraKeysDefaultRow.forWall(null, PaneWallPolicy.availablePages(false, true, true)));
    }

    @Test
    public void theSecondShippedPageIsLeftAlone() {
        String empty = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS2;
        assertSame(empty, ExtraKeysDefaultRow.forWall(empty,
            java.util.Collections.singletonList(PaneWallPage.TERMINAL)));
    }
}
