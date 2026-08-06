package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SessionSwitchIndicatorViewTest {

    @Test
    public void hostParams_pinTheChipToTheTopTrailingCorner() {
        FrameLayout.LayoutParams params = SessionSwitchIndicatorView.buildHostLayoutParams(
            ApplicationProvider.getApplicationContext());

        assertEquals(Gravity.TOP | Gravity.END, params.gravity);
        assertTrue(params.topMargin > 0);
        assertTrue(params.getMarginEnd() > 0);
        assertEquals(FrameLayout.LayoutParams.WRAP_CONTENT, params.width);
        assertEquals(FrameLayout.LayoutParams.WRAP_CONTENT, params.height);
    }

    @Test
    public void reShowingKeepsTheSettledAlphaAndClearsTheSlide() {
        // The re-entrant branch used to set alpha(1f) and reset the wrong axis, so an updated chip
        // snapped to full opacity while keeping a stale offset. Guards that bug class.
        SessionSwitchIndicatorView chip = chip();

        chip.show("first");
        chip.show("second");

        assertEquals("second", chip.getText().toString());
        assertEquals(SessionSwitchIndicatorView.ENTER_ALPHA, chip.getAlpha(), .001f);
        assertEquals(0f, chip.getTranslationX(), .001f);
        assertEquals(0f, chip.getTranslationY(), .001f);
        assertEquals(View.VISIBLE, chip.getVisibility());
    }

    @Test
    public void cancelResetsBothTransforms() {
        SessionSwitchIndicatorView chip = chip();
        chip.show("notice");

        chip.cancel();

        assertEquals(View.GONE, chip.getVisibility());
        assertEquals(0f, chip.getAlpha(), .001f);
        assertEquals(0f, chip.getTranslationX(), .001f);
        assertEquals(0f, chip.getTranslationY(), .001f);
    }

    @Test
    public void anEmptyNoticeIsIgnored() {
        SessionSwitchIndicatorView chip = chip();

        chip.show("");
        chip.show(null);

        assertEquals(View.GONE, chip.getVisibility());
    }

    private static SessionSwitchIndicatorView chip() {
        SessionSwitchIndicatorView chip = new SessionSwitchIndicatorView(
            ApplicationProvider.getApplicationContext());
        // Attached and laid out, so the entrance animation actually runs under Robolectric.
        FrameLayout host = new FrameLayout(ApplicationProvider.getApplicationContext());
        host.addView(chip, SessionSwitchIndicatorView.buildHostLayoutParams(
            ApplicationProvider.getApplicationContext()));
        host.layout(0, 0, 1080, 1920);
        return chip;
    }
}
