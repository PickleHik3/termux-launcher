package com.termux.app.statusbar;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The pinned cards scroll. Two are on screen whatever matched; a vertical swipe on them moves the
 * run a card at a time and a thin line down the right edge says where in it those two sit. The
 * swipe is the cards' own — the status bar's fold reads {@code canScrollVertically} at DOWN and
 * the drag asks the parent chain not to intercept — and a swipe anywhere else never moves them.
 *
 * <p>A sideways swipe dismisses the card under it, through a four-second undo; there is no
 * dismiss control on the card any more, so a tap anywhere on it opens it.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class PinnedNotificationsViewTest {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 68;

    @Test
    public void oneMatchKeepsTheWholeSlotAndNothingScrolls() {
        PinnedNotificationsView view = view(1);
        assertEquals(HEIGHT, PinnedNotificationsView.cardHeightPx(1, HEIGHT, gap(view)), .01f);
        assertEquals(0f, PinnedNotificationsView.maxScrollPx(1, HEIGHT, gap(view)), .01f);
        assertFalse(view.canScrollVertically(1));
        assertFalse(view.canScrollVertically(-1));
        assertNull(view.scrollLineTrack());
    }

    @Test
    public void twoMatchesShareTheSlotWithNothingLeftToScroll() {
        PinnedNotificationsView view = view(2);
        float gap = gap(view);
        assertEquals((HEIGHT - gap) / 2f, PinnedNotificationsView.cardHeightPx(2, HEIGHT, gap), .01f);
        assertEquals(0f, PinnedNotificationsView.maxScrollPx(2, HEIGHT, gap), .01f);
        assertFalse(view.canScrollVertically(1));
        assertNull("nothing to indicate while both are on screen", view.scrollLineTrack());
    }

    @Test
    public void fiveMatchesScrollThreeCardsWorthAndNoFurther() {
        PinnedNotificationsView view = view(5);
        float gap = gap(view);
        float step = PinnedNotificationsView.stepPx(5, HEIGHT, gap);
        float max = PinnedNotificationsView.maxScrollPx(5, HEIGHT, gap);
        assertEquals("five cards, two shown, three to travel", 3f * step, max, .01f);

        assertTrue(view.canScrollVertically(1));
        assertFalse(view.canScrollVertically(-1));

        drag(view, -500f);
        assertEquals("the offset clamps at the end of the run", max, view.scrollPx(), .01f);
        assertFalse(view.canScrollVertically(1));
        assertTrue(view.canScrollVertically(-1));

        drag(view, 500f);
        assertEquals("and at the start of it", 0f, view.scrollPx(), .01f);
    }

    @Test
    public void aReleasedDragSnapsToWholeCards() {
        // The pure rule first: a flick of a quarter of a card takes the next one, anything
        // further lands on whichever boundary it ended nearest, and the ends hold.
        assertEquals(0f, PinnedNotificationsView.snapTargetPx(0f, 10f, 100f, 300f), .01f);
        assertEquals(100f, PinnedNotificationsView.snapTargetPx(0f, 30f, 100f, 300f), .01f);
        assertEquals(300f, PinnedNotificationsView.snapTargetPx(0f, 260f, 100f, 300f), .01f);
        assertEquals(0f, PinnedNotificationsView.snapTargetPx(100f, 60f, 100f, 300f), .01f);
        assertEquals(300f, PinnedNotificationsView.snapTargetPx(300f, 340f, 100f, 300f), .01f);

        PinnedNotificationsView view = view(5);
        float step = PinnedNotificationsView.stepPx(5, HEIGHT, gap(view));
        dragAndRelease(view, -step * .6f);
        assertEquals("one card per swipe", step, view.scrollTargetPx(), .01f);
        assertEquals(1, view.firstVisibleIndex());
    }

    @Test
    public void theScrollLineAppearsOnlyPastTwoMatchesAndFollowsTheOffset() {
        PinnedNotificationsView view = view(5);
        RectF track = view.scrollLineTrack();
        RectF thumb = view.scrollLineThumb();
        assertNotNull(track);
        assertNotNull(thumb);
        assertEquals("2dp of line", 2f * density(view), track.width(), .01f);
        assertTrue("inside the view", track.right <= WIDTH && track.left > WIDTH - 12f);
        assertTrue("the thumb is a share of the track", thumb.height() < track.height());
        assertEquals("two of five", track.height() * 2f / 5f, thumb.height(), 1f);
        assertEquals("at the top of the run it sits at the top", track.top, thumb.top, .01f);

        drag(view, -500f);
        thumb = view.scrollLineThumb();
        assertNotNull(thumb);
        assertEquals("at the end of the run it sits at the bottom", track.bottom, thumb.bottom, .51f);
    }

    @Test
    public void aVerticalDragTakesTheStreamAndAsksTheParentNotToIntercept() {
        RecordingParent parent = new RecordingParent(context());
        PinnedNotificationsView view = new PinnedNotificationsView(context());
        parent.addView(view);
        view.setItems(items(5));
        measure(view);

        assertTrue(view.onTouchEvent(event(MotionEvent.ACTION_DOWN, WIDTH / 2f, HEIGHT / 2f)));
        assertFalse("a press alone claims nothing", parent.disallowed);
        assertTrue(view.onTouchEvent(event(MotionEvent.ACTION_MOVE, WIDTH / 2f, HEIGHT / 2f - 30f)));
        assertTrue("the drag is the cards' own", parent.disallowed);
        assertTrue("and the bar's fold is told so at DOWN", view.canScrollVertically(1));
        view.onTouchEvent(event(MotionEvent.ACTION_UP, WIDTH / 2f, HEIGHT / 2f - 30f));
    }

    @Test
    public void aSidewaysDragOnACardIsItsOwnAndNeverScrollsTheRun() {
        RecordingParent parent = new RecordingParent(context());
        PinnedNotificationsView view = new PinnedNotificationsView(context());
        parent.addView(view);
        view.setItems(items(5));
        measure(view);

        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, WIDTH / 2f, HEIGHT / 4f));
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, WIDTH / 2f + 60f, HEIGHT / 4f));
        assertTrue("a drag along the bar is the card's swipe: neither fold nor wall takes it",
            parent.disallowed);
        assertEquals(0f, view.scrollPx(), .01f);
        assertTrue("and ancestors that page sideways are told so", view.canScrollHorizontally(1));
    }

    @Test
    public void dismissingShortensTheRunAndClampsTheOffset() {
        PinnedNotificationsView view = view(5);
        drag(view, -500f);
        float step = PinnedNotificationsView.stepPx(5, HEIGHT, gap(view));
        assertEquals(3f * step, view.scrollPx(), .01f);

        view.setItems(items(3));
        assertEquals("three matches leave one card of travel", step, view.scrollPx(), .01f);
        assertFalse(view.canScrollVertically(1));
        assertTrue(view.canScrollVertically(-1));

        view.setItems(items(2));
        assertEquals(0f, view.scrollPx(), .01f);
        assertNull(view.scrollLineTrack());
    }

    @Test
    public void theContentDescriptionSaysWhichTwoAreShowing() {
        PinnedNotificationsView view = view(2);
        assertEquals("Pinned notifications", String.valueOf(view.getContentDescription()));

        view = view(5);
        assertEquals("Pinned notifications, showing 1 and 2 of 5",
            String.valueOf(view.getContentDescription()));
        dragAndRelease(view, -PinnedNotificationsView.stepPx(5, HEIGHT, gap(view)) * .6f);
        assertEquals("Pinned notifications, showing 2 and 3 of 5",
            String.valueOf(view.getContentDescription()));
    }

    @Test
    public void aTapOpensTheCardItLandedOn() {
        PinnedNotificationsView view = view(5);
        List<PinnedNotification> opened = new ArrayList<>();
        view.setOpenListener(opened::add);
        draw(view);

        float cardCentreY = PinnedNotificationsView.cardHeightPx(5, HEIGHT, gap(view)) / 2f;
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, WIDTH - 12f, cardCentreY));
        view.onTouchEvent(event(MotionEvent.ACTION_UP, WIDTH - 12f, cardCentreY));
        assertEquals("the whole card opens, where the cross used to be too", 1, opened.size());
        assertEquals("key0", opened.get(0).key);
    }

    @Test
    public void aSwipePastTheThresholdOffersTheUndoAndDismissesOnlyWhenItRunsOut() {
        reduceMotion();
        PinnedNotificationsView view = view(2);
        List<PinnedNotification> dismissed = new ArrayList<>();
        view.setListener(dismissed::add);
        float y = PinnedNotificationsView.cardHeightPx(2, HEIGHT, gap(view)) / 2f;

        swipe(view, y, WIDTH * .5f);
        assertNotNull("the card's place offers the undo", view.pendingDismiss());
        assertEquals("key0", view.pendingDismiss().key);
        assertTrue("nothing is dismissed while the undo is on offer", dismissed.isEmpty());

        view.commitPendingDismiss();
        assertEquals(1, dismissed.size());
        assertEquals("key0", dismissed.get(0).key);
        assertNull(view.pendingDismiss());
    }

    @Test
    public void aShortSwipeSpringsBack() {
        reduceMotion();
        PinnedNotificationsView view = view(2);
        float y = PinnedNotificationsView.cardHeightPx(2, HEIGHT, gap(view)) / 2f;
        swipe(view, y, -WIDTH * .2f);
        assertNull("under a third of the width and no fling: back into place",
            view.pendingDismiss());
    }

    @Test
    public void undoBringsTheCardBackAndDismissesNothing() {
        reduceMotion();
        PinnedNotificationsView view = view(2);
        List<PinnedNotification> dismissed = new ArrayList<>();
        view.setListener(dismissed::add);
        float y = PinnedNotificationsView.cardHeightPx(2, HEIGHT, gap(view)) / 2f;
        swipe(view, y, -WIDTH * .6f);
        assertNotNull(view.pendingDismiss());

        // A tap on the undo row takes the undo.
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, 60f, y));
        view.onTouchEvent(event(MotionEvent.ACTION_UP, 60f, y));
        assertNull(view.pendingDismiss());
        view.commitPendingDismiss();
        assertTrue(dismissed.isEmpty());
    }

    @Test
    public void aSecondDismissalCarriesOutTheFirst() {
        PinnedNotificationsView view = view(2);
        List<PinnedNotification> dismissed = new ArrayList<>();
        view.setListener(dismissed::add);
        view.beginPendingDismiss(view.getItems().get(0));
        view.beginPendingDismiss(view.getItems().get(1));
        assertEquals(1, dismissed.size());
        assertEquals("key0", dismissed.get(0).key);
        assertEquals("key1", view.pendingDismiss().key);
    }

    @Test
    public void anUndoWhoseNotificationWentIsDroppedNotCarriedOut() {
        PinnedNotificationsView view = view(3);
        List<PinnedNotification> dismissed = new ArrayList<>();
        view.setListener(dismissed::add);
        view.beginPendingDismiss(view.getItems().get(0));

        view.setItems(items(3).subList(1, 3));
        assertNull(view.pendingDismiss());
        view.commitPendingDismiss();
        assertTrue(dismissed.isEmpty());
    }

    @Test
    public void theLabelReadsSenderMessageAppAndCount() {
        PinnedNotificationsView view = view(1);
        PinnedNotification grouped = new PinnedNotification("k", "com.chat", "Amma", "Chat",
            "Lands at 9:40", "rule", false, System.currentTimeMillis(), "c", null, 3, null);
        assertEquals("Amma, Lands at 9:40, Chat, now, 3 messages",
            view.accessibilityLabel(grouped));
        PinnedNotification untitled = new PinnedNotification("k", "com.bank", null, "Bank",
            "OTP 1234", "rule", false, System.currentTimeMillis());
        assertEquals("the app's name takes the sender line, once", "Bank, OTP 1234, now",
            view.accessibilityLabel(untitled));
    }

    @Test
    public void onlyTheTwoCardsOnScreenAnswerATouch() {
        PinnedNotificationsView view = view(5);
        List<PinnedNotification> opened = new ArrayList<>();
        view.setOpenListener(opened::add);
        dragAndRelease(view, -500f);
        draw(view);

        float cardCentreY = PinnedNotificationsView.cardHeightPx(5, HEIGHT, gap(view)) / 2f;
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, 60f, cardCentreY));
        view.onTouchEvent(event(MotionEvent.ACTION_UP, 60f, cardCentreY));
        assertEquals(1, opened.size());
        assertEquals("the fourth pin is the top card at the end of the run", "key3",
            opened.get(0).key);
    }

    // ---- helpers ----------------------------------------------------------

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private PinnedNotificationsView view(int count) {
        PinnedNotificationsView view = new PinnedNotificationsView(context());
        view.setItems(items(count));
        measure(view);
        return view;
    }

    private static void measure(@NonNull View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
    }

    private static void draw(@NonNull View view) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
    }

    private static float density(@NonNull View view) {
        return view.getResources().getDisplayMetrics().density;
    }

    private static float gap(@NonNull View view) {
        return 2f * density(view);
    }

    /** A drag held at the end, so the offset can be read mid-gesture. */
    private static void drag(@NonNull PinnedNotificationsView view, float dy) {
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, WIDTH / 2f, HEIGHT / 2f));
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, WIDTH / 2f, HEIGHT / 2f + dy));
    }

    private static void swipe(@NonNull PinnedNotificationsView view, float y, float dx) {
        float x = WIDTH / 2f;
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, x, y));
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, x + dx / 2f, y));
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, x + dx, y));
        view.onTouchEvent(event(MotionEvent.ACTION_UP, x + dx, y));
    }

    /** Animations off, so a swipe's settle lands at once. */
    private static void reduceMotion() {
        Settings.Global.putFloat(context().getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
    }

    private static void dragAndRelease(@NonNull PinnedNotificationsView view, float dy) {
        drag(view, dy);
        view.onTouchEvent(event(MotionEvent.ACTION_UP, WIDTH / 2f, HEIGHT / 2f + dy));
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0L, 10L, action, x, y, 0);
    }

    private static List<PinnedNotification> items(int count) {
        List<PinnedNotification> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new PinnedNotification("key" + i, "com.app" + i, "Sender " + i, "App " + i,
                "Body " + i, "rule", false, 1000L + i));
        }
        return items;
    }

    private static final class RecordingParent extends FrameLayout {
        boolean disallowed;

        RecordingParent(Context context) {
            super(context);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            if (disallowIntercept) disallowed = true;
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }
}
