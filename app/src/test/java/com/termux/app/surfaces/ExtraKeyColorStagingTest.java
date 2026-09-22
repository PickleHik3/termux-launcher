package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.io.ExtraKeysLayoutModel;
import com.termux.shared.termux.extrakeys.ExtraKeyColorRole;

import org.junit.Test;

import java.util.Map;

/**
 * The Appearance editor's half of the key colours: what it holds while the card is up, what makes
 * the card read as unsaved, what Discard drops and what Done writes.
 */
public class ExtraKeyColorStagingTest {

    @Test
    public void anUntouchedSessionStagesNothingAndChangesNoSignature() {
        ExtraKeyColorStaging staging = new ExtraKeyColorStaging();
        assertTrue(staging.isEmpty());
        assertEquals(0, staging.size());
        assertEquals("", staging.signature());
        assertFalse(staging.hasPick(0));
        // With no pick, a key shows what it is stored with.
        assertSame(ExtraKeyColorRole.PRIMARY,
            staging.roleFor(0, ExtraKeyColorRole.PRIMARY));
        assertNull(staging.roleFor(0, null));
    }

    @Test
    public void aPickStandsInFrontOfTheStoredColourAndMakesTheCardDirty() {
        ExtraKeyColorStaging staging = new ExtraKeyColorStaging();
        String clean = staging.signature();

        staging.stage(2, ExtraKeyColorRole.ERROR);
        assertTrue(staging.hasPick(2));
        assertSame(ExtraKeyColorRole.ERROR, staging.roleFor(2, ExtraKeyColorRole.PRIMARY));
        assertFalse("a pick has to count toward the unsaved check",
            clean.equals(staging.signature()));

        // Picking again on the same key replaces rather than accumulates.
        staging.stage(2, ExtraKeyColorRole.WHITE);
        assertEquals(1, staging.size());
        assertSame(ExtraKeyColorRole.WHITE, staging.roleFor(2, null));
    }

    @Test
    public void takingAColourOffIsItselfAPick() {
        ExtraKeyColorStaging staging = new ExtraKeyColorStaging();
        staging.stage(1, null);
        assertTrue("staging \"no colour\" is not the same as never picking", staging.hasPick(1));
        assertFalse(staging.isEmpty());
        assertFalse(staging.signature().isEmpty());
        // The stored colour must not come back through the gap.
        assertNull(staging.roleFor(1, ExtraKeyColorRole.TERTIARY_CONTAINER));
    }

    @Test
    public void twoDifferentPicksNeverShareASignature() {
        ExtraKeyColorStaging one = new ExtraKeyColorStaging();
        ExtraKeyColorStaging other = new ExtraKeyColorStaging();
        one.stage(0, ExtraKeyColorRole.PRIMARY);
        other.stage(0, ExtraKeyColorRole.SECONDARY);
        assertFalse(one.signature().equals(other.signature()));

        other.stage(0, ExtraKeyColorRole.PRIMARY);
        assertEquals(one.signature(), other.signature());

        // The key a colour landed on is part of the question too.
        other.stage(1, ExtraKeyColorRole.PRIMARY);
        assertFalse(one.signature().equals(other.signature()));
    }

    @Test
    public void discardDropsEveryPickAndLeavesNothingBehind() {
        ExtraKeyColorStaging staging = new ExtraKeyColorStaging();
        staging.stage(0, ExtraKeyColorRole.BLACK);
        staging.stage(3, null);
        assertEquals(2, staging.size());

        staging.clear();

        assertTrue(staging.isEmpty());
        assertEquals("", staging.signature());
        assertFalse(staging.hasPick(0));
        assertFalse(staging.hasPick(3));
        assertSame("a discarded session leaves the stored colour showing",
            ExtraKeyColorRole.BLACK, staging.roleFor(0, ExtraKeyColorRole.BLACK));
    }

    @Test
    public void theSnapshotSurvivesTheStagingBeingCleared() {
        ExtraKeyColorStaging staging = new ExtraKeyColorStaging();
        staging.stage(0, ExtraKeyColorRole.ERROR);
        staging.stage(1, null);

        Map<Integer, ExtraKeyColorRole> snapshot = staging.snapshot();
        staging.clear();

        assertEquals(2, snapshot.size());
        assertSame(ExtraKeyColorRole.ERROR, snapshot.get(0));
        assertTrue(snapshot.containsKey(1));
        assertNull(snapshot.get(1));
    }

    @Test
    public void doneWritesTheSnapshotIntoTheStoredPage() {
        // The whole commit path, end to end: pick on the row, then write into the page's JSON.
        ExtraKeysLayoutModel stored = ExtraKeysLayoutModel.parse(
            "[[ESC, {key: 'TAB', color: 'primary'}],[CTRL]]");
        ExtraKeyColorStaging staging = new ExtraKeyColorStaging();
        staging.stage(0, ExtraKeyColorRole.SECONDARY_CONTAINER);
        staging.stage(1, null);

        assertTrue(stored.applyColorsByIndex(staging.snapshot()));

        assertSame(ExtraKeyColorRole.SECONDARY_CONTAINER, stored.row(0).get(0).color);
        assertNull("the pick that cleared a colour has to clear it in the file too",
            stored.row(0).get(1).color);
        assertNull(stored.row(1).get(0).color);
        // Round-tripped rather than string-matched: the key order inside a JSON object is the
        // library's business, and this test runs off the plain org.json rather than Android's.
        ExtraKeysLayoutModel reread = ExtraKeysLayoutModel.parse(stored.serialize());
        assertSame(ExtraKeyColorRole.SECONDARY_CONTAINER, reread.row(0).get(0).color);
        assertEquals("ESC", reread.row(0).get(0).key);
        assertNull(reread.row(0).get(1).color);
        assertEquals("TAB", reread.row(0).get(1).key);
        assertEquals("CTRL", reread.row(1).get(0).key);
    }
}
