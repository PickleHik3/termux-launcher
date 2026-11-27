package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;
import com.termux.shared.termux.settings.registry.SettingsFile;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.util.Collections;

/** Terl's look as registry ids: set, follow and export, over the look store. */
public class TerlLookSettingsAdapterTest {

    private final TerlLookStore store = new StoredTerlLookStore(new MemoryLookStore());
    private final SettingsRegistry registry = new SettingsRegistry(which -> {
        throw new AssertionError("a look id reads no registry store");
    }, Collections.emptyList(), Collections.singletonList(new TerlLookSettingsAdapter(store)));

    @Test
    public void aBarsOwnOpacityIsSavedInTheStoreAndReadBack() {
        assertNotNull(registry.set("look.control-bar.opacity", "35").change());
        assertEquals(35, store.value(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY));
        assertTrue(store.holdsOwn(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY));
        assertEquals("35", registry.get("look.control-bar.opacity"));
    }

    @Test
    public void anOwnValueCanFollowTheLookAgain() {
        registry.set("look.keyboard.blur", "12");
        assertTrue(registry.canFollow("look.keyboard.blur"));
        assertNotNull(registry.set("look.keyboard.blur", "follow").change());
        assertFalse(store.holdsOwn(TerlSurface.KEYBOARD, TerlProperty.BLUR));
        assertFalse(registry.canFollow("look.corners"));
    }

    @Test
    public void anExportCarriesOwnValuesAndFollowIsLeftOutOrWritten() {
        registry.set("look.windows.opacity", "20");
        String file = SettingsFile.export(registry);
        assertTrue(file, file.contains("look.windows.opacity = 20"));
        assertTrue(file, file.contains("look.keyboard.opacity = follow"));
    }

    @Test
    public void theLookCornersAndTintAreWrittenThroughTheStore() {
        assertNotNull(registry.set("look.corners", "12").change());
        assertEquals(12, store.cornerRadiusDp());
        assertNotNull(registry.set("look.tint", "secondary").change());
        assertEquals("secondary", store.tint());
        String look = store.lookIds().get(2);
        assertNotNull(registry.set("look", look).change());
        assertEquals(look, store.look());
        registry.set("look.custom.opacity", "44");
        assertEquals(44, store.customValue(TerlProperty.OPACITY));
    }

    @Test
    public void aWrittenLookClearsOwnValuesAsAPickDoes() {
        registry.set("look.keyboard.opacity", "30");
        registry.set("look", store.lookIds().get(1));
        assertFalse(store.holdsOwn(TerlSurface.KEYBOARD, TerlProperty.OPACITY));
    }

    @Test
    public void aValueOutsideTheRangeOrTheChoicesIsRefusedAndSavesNothing() {
        assertEquals("look.control-bar.blur is 0–48",
            registry.set("look.control-bar.blur", "99").refusal().reason);
        assertNotNull(registry.set("look.tint", "pink").refusal());
        assertNotNull(registry.set("look", "nope").refusal());
        assertFalse(store.holdsOwn(TerlSurface.CONTROL_BAR, TerlProperty.BLUR));
    }

    @Test
    public void surfacesThatAlwaysWearTheLookHoldNoOwnId() {
        assertNull(new TerlLookSettingsAdapter(store).declaration("look.sheets.blur"));
        assertNull(new TerlLookSettingsAdapter(store).declaration("look.dock.opacity"));
        assertTrue(TerlLookSettingsAdapter.claims("look.keyboard.opacity"));
    }
}
