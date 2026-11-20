package com.termux.app.terminal.inappkeyboard;

import android.app.Application;
import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.LayoutModifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The one resolution the live keyboard and the Keyboard theme preview share. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class InAppKeyboardLayoutSourceTest {

    private static final String SPANISH = "latn_qwerty_es";

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Resources resources;
    private File home;
    private LayoutModifier.LayoutOptions options;

    @Before
    public void setUp() {
        resources = ApplicationProvider.getApplicationContext().getResources();
        home = folder.getRoot();
        options = InAppKeyboardLayoutSource.textLayoutOptions("");
    }

    private static boolean hasChar(KeyboardData data, char wanted) {
        for (KeyboardData.Row row : data.rows) {
            for (KeyboardData.Key key : row.keys) {
                for (KeyValue value : key.keys) {
                    if (value != null && value.getKind() == KeyValue.Kind.Char
                        && value.getChar() == wanted) return true;
                }
            }
        }
        return false;
    }

    private static int[] keysPerRow(KeyboardData data) {
        int[] counts = new int[data.rows.size()];
        for (int i = 0; i < counts.length; i++) counts[i] = data.rows.get(i).keys.size();
        return counts;
    }

    @Test
    public void activeSpanishLayoutCarriesTheEnyeKey() {
        List<String> ring = Arrays.asList("main", SPANISH);
        String active = InAppKeyboardLayoutSource.activeId(ring, SPANISH);
        assertEquals(SPANISH, active);
        KeyboardData data = InAppKeyboardLayoutSource.load(resources, home, active, options);
        assertNotNull(data);
        assertTrue("home row has no n-tilde key", hasChar(data, 'ñ'));
    }

    @Test
    public void storedIdOutsideTheRingFallsBackToTheFirstEntry() {
        List<String> ring = Arrays.asList("main", "latn_dvorak");
        assertEquals("main", InAppKeyboardLayoutSource.activeId(ring, SPANISH));
        assertEquals("main", InAppKeyboardLayoutSource.activeId(ring, null));
    }

    @Test
    public void previewAndLiveLoadAgreeOnKeysPerRow() {
        for (String id : new String[] {"main", "latn_qwerty_us", SPANISH, "cyrl_yxukeng_os"}) {
            // The live keyboard's path: the catalogue resource through the same options.
            LauncherKeyboardLayouts.Layout layout = LauncherKeyboardLayouts.find(resources, id);
            assertNotNull(id, layout);
            int resId = layout.xmlResId != 0 ? layout.xmlResId
                : juloo.keyboard2.R.xml.termux_launcher_qwerty;
            KeyboardData live = LayoutModifier.modify(
                KeyboardData.load(resources, resId), options, resources);
            KeyboardData preview = InAppKeyboardLayoutSource.load(resources, home, id, options);
            assertNotNull(id, preview);
            assertTrue(id + " rows", Arrays.equals(keysPerRow(live), keysPerRow(preview)));
        }
    }
}
