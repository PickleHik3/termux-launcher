package com.termux.shared.termux.settings.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The Settings applier: a save's refresh at once on screen, else on the launcher's next resume. */
public class SettingsApplierTest {

    private static final class FakeHost implements SettingsApplier.Host {
        boolean resumed;
        final List<SettingRefresh> ran = new ArrayList<>();

        @Override public boolean isLauncherResumed() { return resumed; }

        @Override public void run(@NonNull SettingRefresh refresh) { ran.add(refresh); }
    }

    private final InMemorySettingStore store = new InMemorySettingStore();
    private final SettingsRegistry registry = new SettingsRegistry(which -> store);
    private final FakeHost host = new FakeHost();
    private final SettingsApplier applier = new SettingsApplier(host);

    @Test
    public void aChangeRefreshesAtOnceWhileTheLauncherIsResumed() {
        host.resumed = true;
        applier.apply(registry.set("terminal.contrast", "harder").change());
        assertEquals(Collections.singletonList(SettingRefresh.RESTYLE), host.ran);
        applier.onLauncherResumed();
        assertEquals(1, host.ran.size());
    }

    @Test
    public void aChangeWaitsForTheNextResumeWhileTheLauncherIsAway() {
        applier.apply(registry.set("terminal.contrast", "harder").change());
        applier.apply(registry.set("theme.mode", "dark").change());
        applier.apply(registry.set("terminal.contrast", "softer").change());
        assertTrue(host.ran.isEmpty());

        host.resumed = true;
        applier.onLauncherResumed();
        assertEquals(Arrays.asList(SettingRefresh.RESTYLE, SettingRefresh.THEME), host.ran);
        // Run once: a later resume owes nothing.
        applier.onLauncherResumed();
        assertEquals(2, host.ran.size());
    }

    @Test
    public void aSaveThatChangedNothingRefreshesNothing() {
        host.resumed = true;
        applier.apply(registry.set("text.size", "100").change());
        assertTrue(host.ran.isEmpty());
    }

    @Test
    public void aKeyAListenerAlreadyRefreshesAddsNothing() {
        host.resumed = true;
        SettingDeclaration watched = SettingDeclaration.integer("test.watched",
            SettingDeclaration.Store.APP_PREFERENCES, "watched", 0, 10, 1, 5,
            SettingRefresh.LISTENER);
        SettingsRegistry withListener =
            new SettingsRegistry(which -> store, Collections.singletonList(watched));
        applier.apply(withListener.set("test.watched", "7").change());
        host.resumed = false;
        applier.apply(withListener.set("test.watched", "8").change());
        host.resumed = true;
        applier.onLauncherResumed();
        assertTrue(host.ran.isEmpty());
    }

    @Test
    public void aRefusalOrNothingRefreshesNothing() {
        host.resumed = true;
        applier.apply(registry.set("text.size", "999").change());
        applier.apply(null);
        assertTrue(host.ran.isEmpty());
    }
}
