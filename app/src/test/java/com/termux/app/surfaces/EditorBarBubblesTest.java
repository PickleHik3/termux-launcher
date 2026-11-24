package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.wall.UseAs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The bubble row of the editor bar: what stands in it, what is marked, what a tap reports. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi", application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorBarBubblesTest {

    private EditorBar bar() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        FrameLayout parent = new FrameLayout(themed);
        activity.setContentView(parent);
        EditorBar bar = new EditorBar(themed);
        bar.attach(parent);
        bar.setShown(true);
        return bar;
    }

    @Test
    public void terminalOnlyTakesTheHomeBubbleOutOfTheRow() {
        EditorBar bar = bar();
        bar.setBubbles(EditorBubbles.offered(UseAs.TERMINAL), EditorBubbles.WORKSPACE);
        assertNull(bar.bubbleView(EditorBubbles.HOME).getParent());
        assertNotNull(bar.bubbleView(EditorBubbles.GLOBAL).getParent());
        bar.setBubbles(EditorBubbles.offered(UseAs.HOME), EditorBubbles.HOME);
        View row = (View) bar.bubbleView(EditorBubbles.GLOBAL).getParent();
        assertSame(row, bar.bubbleView(EditorBubbles.HOME).getParent());
        assertEquals(1, ((android.view.ViewGroup) row).indexOfChild(bar.bubbleView(EditorBubbles.HOME)));
    }

    @Test
    public void theMarkFollowsTheCardAndSelectsOnlyItsBubble() {
        EditorBar bar = bar();
        bar.setMarked(EditorBubbles.markedFor(EditorCard.TERMINAL));
        assertTrue(bar.bubbleView(EditorBubbles.WORKSPACE).isSelected());
        assertFalse(bar.bubbleView(EditorBubbles.GLOBAL).isSelected());
        bar.setMarked(EditorBubbles.markedFor(EditorCard.DOCK));
        assertNull(bar.markedBubble());
        assertFalse(bar.bubbleView(EditorBubbles.WORKSPACE).isSelected());
    }

    @Test
    public void aTapReportsItsBubbleAndTheLabelsStayOneLine() {
        EditorBar bar = bar();
        final EditorBubbles[] got = new EditorBubbles[1];
        bar.setListener(new EditorBar.Listener() {
            @Override public void onUndo() {}
            @Override public void onDone() {}
            @Override public void onPalette() {}
            @Override public void onThemeHintSeen() {}
            @Override public void onBubble(EditorBubbles bubble) {
                got[0] = bubble;
            }
        });
        bar.bubbleView(EditorBubbles.WORKSPACE).performClick();
        assertEquals(EditorBubbles.WORKSPACE, got[0]);
        for (EditorBubbles bubble : EditorBubbles.values())
            assertEquals(1, bar.bubbleView(bubble).getMaxLines());
    }

    @Test
    public void theWallpaperButtonReportsAndOpensGlobal() {
        EditorBar bar = bar();
        final int[] taps = new int[1];
        bar.setListener(new EditorBar.Listener() {
            @Override public void onUndo() {}
            @Override public void onDone() {}
            @Override public void onPalette() {}
            @Override public void onThemeHintSeen() {}
            @Override public void onWallpaper() {
                taps[0]++;
            }
        });
        bar.wallpaperButton().performClick();
        assertEquals(1, taps[0]);
        assertEquals(EditorCard.APPEARANCE, EditorBar.WALLPAPER_CARD);
        assertEquals(EditorBubbles.GLOBAL, EditorBubbles.markedFor(EditorBar.WALLPAPER_CARD));
    }

    @Test
    public void theButtonShowsTheThumbAtItsPixelSizeAndTheGlyphWithoutOne() {
        EditorBar bar = bar();
        int px = EditorBar.wallpaperThumbPx(2.625f);
        assertEquals(74, px);
        android.graphics.Bitmap thumb = android.graphics.Bitmap.createBitmap(px, px,
            android.graphics.Bitmap.Config.ARGB_8888);
        bar.setWallpaperThumb(thumb);
        assertEquals(px, bar.wallpaperButton().getIconSize());
        assertTrue(bar.wallpaperButton().getIcon() instanceof android.graphics.drawable.BitmapDrawable);
        bar.setWallpaperThumb(null);
        assertFalse(bar.wallpaperButton().getIcon() instanceof android.graphics.drawable.BitmapDrawable);
    }
}
