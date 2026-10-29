package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.AppEdition;

import org.junit.Test;

public class WhatsNewNotesTest {

    private static final String STABLE = "# Changelog\n\nintro\n\n---\n\n"
        + "## v1.0.6\n\nA Low-memory preset.\n\n### New\n\n- One.\n\n"
        + "## v1.0.5\n\nOlder.\n\n### Fixes\n\n- Two.\n";

    private static final String ROLLING = "# Pre-release channel\n\nLatest stable: v1.0.6.\n\n"
        + "## New in this drop\n\n- Glass.\n";

    @Test
    public void aStableBuildGetsItsOwnSectionAndNoNeighbours() {
        WhatsNewNotes.Notes notes = WhatsNewNotes.select("1.0.6", STABLE, ROLLING, AppEdition.TERMUX);
        assertNotNull(notes);
        assertTrue(notes.body.startsWith("A Low-memory preset."));
        assertTrue(notes.body.contains("- One."));
        assertFalse(notes.body.contains("Older."));
        assertFalse(notes.body.contains("## v1.0.6"));
    }

    @Test
    public void aNightlyFollowsTheReleaseItBuildsOn() {
        WhatsNewNotes.Notes notes =
            WhatsNewNotes.select("1.0.5+dev.abc1234", STABLE, ROLLING, AppEdition.TERMUX);
        assertNotNull(notes);
        assertTrue(notes.body.startsWith("Older."));
        assertEquals("1.0.5", notes.version);
    }

    @Test
    public void aPreReleaseReadsTheRollingListWithoutItsTitle() {
        WhatsNewNotes.Notes notes =
            WhatsNewNotes.select("1.1.0-pre.6", STABLE, ROLLING, AppEdition.TERMUX);
        assertNotNull(notes);
        assertTrue(notes.body.contains("- Glass."));
        assertFalse(notes.body.contains("# Pre-release channel"));
    }

    @Test
    public void aBuildAheadOfItsNotesHasNone() {
        assertNull(WhatsNewNotes.select("1.2.0", STABLE, ROLLING, AppEdition.TERMUX));
        assertNull(WhatsNewNotes.select("1.0.6", null, null, AppEdition.TERMUX));
        assertNull(WhatsNewNotes.select("1.1.0-pre.1", STABLE, null, AppEdition.TERMUX));
    }

    @Test
    public void aLongerVersionIsNotMistakenForAPrefix() {
        String notes = "## v1.0.60\n\nSixty.\n\n## v1.0.6\n\nSix.\n";
        assertEquals("Six.", WhatsNewNotes.select("1.0.6", notes, null, AppEdition.TERMUX).body);
    }

    @Test
    public void theTagCarriesTheEditionPrefix() {
        assertEquals("v1.0.6", WhatsNewNotes.tag("1.0.6+dev.x", AppEdition.TERMUX));
        assertEquals("nix-v1.0.6", WhatsNewNotes.tag("1.0.6", AppEdition.NIX));
        assertEquals("vaj-v1.1.0-pre.6", WhatsNewNotes.tag("1.1.0-pre.6", AppEdition.VAJ));
        assertEquals("https://github.com/PickleHik3/termux-launcher/releases/tag/nix-v1.0.6",
            WhatsNewNotes.url("1.0.6", AppEdition.NIX));
    }
}
