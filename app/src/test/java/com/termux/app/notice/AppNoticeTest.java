package com.termux.app.notice;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

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

    private static AppNoticeItem item(AppNoticeItem.Kind kind, String glyph) {
        return new AppNoticeItem(kind, "title", null, glyph, AppNoticeHostView.HOLD_SHORT_MS);
    }
}
