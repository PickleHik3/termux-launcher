package com.termux.app.notice;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

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

    private static AppNoticeItem item(AppNoticeItem.Kind kind, String glyph) {
        return new AppNoticeItem(kind, "title", null, glyph, AppNoticeHostView.HOLD_SHORT_MS);
    }
}
