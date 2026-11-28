package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassService;
import com.termux.app.window.FramePipeline;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * A Terl window's glass (review P3-1, pong 11-28 "no frost under terminal windows"): the window
 * wears its glass even while the stored frame of its Windows Blur is still baking, and the frame
 * that lands reaches that same drawable. The old window glass answered null while the frame baked,
 * so a shell's frame wore nothing and nothing ever dressed it again.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class TerlWindowGlassTest {

    private GlassService glass;
    private TerlGlassWear wear;
    private FrameLayout window;
    @Nullable private Object baked;
    private boolean sampleable = true;
    private final List<Object> storedWith = new ArrayList<>();

    private TerlWindowGlass windows() {
        return new TerlWindowGlass(wear, new TerlWindowGlass.Frames() {
            @Override public boolean sampleable() { return sampleable; }
            @Nullable @Override public Object frame(int blurDp) { return baked; }
        }, new TerlWindowGlass.Dress() {
            @NonNull @Override public Drawable stored(float radiusPx, @NonNull View anchor, int blurDp,
                                                      int tintPercent, @Nullable Object frame) {
                storedWith.add(frame == null ? "none" : frame);
                return new ColorDrawable(0x40000000);
            }
            @NonNull @Override public Drawable live(float radiusPx, int blurDp, int tintPercent,
                                                    @NonNull LivePicture live) {
                return new ColorDrawable(0x40000000);
            }
        }, 2f);
    }

    @Before
    public void setUp() {
        glass = new GlassService(new FramePipeline());
        wear = new TerlGlassWear(() -> glass);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        window = new FrameLayout(activity);
        activity.setContentView(window);
    }

    private View attached() {
        View view = new View(window.getContext());
        window.addView(view);
        return view;
    }

    @Test
    public void aWindowDressedWhileItsFrameBakesWearsTheFrameThatLands() {
        View shell = attached();
        baked = null;
        Drawable worn = windows().glass(28f, shell, 7, 0, 3, false);
        assertNotNull("the window wears its glass before its frame has baked", worn);
        assertNotNull("as an entry of the glass service", glass.entry(TerlGlassWear.windowElement(3)));
        assertEquals("none", storedWith.get(storedWith.size() - 1));
        // The chrome's frame-ready pass, as ChromeRenderer.onBlurFrameReady runs it.
        baked = new Object();
        wear.refreshStored();
        assertSame("the frame that landed reaches the drawable the window wears", baked,
            storedWith.get(storedWith.size() - 1));
    }

    @Test
    public void aWindowIsATileOrAFloatInTheBudget() {
        View tile = attached();
        View floating = attached();
        windows().glass(28f, tile, 7, 0, 3, false);
        windows().glass(28f, floating, 7, 46, 4, true);
        assertEquals(GlassEntry.PRIORITY_TILE, glass.entry(TerlGlassWear.windowElement(3)).priority);
        assertEquals(GlassEntry.PRIORITY_FLOAT, glass.entry(TerlGlassWear.windowElement(4)).priority);
    }

    @Test
    public void noBlurOrNoWallpaperLetsTheEntryGo() {
        View tile = attached();
        TerlWindowGlass windows = windows();
        windows.glass(28f, tile, 7, 0, 3, false);
        assertNull(windows.glass(28f, tile, 0, 0, 3, false));
        assertNull(glass.entry(TerlGlassWear.windowElement(3)));
        windows.glass(28f, tile, 7, 0, 3, false);
        sampleable = false;
        assertNull(windows.glass(28f, tile, 7, 0, 3, false));
        assertNull(glass.entry(TerlGlassWear.windowElement(3)));
    }
}
