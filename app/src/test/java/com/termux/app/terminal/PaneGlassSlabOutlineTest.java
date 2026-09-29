package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

/**
 * What a copy of the glass may cover. The window-switch card once painted the blurred frame over
 * the whole terminal rectangle; it now clips to this outline, which is the panes' rounded slabs
 * and nothing around them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class PaneGlassSlabOutlineTest {

    private static FrameLayout pane(Activity activity, FrameLayout host, int l, int t, int r, int b,
                                    int backdropVisibility) {
        FrameLayout frame = new FrameLayout(activity);
        PaneGlassBackdropView backdrop = new PaneGlassBackdropView(activity);
        backdrop.setId(R.id.terminal_pane_glass);
        backdrop.setVisibility(backdropVisibility);
        frame.addView(backdrop);
        host.addView(frame);
        frame.layout(l, t, r, b);
        return frame;
    }

    @Test
    public void theOutlineIsTheSlabsRoundedRectsNotTheHostRectangle() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host);
        host.layout(0, 0, 1000, 1000);
        FrameLayout a = pane(activity, host, 20, 20, 480, 980, View.VISIBLE);
        FrameLayout b = pane(activity, host, 520, 20, 980, 980, View.VISIBLE);

        Path outline = PaneGlass.slabOutline(Arrays.asList(a, b), host, 60f, new Path());
        RectF bounds = new RectF();
        outline.computeBounds(bounds, true);

        assertTrue(bounds.left >= 20f && bounds.right <= 980f);
        // Inside a pane and in the gap between them: the gap is not glass.
        assertTrue(contains(outline, 250f, 500f));
        assertFalse(contains(outline, 500f, 500f));
        // The rounded corner of a pane and the host's own corner are not glass either.
        assertFalse(contains(outline, 21f, 21f));
        assertFalse(contains(outline, 5f, 5f));
    }

    @Test
    public void aPaneWithoutASlabAddsNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host);
        host.layout(0, 0, 1000, 1000);
        FrameLayout bare = pane(activity, host, 20, 20, 480, 980, View.GONE);

        Path outline = PaneGlass.slabOutline(Collections.singletonList(bare), host, 60f, new Path());

        assertTrue(outline.isEmpty());
    }

    private static boolean contains(Path path, float x, float y) {
        android.graphics.Region clip = new android.graphics.Region(0, 0, 1000, 1000);
        android.graphics.Region region = new android.graphics.Region();
        region.setPath(path, clip);
        return region.contains((int) x, (int) y);
    }
}
