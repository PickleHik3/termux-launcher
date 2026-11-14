package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.io.ExtraKeySetResolver.Source;
import com.termux.app.terminal.io.ExtraKeysTargetDrafts.Write;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtraKeysTargetDraftsTest {

    private static ExtraKeySetResolver.Lines lines(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put(kv[i], kv[i + 1]);
        return map::get;
    }

    private static List<Write> writesOf(ExtraKeysTargetDrafts drafts, ExtraKeysTarget target) {
        List<Write> out = new ArrayList<>();
        for (Write w : drafts.writes()) {
            if (w.key.equals(target.page1Key()) || w.key.equals(target.page2Key())
                || w.key.equals(target.heightKey())) out.add(w);
        }
        return out;
    }

    // ---- load

    @Test
    public void loadReadsOwnShippedAndGlobalPerTarget() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL, "[[ESC]]"));
        assertTrue(d.isOwn(ExtraKeysTarget.GLOBAL));
        assertEquals(Source.OWN, d.sourceOf(ExtraKeysTarget.TERMINAL));
        assertEquals(Source.GLOBAL, d.sourceOf(ExtraKeysTarget.HOME));
        assertEquals(Source.SHIPPED, d.sourceOf(ExtraKeysTarget.DISPLAY));
        assertEquals(Source.SHIPPED, d.sourceOf(ExtraKeysTarget.ASSISTANT));
        assertNull(d.draftOf(ExtraKeysTarget.HOME));
        assertEquals(1, d.draftOf(ExtraKeysTarget.TERMINAL).page(0).rowCount());
    }

    @Test
    public void ownTargetLoadsItsOwnKeySize() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL, "[[ESC]]",
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL_HEIGHT, "1.5"));
        assertEquals(15, d.draftOf(ExtraKeysTarget.TERMINAL).keySizeTenths());
    }

    // ---- give / use default

    @Test
    public void giveOwnSetCopiesGlobalAndItsKeySize() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR, "1.3"));
        d.giveOwnSet(ExtraKeysTarget.HOME);
        assertTrue(d.isOwn(ExtraKeysTarget.HOME));
        assertEquals(Source.OWN, d.sourceOf(ExtraKeysTarget.HOME));
        assertEquals(13, d.draftOf(ExtraKeysTarget.HOME).keySizeTenths());
        assertEquals(d.draftOf(ExtraKeysTarget.GLOBAL).valueFor(0), d.draftOf(ExtraKeysTarget.HOME).valueFor(0));
        // A copy: editing Home leaves Global alone.
        d.draftOf(ExtraKeysTarget.HOME).addRow(0);
        d.draftOf(ExtraKeysTarget.HOME).addKey(0, d.draftOf(ExtraKeysTarget.HOME).page(0).rowCount() - 1,
            new ExtraKeysLayoutModel.Key("ESC"));
        assertNotEquals(d.draftOf(ExtraKeysTarget.GLOBAL).valueFor(0), d.draftOf(ExtraKeysTarget.HOME).valueFor(0));
    }

    @Test
    public void giveOwnSetStartsFromTheBuiltInKeysWhenThereAreSome() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines());
        d.giveOwnSet(ExtraKeysTarget.DISPLAY);
        assertEquals(ExtraKeysLayoutModel.parse(ExtraKeysTarget.DISPLAY.shippedPage1()).serialize(),
            d.draftOf(ExtraKeysTarget.DISPLAY).valueFor(0));
        assertEquals("[]", d.draftOf(ExtraKeysTarget.DISPLAY).valueFor(1));
    }

    @Test
    public void useDefaultRoundTrips() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines());
        String before = d.fingerprint();
        d.giveOwnSet(ExtraKeysTarget.DISPLAY);
        assertNotEquals(before, d.fingerprint());
        d.useDefault(ExtraKeysTarget.DISPLAY);
        assertFalse(d.isOwn(ExtraKeysTarget.DISPLAY));
        assertNull(d.draftOf(ExtraKeysTarget.DISPLAY));
        assertEquals(Source.SHIPPED, d.sourceOf(ExtraKeysTarget.DISPLAY));
        assertEquals(before, d.fingerprint());
    }

    @Test
    public void globalCannotLoseItsSet() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines());
        d.useDefault(ExtraKeysTarget.GLOBAL);
        assertTrue(d.isOwn(ExtraKeysTarget.GLOBAL));
    }

    @Test
    public void snapshotRestoreBringsBackAnOwnSet() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL, "[[ESC]]"));
        String before = d.fingerprint();
        ExtraKeysTargetDrafts.Snapshot snap = d.snapshot();
        d.useDefault(ExtraKeysTarget.TERMINAL);
        assertNotEquals(before, d.fingerprint());
        d.restore(snap);
        assertEquals(before, d.fingerprint());
        assertTrue(d.isOwn(ExtraKeysTarget.TERMINAL));
    }

    // ---- writes

    @Test
    public void untouchedWritesOnlyGlobalsPages() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME_HEIGHT, "2.0"));
        for (ExtraKeysTarget t : ExtraKeysTarget.values()) {
            if (t == ExtraKeysTarget.GLOBAL) continue;
            assertTrue(t.name(), writesOf(d, t).isEmpty());
        }
        List<Write> global = writesOf(d, ExtraKeysTarget.GLOBAL);
        assertEquals(2, global.size());
        assertEquals(ExtraKeysTarget.GLOBAL.page1Key(), global.get(0).key);
        assertEquals(ExtraKeysTarget.GLOBAL.page2Key(), global.get(1).key);
    }

    @Test
    public void globalWritesItsKeySizeWhenStoredOrNotDefault() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines());
        assertEquals(2, writesOf(d, ExtraKeysTarget.GLOBAL).size());
        d.draftOf(ExtraKeysTarget.GLOBAL).setKeySizeTenths(12);
        List<Write> w = writesOf(d, ExtraKeysTarget.GLOBAL);
        assertEquals(new Write(ExtraKeysTarget.GLOBAL.heightKey(), "1.2"), w.get(2));
        ExtraKeysTargetDrafts stored = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR, "1.0"));
        assertEquals(new Write(ExtraKeysTarget.GLOBAL.heightKey(), "1.0"),
            writesOf(stored, ExtraKeysTarget.GLOBAL).get(2));
    }

    @Test
    public void ownTargetWritesBothPagesAndHeightOnlyWhenItDiffersFromGlobal() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines());
        d.giveOwnSet(ExtraKeysTarget.HOME);
        List<Write> same = writesOf(d, ExtraKeysTarget.HOME);
        assertEquals(2, same.size());
        assertEquals("[]", same.get(1).value);
        d.draftOf(ExtraKeysTarget.HOME).setKeySizeTenths(18);
        List<Write> sized = writesOf(d, ExtraKeysTarget.HOME);
        assertEquals(3, sized.size());
        assertEquals(new Write(ExtraKeysTarget.HOME.heightKey(), "1.8"), sized.get(2));
    }

    @Test
    public void ownTargetKeepsItsStoredHeightLine() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL, "[[ESC]]",
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL_HEIGHT, "1.0"));
        assertEquals(3, writesOf(d, ExtraKeysTarget.TERMINAL).size());
    }

    @Test
    public void aTargetTurnedBackHasItsThreeLinesRemoved() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL, "[[ESC]]"));
        d.useDefault(ExtraKeysTarget.TERMINAL);
        List<Write> w = writesOf(d, ExtraKeysTarget.TERMINAL);
        assertEquals(3, w.size());
        for (Write write : w) assertNull(write.value);
    }

    @Test
    public void givenAndTakenBackBeforeSavingWritesNothingForIt() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines());
        d.giveOwnSet(ExtraKeysTarget.HOME);
        d.useDefault(ExtraKeysTarget.HOME);
        assertTrue(writesOf(d, ExtraKeysTarget.HOME).isEmpty());
    }

    @Test
    public void markSavedMakesTheSavedStateTheBaseline() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL, "[[ESC]]"));
        d.useDefault(ExtraKeysTarget.TERMINAL);
        d.markSaved();
        assertTrue(writesOf(d, ExtraKeysTarget.TERMINAL).isEmpty());
        d.giveOwnSet(ExtraKeysTarget.HOME);
        d.draftOf(ExtraKeysTarget.HOME).setKeySizeTenths(18);
        d.markSaved();
        assertEquals(3, writesOf(d, ExtraKeysTarget.HOME).size());
    }

    // ---- fingerprint

    @Test
    public void fingerprintChangesWithEditsAndSwitches() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines());
        String start = d.fingerprint();
        d.draftOf(ExtraKeysTarget.GLOBAL).setKeySizeTenths(20);
        assertNotEquals(start, d.fingerprint());
        d.draftOf(ExtraKeysTarget.GLOBAL).setKeySizeTenths(ExtraKeysDraft.KEY_SIZE_DEFAULT_TENTHS);
        assertEquals(start, d.fingerprint());
        d.giveOwnSet(ExtraKeysTarget.ASSISTANT);
        assertNotEquals(start, d.fingerprint());
    }

    // ---- Home's keys switch

    private static final String HOME_KEY = TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME;

    @Test
    public void homeOffWithNoLineIsNotEditedAndShowingStartsFromTheStarterKeys() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_EXTRA_KEYS, "[[F1,F2,F3,F4]]"), false);
        assertFalse(d.isEditing(ExtraKeysTarget.HOME));
        d.showHome();
        assertTrue(d.isEditing(ExtraKeysTarget.HOME));
        assertTrue(d.homeShownChanged());
        assertEquals(3, d.draftOf(ExtraKeysTarget.HOME).page(0).keyCount());
        assertEquals(ExtraKeysLayoutModel.parse(TermuxPropertyConstants.HOME_PILL_STARTER_EXTRA_KEYS)
            .serialize(), d.draftOf(ExtraKeysTarget.HOME).page(0).serialize());
    }

    @Test
    public void homeOnShowsItsSetForEditingAndOffKeepsTheLine() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(HOME_KEY, "[[F1]]"), true);
        assertTrue(d.isEditing(ExtraKeysTarget.HOME));
        assertFalse(d.homeShownChanged());
        d.hideHome();
        assertFalse(d.isEditing(ExtraKeysTarget.HOME));
        assertTrue(d.homeShownChanged());
        assertTrue("the line stays", d.isOwn(ExtraKeysTarget.HOME));
        for (Write w : d.writes()) {
            if (w.key.equals(HOME_KEY)) assertNotNull("the line is not removed", w.value);
        }
    }

    @Test
    public void homeOffWithALineShowsThatLineWhenSwitchedOnNotTheStarter() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(HOME_KEY, "[[F1]]"), false);
        assertFalse(d.isEditing(ExtraKeysTarget.HOME));
        d.showHome();
        assertTrue(d.isEditing(ExtraKeysTarget.HOME));
        assertEquals(1, d.draftOf(ExtraKeysTarget.HOME).page(0).keyCount());
        assertEquals("F1", d.draftOf(ExtraKeysTarget.HOME).page(0).row(0).get(0).key);
    }

    @Test
    public void theSwitchIsPartOfWhatSaveHasToApplyAndUndoBringsItBack() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(), false);
        String before = d.fingerprint();
        ExtraKeysTargetDrafts.Snapshot snap = d.snapshot();
        d.showHome();
        assertNotEquals(before, d.fingerprint());
        d.restore(snap);
        assertEquals(before, d.fingerprint());
        assertFalse(d.homeShownChanged());
        d.showHome();
        d.markSaved();
        assertFalse(d.homeShownChanged());
    }

    @Test
    public void otherTargetsAreEditedExactlyWhenOwn() {
        ExtraKeysTargetDrafts d = ExtraKeysTargetDrafts.load(lines(
            TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL, "[[ESC]]"), true);
        assertTrue(d.isEditing(ExtraKeysTarget.TERMINAL));
        assertFalse(d.isEditing(ExtraKeysTarget.DISPLAY));
        assertTrue(d.isEditing(ExtraKeysTarget.GLOBAL));
    }
}
