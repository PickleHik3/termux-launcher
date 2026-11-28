package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
 * Round 6 (emulator, 39b): the app drawer's scroll put the keyboard down for real while the drawer
 * held the stack. The stack's writer cut the down keyboard whole; the drawer, closing, handed the
 * keyboard back as it had found it (no cut, no translation), and the writer, trusting what it had
 * last written, wrote nothing: the keyboard's top row stayed on screen along Home's bottom edge.
 * Once the drawer lets go the writer writes its rest whole. Driven through the real writer over a
 * 300 px stack above a 400 px keyboard column, the keyboard laid out and down.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StackRendererLetGoTest {

    private static final int WIDTH = 1080;
    private static final int STACK = 300;
    private static final int KEYBOARD = 400;

    private final SparseArray<View> mViews = new SparseArray<>();
    private int mDownPx;
    private boolean mHeld;
    private int mFootPx;
    private StackRenderer mRenderer;
    private FrameLayout mContainer;
    private KeyboardContainerLayout mKeyboard;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        mContainer = new FrameLayout(context);
        FrameLayout host = new FrameLayout(context);
        FrameLayout rows = new FrameLayout(context);
        FrameLayout column = new FrameLayout(context);
        mKeyboard = new KeyboardContainerLayout(context);
        column.addView(mKeyboard);
        mContainer.addView(host);
        mContainer.addView(rows);
        mContainer.addView(column);
        mContainer.layout(0, 0, WIDTH, STACK + KEYBOARD);
        host.layout(0, 0, WIDTH, STACK);
        rows.layout(0, 0, WIDTH, STACK);
        column.layout(0, STACK, WIDTH, STACK + KEYBOARD);
        mKeyboard.layout(0, 0, WIDTH, KEYBOARD);
        mViews.put(R.id.accessory_stack_container, mContainer);
        mViews.put(R.id.accessory_surface_host, host);
        mViews.put(R.id.accessory_row_stack, rows);
        mViews.put(R.id.accessory_keyboard_column, column);
        mViews.put(R.id.inapp_keyboard_container, mKeyboard);
        mRenderer = new StackRenderer(new StackRenderer.Views() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
            @Override public int keyboardDesiredHeightPx() { return KEYBOARD; }
            @Override public boolean underKeyboardBandsShown() { return false; }
            @Override public boolean keyboardShown() { return mDownPx == 0; }
            @Override public int keyboardDownLaidOutPx() { return mDownPx; }
            @Override public boolean othersOwnStack() { return mHeld; }
            @Override public int keyboardFootPx() { return mFootPx; }
        });
    }

    @Test public void aKeyboardPutDownUnderTheDrawerIsCutWholeAgainWhenTheDrawerLetsGo() {
        // Up, then the drawer takes the stack and its scroll puts the keyboard down for real: the
        // writer still draws a frame of the down rest while held (the keyboard's own request).
        mRenderer.apply();
        mHeld = true;
        mDownPx = KEYBOARD;
        mRenderer.apply();
        Rect cut = mKeyboard.getClipBounds();
        assertNotNull("the down keyboard is cut", cut);
        assertEquals("cut whole: its bottom at its top", 0, cut.bottom);

        // The drawer slides the keyboard band itself, then hands it back as it found it.
        mKeyboard.setTranslationY(120f);
        mKeyboard.setClipBounds(new Rect(0, 40, WIDTH, KEYBOARD));
        mKeyboard.setTranslationY(0f);
        mKeyboard.setClipBounds(null);
        mHeld = false;
        mRenderer.othersLetGo();

        Rect after = mKeyboard.getClipBounds();
        assertNotNull("the writer cuts the down keyboard again", after);
        assertEquals("whole: nothing of it shows", 0, after.bottom);
        assertEquals(KEYBOARD, mContainer.getTranslationY(), 0f);
    }

    @Test public void theFirstFrameAfterTheDrawerLetsGoWritesTheRestWholeWithoutBeingTold() {
        mRenderer.apply();
        mHeld = true;
        mDownPx = KEYBOARD;
        mRenderer.apply();
        mKeyboard.setClipBounds(null);
        mHeld = false;
        // A frame booked by anything else (a geometry pass, a share) after the close.
        mRenderer.apply();
        Rect after = mKeyboard.getClipBounds();
        assertNotNull(after);
        assertEquals(0, after.bottom);
    }

    /**
     * Pong state-2 (build 25dc7048d): Home reached with no drawer involved, the keyboard laid out
     * down, and its top number row peeking under the bar. The pass that laid it out down counted
     * its keys from its own measure and foot (here 340 of a 400 px keyboard with a 48 px foot);
     * the writer reads 352 from the measure it is handed, the share fell short, and the cut left
     * the top of the keys in the band under the pill. At rest with the keyboard laid out down,
     * none of it shows, whatever route reached the rest.
     */
    @Test public void atRestTheKeyboardLaidOutDownIsCutWholeWhenItsCountsDiffer() {
        mFootPx = 48;
        mDownPx = 340;
        mRenderer.apply();
        Rect cut = mKeyboard.getClipBounds();
        assertNotNull("the down keyboard is cut", cut);
        assertEquals("whole: no row of it shows", 0, cut.bottom);
    }
}
