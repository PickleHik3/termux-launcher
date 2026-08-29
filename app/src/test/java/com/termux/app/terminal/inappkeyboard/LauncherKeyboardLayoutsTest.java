package com.termux.app.terminal.inappkeyboard;

import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The layout catalogue and the ring arithmetic behind hot-swapping. The catalogue is generated
 * at build time from the layout resources, so this also guards the generator: a keyboard module
 * whose {@code layouts.xml} went stale shows up here rather than as an empty picker.
 */
@RunWith(RobolectricTestRunner.class)
public class LauncherKeyboardLayoutsTest {

    private Resources resources;

    @Before
    public void setUp() {
        resources = ApplicationProvider.getApplicationContext().getResources();
    }

    @Test
    public void catalogLeadsWithTheLaunchersOwnLayout() {
        List<LauncherKeyboardLayouts.Layout> catalog = LauncherKeyboardLayouts.catalog(resources);
        assertTrue("catalogue is far shorter than the bundled layouts", catalog.size() > 50);
        assertEquals(LauncherKeyboardLayouts.LAYOUT_MAIN, catalog.get(0).id);
        assertEquals(0, catalog.get(0).xmlResId);
        for (int i = 1; i < catalog.size(); i++) {
            assertTrue(catalog.get(i).id + " has no layout resource", catalog.get(i).xmlResId != 0);
            assertFalse(catalog.get(i).label.isEmpty());
        }
    }

    @Test
    public void catalogCarriesTheProgrammingLayouts() {
        assertNotNull(LauncherKeyboardLayouts.find(resources, "latn_qwerty_us"));
        assertNotNull(LauncherKeyboardLayouts.find(resources, "latn_dvorak"));
        assertNotNull(LauncherKeyboardLayouts.find(resources, "latn_colemak"));
        assertNull(LauncherKeyboardLayouts.find(resources, "numeric"));   // a pad, not a layout
        assertNull(LauncherKeyboardLayouts.find(resources, "bottom_row"));
        assertEquals("Dvorak", LauncherKeyboardLayouts.labelFor(resources, "latn_dvorak"));
    }

    @Test
    public void selectionKeepsOrderAndDropsWhatIsGone() {
        assertEquals(Arrays.asList("main", "latn_dvorak"),
            LauncherKeyboardLayouts.parseSelection(resources, "main, latn_dvorak"));
        assertEquals(Arrays.asList("latn_dvorak", "main"),
            LauncherKeyboardLayouts.parseSelection(resources, "latn_dvorak,main"));
        assertEquals(Collections.singletonList("main"),
            LauncherKeyboardLayouts.parseSelection(resources, "main,main"));
        assertEquals(Collections.singletonList("main"),
            LauncherKeyboardLayouts.parseSelection(resources, "latn_atlantean,numeric"));
        assertEquals(Collections.singletonList("main"),
            LauncherKeyboardLayouts.parseSelection(resources, ""));
        assertEquals(Collections.singletonList("main"),
            LauncherKeyboardLayouts.parseSelection(resources, null));
    }

    @Test
    public void selectionIsCapped() {
        List<String> ids = new ArrayList<>();
        for (LauncherKeyboardLayouts.Layout layout : LauncherKeyboardLayouts.catalog(resources)) {
            ids.add(layout.id);
        }
        String stored = LauncherKeyboardLayouts.joinSelection(ids);
        assertEquals(LauncherKeyboardLayouts.MAX_SELECTION,
            LauncherKeyboardLayouts.parseSelection(resources, stored).size());
    }

    @Test
    public void cyclingWrapsBothWays() {
        List<String> ring = Arrays.asList("main", "latn_dvorak", "arab_pc");
        assertEquals("latn_dvorak", LauncherKeyboardLayouts.cycle(ring, "main", 1));
        assertEquals("main", LauncherKeyboardLayouts.cycle(ring, "arab_pc", 1));
        assertEquals("arab_pc", LauncherKeyboardLayouts.cycle(ring, "main", -1));
        assertEquals("main", LauncherKeyboardLayouts.cycle(ring, "latn_dvorak", -1));
    }

    /** Removing the layout in use must not strand the keyboard outside its own ring. */
    @Test
    public void cyclingFromOutsideTheRingRestartsIt() {
        List<String> ring = Arrays.asList("main", "latn_dvorak");
        assertEquals("main", LauncherKeyboardLayouts.cycle(ring, "arab_pc", 1));
        assertEquals("main", LauncherKeyboardLayouts.cycle(ring, null, 1));
        assertEquals(LauncherKeyboardLayouts.LAYOUT_MAIN,
            LauncherKeyboardLayouts.cycle(Collections.<String>emptyList(), "main", 1));
    }

    @Test
    public void joinIsTheInverseOfParse() {
        List<String> ring = Arrays.asList("latn_dvorak", "main");
        assertEquals(ring, LauncherKeyboardLayouts.parseSelection(resources,
            LauncherKeyboardLayouts.joinSelection(ring)));
        assertEquals(LauncherKeyboardLayouts.LAYOUT_MAIN,
            LauncherKeyboardLayouts.joinSelection(Collections.<String>emptyList()));
    }
}
