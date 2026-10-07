package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Every built-in widget, at every bucket, from Android's minimum for the bucket to well past its
 * design size: no visible view may extend past its parent's padded bounds. The widgets are built
 * as picker cards (sample data), which is the text the design was drawn with.
 */
@RunWith(RobolectricTestRunner.class)
// Native graphics: real text metrics, so a label's width and height are the device's, not a stub's.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BuiltinWidgetRangeClipTest {
    private static final float DENSITY = 2.625f;

    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    /** The sizes, in dp, a bucket is checked at: its minimum, its design and a roomy end. */
    private static int[][] sizesFor(@NonNull BuiltinWidgetSpan span) {
        int minW = span.columns == 1 ? 57 : span.minWidthDp;
        int minH = span.columns == 1 ? 57 : span.minHeightDp;
        // 1.5x the design stays this bucket's: the view spans this bucket's cells, and the next
        // bucket up is drawn only from 94% of its own design, which 1.5x of this one never reaches.
        return new int[][] {
            {minW, minH}, {span.widthDp, span.heightDp},
            {Math.round(span.widthDp * 1.5f), Math.round(span.heightDp * 1.5f)}
        };
    }

    @Test public void nothingClipsFromTheMinimumToPastTheDesign() {
        Context context = RuntimeEnvironment.getApplication();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY;
        metrics.densityDpi = 420;

        List<String> clips = new ArrayList<>();
        for (BuiltinWidgetStyle.Direction direction : BuiltinWidgetStyle.Direction.values()) {
            BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(context, direction, false, null);
            for (BuiltinWidgetKind kind : BuiltinWidgetKind.values()) {
                for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
                    for (int[] size : sizesFor(span)) {
                        BuiltinWidgetServices services = new BuiltinWidgetServices(context, HOST);
                        BuiltinWidgetView view = BuiltinWidgetFactory.create(context, kind,
                            services, style);
                        view.setPreview(true);
                        view.setCells(span.columns, span.rows);
                        view.bind(null);
                        // Whole pixels that are not under the dp asked for: a minimum met in dp
                        // must be met once rounded, as a cell's pixels are.
                        int w = (int) Math.ceil(size[0] * DENSITY);
                        int h = (int) Math.ceil(size[1] * DENSITY);
                        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                        view.layout(0, 0, w, h);
                        String where = kind + "/" + direction + "/" + span + " at "
                            + size[0] + "x" + size[1] + "dp";
                        assertEquals(where + " draws the bucket it was sized for", span,
                            view.span());
                        collectClips(view, where, clips);
                        services.destroy();
                    }
                }
            }
        }
        assertTrue("clipped views:\n" + String.join("\n", clips), clips.isEmpty());
    }

    private static void collectClips(@NonNull ViewGroup parent, @NonNull String where,
                                     @NonNull List<String> out) {
        int innerLeft = parent.getPaddingLeft();
        int innerTop = parent.getPaddingTop();
        int innerRight = parent.getWidth() - parent.getPaddingRight();
        int innerBottom = parent.getHeight() - parent.getPaddingBottom();
        boolean scrolls = parent instanceof ScrollView || parent instanceof HorizontalScrollView
            || parent instanceof AbsListView;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            if (!scrolls && (child.getLeft() < innerLeft || child.getTop() < innerTop
                || child.getRight() > innerRight || child.getBottom() > innerBottom)) {
                out.add(where + ": " + child.getClass().getSimpleName() + " ["
                    + child.getLeft() + "," + child.getTop() + "," + child.getRight() + ","
                    + child.getBottom() + "] outside "
                    + parent.getClass().getSimpleName() + " " + innerLeft + "," + innerTop + ","
                    + innerRight + "," + innerBottom);
            }
            if (child instanceof ViewGroup) collectClips((ViewGroup) child, where, out);
        }
    }
}
