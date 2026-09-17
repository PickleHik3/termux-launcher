package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Build;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where a place mark's colour comes from. The extra-keys row decides it — a place switch takes its
 * key's role, and the user can change that role in the editor — so the bar follows the row rather
 * than reading a palette of its own, which is how the two came to be different colours for the
 * same three places. What is pinned here is that the row wins where it has spoken and the theme's
 * own accent still stands where it has not.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarLensAccentTest {

    private static final int ROW_TERMINAL = 0xFF53E0A0;
    private static final int ROW_WIDGETS = 0xFFE07CD8;

    private StatusBarLensView lens() {
        return new StatusBarLensView(new android.view.ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight), null);
    }

    @Test
    public void aLensToldNothingKeepsTheThemesOwnAccentForEveryPlace() {
        StatusBarLensView lens = lens();
        for (PaneWallPage page : PaneWallPage.values()) {
            assertEquals(page.toString(),
                StatusBarLensView.accentFor(lens.getContext(), page), lens.accent(page));
        }
    }

    @Test
    public void aPlaceTheRowStandsAKeyForIsDrawnInTheKeysOwnColour() {
        StatusBarLensView lens = lens();
        Map<PaneWallPage, Integer> row = new EnumMap<>(PaneWallPage.class);
        row.put(PaneWallPage.TERMINAL, ROW_TERMINAL);
        row.put(PaneWallPage.WIDGETS, ROW_WIDGETS);
        lens.setPlaceAccents(row);

        assertEquals(ROW_TERMINAL, lens.accent(PaneWallPage.TERMINAL));
        assertEquals(ROW_WIDGETS, lens.accent(PaneWallPage.WIDGETS));
        // The row carries no Display switch here, so that place keeps what it always wore.
        assertEquals(StatusBarLensView.accentFor(lens.getContext(), PaneWallPage.DISPLAY),
            lens.accent(PaneWallPage.DISPLAY));
    }

    @Test
    public void aRowWithNoPlaceKeysAtAllHandsThePlacesBack() {
        StatusBarLensView lens = lens();
        Map<PaneWallPage, Integer> row = new EnumMap<>(PaneWallPage.class);
        row.put(PaneWallPage.TERMINAL, ROW_TERMINAL);
        lens.setPlaceAccents(row);
        assertEquals(ROW_TERMINAL, lens.accent(PaneWallPage.TERMINAL));

        lens.setPlaceAccents(new EnumMap<>(PaneWallPage.class));
        assertEquals(StatusBarLensView.accentFor(lens.getContext(), PaneWallPage.TERMINAL),
            lens.accent(PaneWallPage.TERMINAL));
        lens.setPlaceAccents(row);
        lens.setPlaceAccents(null);
        assertEquals(StatusBarLensView.accentFor(lens.getContext(), PaneWallPage.TERMINAL),
            lens.accent(PaneWallPage.TERMINAL));
    }
}
