package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The Paste shortcut of a Linux window: the app's own over the display's, kept per class. */
public class LinuxPasteShortcutsTest {

    private static final class Fake implements LinuxPasteShortcuts.Source {
        String global = "ctrl_v";
        final Map<String, String> overrides = new HashMap<>();
        final Map<String, String> apps = new HashMap<>();
        final Set<String> desktops = new HashSet<>();
        int scans;

        @NonNull @Override public String globalShortcut() { return global; }
        @Nullable @Override public String overrideOf(@NonNull String appId) { return overrides.get(appId); }
        @Override public boolean followsDisplay(@NonNull String appId) { return desktops.contains(appId); }
        @Nullable @Override public String appIdFor(@NonNull String wmClass) { scans++; return apps.get(wmClass); }
    }

    private final Fake fake = new Fake();
    private final Deque<Runnable> queue = new ArrayDeque<>();
    private final LinuxPasteShortcuts shortcuts = new LinuxPasteShortcuts(fake, queue::add);

    private void runWork() {
        while (!queue.isEmpty()) queue.poll().run();
    }

    @Test
    public void theDisplaysShortcutAnswersUntilTheAppResolves() {
        fake.apps.put("foot", "foot.desktop");
        fake.overrides.put("foot.desktop", "ctrl_shift_v");
        assertFalse(shortcuts.withShift("foot"));
        runWork();
        assertTrue(shortcuts.withShift("foot"));
    }

    @Test
    public void anAppsOwnShortcutWinsWithNoX11Involved() {
        fake.global = "ctrl_shift_v";
        fake.apps.put("gedit", "gedit.desktop");
        fake.overrides.put("gedit.desktop", "ctrl_v");
        shortcuts.withShift("gedit");
        runWork();
        assertFalse(shortcuts.withShift("gedit"));
    }

    @Test
    public void aRunningDesktopOnlyCapturesItsOwnWindow() {
        fake.desktops.add("sway.desktop");
        fake.apps.put("sway", "sway.desktop");
        fake.apps.put("other", "other.desktop");
        fake.overrides.put("sway.desktop", "ctrl_shift_v");
        fake.overrides.put("other.desktop", "ctrl_shift_v");
        shortcuts.withShift("sway");
        shortcuts.withShift("other");
        runWork();
        assertFalse(shortcuts.withShift("sway"));
        assertTrue(shortcuts.withShift("other"));
    }

    @Test
    public void anAnswerIsKeptUntilSettingsChange() {
        fake.apps.put("foot", "foot.desktop");
        shortcuts.withShift("foot");
        runWork();
        shortcuts.withShift("foot");
        shortcuts.withShift("foot");
        runWork();
        assertEquals(1, fake.scans);
        fake.overrides.put("foot.desktop", "ctrl_shift_v");
        shortcuts.invalidate();
        shortcuts.withShift("foot");
        runWork();
        assertTrue(shortcuts.withShift("foot"));
    }

    @Test
    public void anUnknownClassFollowsTheDisplay() {
        fake.global = "ctrl_shift_v";
        shortcuts.withShift("mystery");
        runWork();
        assertTrue(shortcuts.withShift("mystery"));
    }
}
