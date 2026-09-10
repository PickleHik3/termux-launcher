package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.termux.app.place.PlaceArrangeModel;
import com.termux.app.place.PlaceArrangeModel.Element;
import com.termux.app.place.PlaceArrangeModel.Group;
import com.termux.app.place.PlaceArrangeModel.Pills;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.List;

/**
 * The editor survives a rotation mid-edit, and the Place section then speaks for the orientation
 * the screen has turned into.
 *
 * <p>The first half of that is the manifest: the launcher handles {@code orientation} and
 * {@code screenSize} itself, so a turn of the screen is a layout pass rather than a recreate, and
 * the open card, the entry snapshot and the dirtiness comparison are simply never torn down. That
 * is what this holds — the editor keeps no saved state of its own, so the declaration is the
 * mechanism.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SurfaceEditorRotationTest {

    private Application app;
    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("surface-editor-rotation-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    @Test
    public void theLauncherHandlesRotationItselfSoTheEditorIsNotTornDown() throws Exception {
        PackageManager packages = app.getPackageManager();
        ActivityInfo info = packages.getActivityInfo(
            new ComponentName(app, "com.termux.app.TermuxActivity"), 0);

        assertTrue("the editor's session would not survive a recreate",
            (info.configChanges & ActivityInfo.CONFIG_ORIENTATION) != 0);
        assertTrue((info.configChanges & ActivityInfo.CONFIG_SCREEN_SIZE) != 0);
    }

    @Test
    public void thePlaceSectionSpeaksForWhicheverOrientationIsOnScreen() {
        // What the card offered before the turn, and what it offers after: the same row, a
        // different set, because they are different stored values.
        List<Group> portrait = PlaceArrangeModel.groups(places, PaneWallPage.TERMINAL,
            PlaceOrientation.PORTRAIT, Element.STATUS_BAR);
        List<Group> landscape = PlaceArrangeModel.groups(places, PaneWallPage.TERMINAL,
            PlaceOrientation.LANDSCAPE, Element.STATUS_BAR);

        assertEquals(Arrays.asList("top", "bottom"),
            Arrays.asList(((Pills) portrait.get(0)).values));
        assertEquals(Arrays.asList("top", "bottom", "left", "right"),
            Arrays.asList(((Pills) landscape.get(0)).values));

        // And a pick taken after the turn lands in the orientation the screen is now in.
        ((Pills) landscape.get(0)).writer.write("left");
        assertEquals("left", ((Pills) PlaceArrangeModel.groups(places, PaneWallPage.TERMINAL,
            PlaceOrientation.LANDSCAPE, Element.STATUS_BAR).get(0)).selected);
        assertNotEquals("left", ((Pills) PlaceArrangeModel.groups(places, PaneWallPage.TERMINAL,
            PlaceOrientation.PORTRAIT, Element.STATUS_BAR).get(0)).selected);
    }
}
