package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.chrome.PageMarkInk;
import com.termux.app.chrome.WallpaperInk;
import com.termux.app.launcher.paging.PageTickStripView;
import com.termux.app.launcher.widget.HomeLabelInk;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * What is painted over the wallpaper above Home's dock, in system light and night over a pale and
 * a dark wallpaper (the emulator's two gradients). With one dock page the strip paints nothing and
 * Home's grabber is the mark there; with two the strip's capsules are, and the grabber steps
 * aside. Each is checked at the colour it is painted in, alpha included, on that ground.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DockPageMarkPaintTest {

    private static final int PALE = 0xFFD9E1EB;
    private static final int DARK = 0xFF21141F;

    @NonNull
    private static Context themed(boolean night) {
        return new ContextThemeWrapper(RuntimeEnvironment.getApplication(), night
            ? com.google.android.material.R.style.Theme_Material3_Dark
            : com.google.android.material.R.style.Theme_Material3_Light);
    }

    private static double drawn(@ColorInt int color, @ColorInt int ground) {
        return OnGlass.ratio(GlassInk.effective(color, ground), OnGlass.opaque(ground));
    }

    @Test
    public void onePageThePaintedGrabberReadsOnTheWallpaper() {
        for (boolean night : new boolean[] {false, true}) {
            Context context = themed(night);
            for (int ground : new int[] {PALE, DARK}) {
                String where = (night ? "night" : "light") + (ground == PALE ? " pale" : " dark");
                PageTickStripView strip = new PageTickStripView(context);
                strip.setPages(1, 0f);
                assertEquals(where + ": one page, no capsule", 0, strip.airMarkThicknessPx());
                BorderGrabber grabber = new BorderGrabber(new View(context));
                grabber.setHomeGround(ground);
                double rest = drawn(grabber.homePaintColor(0f, 1f), ground);
                double pressed = drawn(grabber.homePaintColor(1f, 1f), ground);
                assertTrue(where + " grabber at rest " + rest, rest >= OnGlass.TARGET_LARGE_TEXT);
                assertTrue(where + " pressed " + pressed + " over rest " + rest,
                    pressed >= rest);
            }
        }
    }

    @Test
    public void twoPagesThePaintedCapsulesReadOnTheWallpaper() {
        for (boolean night : new boolean[] {false, true}) {
            Context context = themed(night);
            int accent = BorderGrabber.accent(context);
            for (int ground : new int[] {PALE, DARK}) {
                String where = (night ? "night" : "light") + (ground == PALE ? " pale" : " dark");
                PageTickStripView strip = new PageTickStripView(context);
                strip.setGlassInk(ground, WallpaperInk.bareInk(ground, HomeLabelInk.DARK_INK,
                    HomeLabelInk.PALE_INK));
                strip.setAccentColor(accent);
                strip.setPages(2, 0f);
                double active = drawn(strip.tickColorAt(0), ground);
                double resting = drawn(strip.tickColorAt(1), ground);
                assertTrue(where + " active " + active, active >= PageMarkInk.CURRENT);
                assertTrue(where + " resting " + resting, resting >= PageMarkInk.OTHER_FLOOR);
                assertTrue(where + " active over resting", active > resting);
            }
        }
    }
}
