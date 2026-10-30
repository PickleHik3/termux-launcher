package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.terminal.io.ExtraKeyEligibility;
import com.termux.app.terminal.io.ExtraKeysDefaultRow;
import com.termux.app.terminal.io.ExtraKeysLayoutModel;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The wall keys follow the arranged page list: a kind's key is there iff the list has its page. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallKeysTest {

    private static final WallPages.PageKind H = WallPages.PageKind.HOME;
    private static final WallPages.PageKind T = WallPages.PageKind.TERMINAL;
    private static final WallPages.PageKind D = WallPages.PageKind.DISPLAY;
    private static final String SHIPPED = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS;

    private static WallPages of(WallPages.PageKind... kinds) {
        List<WallPages.Page> all = new ArrayList<>();
        for (int i = 0; i < kinds.length; i++) all.add(new WallPages.Page(i + 1, kinds[i], null, null));
        return new WallPages(all, Collections.<Integer>emptyList(),
            Collections.<WallPages.Page>emptyList(), 1, kinds.length + 1, false);
    }

    private static String key(String tool) {
        return TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX + tool;
    }

    private static List<String> tools(String row) {
        List<String> names = new ArrayList<>();
        for (List<ExtraKeysLayoutModel.Key> line : ExtraKeysLayoutModel.parse(row).rows())
            for (ExtraKeysLayoutModel.Key k : line)
                if (k.key.startsWith(TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX))
                    names.add(ExtraKeyEligibility.toolNameOf(k.key));
        return names;
    }

    @Test
    public void kindsFollowTheListInOrderOfFirstPage() {
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY),
            WallKeys.kinds(of(H, T, D, H, T), UseAs.HOME));
        assertEquals(Arrays.asList(PaneWallPage.DISPLAY, PaneWallPage.TERMINAL),
            WallKeys.kinds(of(D, T), UseAs.HOME));
    }

    @Test
    public void underTerminalOnlyThereIsNoHomeKind() {
        assertEquals(Arrays.asList(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY),
            WallKeys.kinds(of(H, T, D), UseAs.TERMINAL));
    }

    @Test
    public void everyKindPresentOrAbsentDecidesItsKey() {
        String home = key(LauncherToolRegistry.TOOL_WALL_WIDGETS);
        String term = key(LauncherToolRegistry.TOOL_WALL_TERMINAL);
        String disp = key(LauncherToolRegistry.TOOL_WALL_DISPLAY);
        List<PaneWallPage> noDisplay = WallKeys.kinds(of(H, T), UseAs.HOME);
        assertTrue(ExtraKeyEligibility.isUsable(home, PaneWallPage.TERMINAL, noDisplay));
        assertTrue(ExtraKeyEligibility.isUsable(term, PaneWallPage.TERMINAL, noDisplay));
        assertFalse(ExtraKeyEligibility.isUsable(disp, PaneWallPage.TERMINAL, noDisplay));
        List<PaneWallPage> noHome = WallKeys.kinds(of(T, D), UseAs.HOME);
        assertFalse(ExtraKeyEligibility.isUsable(home, PaneWallPage.TERMINAL, noHome));
        assertTrue(ExtraKeyEligibility.isUsable(disp, PaneWallPage.TERMINAL, noHome));
        List<PaneWallPage> noTerm = WallKeys.kinds(of(H, D), UseAs.HOME);
        assertFalse(ExtraKeyEligibility.isUsable(term, PaneWallPage.WIDGETS, noTerm));
    }

    /** The sweep's finding 2: on a Display page reached by key the row still carries Home. */
    @Test
    public void theRowOnADisplayPageCarriesHomeAndTerminal() {
        List<String> tools = tools(ExtraKeysDefaultRow.forWall(SHIPPED,
            WallKeys.kinds(of(H, T, D), UseAs.HOME)));
        assertTrue(tools.contains(LauncherToolRegistry.TOOL_WALL_WIDGETS));
        assertTrue(tools.contains(LauncherToolRegistry.TOOL_WALL_TERMINAL));
        assertTrue(tools.contains(LauncherToolRegistry.TOOL_WALL_DISPLAY));
    }

    /** A fresh wall with a Display page and the display never run: the Display key is there. */
    @Test
    public void aFreshWallWithADisplayPageShowsTheDisplayKey() {
        assertTrue(tools(ExtraKeysDefaultRow.forWall(SHIPPED,
            WallKeys.kinds(of(H, T, D), UseAs.HOME))).contains(LauncherToolRegistry.TOOL_WALL_DISPLAY));
        assertFalse(tools(ExtraKeysDefaultRow.forWall(SHIPPED,
            WallKeys.kinds(of(H, T), UseAs.HOME))).contains(LauncherToolRegistry.TOOL_WALL_DISPLAY));
    }

    /** A single-kind wall keeps the key of the kind it has and drops the absent kinds'. */
    @Test
    public void aSingleKindWallKeepsItsOwnKey() {
        List<String> tools = tools(ExtraKeysDefaultRow.forWall(SHIPPED,
            WallKeys.kinds(of(T), UseAs.HOME)));
        assertTrue(tools.contains(LauncherToolRegistry.TOOL_WALL_TERMINAL));
        assertFalse(tools.contains(LauncherToolRegistry.TOOL_WALL_WIDGETS));
        assertFalse(tools.contains(LauncherToolRegistry.TOOL_WALL_DISPLAY));
    }
}
