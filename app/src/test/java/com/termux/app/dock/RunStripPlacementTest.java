package com.termux.app.dock;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The run strip stands on the dock, or on the stack's top while a keyboard is up (the dock riding
 * on it), whatever transform the launcher wore when it was placed (pass 4): the editor's frame
 * scaled to 0.6 while a Dock style was picked left the strip mid-screen after Done with Home's
 * keyboard always on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class RunStripPlacementTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2400;
    /** The dock's rows and the keyboard under them, laid at the screen's bottom. */
    private static final int STACK_PX = 864;
    private static final int PAGE_TOP_PX = 40;

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    /** root > frame (the editor's) > { page > layer, stack at the bottom }, laid out. */
    private static final class Tree {
        final FrameLayout root = new FrameLayout(context());
        final FrameLayout frame = new FrameLayout(context());
        final FrameLayout page = new FrameLayout(context());
        final FrameLayout layer = new FrameLayout(context());
        final View stack = new View(context());

        Tree() {
            root.addView(frame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams pageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            pageParams.topMargin = PAGE_TOP_PX;
            frame.addView(page, pageParams);
            page.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            frame.addView(stack, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                STACK_PX, Gravity.BOTTOM));
            root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, WIDTH, HEIGHT);
        }
    }

    @Test
    public void laidTopIgnoresTheEditorFramesScaleAndLift() {
        Tree tree = new Tree();
        tree.frame.setPivotY(0f);
        tree.frame.setScaleX(0.6f);
        tree.frame.setScaleY(0.6f);
        tree.frame.setTranslationY(120f);
        tree.page.setTranslationX(300f);
        assertEquals(PAGE_TOP_PX, RunStripPlacement.laidTopIn(tree.root, tree.layer));
        assertEquals(HEIGHT - STACK_PX, RunStripPlacement.laidTopIn(tree.root, tree.stack));
    }

    @Test
    public void laidTopLessesTheParentsScroll() {
        Tree tree = new Tree();
        tree.page.scrollTo(0, 25);
        assertEquals(PAGE_TOP_PX - 25, RunStripPlacement.laidTopIn(tree.root, tree.layer));
    }

    @Test
    public void aViewNotUnderTheAncestorHasNoLaidTop() {
        Tree tree = new Tree();
        View loose = new View(context());
        assertEquals(RunStripPlacement.NOT_UNDER, RunStripPlacement.laidTopIn(tree.root, loose));
        assertEquals(RunStripPlacement.NOT_UNDER,
            RunStripPlacement.laidTopIn(tree.page, tree.stack));
    }

    @Test
    public void theStripStandsTheBandAboveTheKeyboardsTopWhileOneIsUp() {
        Tree tree = new Tree();
        // Placed while the frame is scaled: the edges come from the laid-out tops all the same.
        tree.frame.setPivotY(0f);
        tree.frame.setScaleY(0.6f);
        int layerTop = RunStripPlacement.laidTopIn(tree.root, tree.layer);
        int stackTop = RunStripPlacement.laidTopIn(tree.root, tree.stack);
        int keyboardTopInLayer = stackTop - layerTop;
        int dockTopInLayer = HEIGHT - 300 - layerTop;
        int band = 131;
        float y = RunStripPlacement.translationYPx(dockTopInLayer, keyboardTopInLayer, band, 0);
        // On screen at rest: the layer's top plus the translation is the band above the stack.
        assertEquals(HEIGHT - STACK_PX - band, layerTop + y, 0f);
    }

    @Test
    public void theStripStandsTheBandAboveTheDockWithTheKeyboardDown() {
        assertEquals(2000 - 131 - 12f,
            RunStripPlacement.translationYPx(2000, -1, 131, 12), 0f);
        assertEquals(1400 - 131 - 12f,
            RunStripPlacement.translationYPx(2000, 1400, 131, 12), 0f);
        assertEquals(-131f, RunStripPlacement.translationYPx(2000, 0, 131, 0), 0f);
    }
}
