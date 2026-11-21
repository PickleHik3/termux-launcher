package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Rect;
import android.os.Build;
import android.view.ContextThemeWrapper;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.utilities.Hct;
import com.termux.app.launcher.paging.PageTickStripView;
import com.termux.app.launcher.widget.HomeLabelInk;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetGlass;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetStyle;
import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.app.wall.pageswitch.PageBarGlass;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The page bar's pill is a surface of the legibility contract (ADR 0014): it wears a widget's
 * body and ink. For the same rect, glass and accent, the pill's answer equals a built-in widget's
 * (its card on the widget's side, its weakest text ink, the lift, the veil and the even-out the
 * slab is drawn through), by day and at night, on either page ink, with Adaptive legibility off
 * and on; and every page mark reads on the ground it is drawn on, the current page strongest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PageBarLegibilityParityTest {

    private static final Rect WHOLE = new Rect(0, 0, 1080, 2400);
    private static final Rect PILL = new Rect(300, 2100, 780, 2190);
    private static final int DARK = 0xFF202428;
    private static final int LIGHT = 0xFFE4E6EA;
    private static final int MID = 0xFF808890;
    /** A Clear/Mist tint: pale and thin. */
    private static final int PALE_TINT = 0x40F4F6FA;
    private static final int ACCENT = 0xFF88AAFF;
    /** The Look's Opacity: high enough that the widget even-out is on. */
    private static final int OPACITY_PERCENT = 30;
    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;

    /** A Look's widget band over one {@link ChromeInk}, on a fixed page ink. */
    private static final class Source implements WidgetInkSource {
        final ChromeInk ink;
        final ChromeInk.Polarity polarity;

        Source(@NonNull ChromeInk ink, @NonNull ChromeInk.Polarity polarity) {
            this.ink = ink;
            this.polarity = polarity;
        }

        @NonNull @Override public ChromeInk.Polarity widgetPolarity() { return polarity; }
        @Override public boolean widgetFrameReady() { return true; }
        @NonNull @Override
        public OnGlass.Resolution widgetInk(@NonNull Rect rootRect, int body, int veilColor,
                                            int textInk, int graphicInk) {
            return ink.widget(rootRect, body, veilColor, textInk, graphicInk, OPACITY_PERCENT);
        }
        @Nullable @Override public ColorMatrix widgetEvenOut(@NonNull Rect rootRect) {
            return ink.widgetEvenOutMatrix(rootRect);
        }
        @Override public int widgetWallpaper(@NonNull Rect rootRect) {
            return ink.widgetWallpaper(rootRect);
        }
    }

    /** A fresh ink engine over a wallpaper that is {@code split} (dark left, pale right) or all {@code flat}. */
    @NonNull
    private static ChromeInk newInk(boolean adaptive, @ColorInt int flat, boolean split,
                                    @NonNull ChromeInk.Polarity polarity) {
        Application app = RuntimeEnvironment.getApplication();
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(app);
        surfaces.adaptive = adaptive;
        ChromeInk ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.setWholePictureForTest(WHOLE);
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            private int colorFor(@NonNull Rect rect) {
                if (!split) return flat;
                if (rect.equals(WHOLE)) return MID;
                return rect.centerX() < WHOLE.centerX() ? DARK : LIGHT;
            }

            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                return colorFor(screenRect);
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                int color = colorFor(screenRect);
                double tone = color == DARK ? 12d : color == LIGHT ? 90d : 50d;
                return new GlassBackdropCache.Lightness(tone - 5d, tone, tone + 5d,
                    Hct.from(250d, 6d, tone).toInt());
            }
        });
        EvenOut.Ceiling[] ceilings = MaterialTerminalColorScheme.evenOutCeilings(app, LEVEL);
        ink.setEvenOutPolicy(new ChromeInk.EvenOutPolicy(LEVEL, ceilings[0], ceilings[1],
            polarity));
        return ink;
    }

    @NonNull
    private static Context themed(boolean night) {
        return new ContextThemeWrapper(RuntimeEnvironment.getApplication(), night
            ? com.google.android.material.R.style.Theme_Material3_Dark
            : com.google.android.material.R.style.Theme_Material3_Light);
    }

    /** What a built-in widget at {@code rect} solves, the way {@code BuiltinWidgetView} does. */
    private static void widget(@NonNull Context context, @NonNull WidgetInkSource source,
                               boolean glass, @NonNull WidgetBody.Answer out) {
        BuiltinWidgetStyle host = BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        BuiltinWidgetStyle onInk = host.onInk(
            BuiltinWidgetGlass.darkCard(false, source.widgetPolarity()));
        BuiltinWidgetStyle style = glass ? onInk.glassVariant(context) : onInk;
        WidgetBody.solve(source, PILL, glass, style.card, PALE_TINT, style.onSurfaceVariant,
            style.primary, out);
    }

    private static void assertSameBody(@NonNull String where, @NonNull WidgetBody.Answer widget,
                                       @NonNull WidgetBody.Answer pill) {
        assertNotNull(where, widget.resolution);
        assertNotNull(where, pill.resolution);
        assertEquals(where + " lift", widget.lift, pill.lift);
        assertEquals(where + " body", widget.body, pill.body);
        assertEquals(where + " fill", widget.fill, pill.fill);
        assertEquals(where + " ink", widget.resolution.ink, pill.resolution.ink);
        assertEquals(where + " veil", widget.resolution.veil, pill.resolution.veil);
        assertEquals(where + " surface", widget.resolution.surface, pill.resolution.surface);
        if (widget.evenOut == null) {
            assertNull(where + " even-out", pill.evenOut);
        } else {
            assertNotNull(where + " even-out", pill.evenOut);
            assertArrayEquals(where + " even-out", widget.evenOut.getArray(),
                pill.evenOut.getArray(), 0f);
        }
    }

    @Test
    public void thePillWearsAWidgetsBodyAndInk() {
        for (boolean night : new boolean[] {false, true}) {
            Context context = themed(night);
            for (ChromeInk.Polarity polarity : ChromeInk.Polarity.values()) {
                for (boolean adaptive : new boolean[] {false, true}) {
                    for (boolean glass : new boolean[] {true, false}) {
                        String where = (night ? "night" : "day") + " " + polarity
                            + (adaptive ? " adaptive" : " shared") + (glass ? " glass" : " solid");
                        // Each on its own engine, so no memo hands one the other's answer.
                        WidgetBody.Answer widget = new WidgetBody.Answer();
                        widget(context, new Source(newInk(adaptive, MID, true, polarity),
                            polarity), glass, widget);
                        WidgetBody.Answer pill = new WidgetBody.Answer();
                        PageBarGlass.solve(context, new Source(newInk(adaptive, MID, true,
                            polarity), polarity), PILL, glass, PALE_TINT, ACCENT, pill,
                            new PageBarGlass.Ink());
                        assertSameBody(where, widget, pill);
                    }
                }
            }
        }
    }

    /** The drawn contrast of {@code color} (alpha included) on the opaque {@code ground}. */
    private static double drawn(@ColorInt int color, @ColorInt int ground) {
        return OnGlass.ratio(GlassInk.effective(color, ground), OnGlass.opaque(ground));
    }

    /**
     * System light and night, each over a pale and a dark wallpaper (the page's ink following the
     * wallpaper): every page mark reads on the ground it is drawn on, and the current page is the
     * strongest. The pill's glyphs on its body (the bubble's disc wears that same body and draws
     * the same current glyph), the bubble's label on the bare wallpaper, and the dock's capsules
     * on the wallpaper they stand over; with Adaptive legibility off and on.
     */
    @Test
    public void everyPageMarkReadsOnItsGroundInTheFourStates() {
        for (boolean night : new boolean[] {false, true}) {
            Context context = themed(night);
            int accent = com.termux.app.material.M3.primary(context);
            for (int wallpaper : new int[] {LIGHT, DARK}) {
                ChromeInk.Polarity polarity = wallpaper == LIGHT ? ChromeInk.Polarity.DARK_INK
                    : ChromeInk.Polarity.PALE_INK;
                for (boolean adaptive : new boolean[] {false, true}) {
                    String where = (night ? "night" : "light") + " over "
                        + (wallpaper == LIGHT ? "pale" : "dark")
                        + (adaptive ? " adaptive" : " shared");
                    ChromeInk ink = newInk(adaptive, wallpaper, false, polarity);
                    PageBarGlass.Ink pill = new PageBarGlass.Ink();
                    PageBarGlass.solve(context, new Source(ink, polarity), PILL, true,
                        PALE_TINT, accent, new WidgetBody.Answer(), pill);
                    double current = drawn(pill.accent, pill.ground);
                    double other = drawn(pill.other, pill.ground);
                    assertTrue(where + " current glyph " + current,
                        current >= PageMarkInk.CURRENT);
                    assertTrue(where + " other glyph " + other, other >= PageMarkInk.OTHER_FLOOR);
                    assertTrue(where + " current " + current + " over other " + other,
                        current > other);

                    int label = PageBarGlass.labelInk(wallpaper, pill.ink);
                    assertTrue(where + " label", drawn(label, wallpaper)
                        >= OnGlass.TARGET_BODY_TEXT);

                    PageTickStripView strip = new PageTickStripView(context);
                    strip.setGlassInk(wallpaper, WallpaperInk.bareInk(wallpaper,
                        HomeLabelInk.DARK_INK, HomeLabelInk.PALE_INK));
                    strip.setAccentColor(accent);
                    strip.setPages(2, 0f);
                    double active = drawn(strip.tickColorAt(0), wallpaper);
                    double resting = drawn(strip.tickColorAt(1), wallpaper);
                    assertTrue(where + " capsule active " + active, active >= PageMarkInk.CURRENT);
                    assertTrue(where + " capsule resting " + resting,
                        resting >= PageMarkInk.OTHER_FLOOR);
                    assertTrue(where + " capsule active " + active + " over resting " + resting,
                        active > resting);
                }
            }
        }
    }
}
