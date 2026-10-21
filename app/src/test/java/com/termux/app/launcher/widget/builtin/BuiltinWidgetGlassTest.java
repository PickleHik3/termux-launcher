package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.graphics.Color;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.chrome.WidgetInkSource;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Glass switch of a built-in widget: off is the solid card, on is the Look's glass — the slab
 * a terminal pane wears, fed by the page from the Look (Base's values) — and back off returns to
 * the solid card. Every widget's corner is the Look's.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BuiltinWidgetGlassTest {

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    private static final class Plain extends BuiltinWidgetView {
        Plain(@NonNull Context context, @NonNull BuiltinWidgetServices services,
              @NonNull BuiltinWidgetStyle style) {
            super(context, BuiltinWidgetKind.DEVICE, services, style);
        }

        int builds;
        @Nullable GradientDrawable inner;

        @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                         @NonNull BuiltinWidgetUi ui) {
            builds++;
            TextView follower = ui.followCorner(new TextView(getContext()));
            inner = ui.innerRounded(0xFF000000, 10);
            follower.setBackground(inner);
            frame.addView(follower);
        }

        int groundAlpha() { return cardAlpha(); }
    }

    /** A page that hands out the Look and keeps who is live on it. */
    private static final class Page extends FrameLayout implements BuiltinWidgetView.GlassSource {
        final Set<BuiltinWidgetView> live = new LinkedHashSet<>();
        @Nullable PaneSurfaceStyle glass;
        boolean onGlass;

        Page(@NonNull Context context) { super(context); }

        @Nullable @Override public PaneSurfaceStyle widgetGlassStyle() { return glass; }
        @Override public boolean widgetsOnGlass() { return onGlass; }
        @Override public void addGlassWidget(@NonNull BuiltinWidgetView widget) { live.add(widget); }
        @Override public void removeGlassWidget(@NonNull BuiltinWidgetView widget) {
            live.remove(widget);
        }
    }

    /** The Look's glass, at the shipped Base Corners unless told otherwise. */
    private static PaneSurfaceStyle glass(boolean active, @Nullable Bitmap frame) {
        return glass(active, frame, TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS);
    }

    private static PaneSurfaceStyle glass(boolean active, @Nullable Bitmap frame, int cornersDp) {
        return new PaneSurfaceStyle() {
            @Override public boolean isPaneGlassActive() { return active; }
            @Override public Bitmap paneGlassBlurFrame() { return frame; }
            @NonNull @Override public Rect paneGlassBlurFrameRect() { return new Rect(0, 0, 100, 200); }
            @Override public android.graphics.ColorFilter paneGlassFrostFilter() { return null; }
            @Override public int paneGlassTintColor() { return 0x80000000; }
            @Override public android.graphics.drawable.Drawable paneGlassGrainLayer() { return null; }
            @Override public int paneGlassGrainStrength() { return 0; }
            @Override public float paneGlassCornerRadiusPx() { return 0f; }
            @Override public int paneCornerRadiusDp() { return cornersDp; }
            @Override public int paneGapDp() { return 0; }
        };
    }

    private Context context;
    private BuiltinWidgetServices services;
    private BuiltinWidgetStyle style;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        services = new BuiltinWidgetServices(context, HOST);
        style = BuiltinWidgetStyle.resolve(context, BuiltinWidgetStyle.Numerals.SANS, false, null);
    }

    private static Bundle glassOn() {
        Bundle config = new Bundle();
        config.putString(BuiltinWidgetGlass.CONFIG_KEY, BuiltinWidgetGlass.ON);
        return config;
    }

    @Nullable private static PaneGlassBackdropView slabOf(@NonNull BuiltinWidgetView view) {
        for (int i = 0; i < view.getChildCount(); i++) {
            if (view.getChildAt(i) instanceof PaneGlassBackdropView)
                return (PaneGlassBackdropView) view.getChildAt(i);
        }
        return null;
    }

    @NonNull private static View cardOf(@NonNull BuiltinWidgetView view) {
        return view.findViewById(android.R.id.background);
    }

    @Test
    public void aWidgetsChoiceOverridesThePageAndAbsentFollowsIt() {
        for (boolean page : new boolean[] {false, true}) {
            assertEquals("absent follows the page", page, BuiltinWidgetGlass.resolve(null, page));
            assertEquals("empty follows the page", page, BuiltinWidgetGlass.resolve("", page));
            assertEquals("unknown follows the page", page,
                BuiltinWidgetGlass.resolve("true", page));
            assertTrue("on is an explicit on", BuiltinWidgetGlass.resolve(BuiltinWidgetGlass.ON, page));
            assertFalse("off is an explicit off",
                BuiltinWidgetGlass.resolve(BuiltinWidgetGlass.OFF, page));
        }
        Map<String, String> values = new HashMap<>();
        BuiltinWidgetGlass.write(values, BuiltinWidgetGlass.ON);
        assertEquals(BuiltinWidgetGlass.ON, values.get(BuiltinWidgetGlass.CONFIG_KEY));
        BuiltinWidgetGlass.write(values, BuiltinWidgetGlass.OFF);
        assertEquals(BuiltinWidgetGlass.OFF, values.get(BuiltinWidgetGlass.CONFIG_KEY));
        BuiltinWidgetGlass.write(values, "");
        assertFalse("follow the page is stored as nothing",
            BuiltinWidgetHost.configOf(values).containsKey(BuiltinWidgetGlass.CONFIG_KEY));
    }

    @Test
    public void thePagesSwitchDressesWidgetsThatFollowItAndLeavesOverriddenOnesBe() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Page page = new Page(activity);
        InkLook look = new InkLook(glass(true, Bitmap.createBitmap(10, 20,
            Bitmap.Config.ARGB_8888)));
        page.glass = look;
        Plain follows = new Plain(activity, services, dayStyle());
        Plain forcedOn = new Plain(activity, services, dayStyle());
        Plain forcedOff = new Plain(activity, services, dayStyle());
        follows.bind(null);
        forcedOn.bind(glassOn());
        Bundle off = new Bundle();
        off.putString(BuiltinWidgetGlass.CONFIG_KEY, BuiltinWidgetGlass.OFF);
        forcedOff.bind(off);
        for (Plain view : new Plain[] {follows, forcedOn, forcedOff})
            page.addView(view, new FrameLayout.LayoutParams(200, 100));
        activity.setContentView(page);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(follows.isGlassOn());
        assertTrue(forcedOn.isGlassOn());
        assertFalse(forcedOff.isGlassOn());

        page.onGlass = true;
        for (Plain view : new Plain[] {follows, forcedOn, forcedOff}) view.onGlassStyle(look);
        assertTrue("follows the page now", follows.isGlassOn());
        assertTrue(follows.style().glass);
        assertTrue(forcedOn.isGlassOn());
        assertFalse("overridden off stays solid", forcedOff.isGlassOn());
        assertFalse(forcedOff.style().glass);

        page.onGlass = false;
        for (Plain view : new Plain[] {follows, forcedOn, forcedOff}) view.onGlassStyle(look);
        assertFalse(follows.isGlassOn());
        assertFalse(follows.style().glass);
        assertTrue("overridden on keeps its glass", forcedOn.style().glass);
    }

    @Test
    public void theLooksSlabShowsOnlyOnALiveWidgetWithItsSwitchOnAndTheLookWorn() {
        assertTrue(BuiltinWidgetGlass.slabShown(true, false, true));
        assertFalse(BuiltinWidgetGlass.slabShown(false, false, true));
        assertFalse("a picker card samples nothing", BuiltinWidgetGlass.slabShown(true, true, true));
        assertFalse("off the wallpaper: the solid card",
            BuiltinWidgetGlass.slabShown(true, false, false));
    }

    @Test
    public void onlyCornersThatReallyMovedRestyle() {
        assertFalse(BuiltinWidgetGlass.cornersMoved(48f, 48f));
        assertFalse("float noise from the same dp", BuiltinWidgetGlass.cornersMoved(48f, 48.1f));
        assertTrue(BuiltinWidgetGlass.cornersMoved(48f, 40f));
        assertTrue(BuiltinWidgetGlass.cornersMoved(0f, 1f));
    }

    @Test
    public void everyStyleIsCutAtTheLooksCornersWhateverTheStyleSetting() {
        float density = context.getResources().getDisplayMetrics().density;
        float look = TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS * density;
        assertEquals(look, style.cornerRadiusPx, 0.01f);
        assertEquals("Pane is no longer its own 12dp", look, BuiltinWidgetStyle.resolve(context,
            BuiltinWidgetStyle.Numerals.MONO, false, null).cornerRadiusPx, 0.01f);
    }

    @Test
    public void offIsTheSolidCardAndOnWithNoGlassToWearIsTooSo() {
        Plain view = new Plain(context, services, style);
        view.bind(null);
        assertSame("off: the host's own look", style, view.style());
        assertEquals("the solid card's ground is whole", 255, view.groundAlpha());
        view.bind(glassOn());
        assertTrue(view.isGlassOn());
        assertSame("no Look to wear: the solid card, not a see-through one", style,
            view.style());
        assertEquals(255, view.groundAlpha());
        view.bind(new Bundle());
        assertFalse(view.isGlassOn());
        assertSame("back off: the solid card again", style, view.style());
    }

    /** Spec #101: each named condition alone puts the solid card on. */
    @Test
    public void theSolidCardStandsInForGlassUnderEachNamedCondition() {
        assertFalse("glass", BuiltinWidgetGlass.solidFallback(true, true, false, false, false));
        assertTrue("off the wallpaper",
            BuiltinWidgetGlass.solidFallback(false, true, false, false, false));
        assertTrue("the shared frame not ready",
            BuiltinWidgetGlass.solidFallback(true, false, false, false, false));
        assertTrue("battery saver", BuiltinWidgetGlass.solidFallback(true, true, true, false, false));
        assertTrue("outline text", BuiltinWidgetGlass.solidFallback(true, true, false, true, false));
        assertTrue("high contrast",
            BuiltinWidgetGlass.solidFallback(true, true, false, false, true));
        assertTrue(BuiltinWidgetGlass.glassWorn(true, false, true, true, false, false, false));
        assertFalse(BuiltinWidgetGlass.glassWorn(true, false, true, true, true, false, false));
        assertFalse("the switch off", BuiltinWidgetGlass.glassWorn(false, false, true, true,
            false, false, false));
    }

    /** The card follows the page's ink; night forces dark whatever the ink. */
    @Test
    public void theCardsSideFollowsThePagesInkAndNightForcesDark() {
        assertTrue(BuiltinWidgetGlass.darkCard(false, ChromeInk.Polarity.PALE_INK));
        assertFalse(BuiltinWidgetGlass.darkCard(false, ChromeInk.Polarity.DARK_INK));
        assertFalse("no chrome to ask: the theme's card", BuiltinWidgetGlass.darkCard(false, null));
        assertTrue(BuiltinWidgetGlass.darkCard(true, ChromeInk.Polarity.DARK_INK));

        BuiltinWidgetStyle night = themed(com.google.android.material.R.style.Theme_Material3_Dark);
        assertTrue(night.dark && night.night);
        assertSame("night forces the dark card on either ink", night, night.onInk(false));
        assertSame(night, night.onInk(true));

        BuiltinWidgetStyle day = dayStyle();
        assertFalse("the day theme's card is light", day.dark);
        BuiltinWidgetStyle dark = day.onInk(true);
        assertTrue(dark.dark);
        assertFalse("still the day theme", dark.night);
        assertSame("made once", dark, day.onInk(true));
        assertSame("the dark ink keeps the theme's card", day, day.onInk(false));
        assertSame("a dark card is itself on either ink", dark, dark.onInk(false));
        assertTrue("a dark card", OnGlass.ratio(dark.card, Color.BLACK) < OnGlass.ratio(dark.card,
            Color.WHITE));
        for (int text : new int[] {dark.onSurface, dark.onSurfaceVariant}) {
            assertTrue("pale text reads on it", OnGlass.ratio(text, dark.card)
                >= OnGlass.TARGET_BODY_TEXT);
        }
        assertTrue("and its accent", OnGlass.ratio(dark.primary, dark.card)
            >= OnGlass.TARGET_LARGE_TEXT);
        for (int fill : new int[] {dark.card, dark.container, dark.containerHigh,
                day.glassVariant(context).container, day.glassVariant(context).card}) {
            assertEquals("every fill is opaque: no glass on glass", 255, Color.alpha(fill));
        }
    }

    /** A style resolved in {@code theme}, whatever theme the test's application wears. */
    private BuiltinWidgetStyle themed(int theme) {
        return BuiltinWidgetStyle.resolve(new android.view.ContextThemeWrapper(context, theme),
            BuiltinWidgetStyle.Numerals.SANS, false, null);
    }

    private BuiltinWidgetStyle dayStyle() {
        return themed(com.google.android.material.R.style.Theme_Material3_Light);
    }

    /** The edge rim: wanted where the card would vanish into the wallpaper behind it. */
    @Test
    public void theRimDrawsWhereTheCardWouldVanish() {
        BuiltinWidgetStyle day = dayStyle();
        assertTrue(BuiltinWidgetGlass.edgeRimWanted(day.card, 0xFFE8E4DC));
        assertFalse(BuiltinWidgetGlass.edgeRimWanted(day.card, Color.BLACK));
        BuiltinWidgetStyle dark = day.onInk(true);
        assertTrue(BuiltinWidgetGlass.edgeRimWanted(dark.card, 0xFF1B1A17));
        assertFalse(BuiltinWidgetGlass.edgeRimWanted(dark.card, Color.WHITE));
    }

    /**
     * The Terminal widget's veil: on glass, the palette's background is laid over the card at
     * the smallest alpha the palette's foreground reads at, never less than the card's own.
     */
    @Test
    public void theTerminalWidgetsVeilHoldsThePalettesInk() {
        int lightBg = 0xFFFAFAFA;
        int darkFg = 0xFF202020;
        int darkGround = 0xFF303236;
        int alpha = TerminalWidgetView.terminalGroundAlpha(102, darkGround, lightBg, darkFg);
        assertTrue("raised above the card's own: " + alpha, alpha > 102);
        int surface = OnGlass.opaque(OnGlass.composite(OnGlass.withAlpha(lightBg, alpha),
            darkGround));
        assertTrue(OnGlass.ratio(darkFg, surface) >= OnGlass.TARGET_BODY_TEXT);
        assertEquals("a palette that reads already keeps the card's", 102,
            TerminalWidgetView.terminalGroundAlpha(102, darkGround, 0xFF101010, 0xFFF0F0F0));
        assertEquals("the solid card is whole", 255,
            TerminalWidgetView.terminalGroundAlpha(255, darkGround, lightBg, darkFg));
    }

    /** A Look the chrome stands behind: the widget band's polarity, frame and answers. */
    private static final class InkLook implements PaneSurfaceStyle, WidgetInkSource {
        @NonNull final PaneSurfaceStyle look;
        @NonNull ChromeInk.Polarity polarity = ChromeInk.Polarity.DARK_INK;
        boolean frameReady = true;
        int wallpaper = 0xFF202226;

        InkLook(@NonNull PaneSurfaceStyle look) { this.look = look; }

        @NonNull @Override public ChromeInk.Polarity widgetPolarity() { return polarity; }
        @Override public boolean widgetFrameReady() { return frameReady; }
        @NonNull @Override public OnGlass.Resolution widgetInk(@NonNull Rect rootRect, int body,
                                                               int veilColor, int textInk,
                                                               int graphicInk) {
            return OnGlass.resolveWidget(wallpaper, body, textInk, OnGlass.TARGET_BODY_TEXT,
                graphicInk, OnGlass.TARGET_LARGE_TEXT, veilColor);
        }
        @Override public int widgetWallpaper(@NonNull Rect rootRect) { return wallpaper; }

        @Override public boolean isPaneGlassActive() { return look.isPaneGlassActive(); }
        @Override public Bitmap paneGlassBlurFrame() { return look.paneGlassBlurFrame(); }
        @NonNull @Override public Rect paneGlassBlurFrameRect() { return look.paneGlassBlurFrameRect(); }
        @Override public android.graphics.ColorFilter paneGlassFrostFilter() { return null; }
        @Override public int paneGlassTintColor() { return 0x4DE1E7F2; }
        @Override public android.graphics.drawable.Drawable paneGlassGrainLayer() { return null; }
        @Override public int paneGlassGrainStrength() { return 0; }
        @Override public float paneGlassCornerRadiusPx() { return 0f; }
        @Override public int paneCornerRadiusDp() { return look.paneCornerRadiusDp(); }
        @Override public int paneGapDp() { return 0; }
    }

    /**
     * On the page: a frame not ready is the solid card until it lands; the glass card over a
     * dark wallpaper wears its veil, so its ground is thicker than the Look's tint and lift alone;
     * the page's pale ink puts the dark card on and builds the layout in it, once.
     */
    @Test
    public void onThePageTheCardFollowsTheBandAndFallsBackToSolid() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Page page = new Page(activity);
        InkLook look = new InkLook(glass(true, Bitmap.createBitmap(10, 20,
            Bitmap.Config.ARGB_8888)));
        look.frameReady = false;
        page.glass = look;
        Plain view = new Plain(activity, services, dayStyle());
        view.bind(glassOn());
        page.addView(view, new FrameLayout.LayoutParams(200, 100));
        activity.setContentView(page);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        PaneGlassBackdropView slab = slabOf(view);
        assertTrue("no frame yet: no slab", slab == null || slab.getVisibility() == View.GONE);
        assertFalse(view.style().glass);
        assertEquals(255, view.groundAlpha());

        look.frameReady = true;
        view.onGlassStyle(look);
        slab = slabOf(view);
        assertTrue(slab != null && slab.getVisibility() == View.VISIBLE);
        assertTrue(view.style().glass);
        int tintAndLift = 255 - Math.round((255 - 0x4D)
            * (255 - OnGlass.widgetLiftAlpha255(0x4D)) / 255f);
        assertTrue("the light card's veil over a dark wallpaper: " + view.groundAlpha(),
            view.groundAlpha() > tintAndLift);

        int builds = view.builds;
        look.polarity = ChromeInk.Polarity.PALE_INK;
        view.onGlassStyle(look);
        assertTrue("the page's pale ink: the dark card", view.style().dark);
        assertEquals("built again in it", builds + 1, view.builds);
        view.onGlassStyle(look);
        assertEquals("the same answer builds nothing", builds + 1, view.builds);
    }

    @Test
    public void aGlassWidgetWearsTheLooksSlabAsItsCardAndPutsItDownWhenOff() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Page page = new Page(activity);
        Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);
        page.glass = glass(true, frame);
        Plain view = new Plain(activity, services, style);
        view.bind(glassOn());
        page.addView(view, new FrameLayout.LayoutParams(200, 100));
        activity.setContentView(page);

        assertTrue("registered with its page", page.live.contains(view));
        PaneGlassBackdropView slab = slabOf(view);
        assertTrue(slab != null && slab.getVisibility() == View.VISIBLE);
        assertSame("under the card", slab, view.getChildAt(0));
        assertSame(frame, slab.heldFrame());
        assertTrue("the card's fill over it is the body's lift",
            cardOf(view).getBackground() instanceof GradientDrawable);
        int lift = OnGlass.widgetLiftAlpha255(0x80);
        assertEquals("the ground is the Look's tint and the lift",
            255 - Math.round((255 - 0x80) * (255 - lift) / 255f), view.groundAlpha());

        // Off the wallpaper there is no glass to wear: the solid card comes back.
        view.onGlassStyle(glass(false, null));
        assertEquals(View.GONE, slab.getVisibility());
        assertTrue(cardOf(view).getBackground() instanceof GradientDrawable);
        assertFalse(view.style().glass);
        assertEquals(255, view.groundAlpha());
        view.onGlassStyle(page.glass);
        assertEquals(View.VISIBLE, slab.getVisibility());

        view.bind(new Bundle());
        assertTrue("still fed the Look's corners", page.live.contains(view));
        assertEquals(View.GONE, slab.getVisibility());
        assertTrue(cardOf(view).getBackground() instanceof GradientDrawable);

        view.bind(glassOn());
        page.removeView(view);
        assertFalse("detached: no longer fed", page.live.contains(view));
    }

    @Test
    public void aWidgetWithGlassOffIsFedTheLookButNeverMakesASlab() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Page page = new Page(activity);
        page.glass = glass(true, Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888));
        Plain view = new Plain(activity, services, style);
        view.bind(null);
        page.addView(view, new FrameLayout.LayoutParams(200, 100));
        activity.setContentView(page);
        assertTrue(page.live.contains(view));
        view.onGlassStyle(page.glass);
        assertNull(slabOf(view));
    }

    @Test
    public void aCornersChangeReDressesInPlaceWithoutARebuild() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Page page = new Page(activity);
        page.glass = glass(true, Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888));
        Plain view = new Plain(activity, services, style);
        view.bind(glassOn());
        page.addView(view, new FrameLayout.LayoutParams(400, 400));
        activity.setContentView(page);
        int builds = view.builds;
        float density = context.getResources().getDisplayMetrics().density;
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS * density,
            view.corner().radiusPx(), 0.01f);

        // A Corners slider's ticks: every one lands, none builds the layout again.
        for (int dp : new int[] {30, 36, 40}) {
            view.onGlassStyle(glass(true, null, dp));
            assertEquals(dp * density, view.corner().radiusPx(), 0.01f);
            assertEquals("the inner shape follows: the corner less its 10dp inset",
                Math.max(0f, (dp - 10) * density), view.inner.getCornerRadius(), 0.01f);
        }
        view.onGlassStyle(glass(true, null, 0));
        assertEquals("square corners reach the inner shape too", 0f,
            view.inner.getCornerRadius(), 0f);
        assertEquals("nothing was built again", builds, view.builds);
        assertEquals("the layout's shape and follower", 2, view.corner().trackedCount());

        // A real rebuild forgets the old layout's shapes and registers its own.
        view.applyStyle(style);
        assertEquals(builds + 1, view.builds);
        assertEquals(2, view.corner().trackedCount());
    }
}
