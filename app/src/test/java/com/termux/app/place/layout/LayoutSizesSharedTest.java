package com.termux.app.place.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.os.Build;

import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.WallPages.PageKind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.HashMap;
import java.util.Map;

/**
 * The sizes are one document shared by every kind (ADR 0020 §3), so a page change never resizes
 * the keyboard, the dock or the strip. Replaces PlaceLayoutLinkedKeysTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutSizesSharedTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private final Map<String, String> file = new HashMap<>();
    private int notices;

    private LayoutStore store() {
        LayoutStore store = new LayoutStore(new LayoutStore.Storage() {
            @Override public String read(String key) { return file.get(key); }
            @Override public void write(Map<String, String> documents) { file.putAll(documents); }
        }, false);
        store.addListener(layouts -> notices++);
        return store;
    }

    @Test
    public void everyKindReadsTheSameSizes() {
        LayoutStore store = store();
        store.updateSizes(s -> s.with(PORTRAIT, LayoutsJson.KEYBOARD_HEIGHT, 0.8f));
        Layouts layouts = store.current();
        for (PageKind kind : PageKind.values()) {
            for (boolean minimal : new boolean[]{false, true}) {
                assertSame(layouts.sizes(), layouts.sizes());
                layouts.forPage(kind, minimal);
            }
        }
        assertEquals(0.8f, layouts.sizes().orientation(PORTRAIT)
            .decimal(LayoutsJson.KEYBOARD_HEIGHT, 0f), 1e-6f);
    }

    @Test
    public void aKindChangeChangesNoSize() {
        LayoutStore store = store();
        store.updateSizes(s -> s.with(PORTRAIT, LayoutsJson.DOCK_HEIGHT, 1.3f));
        Sizes before = store.current().sizes();
        String sizesFile = file.get(LayoutsJson.KEY_SIZES);
        store.update(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, "status_bar", "bottom"));
        store.update(LayoutKind.HOME, k -> k.withWide(LayoutsJson.DOCK_DESIGN, "lens_beads"));
        assertSame(before, store.current().sizes());
        assertEquals(sizesFile, file.get(LayoutsJson.KEY_SIZES));
        assertEquals(3, notices);
    }

    @Test
    public void anUpdateWritesOneDocumentWholeAndLeavesTheOthers() {
        LayoutStore store = store();
        store.update(LayoutKind.DISPLAY, k -> k.with(PORTRAIT, "status_bar", "top"));
        assertEquals(1, file.size());
        assertEquals("top", store.current().kind(LayoutKind.DISPLAY).portrait()
            .string("status_bar"));
        assertEquals(store.current().kind(LayoutKind.TERMINAL),
            LayoutsJson.shippedDefault(LayoutKind.TERMINAL));
        store.update(LayoutKind.DISPLAY, k -> k);
        assertEquals(1, notices);
    }

    @Test
    public void aReloadReadsWhatWasWritten() {
        LayoutStore store = store();
        store.update(LayoutKind.HOME, k -> k.with(PORTRAIT, LayoutsJson.WIDGET_ROWS, 7));
        assertEquals(store.current(), store().current());
    }
}
