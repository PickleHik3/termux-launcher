package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.LayoutStore;
import com.termux.app.place.layout.LayoutsJson;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A layout document written outside the store ({@code launcherctl layout set}) shows in what a
 * running store reads, with no restart: its cached reads are retired (ADR 0020 §10).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PlaceLayoutLiveDocumentsTest {

    private Application app;
    private PlaceLayoutStore store;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = app.getSharedPreferences("place-live-documents", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        LayoutStore.prefs(app).edit().clear().commit();
        LayoutStore.shared(app).load();
        store = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    @Test
    public void aWriteThroughTheLayoutStoreIsReadAtOnce() {
        PlaceLayoutStore home = store.forVariant(LayoutVariant.HOME);
        DockStyle before = home.dockStyle();
        DockStyle other = before == DockStyle.LENS_BEADS ? DockStyle.GLASS_TRAY : DockStyle.LENS_BEADS;

        LayoutStore.shared(app).update(LayoutKind.HOME,
            layout -> layout.withWide(LayoutsJson.DOCK_DESIGN, other.storageValue()));

        assertEquals(other, home.dockStyle());
        assertEquals("the Terminal's is not touched", before,
            store.forVariant(LayoutVariant.NORMAL).dockStyle());
    }

    @Test
    public void aSizeWrittenOutsideIsReadAtOnce() {
        float before = store.wallStripHeightDp(PlaceOrientation.PORTRAIT);
        int want = Math.round(before) == 60 ? 64 : 60;
        LayoutStore.shared(app).updateSizes(sizes -> sizes.with(PlaceOrientation.PORTRAIT,
            LayoutsJson.WALL_STRIP_HEIGHT, want));
        assertEquals(want, store.forVariant(LayoutVariant.HOME).wallStripHeightDp(PlaceOrientation.PORTRAIT), 0f);
    }
}
