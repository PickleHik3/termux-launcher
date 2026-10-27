package com.termux.app.terminal;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;

/**
 * The minimised Home float's dot as the window bar really draws it: the bar's own pill, attached
 * and laid out as on the phone, drawn through its parent. The 2026-10-21 emulator check saw the
 * chip dimmed with no dot, though the pill wore the dot as its foreground: a pill's title is one
 * centred line, which the pill scrolls into view, and a view's foreground scrolls with its
 * content, so the dot was drawn far outside the pill.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalWindowBarMinimisedDotTest {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 48;

    private static TerminalWindowBar attachedBar(TerminalWindowBar.WindowItem... items) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        TerminalWindowBar bar = new TerminalWindowBar(activity, null);
        root.addView(bar, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
        activity.setContentView(root, new ViewGroup.LayoutParams(WIDTH, HEIGHT));
        bar.setWindows(Arrays.asList(items), 0);
        ShadowLooper.idleMainLooper();
        bar.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, WIDTH, HEIGHT);
        // The pre-draw pass a frame runs, where a one-line title scrolls itself into view.
        bar.getViewTreeObserver().dispatchOnPreDraw();
        return bar;
    }

    /** The bar drawn through its own children, as its parent draws it. */
    private static Bitmap drawn(TerminalWindowBar bar) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bar.draw(new Canvas(bitmap));
        return bitmap;
    }

    @Test
    public void aMinimisedFloatsPill_showsItsDot_inItsTopEndCorner() {
        TerminalWindowBar bar = attachedBar(
            new TerminalWindowBar.WindowItem("home", "home"),
            new TerminalWindowBar.WindowItem("home", "home").withHomeFloat(0, "Home float")
                .withMinimised(true));
        ViewGroup strip = (ViewGroup) bar.chipStripView();
        TextView pill = (TextView) strip.getChildAt(1);
        assertTrue("the pill wears the dot", pill.getForeground() instanceof MinimisedDotDrawable);
        Bitmap withDot = drawn(bar);
        float density = bar.getResources().getDisplayMetrics().density;
        float radius = TerminalWindowBar.WindowItem.MINIMISED_DOT_DP * density / 2f;
        float inset = MinimisedDotDrawable.INSET_DP * density;
        int cx = Math.round(strip.getLeft() + pill.getRight() - inset - radius - bar.getScrollX());
        android.graphics.Rect cap = new android.graphics.Rect();
        pill.getPaint().getTextBounds("H", 0, 1, cap);
        int cy = Math.round(strip.getTop() + pill.getTop() + pill.getBaseline() + cap.top + radius);
        int dotted = withDot.getPixel(cx, cy);

        MinimisedDotDrawable.wear(pill, false);
        int bare = drawn(bar).getPixel(cx, cy);
        assertTrue("the dot shows at (" + cx + ", " + cy + ") of a pill scrolled "
                + pill.getScrollX() + ": " + Integer.toHexString(dotted) + " against "
                + Integer.toHexString(bare),
            distance(dotted, bare) > 60);
    }

    @Test
    public void aMinimisedPill_keepsItsDotClearOfTheLabel_andAnOpenPillKeepsItsPadding() {
        TerminalWindowBar bar = attachedBar(
            new TerminalWindowBar.WindowItem("home", "home"),
            new TerminalWindowBar.WindowItem("home", "home").withHomeFloat(0, "home 1")
                .withMinimised(true));
        ViewGroup strip = (ViewGroup) bar.chipStripView();
        TextView open = (TextView) strip.getChildAt(0);
        TextView pill = (TextView) strip.getChildAt(1);
        float density = bar.getResources().getDisplayMetrics().density;
        int extra = MinimisedDotDrawable.extraEndPaddingPx(density);

        int openEnd = open.getPaddingEnd();
        int pillEnd = pill.getPaddingEnd();
        MinimisedDotDrawable.wear(open, false);
        assertTrue("an open pill's padding is unchanged", open.getPaddingEnd() == openEnd);
        MinimisedDotDrawable.wear(pill, true);
        assertTrue("wearing again does not grow the padding", pill.getPaddingEnd() == pillEnd);
        MinimisedDotDrawable.wear(pill, false);
        assertTrue("dropping the dot gives the extra back", pill.getPaddingEnd() == pillEnd - extra);
        MinimisedDotDrawable.wear(pill, true);
        assertTrue("restored", pill.getPaddingEnd() == pillEnd);

        // The label is one centred line in the pill's content box; the dot lies in the padding.
        float textWidth = pill.getPaint().measureText(pill.getText().toString());
        float contentWidth = pill.getWidth() - pill.getPaddingStart() - pill.getPaddingEnd();
        float textRight = pill.getWidth() - pill.getPaddingEnd()
            - Math.max(0f, (contentWidth - textWidth) / 2f);
        float dotLeft = pill.getWidth() - MinimisedDotDrawable.INSET_DP * density
            - TerminalWindowBar.WindowItem.MINIMISED_DOT_DP * density;
        assertTrue("the dot (left " + dotLeft + ") clears the label (right " + textRight + ")",
            dotLeft >= textRight);
    }

    private static int distance(int a, int b) {
        return Math.abs(Color.alpha(a) - Color.alpha(b)) + Math.abs(Color.red(a) - Color.red(b))
            + Math.abs(Color.green(a) - Color.green(b)) + Math.abs(Color.blue(a) - Color.blue(b));
    }
}
