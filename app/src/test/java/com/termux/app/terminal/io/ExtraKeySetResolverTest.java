package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.terminal.io.ExtraKeySetResolver.Resolved;
import com.termux.app.terminal.io.ExtraKeySetResolver.Source;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ExtraKeySetResolverTest {

    private static final String OWN1 = "[[ESC]]";
    private static final String OWN2 = "[[TAB]]";

    private static ExtraKeySetResolver.Lines lines(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put(kv[i], kv[i + 1]);
        return map::get;
    }

    // ---- targetFor

    @Test
    public void targetFollowsTheSettledPlace() {
        assertEquals(ExtraKeysTarget.DISPLAY, ExtraKeySetResolver.targetFor(PaneWallPage.DISPLAY, false, false));
        assertEquals(ExtraKeysTarget.DISPLAY, ExtraKeySetResolver.targetFor(PaneWallPage.DISPLAY, true, false));
        assertEquals(ExtraKeysTarget.TERMINAL, ExtraKeySetResolver.targetFor(PaneWallPage.TERMINAL, false, false));
        assertEquals(ExtraKeysTarget.TERMINAL, ExtraKeySetResolver.targetFor(PaneWallPage.TERMINAL, true, true));
        assertEquals(ExtraKeysTarget.ASSISTANT, ExtraKeySetResolver.targetFor(PaneWallPage.ASSISTANT, false, false));
        assertEquals(ExtraKeysTarget.ASSISTANT, ExtraKeySetResolver.targetFor(PaneWallPage.ASSISTANT, true, false));
    }

    @Test
    public void homeIsHomeUnlessAFloatHasTheFocus() {
        assertEquals(ExtraKeysTarget.HOME, ExtraKeySetResolver.targetFor(PaneWallPage.WIDGETS, false, false));
        assertEquals(ExtraKeysTarget.HOME, ExtraKeySetResolver.targetFor(PaneWallPage.WIDGETS, false, true));
        assertEquals(ExtraKeysTarget.TERMINAL, ExtraKeySetResolver.targetFor(PaneWallPage.WIDGETS, true, false));
        assertEquals(ExtraKeysTarget.DISPLAY, ExtraKeySetResolver.targetFor(PaneWallPage.WIDGETS, true, true));
    }

    @Test
    public void noPlaceIsTheTerminal() {
        assertEquals(ExtraKeysTarget.TERMINAL, ExtraKeySetResolver.targetFor(null, false, false));
        assertEquals(ExtraKeysTarget.TERMINAL, ExtraKeySetResolver.targetFor(null, true, true));
    }

    // ---- resolve: own, shipped, global

    @Test
    public void ownSetWinsForEveryTarget() {
        for (ExtraKeysTarget t : ExtraKeysTarget.values()) {
            if (t == ExtraKeysTarget.GLOBAL) continue;
            Resolved r = ExtraKeySetResolver.resolve(t, lines(t.pageKeys()[0], OWN1, t.pageKeys()[1], OWN2));
            assertEquals(t.name(), Source.OWN, r.source);
            assertEquals(t.name(), OWN1, r.page1);
            assertEquals(t.name(), OWN2, r.page2);
            assertEquals(t, r.target);
        }
    }

    @Test
    public void ownSetWithoutPageTwoHasNone() {
        Resolved r = ExtraKeySetResolver.resolve(ExtraKeysTarget.HOME,
            lines(ExtraKeysTarget.HOME.pageKeys()[0], OWN1, TermuxPropertyConstants.KEY_EXTRA_KEYS2, "[[F1]]"));
        assertEquals(Source.OWN, r.source);
        assertNull(r.page2);
        Resolved blank = ExtraKeySetResolver.resolve(ExtraKeysTarget.HOME,
            lines(ExtraKeysTarget.HOME.pageKeys()[0], OWN1, ExtraKeysTarget.HOME.pageKeys()[1], "  "));
        assertNull(blank.page2);
    }

    @Test
    public void shippedSetForDisplayAndAssistantWithNoPageTwo() {
        Resolved d = ExtraKeySetResolver.resolve(ExtraKeysTarget.DISPLAY,
            lines(TermuxPropertyConstants.KEY_EXTRA_KEYS2, "[[F1]]", ExtraKeysTarget.DISPLAY.pageKeys()[1], OWN2));
        assertEquals(Source.SHIPPED, d.source);
        assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_DISPLAY, d.page1);
        assertNull(d.page2);
        Resolved a = ExtraKeySetResolver.resolve(ExtraKeysTarget.ASSISTANT, lines());
        assertEquals(Source.SHIPPED, a.source);
        assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_ASSISTANT, a.page1);
        assertNull(a.page2);
    }

    @Test
    public void terminalAndHomeFollowGlobalWhenTheyHaveNoSet() {
        for (ExtraKeysTarget t : new ExtraKeysTarget[]{ExtraKeysTarget.TERMINAL, ExtraKeysTarget.HOME}) {
            Resolved r = ExtraKeySetResolver.resolve(t, lines(TermuxPropertyConstants.KEY_EXTRA_KEYS, "[[ALT]]",
                TermuxPropertyConstants.KEY_EXTRA_KEYS2, "[[F2]]"));
            assertEquals(Source.GLOBAL, r.source);
            assertEquals("[[ALT]]", r.page1);
            assertEquals("[[F2]]", r.page2);
            assertEquals(t, r.target);
        }
    }

    @Test
    public void globalFallsBackToTheShippedGlobalRow() {
        Resolved r = ExtraKeySetResolver.resolve(ExtraKeysTarget.TERMINAL, lines());
        assertEquals(Source.GLOBAL, r.source);
        assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, r.page1);
        assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS2, r.page2);
    }

    @Test
    public void aBlankPageOneIsAbsent() {
        Resolved shipped = ExtraKeySetResolver.resolve(ExtraKeysTarget.DISPLAY,
            lines(ExtraKeysTarget.DISPLAY.pageKeys()[0], "   ", ExtraKeysTarget.DISPLAY.pageKeys()[1], OWN2));
        assertEquals(Source.SHIPPED, shipped.source);
        Resolved global = ExtraKeySetResolver.resolve(ExtraKeysTarget.TERMINAL,
            lines(ExtraKeysTarget.TERMINAL.pageKeys()[0], ""));
        assertEquals(Source.GLOBAL, global.source);
    }

    @Test
    public void globalTargetIgnoresTargetLines() {
        Resolved r = ExtraKeySetResolver.resolve(ExtraKeysTarget.GLOBAL, lines(
            ExtraKeysTarget.TERMINAL.pageKeys()[0], OWN1,
            ExtraKeysTarget.DISPLAY.pageKeys()[0], OWN1,
            ExtraKeysTarget.GLOBAL.heightKey(), "1.5",
            ExtraKeysTarget.DISPLAY.heightKey(), "2"));
        assertEquals(Source.GLOBAL, r.source);
        assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, r.page1);
        assertEquals(1.5f, r.scale, 0f);
    }

    // ---- scale

    @Test
    public void scaleIsTheTargetsOwnWhenInRange() {
        Resolved r = ExtraKeySetResolver.resolve(ExtraKeysTarget.ASSISTANT, lines(
            ExtraKeysTarget.ASSISTANT.heightKey(), "1.8", ExtraKeysTarget.GLOBAL.heightKey(), "1.2"));
        assertEquals(1.8f, r.scale, 0f);
    }

    @Test
    public void scaleOutOfRangeOrUnparsableFollowsGlobal() {
        for (String bad : new String[]{"0.1", "3.5", "wide", "", "NaN"}) {
            Resolved r = ExtraKeySetResolver.resolve(ExtraKeysTarget.HOME, lines(
                ExtraKeysTarget.HOME.heightKey(), bad, ExtraKeysTarget.GLOBAL.heightKey(), "1.2"));
            assertEquals(bad, 1.2f, r.scale, 0f);
        }
    }

    @Test
    public void scaleAbsentFollowsGlobalAndGlobalDefaultsToOne() {
        assertEquals(1.2f, ExtraKeySetResolver.resolve(ExtraKeysTarget.TERMINAL,
            lines(ExtraKeysTarget.GLOBAL.heightKey(), "1.2")).scale, 0f);
        assertEquals(1f, ExtraKeySetResolver.resolve(ExtraKeysTarget.TERMINAL, lines()).scale, 0f);
    }

    // ---- value semantics and the target table

    @Test
    public void resolvedHasValueEquality() {
        Resolved a = ExtraKeySetResolver.resolve(ExtraKeysTarget.TERMINAL, lines());
        Resolved b = ExtraKeySetResolver.resolve(ExtraKeysTarget.TERMINAL, lines());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, ExtraKeySetResolver.resolve(ExtraKeysTarget.TERMINAL,
            lines(ExtraKeysTarget.TERMINAL.heightKey(), "2")));
    }

    @Test
    public void targetTable() {
        assertEquals("extra-keys", ExtraKeysTarget.GLOBAL.pageKeys()[0]);
        assertEquals("extra-keys2", ExtraKeysTarget.GLOBAL.pageKeys()[1]);
        assertEquals("terminal-toolbar-height", ExtraKeysTarget.GLOBAL.heightKey());
        assertEquals("extra-keys-home", ExtraKeysTarget.HOME.pageKeys()[0]);
        assertEquals("extra-keys-home2", ExtraKeysTarget.HOME.pageKeys()[1]);
        assertEquals("extra-keys-home-height", ExtraKeysTarget.HOME.heightKey());
        assertNull(ExtraKeysTarget.TERMINAL.shippedPage1());
        assertNull(ExtraKeysTarget.HOME.shippedPage1());
        assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, ExtraKeysTarget.GLOBAL.shippedPage1());
        assertNull(ExtraKeysTarget.GLOBAL.markingPlace());
        assertEquals(PaneWallPage.TERMINAL, ExtraKeysTarget.TERMINAL.markingPlace());
        assertEquals(PaneWallPage.DISPLAY, ExtraKeysTarget.DISPLAY.markingPlace());
        assertEquals(PaneWallPage.WIDGETS, ExtraKeysTarget.HOME.markingPlace());
        assertEquals(PaneWallPage.ASSISTANT, ExtraKeysTarget.ASSISTANT.markingPlace());
    }

    // ---- the page switcher key on the built-in sets

    private static final String ADDED = TermuxPropertyConstants.KEY_EXTRA_KEYS_PAGE_SWITCHER_ADDED;

    @Test
    public void builtInSetsLeadWithThePageSwitcherKeyOnlyWhileTheFlagIsSet() {
        for (ExtraKeysTarget target : new ExtraKeysTarget[] {ExtraKeysTarget.DISPLAY, ExtraKeysTarget.ASSISTANT}) {
            ExtraKeySetResolver.Resolved off = ExtraKeySetResolver.resolve(target, lines());
            assertEquals(ExtraKeySetResolver.Source.SHIPPED, off.source);
            assertEquals(target.shippedPage1(), off.page1);
            ExtraKeySetResolver.Resolved on = ExtraKeySetResolver.resolve(target, lines(ADDED, "true"));
            assertEquals(ExtraKeySetResolver.Source.SHIPPED, on.source);
            assertEquals(PageSwitcherKeyAdd.withKey(target.shippedPage1()), on.page1);
            assertEquals(PageSwitcherKeyAdd.KEY,
                ExtraKeysLayoutModel.parse(on.page1).rows().get(0).get(0).key);
        }
        // Global's built-in row too, for the bars that follow it.
        String global = ExtraKeySetResolver.resolve(ExtraKeysTarget.TERMINAL, lines()).page1;
        assertEquals(PageSwitcherKeyAdd.withKey(global),
            ExtraKeySetResolver.resolve(ExtraKeysTarget.TERMINAL, lines(ADDED, "true")).page1);
    }

    @Test
    public void storedRowsAreNotChangedByTheFlagAndTheKeyIsNeverDoubled() {
        String stored = "[[\"ESC\"]]";
        assertEquals(stored, ExtraKeySetResolver.resolve(ExtraKeysTarget.TERMINAL,
            lines(TermuxPropertyConstants.KEY_EXTRA_KEYS, stored, ADDED, "true")).page1);
        assertEquals(stored, ExtraKeySetResolver.resolve(ExtraKeysTarget.DISPLAY,
            lines(ExtraKeysTarget.DISPLAY.pageKeys()[0], stored, ADDED, "true")).page1);
        String led = ExtraKeySetResolver.resolve(ExtraKeysTarget.DISPLAY, lines(ADDED, "true")).page1;
        assertEquals(led, ExtraKeySetResolver.builtInPage1(ExtraKeysTarget.DISPLAY,
            lines(ADDED, "true")));
        // A built-in set already holding the key is not led twice.
        assertEquals(null, PageSwitcherKeyAdd.withKey(led));
    }
}
