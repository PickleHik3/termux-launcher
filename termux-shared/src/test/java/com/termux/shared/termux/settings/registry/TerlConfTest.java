package com.termux.shared.termux.settings.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** terl.conf: applied all or nothing, once, with follow and default. */
public class TerlConfTest {

    private final InMemorySettingStore store = new InMemorySettingStore();
    private final List<SettingRefresh> refreshed = new ArrayList<>();
    private final SettingsApplier applier = new SettingsApplier(new SettingsApplier.Host() {
        @Override public boolean isLauncherResumed() { return true; }
        @Override public void run(@NonNull SettingRefresh refresh) { refreshed.add(refresh); }
    });
    private final MemoryRecord record = new MemoryRecord();

    private static final class MemoryRecord implements TerlConf.Record {
        @Nullable String held;
        @Nullable @Override public String applied() { return held; }
        @Override public void remember(@NonNull String fingerprint) { held = fingerprint; }
    }

    /** A surface value that can follow Global, and one that refuses 99 only while saving. */
    private static final SettingDeclaration SURFACE = SettingDeclaration.integer("look.dock.opacity",
        SettingDeclaration.Store.APP_PREFERENCES, "x", 0, 100, 1, 50, SettingRefresh.LISTENER);
    private static final SettingDeclaration LATE = SettingDeclaration.integer("late.fail",
        SettingDeclaration.Store.APP_PREFERENCES, "y", 0, 100, 1, 0, SettingRefresh.LISTENER);

    private static final class Surfaces implements SettingAdapter {
        int global = 70;
        @Nullable Integer own;

        @Nullable @Override public SettingDeclaration declaration(@NonNull String id) {
            return id.equals(SURFACE.id) ? SURFACE : id.equals(LATE.id) ? LATE : null;
        }
        @NonNull @Override public List<SettingDeclaration> declarations() {
            return Arrays.asList(SURFACE, LATE);
        }
        @Override public boolean canFollow(@NonNull SettingDeclaration d) { return d == SURFACE; }
        @Override public boolean overrides(@NonNull SettingDeclaration d) {
            return d != SURFACE || own != null;
        }
        @NonNull @Override public String get(@NonNull SettingDeclaration d) {
            return d == SURFACE ? String.valueOf(own != null ? own : global) : "0";
        }
        @Nullable @Override public SettingsRegistry.Refusal check(@NonNull SettingDeclaration d,
                                                                 @NonNull String value) {
            return null; // LATE refuses only when saving
        }
        @NonNull @Override public SettingsRegistry.Outcome set(@NonNull SettingDeclaration d,
                                                              @NonNull String value) {
            if (d == LATE) return new SettingsRegistry.Refusal("late.fail refuses " + value, null);
            String old = get(d);
            own = Integer.parseInt(value);
            return new SettingsRegistry.Change(d, old, value);
        }
        @NonNull @Override public SettingsRegistry.Outcome follow(@NonNull SettingDeclaration d) {
            String old = get(d);
            own = null;
            return new SettingsRegistry.Change(d, old, get(d));
        }
    }

    private final Surfaces surfaces = new Surfaces();
    private final SettingsRegistry registry = new SettingsRegistry(which -> store,
        SettingDeclarations.ALL, Collections.singletonList(surfaces));

    private TerlConf.Outcome sync(String text) {
        return TerlConf.sync(text, record, registry, applier);
    }

    @Test
    public void aValidFileAppliesEveryLine() {
        TerlConf.Outcome out = sync("text.size = 120\nterminal.contrast = harder\n");
        assertEquals(TerlConf.Kind.APPLIED, out.kind);
        assertEquals("120", registry.get("text.size"));
        assertEquals("harder", registry.get("terminal.contrast"));
        assertNotNull(record.held);
    }

    @Test
    public void oneBadLineAppliesNothingAndNamesTheLine() {
        TerlConf.Outcome out = sync("text.size = 120\nterminal.sparkle = 1\n");
        assertEquals(TerlConf.Kind.FAILED, out.kind);
        assertEquals(Arrays.asList("line 2: no setting is called \"terminal.sparkle\""), out.errors);
        assertEquals("100", registry.get("text.size"));
        assertNull(record.held);
    }

    @Test
    public void aRefusalWhileSavingRollsBackEarlierLines() {
        surfaces.own = 10;
        TerlConf.Outcome out = sync("text.size = 120\nlook.dock.opacity = 40\nlate.fail = 5\n");
        assertEquals(TerlConf.Kind.FAILED, out.kind);
        assertEquals(Arrays.asList("line 3: late.fail refuses 5"), out.errors);
        assertEquals("100", registry.get("text.size"));
        assertEquals("10", registry.get("look.dock.opacity"));
        assertNull(record.held);
        assertTrue(refreshed.isEmpty());
    }

    @Test
    public void aRollbackPutsAFollowerBackOnFollowing() {
        TerlConf.Outcome out = sync("look.dock.opacity = 40\nlate.fail = 5\n");
        assertEquals(TerlConf.Kind.FAILED, out.kind);
        assertNull(surfaces.own);
        assertEquals("70", registry.get("look.dock.opacity"));
    }

    @Test
    public void anUnchangedFileIsNotAppliedAgain() {
        String file = "text.size = 120\n";
        assertEquals(TerlConf.Kind.APPLIED, sync(file).kind);
        store.putInt("text_size_percent", 90); // an edit made in the screens
        assertEquals(TerlConf.Kind.UNCHANGED, sync(file).kind);
        assertEquals("90", registry.get("text.size"));
        assertEquals(TerlConf.Kind.APPLIED, sync(file + "# changed\n").kind);
        assertEquals("120", registry.get("text.size"));
    }

    @Test
    public void aFileChangedWhileOffAppliesAtTheNextStart() {
        record.held = TerlConf.fingerprint("text.size = 120\n");
        assertEquals(TerlConf.Kind.APPLIED, sync("text.size = 130\n").kind);
        assertEquals("130", registry.get("text.size"));
    }

    @Test
    public void aFailedFileIsReportedAgainWhenSavedAgain() {
        assertEquals(TerlConf.Kind.FAILED, sync("text.size = 999\n").kind);
        assertEquals(TerlConf.Kind.FAILED, sync("text.size = 999\n").kind);
    }

    @Test
    public void noFileDoesNothing() {
        assertEquals(TerlConf.Kind.ABSENT, sync(null).kind);
        assertNull(record.held);
    }

    @Test
    public void omittedIdsKeepTheirValues() {
        store.putInt("text_size_percent", 90);
        sync("terminal.contrast = softer\n");
        assertEquals("90", registry.get("text.size"));
    }

    @Test
    public void defaultResetsAndFollowPutsASurfaceBackOnGlobal() {
        store.putInt("text_size_percent", 90);
        surfaces.own = 10;
        TerlConf.Outcome out = sync("text.size = default\nlook.dock.opacity = follow\n");
        assertEquals(TerlConf.Kind.APPLIED, out.kind);
        assertEquals("100", registry.get("text.size"));
        assertNull(surfaces.own);
        assertTrue(registry.follows("look.dock.opacity"));
    }

    @Test
    public void followAndDefaultAreNotPlainValues() {
        assertEquals("text.size has no Global to follow",
            registry.set("text.size", "follow").refusal().reason);
        assertEquals("late.fail has no Global to follow",
            registry.set("late.fail", "FOLLOW").refusal().reason);
        TerlConf.Outcome out = sync("text.size = follow\n");
        assertEquals(TerlConf.Kind.FAILED, out.kind);
        assertEquals(Arrays.asList("line 1: text.size has no Global to follow"), out.errors);
        // default on a choice named default still lands on the default
        store.putString("terminal_contrast_level", "harder");
        sync("terminal.contrast = default\n");
        assertEquals("default", registry.get("terminal.contrast"));
    }

    @Test
    public void exportWritesFollowForASurfaceOnGlobalAndReappliesCleanly() {
        String onGlobal = SettingsFile.export(registry);
        assertTrue(onGlobal, onGlobal.contains("\nlook.dock.opacity = follow"));
        surfaces.own = 25;
        String detached = SettingsFile.export(registry);
        assertTrue(detached, detached.contains("\nlook.dock.opacity = 25"));
        assertFalse(detached, detached.contains("= follow"));
        // an exported file applies clean, and leaves a follower following
        surfaces.own = null;
        TerlConf.Outcome out = sync(onGlobal.replace("late.fail = 0\n", "").replace("\nlate.fail = 0", ""));
        assertEquals(out.errors.toString(), TerlConf.Kind.APPLIED, out.kind);
        assertNull(surfaces.own);
    }

    @Test
    public void changesReachTheApplierOnlyWhenTheWholeFileLands() {
        sync("terminal.contrast = harder\n");
        assertFalse(refreshed.isEmpty());
    }
}
