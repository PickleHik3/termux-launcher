package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.page.PageKind;
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

    /**
     * The three place switches left the shipped row for the navigation dot, so there is nothing
     * for the wall to cut from it: whatever places the wall has, the shipped row comes back as it
     * went in. (A row that still carries wall keys is a row the user wrote, and is never cut.)
     */
    @Test
    public void theShippedRowCarriesNoPlaceSwitchAndIsNeverCut() {
        assertFalse(tools(SHIPPED).contains(LauncherToolRegistry.TOOL_WALL_WIDGETS));
        assertFalse(tools(SHIPPED).contains(LauncherToolRegistry.TOOL_WALL_TERMINAL));
        assertFalse(tools(SHIPPED).contains(LauncherToolRegistry.TOOL_WALL_DISPLAY));
        boolean[][] walls = {{false, true, true}, {false, true, false}, {false, false, true},
            {true, true, false}, {true, false, false}};
        for (boolean[] w : walls) {
            assertSame("nothing to cut, nothing rebuilt", SHIPPED,
                ExtraKeysDefaultRow.forWall(SHIPPED, PaneWallPolicy.availablePages(w[0], w[1], w[2])));
        }
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
            java.util.Collections.singletonList(PageKind.TERMINAL)));
    }
}
