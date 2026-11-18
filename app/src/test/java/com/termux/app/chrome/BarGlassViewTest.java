package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.FramePipeline;
import com.termux.app.window.WindowLayer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A bar's glass draws its own wallpaper frost under its background and registers one entry with
 * the glass service under its id's name (ADR 0029 sections 1 and 5).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BarGlassViewTest {

    private static final int[] IDS = {R.id.terminal_status_bar_glass_surface,
        R.id.terminal_window_bar_background, R.id.terminal_window_bar_background_side};
    private static final String[] NAMES = {"terminal_status_bar_glass_surface",
        "terminal_window_bar_background", "terminal_window_bar_background_side"};

    /** Writes its name into a shared log when drawn. */
    private static final class Marker extends Drawable {
        private final String mName;
        private final List<String> mLog;
        ColorFilter filter;

        Marker(String name, List<String> log) {
            mName = name;
            mLog = log;
        }

        @Override public void draw(@NonNull Canvas canvas) { mLog.add(mName); }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter colorFilter) { filter = colorFilter; }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private Activity activity;
    private FrameLayout root;
    private GlassService glass;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new FrameLayout(activity);
        glass = new GlassService(new FramePipeline());
        GlassService.install(activity.getWindow().getDecorView(), glass);
    }

    private BarGlassView add(int id) {
        BarGlassView view = new BarGlassView(activity);
        view.setId(id);
        root.addView(view, new FrameLayout.LayoutParams(200, 40));
        return view;
    }

    private void attach() {
        activity.setContentView(root);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void eachBarGlassRegistersOneEntryUnderItsIdAndLetsGoOnDetach() {
        for (int id : IDS) add(id);
        attach();
        assertEquals(3, glass.size());
        for (String name : NAMES) {
            GlassEntry entry = glass.entry(name);
            assertNotNull(name, entry);
            assertEquals(GlassKind.WALLPAPER, glass.granted(name));
            assertEquals(WindowLayer.CHROME, entry.layer);
        }
        // A second view under the same name is refused: the registry has no twins.
        BarGlassView twin = add(IDS[0]);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(3, glass.size());
        assertSame(root.getChildAt(0), glass.entry(NAMES[0]).view);
        root.removeView(twin);
        assertEquals("the refused twin's detach leaves the held entry", 3, glass.size());

        root.removeAllViews();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, glass.size());
    }

    @Test
    public void theFrostDrawsBeforeTheBackground() {
        BarGlassView view = add(IDS[1]);
        attach();
        List<String> log = new ArrayList<>();
        view.setBackground(new Marker("background", log));
        Marker frost = new Marker("frost", log);
        view.setFrost(frost);
        view.setFrostShown(true);
        view.layout(0, 0, 200, 40);

        view.draw(new Canvas());
        assertEquals(Arrays.asList("frost", "background"), log);
        assertEquals("the frost fills the view", 200, frost.getBounds().width());
    }

    @Test
    public void aFrostNotShownDrawsNothingAndTheFilterReachesTheFrost() {
        BarGlassView view = add(IDS[0]);
        attach();
        List<String> log = new ArrayList<>();
        view.setBackground(new Marker("background", log));
        Marker frost = new Marker("frost", log);
        ColorFilter filter = new PorterDuffColorFilter(0, PorterDuff.Mode.SRC_OVER);
        view.setFrostColorFilter(filter);
        view.setFrost(frost);
        assertSame(filter, frost.filter);
        assertFalse(view.frostShown());
        view.layout(0, 0, 200, 40);

        view.draw(new Canvas());
        assertEquals(Arrays.asList("background"), log);

        view.setFrostShown(true);
        log.clear();
        view.draw(new Canvas());
        assertEquals(Arrays.asList("frost", "background"), log);
        assertTrue(view.frostShown());
    }
}
