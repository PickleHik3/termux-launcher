package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Plain-JUnit coverage of the serialise/restore/expiry logic {@link SettingsActivity} builds on,
 * kept free of Robolectric since {@link SettingsBackStackState} touches no Android framework class.
 */
public class SettingsBackStackStateTest {

    @Test
    public void serializeThenParseRoundTripsEveryField() {
        List<SettingsBackStackState.Entry> entries = Arrays.asList(
            new SettingsBackStackState.Entry("com.termux.app.fragments.settings.termux.KeyboardPreferencesFragment",
                42, null, null, null),
            new SettingsBackStackState.Entry("com.termux.app.fragments.settings.termux.SpeechModelPreferencesFragment",
                0, "Speech model", "widgets", "row_key"));
        SettingsBackStackState original = new SettingsBackStackState(entries, 1_000_000L);

        SettingsBackStackState restored = SettingsBackStackState.parse(original.serialize());

        assertTrue(restored != null);
        assertEquals(1_000_000L, restored.stoppedAtEpochMs);
        assertEquals(2, restored.entries.size());

        SettingsBackStackState.Entry first = restored.entries.get(0);
        assertEquals("com.termux.app.fragments.settings.termux.KeyboardPreferencesFragment", first.className);
        assertEquals(42, first.titleResId);
        assertNull(first.titleText);
        assertNull(first.place);
        assertNull(first.scrollToKey);

        SettingsBackStackState.Entry second = restored.entries.get(1);
        assertEquals("com.termux.app.fragments.settings.termux.SpeechModelPreferencesFragment", second.className);
        assertEquals(0, second.titleResId);
        assertEquals("Speech model", second.titleText);
        assertEquals("widgets", second.place);
        assertEquals("row_key", second.scrollToKey);
    }

    @Test
    public void serializeOfAnEmptyStackParsesBackToAnEmptyStack() {
        SettingsBackStackState original = new SettingsBackStackState(new ArrayList<>(), 500L);
        SettingsBackStackState restored = SettingsBackStackState.parse(original.serialize());
        assertTrue(restored != null);
        assertTrue(restored.entries.isEmpty());
        assertEquals(500L, restored.stoppedAtEpochMs);
    }

    @Test
    public void parseOfNullOrBlankOrMalformedInputReturnsNull() {
        assertNull(SettingsBackStackState.parse(null));
        assertNull(SettingsBackStackState.parse(""));
        assertNull(SettingsBackStackState.parse("   "));
        assertNull(SettingsBackStackState.parse("{not json"));
    }

    @Test
    public void parseSkipsAnEntryWithNoClassNameInsteadOfFailingTheWholeStack() {
        String raw = "{\"stopped_at_epoch_ms\":10,\"entries\":["
            + "{\"title_res_id\":1},"
            + "{\"class_name\":\"com.termux.app.fragments.settings.termux.TaiPreferencesFragment\",\"title_res_id\":7}"
            + "]}";
        SettingsBackStackState restored = SettingsBackStackState.parse(raw);
        assertTrue(restored != null);
        assertEquals(1, restored.entries.size());
        assertEquals("com.termux.app.fragments.settings.termux.TaiPreferencesFragment",
            restored.entries.get(0).className);
    }

    @Test
    public void isFreshIsTrueJustUnderTheRetainWindowAndFalseAtOrOverIt() {
        long stoppedAt = 1_000_000L;
        SettingsBackStackState state = new SettingsBackStackState(new ArrayList<>(), stoppedAt);

        assertTrue(state.isFresh(stoppedAt));
        assertTrue(state.isFresh(stoppedAt + SettingsBackStackState.RETAIN_WINDOW_MS - 1));
        assertFalse(state.isFresh(stoppedAt + SettingsBackStackState.RETAIN_WINDOW_MS));
        assertFalse(state.isFresh(stoppedAt + SettingsBackStackState.RETAIN_WINDOW_MS + 1));
    }

    @Test
    public void isFreshIsFalseWhenTheSavedTimestampIsInTheFuture() {
        // A device clock moved backwards must not be trusted to mean "just now".
        SettingsBackStackState state = new SettingsBackStackState(new ArrayList<>(), 1_000_000L);
        assertFalse(state.isFresh(999_999L));
    }

    @Test
    public void isFreshIsFalseWhenNeverStopped() {
        SettingsBackStackState state = new SettingsBackStackState(new ArrayList<>(), 0);
        assertFalse(state.isFresh(System.currentTimeMillis()));
    }
}
