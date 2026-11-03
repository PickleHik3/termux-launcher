package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.LiveBackdrop;
import com.termux.app.chrome.LiveBackdropFrameLayout;
import com.termux.app.chrome.LivePicture;
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

import java.util.List;

/**
 * A built-in widget's glass takes Live blur's picture only while Live blur is on, the phone can
 * record, the slab is worn and its page is shown and on screen; it gives the picture back when
 * the page leaves or the switch goes off, and with Live off it never holds one.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Application.class)
public class WidgetLiveGlassTest {

    @Test
    public void wantsEveryTermAndNothingLess() {
        int[] sdks = {Build.VERSION_CODES.R, Build.VERSION_CODES.S, Build.VERSION_CODES.TIRAMISU};
        for (int sdk : sdks) {
            for (int bits = 0; bits < 16; bits++) {
                boolean live = (bits & 1) != 0;
                boolean slab = (bits & 2) != 0;
                boolean page = (bits & 4) != 0;
                boolean screen = (bits & 8) != 0;
                boolean expected = bits == 15 && sdk >= Build.VERSION_CODES.S;
                assertEquals("sdk " + sdk + " bits " + bits, expected,
                    WidgetLiveGlass.wants(live, sdk, slab, page, screen));
            }
        }
    }

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    /** A page that hands out the Look and Live blur's pictures, at the widgets' rank. */
    private static final class Page extends LiveBackdropFrameLayout
            implements BuiltinWidgetView.GlassSource {
        @Nullable PaneSurfaceStyle glass;
        @Nullable LiveBackdrop recorder;
        boolean live;

        Page(@NonNull Context context) { super(context); }

        @Nullable @Override public PaneSurfaceStyle widgetGlassStyle() { return glass; }
        @Override public boolean widgetsOnGlass() { return true; }
        @Override public void addGlassWidget(@NonNull BuiltinWidgetView widget) { }
        @Override public void removeGlassWidget(@NonNull BuiltinWidgetView widget) { }
        @Override public boolean liveBlur() { return live; }
        @Nullable @Override public LivePicture attachLiveGlass(@NonNull View slab) {
            if (!live || recorder == null) return null;
            return recorder.attach(slab, 20f, LiveBackdrop.RANK_WIDGET);
        }
        @Override public void detachLiveGlass(@NonNull View slab) {
            if (recorder != null) recorder.detach(slab);
        }
    }

    /** The Look on the wallpaper, its frame ready. */
    private static final class Look implements PaneSurfaceStyle, WidgetInkSource {
        @NonNull private final Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);

        @NonNull @Override public ChromeInk.Polarity widgetPolarity() {
            return ChromeInk.Polarity.PALE_INK;
        }
        @Override public boolean widgetFrameReady() { return true; }
        @NonNull @Override public OnGlass.Resolution widgetInk(@NonNull Rect rootRect, int body,
                                                               int veilColor, int textInk,
                                                               int graphicInk) {
            return OnGlass.resolveWidget(0xFF101114, body, textInk, OnGlass.TARGET_BODY_TEXT,
                graphicInk, OnGlass.TARGET_LARGE_TEXT, veilColor);
        }
        @Override public int widgetWallpaper(@NonNull Rect rootRect) { return 0xFF101114; }
        @Override public boolean isPaneGlassActive() { return true; }
        @Override public Bitmap paneGlassBlurFrame() { return frame; }
        @NonNull @Override public Rect paneGlassBlurFrameRect() { return new Rect(0, 0, 100, 200); }
        @Override public android.graphics.ColorFilter paneGlassFrostFilter() { return null; }
        @Override public int paneGlassTintColor() { return 0x4DE1E7F2; }
        @Override public android.graphics.drawable.Drawable paneGlassGrainLayer() { return null; }
        @Override public int paneGlassGrainStrength() { return 0; }
        @Override public float paneGlassCornerRadiusPx() { return 0f; }
        @Override public int paneCornerRadiusDp() {
            return TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS;
        }
        @Override public int paneGapDp() { return 0; }
    }

    private BuiltinWidgetServices services;

    @Before public void setUp() {
        services = new BuiltinWidgetServices(RuntimeEnvironment.getApplication(), HOST);
    }

    private BuiltinWidgetStyle dayStyle() {
        return BuiltinWidgetStyle.resolve(new android.view.ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                com.google.android.material.R.style.Theme_Material3_Light),
            BuiltinWidgetStyle.Numerals.SANS, false, null);
    }

    private static void idle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Nullable
    private static PaneGlassBackdropView slabOf(@NonNull BuiltinWidgetView widget) {
        for (int i = 0; i < widget.getChildCount(); i++) {
            if (widget.getChildAt(i) instanceof PaneGlassBackdropView)
                return (PaneGlassBackdropView) widget.getChildAt(i);
        }
        return null;
    }

    private static final class Scene {
        Page page;
        ClockWidgetView clock;
        PaneGlassBackdropView slab;
    }

    private Scene scene(boolean live) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Scene scene = new Scene();
        scene.page = new Page(activity);
        scene.page.glass = new Look();
        scene.page.live = live;
        scene.page.recorder = new LiveBackdrop(scene.page);
        scene.clock = new ClockWidgetView(activity, services, dayStyle());
        scene.clock.bind(null);
        scene.page.addView(scene.clock, new FrameLayout.LayoutParams(400, 200));
        activity.setContentView(scene.page);
        idle();
        scene.clock.onGlassStyle(scene.page.glass);
        idle();
        scene.slab = slabOf(scene.clock);
        assertNotNull("the clock wears the Look's slab", scene.slab);
        assertEquals(View.VISIBLE, scene.slab.getVisibility());
        return scene;
    }

    @Test
    public void liveOffNeverHoldsAPicture() {
        Scene scene = scene(false);
        assertNull(scene.slab.livePicture());
        assertEquals(0, scene.page.recorder.size());
        scene.clock.onGlassMoved();
        assertNull(scene.slab.livePicture());
    }

    @Test
    public void aShownPageTakesThePictureAndALeavingOneGivesItBack() {
        Scene scene = scene(true);
        assertNotNull("a widget on the page in front is live", scene.slab.livePicture());
        assertEquals(1, scene.page.recorder.size());

        // The wall slides Home off (INVISIBLE), or the pager drops the page beside (GONE).
        scene.page.setVisibility(View.INVISIBLE);
        assertNull("a page off screen holds no picture", scene.slab.livePicture());
        assertEquals(0, scene.page.recorder.size());

        scene.page.setVisibility(View.VISIBLE);
        assertNotNull("back on screen, live again", scene.slab.livePicture());
        assertEquals(1, scene.page.recorder.size());

        // Live blur off: the next dress pass gives it back.
        scene.page.live = false;
        scene.clock.onGlassStyle(scene.page.glass);
        assertNull(scene.slab.livePicture());
        assertEquals(0, scene.page.recorder.size());
    }

    @Test
    public void aWidgetLeavingTheWindowGivesItsPictureBack() {
        Scene scene = scene(true);
        assertNotNull(scene.slab.livePicture());
        scene.page.removeView(scene.clock);
        assertNull(scene.slab.livePicture());
        assertEquals(0, scene.page.recorder.size());
    }

    @Test
    public void aWidgetSlidOffScreenGivesItsPictureBackOnTheMove() {
        Scene scene = scene(true);
        assertNotNull(scene.slab.livePicture());
        scene.clock.setTranslationX(100_000f);
        scene.clock.onGlassMoved();
        assertNull("slid out of the window", scene.slab.livePicture());
        scene.clock.setTranslationX(0f);
        scene.clock.onGlassMoved();
        assertNotNull(scene.slab.livePicture());
    }

    @Test
    public void aDisplacedWidgetFallsBackToItsStoredFrame() {
        Scene scene = scene(true);
        LivePicture picture = scene.slab.livePicture();
        assertNotNull(picture);
        for (int i = 0; i < LiveBackdrop.MAX_LIVE_SURFACES; i++) {
            View chrome = new View(scene.page.getContext());
            scene.page.addView(chrome);
            assertNotNull(scene.page.recorder.attach(chrome, 20f));
        }
        assertTrue("the dock and the panels come before a widget", picture.released());
        assertSame(picture, scene.slab.livePicture());
        assertEquals(LiveBackdrop.MAX_LIVE_SURFACES, scene.page.recorder.size());
    }
}
