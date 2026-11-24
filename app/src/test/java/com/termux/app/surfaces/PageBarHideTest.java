package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.home.HomePageIndicator;
import com.termux.app.launcher.home.HomeSettings;
import com.termux.app.layouteditor.BarLandingPolicy;
import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.FrameState;
import com.termux.app.layouteditor.BarLandingPolicy.Hold;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.layouteditor.LayoutEditorPlan;
import com.termux.app.layouteditor.LayoutEditorPlan.TrayItem;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.statusbar.StatusPlace;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home's page bar can be put away from the frame and brought back (#344): a drop on the Hidden
 * column writes "off" into the store the editor's snapshot keeps, so Discard puts it back, and its
 * chip in the column writes "dots" again.
 */
public class PageBarHideTest {

    private static final class MapStore implements EditorSettingsSnapshot.Store {
        final Map<String, Object> values = new HashMap<>();

        @Nullable @Override public String getString(@NonNull String key) {
            return (String) values.get(key);
        }

        @Override public void putString(@NonNull String key, @Nullable String value) {
            values.put(key, value);
        }

        @Override public boolean getBoolean(@NonNull String key) {
            Object value = values.get(key);
            return value == null ? EditorSettingsSnapshot.defaultOf(key) : (Boolean) value;
        }

        @Override public void putBoolean(@NonNull String key, boolean value) {
            values.put(key, value);
        }
    }

    /** The editor's half, with the page bar's setting in a map as the editor writes it. */
    private static final class Host implements BarDragController.Host {
        final MapStore store;

        Host(MapStore store) {
            this.store = store;
        }

        @Override public boolean pageBarShown() {
            return HomePageIndicator.fromValue(
                store.getString(HomeSettings.KEY_PAGE_INDICATOR)).glyphsShown();
        }

        @Override public void setPageBarShown(boolean shown) {
            store.putString(HomeSettings.KEY_PAGE_INDICATOR,
                (shown ? HomePageIndicator.GLYPHS : HomePageIndicator.OFF).value);
        }

        @Nullable @Override public LayoutEditorController layout() { return null; }
        @NonNull @Override public StatusPlace statusPlace() { return StatusPlace.STRIP; }
        @Override public void onWritten() {}
        @NonNull @Override public Context context() { throw new UnsupportedOperationException(); }
        @Nullable @Override public ViewGroup overlay() { return null; }
        @Override public float scale() { return 1f; }
        @Override public boolean open() { return true; }
        @Override public boolean rectOf(@NonNull Bar bar, @NonNull float[] out) { return false; }
        @Override public int primaryColor() { return 0; }
        @Override public int surfaceColor() { return 0; }
        @Override public int onSurfaceColor() { return 0; }
    }

    private static Landing hideLanding() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        PlaceLayout layout = new PlaceLayout(slots, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
        return BarLandingPolicy.land(new FrameState(layout, PlaceOrientation.PORTRAIT,
            LayoutKind.HOME, StatusPlace.STRIP), Bar.PAGE_BAR, Hold.hidden());
    }

    @Test
    public void hidingWritesOffAndDiscardPutsItBack() {
        MapStore store = new MapStore();
        Host host = new Host(store);
        EditorSettingsSnapshot entry = EditorSettingsSnapshot.capture(store);
        BarDragController drag = new BarDragController(host);

        assertTrue(drag.commit(Bar.PAGE_BAR, hideLanding()));
        assertEquals("off", store.getString(HomeSettings.KEY_PAGE_INDICATOR));
        assertFalse(host.pageBarShown());

        List<String> written = entry.restore(store);

        assertTrue(written.contains(HomeSettings.KEY_PAGE_INDICATOR));
        assertTrue(host.pageBarShown());
    }

    @Test
    public void thePageBarChipStandsInTheColumnWhileHiddenAndBringsItBack() {
        MapStore store = new MapStore();
        Host host = new Host(store);
        new BarDragController(host).commit(Bar.PAGE_BAR, hideLanding());

        List<TrayItem> tray = LayoutEditorPlan.withPageBar(Collections.<TrayItem>emptyList(), true,
            host.pageBarShown());
        assertEquals(Collections.singletonList(TrayItem.PAGE_BAR), tray);

        host.setPageBarShown(true);

        assertEquals("dots", store.getString(HomeSettings.KEY_PAGE_INDICATOR));
        assertEquals(new ArrayList<TrayItem>(),
            LayoutEditorPlan.withPageBar(new ArrayList<TrayItem>(), true, host.pageBarShown()));
    }

    @Test
    public void onlyHomeListsThePageBarChip() {
        assertTrue(LayoutEditorPlan.withPageBar(Collections.<TrayItem>emptyList(), false, false)
            .isEmpty());
    }

    @Test
    public void aPutAwayPageBarIsNotHiddenTwice() {
        MapStore store = new MapStore();
        BarDragController drag = new BarDragController(new Host(store));
        assertTrue(drag.commit(Bar.PAGE_BAR, hideLanding()));
        assertFalse(drag.commit(Bar.PAGE_BAR, hideLanding()));
    }
}
