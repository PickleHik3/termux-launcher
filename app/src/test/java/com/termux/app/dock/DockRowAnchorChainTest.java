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
 * pins down what RelativeLayout does when links in that chain are GONE — extra keys off, apps row
 * off, keyboard closed — with and without {@code alignWithParentIfMissing}, because the difference
 * is a dock whose rows sit at its top edge instead of its bottom, or letters sitting under the
 * keyboard instead of on the glass above it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DockRowAnchorChainTest {

    private static final int STACK_HEIGHT = 400;
    private static final int ROW_HEIGHT = 40;
    private static final int BAND_HEIGHT = 3;
    private static final int KEYBOARD_HEIGHT = 200;

    @Test
    public void rowsWithAWhollyGoneAnchorChainLandAtTheTopWithoutTheFallback() {
        Stack stack = build(false, true, false, false);
        // Both rows collapse onto the top edge, one over the other: the letters land in the middle
        // of the icon row and the pair sits on the dock's top border.
        assertEquals(0, stack.az.getTop());
        assertEquals(0, stack.apps.getTop());
    }

    @Test
    public void alignWithParentIfMissingKeepsTheRowsOnTheBottomEdge() {
        Stack stack = build(true, true, false, false);
        assertEquals(STACK_HEIGHT, stack.az.getBottom());
        assertEquals(STACK_HEIGHT - ROW_HEIGHT - BAND_HEIGHT, stack.apps.getBottom());
    }

    /**
     * The letters' own anchor is the extra-keys row. With the extra keys off, the chain has to walk
     * through it to the keyboard rather than fall back to the parent — a fallback here would put
     * the letters at the bottom of the stack, which is behind the keyboard.
     */
    @Test
    public void theLettersRideAboveTheKeyboardWhenTheExtraKeysAreOff() {
        Stack stack = build(true, true, false, true);
        assertEquals(STACK_HEIGHT - KEYBOARD_HEIGHT, stack.az.getBottom());
        assertEquals(STACK_HEIGHT - KEYBOARD_HEIGHT - ROW_HEIGHT - BAND_HEIGHT,
            stack.apps.getBottom());
    }

    /** With the extra keys on, the letters sit on the row above them, keyboard or no keyboard. */
    @Test
    public void theLettersSitOnTheExtraKeysRowWhenItIsShown() {
        assertEquals(STACK_HEIGHT - ROW_HEIGHT,
            build(true, true, true, false).az.getBottom());
        assertEquals(STACK_HEIGHT - KEYBOARD_HEIGHT - ROW_HEIGHT,
            build(true, true, true, true).az.getBottom());
    }

    /**
     * The Alphabets bar standing alone — no apps row above it, no extra keys below it. It is the
     * dock's only row, so it must be flush on the glass's bottom edge in both keyboard states;
     * nothing above it exists to hold it up.
     */
    @Test
    public void theLettersStandingAloneStayFlushOnTheDocksBottomEdge() {
        assertEquals(STACK_HEIGHT, build(true, false, false, false).az.getBottom());
        assertEquals(STACK_HEIGHT - KEYBOARD_HEIGHT, build(true, false, false, true).az.getBottom());
    }

    private static Stack build(boolean alignWithParentIfMissing, boolean appsShown,
                               boolean extraKeysShown, boolean keyboardShown) {
        Context context = ApplicationProvider.getApplicationContext();
        RelativeLayout root = new RelativeLayout(context);
        Stack stack = new Stack(root, new View(context), new View(context), new View(context),
            new View(context), new View(context));
        stack.apps.setId(View.generateViewId());
        stack.band.setId(View.generateViewId());
        stack.az.setId(View.generateViewId());
        stack.toolbar.setId(View.generateViewId());
        stack.keyboard.setId(View.generateViewId());
        // The band is the air between the two rows: it is only ever shown with both of them.
        stack.apps.setVisibility(appsShown ? View.VISIBLE : View.GONE);
        stack.band.setVisibility(appsShown ? View.VISIBLE : View.GONE);
        stack.toolbar.setVisibility(extraKeysShown ? View.VISIBLE : View.GONE);
        stack.keyboard.setVisibility(keyboardShown ? View.VISIBLE : View.GONE);

        root.addView(stack.apps,
            above(stack.band.getId(), ROW_HEIGHT, alignWithParentIfMissing));
        root.addView(stack.band,
            above(stack.az.getId(), BAND_HEIGHT, alignWithParentIfMissing));
        root.addView(stack.az,
            above(stack.toolbar.getId(), ROW_HEIGHT, alignWithParentIfMissing));
        root.addView(stack.toolbar,
            above(stack.keyboard.getId(), ROW_HEIGHT, true));
        RelativeLayout.LayoutParams keyboardParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, KEYBOARD_HEIGHT);
        keyboardParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        root.addView(stack.keyboard, keyboardParams);

        root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(STACK_HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 600, STACK_HEIGHT);
        return stack;
    }

    private static RelativeLayout.LayoutParams above(int anchorId, int height,
                                                     boolean alignWithParentIfMissing) {
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, height);
        params.addRule(RelativeLayout.ABOVE, anchorId);
        params.alignWithParent = alignWithParentIfMissing;
        return params;
    }

    private static final class Stack {
        final RelativeLayout root;
        final View apps;
        final View band;
        final View az;
        final View toolbar;
        final View keyboard;

        Stack(RelativeLayout root, View apps, View band, View az, View toolbar, View keyboard) {
            this.root = root;
            this.apps = apps;
            this.band = band;
            this.az = az;
            this.toolbar = toolbar;
            this.keyboard = keyboard;
        }
    }
}
