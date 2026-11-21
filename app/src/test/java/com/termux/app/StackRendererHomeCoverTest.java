package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.inappkeyboard.KeyboardContainerLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The keyboard over Home's dock (#309), through the renderer: Home's host and rows are drawn at
 * their keyboard-down screen rect at every reveal of the keyboard, the part under the keyboard's
 * top is cut, the Terminal's stack is as before, and the system keyboard does not move Home's
 * container. The geometry: a 300 px stack (host and rows) above a 400 px keyboard column.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StackRendererHomeCoverTest {

    private static final int WIDTH = 1080;
    private static final int STACK = 300;
    private static final int KEYBOARD = 400;

    private final SparseArray<View> mViews = new SparseArray<>();
    private boolean mFrontHome;
    private int mDownPx;
    private StackRenderer mRenderer;
    private FrameLayout mContainer;
    private FrameLayout mHost;
    private FrameLayout mRows;
    private KeyboardContainerLayout mKeyboard;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        mContainer = new FrameLayout(context);
        mHost = new FrameLayout(context);
        mRows = new FrameLayout(context);
        FrameLayout column = new FrameLayout(context);
        mKeyboard = new KeyboardContainerLayout(context);
        column.addView(mKeyboard);
        mContainer.addView(mHost);
        mContainer.addView(mRows);
        mContainer.addView(column);
        mContainer.layout(0, 0, WIDTH, STACK + KEYBOARD);
        mHost.layout(0, 0, WIDTH, STACK);
        mRows.layout(0, 0, WIDTH, STACK);
        column.layout(0, STACK, WIDTH, STACK + KEYBOARD);
        mKeyboard.layout(0, 0, WIDTH, KEYBOARD);
        mViews.put(R.id.accessory_stack_container, mContainer);
        mViews.put(R.id.accessory_surface_host, mHost);
        mViews.put(R.id.accessory_row_stack, mRows);
        mViews.put(R.id.accessory_keyboard_column, column);
        mViews.put(R.id.inapp_keyboard_container, mKeyboard);
        mRenderer = new StackRenderer(new StackRenderer.Views() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
            @Override public int keyboardDesiredHeightPx() { return KEYBOARD; }
            @Override public boolean underKeyboardBandsShown() { return false; }
            @Override public boolean keyboardShown() { return false; }
            @Override public int keyboardDownLaidOutPx() { return mDownPx; }
            @Override public boolean frontIsHome() { return mFrontHome; }
        });
    }

    private float screenOffset(View layer) {
        return mContainer.getTranslationY() + layer.getTranslationY();
    }

    @Test public void homesRowsStandAtTheirKeyboardDownRectAtEveryReveal() {
        mFrontHome = true;
        mRenderer.setHomeCover(true, 0);
        for (float reveal : new float[] {0f, 0.5f, 1f}) {
            mDownPx = Math.round((1f - reveal) * KEYBOARD);
            mRenderer.apply();
            String label = "reveal=" + reveal;
            assertEquals(label, mDownPx, mContainer.getTranslationY(), 0f);
            assertEquals(label, KEYBOARD, screenOffset(mHost), 0f);
            assertEquals(label, KEYBOARD, screenOffset(mRows), 0f);
            int cut = KEYBOARD - mDownPx;
            Rect clip = mHost.getClipBounds();
            if (cut == 0) {
                assertNull(label + " keyboard down: nothing under it", clip);
            } else {
                assertNotNull(label, clip);
                assertEquals(label + " cut at the keyboard's top", Math.max(0, STACK - cut),
                    clip.bottom);
                assertEquals(label, Math.max(0, STACK - cut), mRows.getClipBounds().bottom);
            }
        }
    }

    @Test public void theTerminalsStackIsAsBefore() {
        mFrontHome = false;
        mRenderer.setHomeCover(true, 0);
        for (float reveal : new float[] {0f, 0.5f, 1f}) {
            mDownPx = Math.round((1f - reveal) * KEYBOARD);
            mRenderer.apply();
            assertEquals(mDownPx, mContainer.getTranslationY(), 0f);
            assertEquals(0f, mHost.getTranslationY(), 0f);
            assertEquals(0f, mRows.getTranslationY(), 0f);
            assertNull(mHost.getClipBounds());
        }
    }

    @Test public void withTheCoverOffHomeIsAsBefore() {
        mFrontHome = true;
        mDownPx = 200;
        mRenderer.apply();
        assertEquals(200f, mContainer.getTranslationY(), 0f);
        assertEquals(0f, mHost.getTranslationY(), 0f);
        assertNull(mHost.getClipBounds());
    }

    @Test public void theSystemKeyboardDoesNotMoveHomesContainer() {
        mFrontHome = true;
        mRenderer.setHomeCover(true, 120);
        mRenderer.setTravel(0f, 0f, 30f);
        // The root's padding lifted the layout by 120 and the ride by 30; the draw offset stands
        // both back down, so the stack is where it is with the keyboard away.
        assertEquals(120f, mContainer.getTranslationY(), 0f);
        mRenderer.setTravel(0f, 0f, 0f);
        assertEquals(120f, mContainer.getTranslationY(), 0f);
    }
}
