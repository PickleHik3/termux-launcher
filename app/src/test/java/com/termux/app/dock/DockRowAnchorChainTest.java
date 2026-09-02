package com.termux.app.dock;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/**
 * The dock rows are a chain of {@code layout_above} rules ending at the keyboard container. This
 * pins down what RelativeLayout does when every link below a row is GONE — extra keys off, keyboard
 * closed — with and without {@code alignWithParentIfMissing}, because the difference is a dock whose
 * rows sit at its top edge instead of its bottom.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DockRowAnchorChainTest {

    private static final int STACK_HEIGHT = 400;
    private static final int ROW_HEIGHT = 40;

    @Test
    public void rowsWithAWhollyGoneAnchorChainLandAtTheTopWithoutTheFallback() {
        RelativeLayout stack = build(false);
        View apps = stack.getChildAt(0);
        View az = stack.getChildAt(1);
        // Both rows collapse onto the top edge, one over the other: the letters land in the middle
        // of the icon row and the pair sits on the dock's top border.
        assertEquals(0, az.getTop());
        assertEquals(0, apps.getTop());
    }

    @Test
    public void alignWithParentIfMissingKeepsTheRowsOnTheBottomEdge() {
        RelativeLayout stack = build(true);
        View apps = stack.getChildAt(0);
        View az = stack.getChildAt(1);
        assertEquals(STACK_HEIGHT, az.getBottom());
        assertEquals(STACK_HEIGHT - ROW_HEIGHT, apps.getBottom());
    }

    private static RelativeLayout build(boolean alignWithParentIfMissing) {
        Context context = ApplicationProvider.getApplicationContext();
        RelativeLayout stack = new RelativeLayout(context);
        View apps = new View(context);
        apps.setId(View.generateViewId());
        View az = new View(context);
        az.setId(View.generateViewId());
        View toolbar = new View(context);
        toolbar.setId(View.generateViewId());
        toolbar.setVisibility(View.GONE);
        View keyboard = new View(context);
        keyboard.setId(View.generateViewId());
        keyboard.setVisibility(View.GONE);

        RelativeLayout.LayoutParams appsParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, ROW_HEIGHT);
        appsParams.addRule(RelativeLayout.ABOVE, az.getId());
        appsParams.alignWithParent = alignWithParentIfMissing;
        RelativeLayout.LayoutParams azParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, ROW_HEIGHT);
        azParams.addRule(RelativeLayout.ABOVE, toolbar.getId());
        azParams.alignWithParent = alignWithParentIfMissing;
        RelativeLayout.LayoutParams toolbarParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, ROW_HEIGHT);
        toolbarParams.addRule(RelativeLayout.ABOVE, keyboard.getId());
        toolbarParams.alignWithParent = true;
        RelativeLayout.LayoutParams keyboardParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        keyboardParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);

        stack.addView(apps, appsParams);
        stack.addView(az, azParams);
        stack.addView(toolbar, toolbarParams);
        stack.addView(keyboard, keyboardParams);
        stack.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(STACK_HEIGHT, View.MeasureSpec.EXACTLY));
        stack.layout(0, 0, 600, STACK_HEIGHT);
        return stack;
    }
}
