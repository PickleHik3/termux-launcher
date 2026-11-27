package com.termux.app.look;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.SurfacePresets;
import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class StoredTerlLookStoreTest {

    private final MemoryLookStore backing = new MemoryLookStore();
    private final TerlLookStore store = new StoredTerlLookStore(backing);

    @Test
    public void onlyBarKeyboardAndWindowsAcceptOwnValues() {
        for (TerlSurface surface : TerlSurface.values()) {
            for (TerlProperty property : TerlProperty.values()) {
                boolean accepted = store.setOwn(surface, property, 33);
                boolean should = surface == TerlSurface.CONTROL_BAR
                    || surface == TerlSurface.KEYBOARD || surface == TerlSurface.WINDOWS;
                assertEquals(surface + " " + property, should, accepted);
                assertEquals(should, store.holdsOwn(surface, property));
            }
        }
    }

    @Test
    public void sheetsAndHomeWidgetsAlwaysWearTheLook() {
        assertFalse(store.setOwn(TerlSurface.SHEETS, TerlProperty.OPACITY, 5));
        assertFalse(store.setOwn(TerlSurface.HOME_WIDGETS, TerlProperty.BLUR, 5));
        store.pick(TerlLooks.SOLID, true);
        assertEquals(92, store.value(TerlSurface.SHEETS, TerlProperty.OPACITY));
        assertEquals(92, store.value(TerlSurface.HOME_WIDGETS, TerlProperty.OPACITY));
        assertTrue(backing.keys().isEmpty() || !backing.keys().contains("terl.look.own.sheets.opacity"));
    }

    @Test
    public void aSurfaceWithoutAnOwnValueFollowsGlobal() {
        store.pick(TerlLooks.FROSTED, true);
        assertEquals(60, store.value(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY));
        store.pick(TerlLooks.CLEAR, true);
        assertEquals(10, store.value(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY));
    }

    @Test
    public void anOwnValueWinsAndFollowGlobalClearsIt() {
        store.pick(TerlLooks.FROSTED, true);
        store.setOwn(TerlSurface.KEYBOARD, TerlProperty.OPACITY, 80);
        assertEquals(80, store.value(TerlSurface.KEYBOARD, TerlProperty.OPACITY));
        assertEquals(60, store.value(TerlSurface.WINDOWS, TerlProperty.OPACITY));
        store.followGlobal(TerlSurface.KEYBOARD, TerlProperty.OPACITY);
        assertFalse(store.holdsOwn(TerlSurface.KEYBOARD, TerlProperty.OPACITY));
        assertEquals(60, store.value(TerlSurface.KEYBOARD, TerlProperty.OPACITY));
    }

    @Test
    public void ownValuesAreClamped() {
        store.setOwn(TerlSurface.WINDOWS, TerlProperty.OPACITY, 250);
        store.setOwn(TerlSurface.WINDOWS, TerlProperty.BLUR, -3);
        assertEquals(100, store.value(TerlSurface.WINDOWS, TerlProperty.OPACITY));
        assertEquals(0, store.value(TerlSurface.WINDOWS, TerlProperty.BLUR));
        store.setCustomValue(TerlProperty.BLUR, 99);
        assertEquals(48, store.customValue(TerlProperty.BLUR));
    }

    @Test
    public void customValuesSurviveOtherLooksAndComeBack() {
        store.setCustomValue(TerlProperty.OPACITY, 71);
        store.setCustomValue(TerlProperty.BLUR, 17);
        store.pick(TerlLookStore.CUSTOM, true);
        assertEquals(71, store.global(TerlProperty.OPACITY));
        store.pick(TerlLooks.SOLID, true);
        assertEquals(92, store.global(TerlProperty.OPACITY));
        assertEquals(71, store.customValue(TerlProperty.OPACITY));
        store.pick(TerlLooks.CLEAR, true);
        store.pick(TerlLookStore.CUSTOM, true);
        assertEquals(71, store.global(TerlProperty.OPACITY));
        assertEquals(17, store.global(TerlProperty.BLUR));
    }

    @Test
    public void customStartsWithTheFirstLooksNumbers() {
        TerlLookSource.Look first = TerlLooks.shipped().looks().get(0);
        assertEquals(first.opacity, store.customValue(TerlProperty.OPACITY));
        assertEquals(first.blur, store.customValue(TerlProperty.BLUR));
    }

    @Test
    public void editingCustomWhileAnotherLookIsWornDoesNotChangeTheLook() {
        store.pick(TerlLooks.SOLID, true);
        store.setCustomValue(TerlProperty.OPACITY, 5);
        assertEquals(92, store.global(TerlProperty.OPACITY));
        assertEquals(TerlLooks.SOLID, store.look());
    }

    @Test
    public void aPickReportsOwnValuesAndClearsNothingUntilConfirmed() {
        store.setOwn(TerlSurface.CONTROL_BAR, TerlProperty.BLUR, 30);
        store.setOwn(TerlSurface.WINDOWS, TerlProperty.OPACITY, 55);
        String before = store.look();

        TerlLookStore.Pick asked = store.pick(TerlLooks.SOLID, false);
        assertFalse(asked.applied);
        assertEquals(2, asked.clears.size());
        assertEquals("look.control-bar.blur", asked.clears.get(0).id());
        assertEquals(30, asked.clears.get(0).value);
        assertEquals("look.windows.opacity", asked.clears.get(1).id());
        assertEquals(before, store.look());
        assertTrue(store.holdsOwn(TerlSurface.CONTROL_BAR, TerlProperty.BLUR));

        TerlLookStore.Pick done = store.pick(TerlLooks.SOLID, true);
        assertTrue(done.applied);
        assertEquals(2, done.clears.size());
        assertEquals(TerlLooks.SOLID, store.look());
        assertTrue(store.ownValues().isEmpty());
    }

    @Test
    public void everyLookPickReportsIncludingCustom() {
        store.setOwn(TerlSurface.KEYBOARD, TerlProperty.BLUR, 9);
        TerlLookStore.Pick asked = store.pick(TerlLookStore.CUSTOM, false);
        assertFalse(asked.applied);
        assertEquals(1, asked.clears.size());
    }

    @Test
    public void aPickWithNoOwnValuesAppliesWithoutAsking() {
        TerlLookStore.Pick pick = store.pick(TerlLooks.LIQUID, false);
        assertTrue(pick.applied);
        assertTrue(pick.clears.isEmpty());
        assertEquals(TerlLooks.LIQUID, store.look());
    }

    @Test
    public void anUnknownLookChangesNothing() {
        store.setOwn(TerlSurface.KEYBOARD, TerlProperty.BLUR, 9);
        TerlLookStore.Pick pick = store.pick("nope", true);
        assertFalse(pick.applied);
        assertTrue(pick.clears.isEmpty());
        assertTrue(store.holdsOwn(TerlSurface.KEYBOARD, TerlProperty.BLUR));
    }

    @Test
    public void lookIdsEndWithCustomAndTakeMoreSources() {
        TerlLookSource wallpaper = () -> Collections.singletonList(
            new TerlLookSource.Look("wall-1", 33, 14));
        TerlLookStore extended = new StoredTerlLookStore(new MemoryLookStore(),
            Arrays.asList(TerlLooks.shipped(), wallpaper));
        assertEquals("wall-1", extended.lookIds().get(extended.lookIds().size() - 2));
        assertEquals(TerlLookStore.CUSTOM, extended.lookIds().get(extended.lookIds().size() - 1));
        extended.pick("wall-1", true);
        assertEquals(33, extended.global(TerlProperty.OPACITY));
    }

    @Test
    public void aStoredLookThatIsGoneFallsBackToTheFirst() {
        backing.putString(StoredTerlLookStore.KEY_LOOK, "wall-1");
        assertEquals(store.lookIds().get(0), store.look());
    }

    @Test
    public void cornersAreGlobalsAloneAndClamped() {
        assertEquals(StoredTerlLookStore.DEFAULT_CORNER_DP, store.cornerRadiusDp());
        store.setCornerRadiusDp(18);
        assertEquals(18, store.cornerRadiusDp());
        store.setCornerRadiusDp(-5);
        assertEquals(0, store.cornerRadiusDp());
        store.pick(TerlLooks.SOLID, true);
        assertEquals(0, store.cornerRadiusDp());
    }

    @Test
    public void settingIdsRoundTrip() {
        assertEquals("look.keyboard.opacity", TerlLookIds.of(TerlSurface.KEYBOARD, TerlProperty.OPACITY));
        TerlLookStore.OwnValue parsed = TerlLookIds.parseOwn("look.windows.blur");
        assertNotNull(parsed);
        assertEquals(TerlSurface.WINDOWS, parsed.surface);
        assertEquals(TerlProperty.BLUR, parsed.property);
        assertNull(TerlLookIds.parseOwn("look.sheets.blur"));
        assertNull(TerlLookIds.parseOwn("look.corners"));
        assertEquals(TerlProperty.BLUR, TerlLookIds.parseCustom("look.custom.blur"));
    }

    @Test
    public void listenersHearTerlKeysOnly() {
        AtomicInteger heard = new AtomicInteger();
        TerlLookStore.Listener listener = heard::incrementAndGet;
        store.addListener(listener);
        backing.putInt("look.opacity", 3);
        assertEquals(0, heard.get());
        store.setOwn(TerlSurface.KEYBOARD, TerlProperty.BLUR, 4);
        store.pick(TerlLooks.SOLID, true);
        assertTrue(heard.get() >= 2);
        store.removeListener(listener);
        int after = heard.get();
        store.setCustomValue(TerlProperty.BLUR, 3);
        assertEquals(after, heard.get());
    }

    @Test
    public void v1LookKeysAreNeverTouched() {
        backing.putInt("look.opacity", 3);
        store.setOwn(TerlSurface.KEYBOARD, TerlProperty.BLUR, 4);
        store.pick(TerlLooks.SOLID, true);
        store.setCornerRadiusDp(10);
        for (String key : backing.keys()) {
            if (!key.equals("look.opacity")) assertTrue(key, key.startsWith("terl.look."));
        }
        assertEquals(3, backing.getInt("look.opacity", -1));
    }

    @Test
    public void shippedLooksMatchV1Presets() {
        int lookOpacity = 0;
        for (SurfacePresets.Preset preset : SurfacePresets.presets()) {
            TerlLookSource.Look look = TerlLooks.shipped().find(preset.id);
            if (look == null) continue;
            lookOpacity++;
            assertEquals(preset.id, preset.values.get(
                LookGrid.key(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE)), look.opacity);
            Object stop = preset.values.get(
                LookGrid.key(LookGrid.Property.FROST, null, LookGrid.NO_PAGE));
            assertEquals(preset.id, FrostStops.blurDpFor((Integer) stop), look.blur);
        }
        assertEquals(5, lookOpacity);
    }
}
