package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.page.PageKind;
import com.termux.app.terminal.io.ExtraKeySetResolver.Resolved;
import com.termux.app.terminal.io.ExtraKeySetResolver.Source;
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
        assertEquals(ExtraKeysTarget.DISPLAY, ExtraKeySetResolver.targetFor(PageKind.DISPLAY, false, false));
        assertEquals(ExtraKeysTarget.DISPLAY, ExtraKeySetResolver.targetFor(PageKind.DISPLAY, true, false));
        assertEquals(ExtraKeysTarget.TERMINAL, ExtraKeySetResolver.targetFor(PageKind.TERMINAL, false, false));
        assertEquals(ExtraKeysTarget.TERMINAL, ExtraKeySetResolver.targetFor(PageKind.TERMINAL, true, true));
        assertEquals(ExtraKeysTarget.ASSISTANT, ExtraKeySetResolver.targetFor(PageKind.ASSISTANT, false, false));
        assertEquals(ExtraKeysTarget.ASSISTANT, ExtraKeySetResolver.targetFor(PageKind.ASSISTANT, true, false));
    }

    @Test
    public void homeIsHomeUnlessAFloatHasTheFocus() {
        assertEquals(ExtraKeysTarget.HOME, ExtraKeySetResolver.targetFor(PageKind.HOME, false, false));
        assertEquals(ExtraKeysTarget.HOME, ExtraKeySetResolver.targetFor(PageKind.HOME, false, true));
        assertEquals(ExtraKeysTarget.TERMINAL, ExtraKeySetResolver.targetFor(PageKind.HOME, true, false));
        assertEquals(ExtraKeysTarget.DISPLAY, ExtraKeySetResolver.targetFor(PageKind.HOME, true, true));
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
    public void displayAndAssistantFollowGlobalWithNoRowOfTheirOwn() {
        for (ExtraKeysTarget t : new ExtraKeysTarget[]{ExtraKeysTarget.DISPLAY, ExtraKeysTarget.ASSISTANT}) {
            Resolved r = ExtraKeySetResolver.resolve(t, lines(TermuxPropertyConstants.KEY_EXTRA_KEYS, "[[ALT]]",
                TermuxPropertyConstants.KEY_EXTRA_KEYS2, "[[F2]]", t.pageKeys()[1], OWN2));
            assertEquals(Source.GLOBAL, r.source);
            assertEquals("[[ALT]]", r.page1);
            assertEquals("[[F2]]", r.page2);
        }
        Resolved bare = ExtraKeySetResolver.resolve(ExtraKeysTarget.ASSISTANT, lines());
        assertEquals(Source.GLOBAL, bare.source);
        assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, bare.page1);
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
        Resolved display = ExtraKeySetResolver.resolve(ExtraKeysTarget.DISPLAY,
            lines(ExtraKeysTarget.DISPLAY.pageKeys()[0], "   ", ExtraKeysTarget.DISPLAY.pageKeys()[1], OWN2));
        assertEquals(Source.GLOBAL, display.source);
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
        assertNull(ExtraKeysTarget.GLOBAL.markingPlace());
        assertEquals(PageKind.TERMINAL, ExtraKeysTarget.TERMINAL.markingPlace());
        assertEquals(PageKind.DISPLAY, ExtraKeysTarget.DISPLAY.markingPlace());
        assertEquals(PageKind.HOME, ExtraKeysTarget.HOME.markingPlace());
        assertEquals(PageKind.ASSISTANT, ExtraKeysTarget.ASSISTANT.markingPlace());
    }
}
