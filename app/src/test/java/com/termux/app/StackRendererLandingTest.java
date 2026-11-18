package com.termux.app;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.place.StackScene;
import com.termux.app.window.FramePipeline;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A crossing's landing frame draws what its last frame at progress 1 drew (#268): the keyboard's
 * rest share is counted once however the landing's frame is asked for, the body surface's cut
 * stands on the side the wall committed to (the front's one owner), and a room set again to the
 * value it holds changes nothing. Docked (no air under the stack) and Floating (a gap), the
 * keyboard up and down on the side arrived at, with and without a keyboard foot.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StackRendererLandingTest {

    private static final int WIDTH = 1080;
    private static final int KEYS = 589;
    private static final int[] FEET = {0, 63};
    private static final int[] PADDINGS = {0, 26};
    private static final int DOCK = 200;

    private final SparseArray<View> mViews = new SparseArray<>();
    private boolean mFrontHome;
    private int mKeyboardDownPx;
    private int mFootPx;
    private StackRenderer mRenderer;
    private FrameLayout mContainer;
    private FrameLayout mKeyboard;
    private FrameLayout mBody;
    private FramePipeline mFrames;

    private void build(int paddingPx, int footPx, boolean keyboardDown) {
        Context context = RuntimeEnvironment.getApplication();
        mViews.clear();
        mFootPx = footPx;
        mKeyboardDownPx = keyboardDown ? KEYS : 0;
        mContainer = new FrameLayout(context);
        mContainer.setPadding(0, 0, 0, paddingPx);
        FrameLayout column = new FrameLayout(context);
        mKeyboard = new FrameLayout(context);
        mContainer.addView(column);
        column.addView(mKeyboard);
        mContainer.layout(0, 0, WIDTH, 1400);
        column.layout(0, 800, WIDTH, 1400);
        mKeyboard.layout(0, 0, WIDTH, KEYS + footPx);
        mViews.put(R.id.accessory_stack_container, mContainer);
        mViews.put(R.id.accessory_keyboard_column, column);
        mViews.put(R.id.inapp_keyboard_container, mKeyboard);
        FrameLayout parent = new FrameLayout(context);
        parent.layout(0, 0, WIDTH, 2000);
        mBody = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        params.bottomMargin = 100;
        parent.addView(mBody, params);
        mBody.layout(0, 0, WIDTH, 1900);
        mViews.put(R.id.terminal_background, mBody);
        mRenderer = new StackRenderer(new StackRenderer.Views() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
            @Override public int keyboardDesiredHeightPx() { return KEYS + mFootPx; }
            @Override public boolean underKeyboardBandsShown() { return false; }
            @Override public boolean keyboardShown() { return false; }
            @Override public int keyboardFootPx() { return mFootPx; }
            @Override public int keyboardDownLaidOutPx() { return mKeyboardDownPx; }
            @Override public boolean frontIsHome() { return mFrontHome; }
        });
        mFrames = new FramePipeline();
        mRenderer.attach(mFrames);
        mRenderer.setRooms(100, 400);
    }

    private static String clip(View v) {
        Rect r = v.getClipBounds();
        return r == null ? "none" : r.toShortString();
    }

    /** What the stack's writer owns on the container, the keyboard and the body surface. */
    private String drawn() {
        return "container ty=" + mContainer.getTranslationY() + " clip=" + clip(mContainer)
            + " keyboard clip=" + clip(mKeyboard) + " body clip=" + clip(mBody);
    }

    /** The crossing's last frame at progress 1, Home to the Terminal, the keyboard laid out. */
    private void lastCrossingFrame(boolean keyboardUp) {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.travel(false, keyboardUp ? 1f : 0f, 1f, KEYS, DOCK, 0, motion);
        mRenderer.setEnds(true, false, 1f);
        mRenderer.setTravel(motion.dockTravelPx, motion.keyboardSharePx, 0f);
        mRenderer.setSink(0f, 0f, 0f, 0f);
        mFrames.flush("test");
    }

    @Test public void theLandingFrameCountsTheKeyboardsRestShareOnce() {
        for (int padding : PADDINGS) for (int foot : FEET) for (boolean down : new boolean[] {
            false, true}) {
            String at = "padding=" + padding + " foot=" + foot + " keyboardDown=" + down;
            build(padding, foot, down);
            mFrontHome = false;
            lastCrossingFrame(!down);
            String last = drawn();
            // The landing's own frame, the crossing's last travel still the writer's motion.
            mRenderer.onLanding();
            mFrames.flush("test");
            assertEquals(at + " landing frame", last, drawn());
            mRenderer.clearEnds();
            mFrames.flush("test");
            assertEquals(at + " clearEnds", last, drawn());
            // And the rest's own shares after it.
            mRenderer.setTravel(0f, 0f, 0f);
            mRenderer.setSink(0f, 0f, 0f, 0f);
            mFrames.flush("test");
            assertEquals(at + " rest", last, drawn());
        }
    }

    @Test public void theBodyCutOnTheLandingFrameIsTheLastCrossingFramesOnTheCommittedSide() {
        for (int foot : FEET) for (boolean down : new boolean[] {false, true}) {
            String at = "foot=" + foot + " keyboardDown=" + down;
            build(0, foot, down);
            mFrontHome = true; // Home is committed while the crossing runs
            lastCrossingFrame(!down);
            String last = clip(mBody);
            assertEquals(at + " cut at p=1", "[-1080,-1900][2160,1600]", last);
            // The wall commits to the Terminal as the slide settles, before the landing's frame.
            mFrontHome = false;
            mRenderer.onLanding();
            mFrames.flush("test");
            assertEquals(at + " landing frame", last, clip(mBody));
            // The same rooms handed again at the landing change nothing.
            mRenderer.setRooms(100, 400);
            mFrames.flush("test");
            assertEquals(at + " same rooms", last, clip(mBody));
            mRenderer.applyAtRest();
            mFrames.flush("test");
            assertEquals(at + " rest", last, clip(mBody));
        }
    }
}
