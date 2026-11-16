package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageSwitcherKeyAddTest {

    private static final String KEY = PageSwitcherKeyAdd.KEY;

    @Test
    public void insertsAtTheLeftEndOfTheFirstRow() {
        assertEquals("[[\"" + KEY + "\",\"ESC\",\"TAB\"]]",
            PageSwitcherKeyAdd.withKey("[[\"ESC\",\"TAB\"]]"));
    }

    @Test
    public void onlyTheFirstRowOfAMultiRowLineChanges() {
        assertEquals("[[\"" + KEY + "\",\"ESC\"],[\"CTRL\",\"ALT\"]]",
            PageSwitcherKeyAdd.withKey("[[\"ESC\"],[\"CTRL\",\"ALT\"]]"));
    }

    @Test
    public void anEmptyRowIsSkipped() {
        assertNull(PageSwitcherKeyAdd.withKey("[]"));
        assertNull(PageSwitcherKeyAdd.withKey("[[]]"));
        assertNull(PageSwitcherKeyAdd.withKey(null));
    }

    @Test
    public void aRowAlreadyHoldingTheKeyIsSkipped() {
        assertNull(PageSwitcherKeyAdd.withKey("[[\"ESC\",\"" + KEY + "\"]]"));
        assertNull(PageSwitcherKeyAdd.withKey("[[\"ESC\"],[{\"key\":\"" + KEY + "\"}]]"));
        assertTrue(PageSwitcherKeyAdd.holds("[[\"" + KEY + "\"]]"));
        assertFalse(PageSwitcherKeyAdd.holds("[[\"ESC\"]]"));
    }

    private static ExtraKeySetResolver.Lines lines(Map<String, String> map) {
        return map::get;
    }

    private static boolean writes(List<ExtraKeysTargetDrafts.Write> plan, ExtraKeysTarget target,
                                  boolean page2) {
        String key = page2 ? target.pageKeys()[1] : target.pageKeys()[0];
        for (ExtraKeysTargetDrafts.Write write : plan) if (write.key.equals(key)) return true;
        return false;
    }

    @Test
    public void aTargetFollowingGlobalGetsTheKeyThroughGlobalOnce() {
        Map<String, String> map = new HashMap<>();
        map.put(TermuxPropertyConstants.KEY_EXTRA_KEYS, "[[\"ESC\"]]");
        map.put(TermuxPropertyConstants.KEY_EXTRA_KEYS2, "[[\"F1\"]]");
        List<ExtraKeysTargetDrafts.Write> plan = PageSwitcherKeyAdd.plan(lines(map));
        // Terminal and Home follow Global: one write, to Global's page 1, nothing detached.
        assertTrue(plan.contains(new ExtraKeysTargetDrafts.Write(
            ExtraKeysTarget.GLOBAL.pageKeys()[0], "[[\"" + KEY + "\",\"ESC\"]]")));
        int globalWrites = 0;
        for (ExtraKeysTargetDrafts.Write write : plan)
            if (write.key.equals(ExtraKeysTarget.GLOBAL.pageKeys()[0])) globalWrites++;
        assertEquals(1, globalWrites);
        assertFalse(writes(plan, ExtraKeysTarget.TERMINAL, false));
        assertFalse(writes(plan, ExtraKeysTarget.HOME, false));
        assertFalse(writes(plan, ExtraKeysTarget.GLOBAL, true));
        assertFalse(writes(plan, ExtraKeysTarget.TERMINAL, true));
        // Display and Assistant show their shipped sets: left as they are.
        assertFalse(writes(plan, ExtraKeysTarget.DISPLAY, false));
        assertFalse(writes(plan, ExtraKeysTarget.ASSISTANT, false));
    }

    @Test
    public void aTargetWithItsOwnRowGetsTheKeyThere() {
        Map<String, String> map = new HashMap<>();
        map.put(ExtraKeysTarget.DISPLAY.pageKeys()[0], "[[\"ESC\"]]");
        map.put(ExtraKeysTarget.HOME.pageKeys()[0], "[[\"TAB\"]]");
        List<ExtraKeysTargetDrafts.Write> plan = PageSwitcherKeyAdd.plan(lines(map));
        assertTrue(plan.contains(new ExtraKeysTargetDrafts.Write(
            ExtraKeysTarget.DISPLAY.pageKeys()[0], "[[\"" + KEY + "\",\"ESC\"]]")));
        assertTrue(plan.contains(new ExtraKeysTargetDrafts.Write(
            ExtraKeysTarget.HOME.pageKeys()[0], "[[\"" + KEY + "\",\"TAB\"]]")));
    }

    @Test
    public void planSkipsATargetThatHasTheKeyOrNoKeys() {
        Map<String, String> map = new HashMap<>();
        map.put(ExtraKeysTarget.TERMINAL.pageKeys()[0], "[[\"" + KEY + "\"]]");
        map.put(ExtraKeysTarget.DISPLAY.pageKeys()[0], "[]");
        List<ExtraKeysTargetDrafts.Write> plan = PageSwitcherKeyAdd.plan(lines(map));
        assertFalse(writes(plan, ExtraKeysTarget.TERMINAL, false));
        assertFalse(writes(plan, ExtraKeysTarget.DISPLAY, false));
        assertTrue(PageSwitcherKeyAdd.isOn(lines(map)));
    }

    @Test
    public void noPageTwoIsEverWritten() {
        Map<String, String> map = new HashMap<>();
        map.put(ExtraKeysTarget.TERMINAL.pageKeys()[0], "[[\"ESC\"]]");
        map.put(ExtraKeysTarget.TERMINAL.pageKeys()[1], "[[\"F2\"]]");
        for (ExtraKeysTargetDrafts.Write write : PageSwitcherKeyAdd.plan(lines(map)))
            for (ExtraKeysTarget target : ExtraKeysTarget.values())
                assertFalse(write.key.equals(target.pageKeys()[1]));
    }
}
