package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import android.app.Application;

/** Moving a divider re-lays out the split alone and leaves the window's layout untouched. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class SplitRelayoutTest {

    private static final int W = 400;
    private static final int H = 1010;
    private static final int GAP = 10;

    private static final class Fixture {
        final FrameLayout outer;
        final LinearLayout split;
        final View a;
        final View b;

        Fixture(Activity activity, boolean laidOut) {
            Context c = activity;
            outer = new FrameLayout(c);
            split = new LinearLayout(c);
            split.setOrientation(LinearLayout.VERTICAL);
            a = new View(c);
            b = new View(c);
            split.addView(a, new LinearLayout.LayoutParams(-1, 0, 1f));
            split.addView(new View(c), new LinearLayout.LayoutParams(-1, GAP));
            split.addView(b, new LinearLayout.LayoutParams(-1, 0, 1f));
            outer.addView(split, new FrameLayout.LayoutParams(-1, -1));
            activity.setContentView(outer);
            if (laidOut) {
                View decor = activity.getWindow().getDecorView();
                decor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
                decor.layout(0, 0, W, H);
            }
        }
    }

    @Test
    public void weightsMoveTheChildrenWithoutARequestAboveTheSplit() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Fixture f = new Fixture(activity, true);
        // Drain anything the attach queued so the state under test is a clean tree.
        assertFalse(f.outer.isLayoutRequested());
        assertFalse(f.split.isLayoutRequested());
        assertEquals((H - GAP) / 2, f.a.getHeight());

        SplitRelayout.applyWeights(f.split, 3f, 1f);

        assertEquals(750, f.a.getHeight());
        assertEquals(250, f.b.getHeight());
        assertEquals(750 + GAP, f.b.getTop());
        assertFalse(f.split.isLayoutRequested());
        assertFalse(f.outer.isLayoutRequested());
    }

    @Test
    public void aSplitThatWasNeverLaidOutFallsBackToARequest() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Fixture f = new Fixture(activity, false);
        f.outer.layout(0, 0, 0, 0);

        SplitRelayout.applyWeights(f.split, 3f, 1f);

        assertTrue(f.a.isLayoutRequested());
        assertEquals(3f, ((LinearLayout.LayoutParams) f.a.getLayoutParams()).weight, 0f);
    }

    @Test
    public void aPendingRequestAboveTheSplitKeepsTheOrdinaryPath() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Fixture f = new Fixture(activity, true);
        f.outer.requestLayout();

        SplitRelayout.applyWeights(f.split, 3f, 1f);

        assertTrue(f.a.isLayoutRequested());
        assertEquals((H - GAP) / 2, f.a.getHeight());
    }
}
