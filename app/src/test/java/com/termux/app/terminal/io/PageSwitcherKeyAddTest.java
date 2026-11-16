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

    @Test
    public void planWritesOnlyTheThreeTargetsAndNeverGlobalOrHome() {
        Map<String, String> map = new HashMap<>();
        map.put(TermuxPropertyConstants.KEY_EXTRA_KEYS, "[[\"ESC\"]]");
        map.put(TermuxPropertyConstants.KEY_EXTRA_KEYS2, "[[\"F1\"]]");
        List<ExtraKeysTargetDrafts.Write> plan = PageSwitcherKeyAdd.plan(lines(map));
        for (ExtraKeysTargetDrafts.Write write : plan) {
            assertFalse(write.key.equals(ExtraKeysTarget.GLOBAL.page1Key()));
            assertFalse(write.key.equals(ExtraKeysTarget.HOME.page1Key()));
            assertFalse(write.key.equals(ExtraKeysTarget.GLOBAL.page2Key()));
        }
        // Terminal follows Global: it gets its own page 1 (with the key) and keeps Global's page 2.
        assertTrue(plan.contains(new ExtraKeysTargetDrafts.Write(
            ExtraKeysTarget.TERMINAL.page1Key(), "[[\"" + KEY + "\",\"ESC\"]]")));
        assertTrue(plan.contains(new ExtraKeysTargetDrafts.Write(
            ExtraKeysTarget.TERMINAL.page2Key(), "[[\"F1\"]]")));
        // Display and Assistant start from their shipped rows.
        boolean display = false;
        boolean assistant = false;
        for (ExtraKeysTargetDrafts.Write write : plan) {
            display |= write.key.equals(ExtraKeysTarget.DISPLAY.page1Key());
            assistant |= write.key.equals(ExtraKeysTarget.ASSISTANT.page1Key());
        }
        assertTrue(display && assistant);
    }

    @Test
    public void planSkipsATargetThatHasTheKeyOrNoKeys() {
        Map<String, String> map = new HashMap<>();
        map.put(ExtraKeysTarget.TERMINAL.page1Key(), "[[\"" + KEY + "\"]]");
        map.put(ExtraKeysTarget.DISPLAY.page1Key(), "[]");
        List<ExtraKeysTargetDrafts.Write> plan = PageSwitcherKeyAdd.plan(lines(map));
        for (ExtraKeysTargetDrafts.Write write : plan) {
            assertFalse(write.key.equals(ExtraKeysTarget.TERMINAL.page1Key()));
            assertFalse(write.key.equals(ExtraKeysTarget.DISPLAY.page1Key()));
        }
        assertTrue(PageSwitcherKeyAdd.isOn(lines(map)));
    }

    @Test
    public void ownRowKeepsItsPageTwoUntouched() {
        Map<String, String> map = new HashMap<>();
        map.put(ExtraKeysTarget.TERMINAL.page1Key(), "[[\"ESC\"]]");
        map.put(ExtraKeysTarget.TERMINAL.page2Key(), "[[\"F2\"]]");
        for (ExtraKeysTargetDrafts.Write write : PageSwitcherKeyAdd.plan(lines(map)))
            assertFalse(write.key.equals(ExtraKeysTarget.TERMINAL.page2Key()));
    }
}
