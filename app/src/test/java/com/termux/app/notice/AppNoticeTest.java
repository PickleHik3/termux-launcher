package com.termux.app.notice;

import android.app.Activity;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@LooperMode(LooperMode.Mode.LEGACY)
public class AppNoticeTest {

    @Test
    public void kindPicksTheGlyphWhenTheCallerHasNoOpinion() {
        assertEquals("›", item(AppNoticeItem.Kind.INFO, null).resolvedGlyph());
        assertEquals("✓", item(AppNoticeItem.Kind.SUCCESS, null).resolvedGlyph());
        assertEquals("⚠", item(AppNoticeItem.Kind.WARNING, null).resolvedGlyph());
        assertEquals("✕", item(AppNoticeItem.Kind.ERROR, null).resolvedGlyph());
    }

    @Test
    public void callerGlyphOutranksTheKind() {
        assertEquals("⧉", item(AppNoticeItem.Kind.SUCCESS, "⧉").resolvedGlyph());
        // An empty override is not an override: it would draw a hole where the glyph belongs.
        assertEquals("✓", item(AppNoticeItem.Kind.SUCCESS, "").resolvedGlyph());
    }

    @Test
    public void hostIsAttachedOnceAndReusedForTheSameActivity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView first = AppNotice.hostFor(activity);
        assertNotNull(first);
        assertSame(first, AppNotice.hostFor(activity));
    }

    @Test
    public void chipStaysOutOfTheWayUntilSomethingIsRaised() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        assertEquals(View.GONE, host.getVisibility());
        host.enqueue(item(AppNoticeItem.Kind.INFO, null));
        assertEquals(View.VISIBLE, host.getVisibility());
    }

    /**
     * The CPU and RAM widgets are opt-in: both lean on the privileged backend, and switching them
     * on runs a Shizuku check first. A regression here would silently re-enable a readout that can
     * be permanently blank on ROMs that deny the unprivileged /proc fallback.
     */
    @Test
    public void statusWidgetsThatNeedShizukuDefaultOff() {
        assertFalse(TERMUX_APP.DEFAULT_STATUS_WIDGET_CPU);
        assertFalse(TERMUX_APP.DEFAULT_STATUS_WIDGET_RAM);
    }

    /**
     * The hold hairline used to be a MATCH_PARENT child, and View.getDefaultSize hands back the
     * full spec size under AT_MOST, so that one child dragged the WRAP_CONTENT chip out to the
     * screen edge — a short notice rendered as a banner the whole width of the status bar.
     */
    @Test
    public void shortNoticeStaysAChipRatherThanSpanningTheScreen() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        host.enqueue(item(AppNoticeItem.Kind.INFO, null));
        int parentWidth = 1080;
        host.measure(
            View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertTrue("chip measured " + host.getMeasuredWidth() + "px of " + parentWidth,
            host.getMeasuredWidth() < parentWidth / 2);
    }

    @Test
    public void attentionOutranksTheKindWhenPickingTheGlyph() {
        AppNoticeItem attention = new AppNoticeItem(AppNoticeItem.Kind.INFO, "title", null, null,
            AppNoticeHostView.HOLD_SHORT_MS, null, true);
        assertEquals("!", attention.resolvedGlyph());
    }

    @Test
    public void aNoticeAboutSomewhereCarriesTheWayThere() {
        AtomicBoolean opened = new AtomicBoolean(false);
        AppNoticeItem targeted = new AppNoticeItem(AppNoticeItem.Kind.INFO, "title", null, null,
            AppNoticeHostView.HOLD_SHORT_MS, () -> opened.set(true), false);
        assertNotNull(targeted.onActivate);
        targeted.onActivate.run();
        assertTrue(opened.get());
    }

    /**
     * The preset-applied confirmation is a notice with an Undo, replacing a snackbar that landed
     * on the keyboard and could not be swiped away. Two things have to survive: the hold is long
     * enough to decide (a confirmation's few seconds are not), and the chip's node says what the
     * tap does — "tap to open" would be a lie about a tap that reverts a bulk write.
     */
    @Test
    public void anUndoNoticeHoldsLongEnoughAndAnnouncesWhatTheTapDoes() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNotice.undoable(activity, "Glass applied", "Tap to undo", () -> {});
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        assertEquals("Glass applied — Tap to undo", String.valueOf(host.getContentDescription()));
        assertTrue(AppNoticeHostView.HOLD_UNDO_MS > AppNoticeHostView.HOLD_LONG_MS * 2);
    }

    /** With no hint of its own, an actionable notice keeps the generic wording. */
    @Test
    public void aNoticeWithoutItsOwnHintFallsBackToTapToOpen() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        host.enqueue(new AppNoticeItem(AppNoticeItem.Kind.INFO, "title", null, null,
            AppNoticeHostView.HOLD_SHORT_MS, () -> {}, false));
        assertEquals("title — " + activity.getString(com.termux.R.string.notice_tap_to_open),
            String.valueOf(host.getContentDescription()));
    }

    /**
     * The offset used to be measured once, when the host was attached, off a toolbar that a screen
     * raising a notice from onCreate has not laid out yet — and it never changed again, so a
     * rotation or a bar that grew left the chip floating in the wrong place. It is derived now, and
     * the derivation has to survive the chrome moving.
     */
    @Test
    public void theChipFollowsTheChromeItHangsFromWhenThatChromeMoves() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View chrome = chromeScreen(activity, 80);
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        layoutContent(activity);
        assertEquals(80 + gapPx(activity), topMarginOf(host));

        chrome.getLayoutParams().height = 40;
        chrome.requestLayout();
        layoutContent(activity);
        assertEquals(40 + gapPx(activity), topMarginOf(host));
    }

    /** No chrome to clear: the pill sits at the top of the content it was given. */
    @Test
    public void aScreenWithoutChromeGetsTheChipAtItsTopEdge() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        layoutContent(activity);
        assertEquals(gapPx(activity), topMarginOf(host));
    }

    /**
     * The reveal is a clip, not a measurement. It used to be the latter — the height was a fraction
     * of the natural height, so every animation frame called requestLayout() and walked a layout
     * pass up through the whole activity. Measuring the chip must not depend on how far out it is.
     */
    @Test
    public void measuringTheChipDoesNotDependOnHowFarItHasCome() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        host.enqueue(item(AppNoticeItem.Kind.INFO, null));
        int outHeight = measuredHeightOf(host);
        assertTrue("chip measured " + outHeight + "px", outHeight > 0);

        host.clear();
        assertEquals(View.GONE, host.getVisibility());
        assertEquals(outHeight, measuredHeightOf(host));
    }

    /**
     * The column under the chip animates its own slide to whatever height it is handed, so it wants
     * one target per notice. It used to be handed one per animation frame, which cancelled and
     * restarted that slide sixty times a second.
     */
    @Test
    public void theColumnBelowIsToldTheHeightOnceRatherThanEveryFrame() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        chromeScreen(activity, 80);
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        List<Integer> reported = new ArrayList<>();
        host.setOccupancyListener(reported::add);
        host.enqueue(item(AppNoticeItem.Kind.INFO, null));
        layoutContent(activity);
        assertTrue("occupancy reported " + reported, reported.contains(measuredHeightOf(host)));
        assertTrue("occupancy reported " + reported.size() + " times: " + reported,
            reported.size() <= 3);
    }

    /**
     * The pill is centred: it is a notice about what just happened, not a label belonging to the
     * corner it used to hang in, and it reads the same on every screen.
     */
    @Test
    public void thePillIsCentredInTheRowItLandsIn() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) host.getLayoutParams();
        assertEquals(Gravity.TOP | Gravity.CENTER_HORIZONTAL, params.gravity);
    }

    /** A screen with a title bar and a content area under it, as the settings screens have. */
    private static View chromeScreen(Activity activity, int chromeHeightPx) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        View chrome = new View(activity);
        chrome.setId(com.termux.shared.R.id.toolbar);
        root.addView(chrome, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, chromeHeightPx));
        root.addView(new FrameLayout(activity), new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        activity.setContentView(root);
        return chrome;
    }

    private static void layoutContent(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        content.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, 1080, 2400);
    }

    private static int gapPx(Activity activity) {
        return Math.round(8f * activity.getResources().getDisplayMetrics().density);
    }

    private static int topMarginOf(View host) {
        return ((ViewGroup.MarginLayoutParams) host.getLayoutParams()).topMargin;
    }

    private static int measuredHeightOf(AppNoticeHostView host) {
        host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST));
        return host.getMeasuredHeight();
    }

    private static AppNoticeItem item(AppNoticeItem.Kind kind, String glyph) {
        return new AppNoticeItem(kind, "title", null, glyph, AppNoticeHostView.HOLD_SHORT_MS);
    }
}
