package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Linux apps launched again for loaded saved Workspaces (#431), with the catalogue read directly. */
public class LinuxAppRelauncherTest {

    private final List<Runnable> files = new ArrayList<>();
    private final List<String> asked = new ArrayList<>();
    private final Map<Long, String> keys = new HashMap<>();
    private long now = 1000L;
    private int scans;
    private List<LinuxAppCatalog.LinuxApp> catalogue = Arrays.asList(
        new LinuxAppCatalog.LinuxApp("firefox", "Firefox", "firefox", "", "", "firefox"),
        new LinuxAppCatalog.LinuxApp("org.kde.kate", "Kate", "kate", "", "", ""));
    private boolean launches = true;

    private final LinuxAppRelauncher relauncher = new LinuxAppRelauncher(files::add, Runnable::run,
        () -> {
            scans++;
            return catalogue;
        }, id -> keys.get(id), () -> now);

    private final LinuxAppRelauncher.Launcher launcher = app -> {
        asked.add("launch " + app.id);
        return launches;
    };

    private final LinuxAppRelauncher.Answer answer = new LinuxAppRelauncher.Answer() {
        @Override public void launched(long token, @NonNull String key) { asked.add("launched " + token + " " + key); }
        @Override public void notFound(long token) { asked.add("not found " + token); }
    };

    @Test
    public void appsAreFoundByKeyElseByWindowClassInOneScanOffTheMainThread() {
        relauncher.relaunch(Arrays.asList(
            new LinuxAppRelauncher.Request(1, "firefox", "firefox"),
            new LinuxAppRelauncher.Request(2, null, "kate"),
            new LinuxAppRelauncher.Request(3, "gone.desktop", "gone")), launcher, answer);
        assertEquals("nothing until the file thread runs", Collections.emptyList(), asked);

        runFiles();

        assertEquals(1, scans);
        assertEquals(Arrays.asList("launch firefox", "launched 1 firefox", "launch org.kde.kate",
            "launched 2 org.kde.kate", "not found 3"), asked);
    }

    @Test
    public void aLaunchThatDoesNotStartIsNotFound() {
        launches = false;
        relauncher.relaunch(Collections.singletonList(new LinuxAppRelauncher.Request(1, "firefox", "")),
            launcher, answer);
        runFiles();

        assertEquals(Arrays.asList("launch firefox", "not found 1"), asked);
        keys.put(70L, "firefox");
        assertNull(relauncher.claim(70L));
    }

    @Test
    public void onlyAWindowOfAnOutstandingRelaunchIsClaimedAndEachOnlyOnce() {
        relauncher.relaunch(Collections.singletonList(new LinuxAppRelauncher.Request(1, "firefox", "")),
            launcher, answer);
        runFiles();
        keys.put(70L, "firefox");
        keys.put(71L, "firefox");
        keys.put(72L, "org.kde.kate");

        assertNull("not a relaunched app", relauncher.claim(72L));
        assertNull("no key known", relauncher.claim(73L));
        assertEquals("firefox", relauncher.claim(70L));
        assertNull("the user's own second Firefox", relauncher.claim(71L));
    }

    @Test
    public void aRelaunchWhoseWindowNeverCameIsForgotten() {
        relauncher.relaunch(Collections.singletonList(new LinuxAppRelauncher.Request(1, "firefox", "")),
            launcher, answer);
        runFiles();
        now += LinuxAppRelauncher.OUTSTANDING_MS + 1;
        keys.put(70L, "firefox");

        assertNull(relauncher.claim(70L));
    }

    @Test
    public void anUnreadableCatalogueFindsNothing() {
        catalogue = null;
        relauncher.relaunch(Collections.singletonList(new LinuxAppRelauncher.Request(5, "firefox", "firefox")),
            launcher, answer);
        runFiles();

        assertEquals(Collections.singletonList("not found 5"), asked);
    }

    private void runFiles() {
        List<Runnable> queued = new ArrayList<>(files);
        files.clear();
        for (Runnable task : queued) task.run();
    }
}
