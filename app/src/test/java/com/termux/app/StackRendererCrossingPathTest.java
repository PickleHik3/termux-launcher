package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.dock.SideRowViews;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.CrossingPath;
import com.termux.app.place.EdgeStackView;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.RestCards.CardId;
import com.termux.app.place.RestCards.StackView;
import com.termux.app.place.StackEnd;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The stack's writer over the crossing rule (CrossingPath): the bands-under apps row glides from
 * the leaving rest's slot to the arriving rest's and stands at 0 at rest; the keyboard rides with
 * the page that has it up, on the docked keyboard's own view or the floating keyboard's host, and
 * stands at 0 at both ends of the crossing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StackRendererCrossingPathTest {

    private static final int WIDTH = 1080;

    private final SparseArray<View> mViews = new SparseArray<>();
    private boolean mFrontHome = true;
    private StackRenderer mRenderer;
    private View mApps;
    private View mKeyboard;
    private View mFloatingHost;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout container = new FrameLayout(context);
        EdgeStackView rows = new EdgeStackView(context);
        rows.setEdge(Edge.BOTTOM);
        EdgeStackView under = new EdgeStackView(context);
        under.setEdge(Edge.BOTTOM);
        mApps = new FrameLayout(context);
        mApps.setId(SideRowViews.id(Element.APPS, SideRowViews.Part.BOTTOM_HOST));
        under.addView(mApps);
        mKeyboard = new FrameLayout(context);
        mFloatingHost = new FrameLayout(context);
        container.addView(rows);
        container.addView(under);
        container.addView(mKeyboard);
        container.layout(0, 0, WIDTH, 600);
        mViews.put(R.id.accessory_stack_container, container);
        mViews.put(R.id.accessory_row_stack, rows);
        mViews.put(R.id.accessory_under_keyboard_stack, under);
        mViews.put(mApps.getId(), mApps);
        mViews.put(R.id.inapp_keyboard_container, mKeyboard);
        mViews.put(R.id.floating_keyboard_host, mFloatingHost);
        mRenderer = new StackRenderer(new StackRenderer.Views() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
            @Override public int keyboardDesiredHeightPx() { return 0; }
            @Override public boolean underKeyboardBandsShown() { return true; }
            @Override public boolean keyboardShown() { return false; }
            @Override public boolean frontIsHome() { return mFrontHome; }
        });
    }

    /** Home's apps row alone under the keyboard; the Terminal's above its extra keys there. */
    private void bandsUnder() {
        StackEnd home = mRenderer.end(true);
        StackEnd terminal = mRenderer.end(false);
        home.rows.set(CrossingPath.APPS, CrossingPath.UNDER, -120f, 120f);
        terminal.rows.set(CrossingPath.EXTRA_KEYS, CrossingPath.UNDER, -98f, 98f);
        terminal.rows.set(CrossingPath.APPS, CrossingPath.UNDER, -218f, 120f);
    }

    @Test public void theBandsUnderAppsRowGlidesToTheArrivingSlotAndRestsAtZero() {
        bandsUnder();
        mRenderer.apply();
        assertEquals(0f, mApps.getTranslationY(), 0f);
        mRenderer.setEnds(true, false, 0f);
        assertEquals(0f, mApps.getTranslationY(), 0f);
        mRenderer.setEnds(true, false, 0.5f);
        float half = mApps.getTranslationY();
        assertEquals(CrossingPath.glideAt(0f, -98f, 0.5f), half, 1e-4f);
        mRenderer.setEnds(true, false, 1f);
        assertEquals("the arriving rest's slot, which the landing lays it out at", -98f,
            mApps.getTranslationY(), 0f);
        // Turned back: the same p, the same frame.
        mRenderer.setEnds(true, false, 0.5f);
        assertEquals(half, mApps.getTranslationY(), 0f);
        mFrontHome = false;
        mRenderer.clearEnds();
        assertEquals("at rest it stands where it is laid out", 0f, mApps.getTranslationY(), 0f);
    }

    private void keyboardUpOnTheTerminal(StackView view) {
        mRenderer.end(false).setCard(view, CardId.KEYBOARD, StackEnd.STACK_SIDE, true,
            new Box(0f, 1700f, WIDTH, 2300f));
    }

    @Test public void theKeyboardLeavesWithTheTerminalSideways() {
        keyboardUpOnTheTerminal(StackView.KEYBOARD_COLUMN);
        mFrontHome = false;
        mRenderer.apply();
        assertEquals(0f, mKeyboard.getTranslationX(), 0f);
        // Terminal to Home: the Terminal leaves to the right, the keyboard with it.
        mRenderer.setEnds(false, true, 0.5f);
        assertEquals(WIDTH / 2f, mKeyboard.getTranslationX(), 0f);
        assertEquals("the floating host is not this keyboard's", 0f,
            mFloatingHost.getTranslationX(), 0f);
        // The wall's own direction wins.
        mRenderer.setEnds(false, true, 0.25f,
            com.termux.app.place.KeyboardColumn.LEAVES_LEFT);
        assertEquals(-WIDTH / 4f, mKeyboard.getTranslationX(), 0f);
        mRenderer.setEnds(false, true, 1f);
        assertEquals("the landing's frame is the rest's", 0f, mKeyboard.getTranslationX(), 0f);
        mRenderer.clearEnds();
        assertEquals(0f, mKeyboard.getTranslationX(), 0f);
    }

    @Test public void theKeyboardArrivesWithTheTerminalAndStaysWhereBothHaveItUp() {
        keyboardUpOnTheTerminal(StackView.KEYBOARD_COLUMN);
        mRenderer.setEnds(true, false, 0.75f);
        assertEquals("in from the right with the Terminal", WIDTH / 4f,
            mKeyboard.getTranslationX(), 0f);
        mRenderer.end(true).setCard(StackView.KEYBOARD_COLUMN, CardId.KEYBOARD,
            StackEnd.STACK_SIDE, true, new Box(0f, 1700f, WIDTH, 2300f));
        mRenderer.setEnds(true, false, 0.5f);
        assertEquals("one keyboard up at both ends: the keys do not move", 0f,
            mKeyboard.getTranslationX(), 0f);
    }

    @Test public void theFloatingKeyboardRidesOnItsHost() {
        keyboardUpOnTheTerminal(StackView.FLOATING_KEYBOARD);
        mRenderer.setEnds(false, true, 0.5f);
        assertEquals(WIDTH / 2f, mFloatingHost.getTranslationX(), 0f);
        assertEquals(0f, mKeyboard.getTranslationX(), 0f);
        mRenderer.setEnds(false, true, 1f);
        assertEquals(0f, mFloatingHost.getTranslationX(), 0f);
    }

    @Test public void homeToHomeHasTwoEndsAndTheKeyboardRidesWithItsPage() {
        keyboardUpOnTheTerminal(StackView.KEYBOARD_COLUMN);
        mFrontHome = true;
        // Both Home pages as Home's rest: one keyboard state, nothing moves.
        mRenderer.setEnds(true, true, 0.5f, com.termux.app.place.KeyboardColumn.LEAVES_LEFT);
        assertEquals(0f, mKeyboard.getTranslationX(), 0f);
        // The leaving page has its keyboard up, the arriving one down: it leaves with its page.
        mRenderer.setHomePageKeyboards(StackEnd.PAGE_KEYBOARD_UP, StackEnd.PAGE_KEYBOARD_DOWN);
        assertEquals(-WIDTH / 2f, mKeyboard.getTranslationX(), 0f);
        assertTrue("two distinct ends", mRenderer.end(true) != mRenderer.arrivingEnd(true, true));
        // The other way: it arrives with the arriving page, from the right.
        mRenderer.setHomePageKeyboards(StackEnd.PAGE_KEYBOARD_DOWN, StackEnd.PAGE_KEYBOARD_UP);
        mRenderer.setEnds(true, true, 0.75f, com.termux.app.place.KeyboardColumn.LEAVES_LEFT);
        assertEquals(WIDTH / 4f, mKeyboard.getTranslationX(), 0f);
        mRenderer.onLanding();
        assertEquals("the landing's rest moves nothing", 0f, mKeyboard.getTranslationX(), 0f);
        assertEquals(StackEnd.PAGE_KEYBOARD_REST, mRenderer.end(true).pageKeyboard);
    }
}
