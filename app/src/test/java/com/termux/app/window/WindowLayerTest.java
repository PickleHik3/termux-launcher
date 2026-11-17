package com.termux.app.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.Set;

/** The window's layer table (ADR 0028 section 6) and where a runtime view joins it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WindowLayerTest {

    @Test
    public void theTableRunsBackToFrontFromGroundToNotices() {
        WindowLayer[] table = WindowLayer.values();
        assertEquals(6, table.length);
        assertEquals(WindowLayer.GROUND, table[0]);
        assertEquals(WindowLayer.PAGES, table[1]);
        assertEquals(WindowLayer.CHROME, table[2]);
        assertEquals(WindowLayer.SHEETS, table[3]);
        assertEquals(WindowLayer.EDITOR, table[4]);
        assertEquals(WindowLayer.NOTICES, table[5]);
    }

    @Test
    public void theLayerNumberIsTheOnlyOrderBetweenLayers() {
        for (int i = 0; i < WindowLayer.values().length; i++) {
            assertEquals(i, WindowLayer.values()[i].number());
        }
        assertTrue(WindowLayer.EDITOR.isAbove(WindowLayer.SHEETS));
        assertTrue(WindowLayer.NOTICES.isAbove(WindowLayer.EDITOR));
        assertFalse(WindowLayer.GROUND.isAbove(WindowLayer.PAGES));
        assertFalse(WindowLayer.SHEETS.isAbove(WindowLayer.SHEETS));
    }

    @Test
    public void everyLayerHasItsOwnHost() {
        Set<Integer> ids = new HashSet<>();
        for (WindowLayer layer : WindowLayer.values()) assertTrue(ids.add(layer.hostId()));
    }

    @Test
    public void aRuntimeViewJoinsItsLayersHost() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout root = windowRoot(context, true);
        View view = new View(context);
        assertTrue(WindowLayers.add(id -> root.findViewById(id), WindowLayer.EDITOR, view,
            new FrameLayout.LayoutParams(1, 1)));
        assertSame(root.findViewById(R.id.window_layer_editor), view.getParent());
    }

    @Test
    public void aMissingHostFallsBackToTheWindowRootSoTheViewIsStillShown() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout root = windowRoot(context, false);
        View view = new View(context);
        ViewGroup host = WindowLayers.host(id -> root.findViewById(id), WindowLayer.NOTICES);
        assertSame(root, host);
        assertTrue(WindowLayers.add(id -> root.findViewById(id), WindowLayer.NOTICES, view,
            new FrameLayout.LayoutParams(1, 1)));
        assertSame(root, view.getParent());
    }

    @Test
    public void withNoHostAtAllNothingIsAddedAndNothingThrows() {
        Context context = RuntimeEnvironment.getApplication();
        View view = new View(context);
        assertFalse(WindowLayers.add(id -> null, WindowLayer.SHEETS, view,
            new FrameLayout.LayoutParams(1, 1)));
        assertEquals(null, view.getParent());
    }

    @Test
    public void raisingWithinALayerMovesTheViewLastAmongItsSiblings() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout host = new FrameLayout(context);
        View a = new View(context);
        View b = new View(context);
        host.addView(a);
        host.addView(b);
        WindowLayers.raiseWithin(a);
        assertSame(a, host.getChildAt(1));
        WindowLayers.raiseWithin(a);
        assertSame(a, host.getChildAt(1));
        assertNotNull(b.getParent());
    }

    private static FrameLayout windowRoot(Context context, boolean withHosts) {
        FrameLayout root = new FrameLayout(context);
        root.setId(R.id.window_layers_root);
        if (withHosts) {
            for (WindowLayer layer : WindowLayer.values()) {
                FrameLayout host = new FrameLayout(context);
                host.setId(layer.hostId());
                root.addView(host);
            }
        }
        return root;
    }
}
