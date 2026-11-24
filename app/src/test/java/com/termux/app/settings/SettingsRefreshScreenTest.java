package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.registry.SettingRefresh;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The refresh switch that used to sit in the activity: each refresh reaches its own call, later. */
public class SettingsRefreshScreenTest {

    private final List<Runnable> posted = new ArrayList<>();
    private final List<String> done = new ArrayList<>();
    private boolean gone;

    private final SettingsRefreshScreen screen = new SettingsRefreshScreen(
        new SettingsRefreshScreen.Host() {
            @Override public boolean isGone() { return gone; }

            @Override public void post(@NonNull Runnable work) { posted.add(work); }

            @Override public void restyle(boolean recreate) { done.add("restyle " + recreate); }

            @Override public void applyTextSize() { done.add("text size"); }

            @Override public void applyFontSizes() { done.add("font sizes"); }
        });

    private void runPosted() {
        for (Runnable each : new ArrayList<>(posted)) each.run();
        posted.clear();
    }

    @Test
    public void eachRefreshIsPostedAndThenReachesItsOwnCall() {
        for (SettingRefresh each : SettingRefresh.values()) screen.run(each);
        assertTrue("nothing runs inside the call that asked", done.isEmpty());
        runPosted();
        assertEquals(Arrays.asList("restyle false", "restyle true", "text size", "font sizes"), done);
    }

    @Test
    public void aListenerRefreshIsLeftToTheListener() {
        screen.run(SettingRefresh.LISTENER);
        runPosted();
        assertEquals(Collections.emptyList(), done);
    }

    @Test
    public void aScreenThatWentAwayBeforeTheRefreshRunsDoesNothing() {
        screen.run(SettingRefresh.THEME);
        gone = true;
        runPosted();
        assertTrue(done.isEmpty());
    }
}
