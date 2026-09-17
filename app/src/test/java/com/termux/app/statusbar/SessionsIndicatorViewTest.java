package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SessionsIndicatorViewTest {

    @Test
    public void sessionLabel_usesNameOrOneBasedNumberWithoutAnIconChild() {
        SessionsIndicatorView view = new SessionsIndicatorView(
            ApplicationProvider.getApplicationContext(), null);

        view.setSession("mainframe", 3, 1);
        assertEquals(1, view.getChildCount());
        assertEquals("mainfram", ((TextView) view.getChildAt(0)).getText().toString());
        assertFalse(view.isShowingSessionNumber());
        assertTrue(view.getContentDescription().toString().contains("mainfram"));

        view.setSession(null, 3, 1);
        assertEquals("2", ((TextView) view.getChildAt(0)).getText().toString());
        assertEquals(18, Math.round(view.getMinimumWidth()
            / view.getResources().getDisplayMetrics().density));

        view.setSession("123456789", 3, 1);
        assertEquals("12345678", ((TextView) view.getChildAt(0)).getText().toString());
        assertFalse(view.isShowingSessionNumber());
    }

    /**
     * The indicator wears the radius it is handed, in either style — the caller resolves it from
     * the status row's chip knob, falling back to the bar's own shape. The two chips in that row
     * have to be able to round together while the surface stays Docked.
     */
    @Test
    public void surfaceStyle_wearsTheRadiusItIsGiven() {
        SessionsIndicatorView view = new SessionsIndicatorView(
            ApplicationProvider.getApplicationContext(), null);
        view.setSurfaceStyle(false, 0f);
        assertEquals(0f, ((GradientDrawable) view.getBackground()).getCornerRadius(), .01f);

        view.setSurfaceStyle(false, 10f);
        assertEquals(10f, ((GradientDrawable) view.getBackground()).getCornerRadius(), .01f);

        view.setSurfaceStyle(true, 42f);
        assertEquals(42f, ((GradientDrawable) view.getBackground()).getCornerRadius(), .01f);
    }

    @Test
    public void numberedSession_tracksExpandedRowHeightAsSquare() {
        SessionsIndicatorView view = new SessionsIndicatorView(
            ApplicationProvider.getApplicationContext(), null);
        int height = Math.round(22 * view.getResources().getDisplayMetrics().density);
        int staleCollapsedWidth = Math.round(20 * view.getResources().getDisplayMetrics().density);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(
            staleCollapsedWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        view.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, height));

        view.setSession(null, 3, 0);
        assertTrue(view.isShowingSessionNumber());
        assertEquals(height, view.getLayoutParams().width);
        view.measure(widthSpec, heightSpec);
        assertEquals(height, view.getMeasuredWidth());
        assertEquals(height, view.getMeasuredHeight());

        view.setSession("main", 3, 0);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, view.getLayoutParams().width);
    }

    @Test
    public void numberedSessions_useTheSameCenteredTextLayoutForEveryDigit() {
        SessionsIndicatorView view = new SessionsIndicatorView(
            ApplicationProvider.getApplicationContext(), null);
        int size = Math.round(20 * view.getResources().getDisplayMetrics().density);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));

        for (int index = 0; index < 4; index++) {
            view.setSession(null, 4, index);
            view.measure(
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, size, size);
            TextView label = (TextView) view.getChildAt(0);
            assertEquals(Gravity.CENTER, label.getGravity());
            assertEquals(size, label.getLeft() + label.getRight(), 1);
            assertEquals(0f, label.getTranslationX(), 0f);
        }
    }

    /**
     * The chip the user said they could not see. In light mode it measured 1.75:1 — a dark label on
     * a fill the place accent had been blended 55% towards black — and in dark mode 3.90:1, under
     * the floor too. Both modes are checked against the glass measured on the reporting device.
     */
    @Test
    public void sessionChip_labelClearsTheBodyFloorOnItsContainerInBothModes() {
        assertChipReads("light", 0xFF6A5755);

        org.robolectric.RuntimeEnvironment.setQualifiers("+night");
        assertChipReads("dark", 0xFF1B1A17);
        org.robolectric.RuntimeEnvironment.setQualifiers("+notnight");
    }

    private static void assertChipReads(String mode, int bandSurface) {
        SessionsIndicatorView view = new SessionsIndicatorView(
            ApplicationProvider.getApplicationContext(), null);
        view.setAccent(0xFF345CA8);
        view.setBandSurface(bandSurface);
        view.setSession("main", 2, 0);

        GradientDrawable chip = (GradientDrawable) view.getBackground();
        int container = chip.getColor().getDefaultColor();
        int surface = com.termux.app.chrome.OnGlass.opaque(
            com.termux.app.chrome.OnGlass.composite(container,
                com.termux.app.chrome.OnGlass.opaque(bandSurface)));
        int label = ((TextView) view.getChildAt(0)).getCurrentTextColor();
        double ratio = com.termux.app.chrome.OnGlass.ratio(label, surface);

        assertTrue("the session chip's label in " + mode + " mode: " + ratio,
            ratio >= com.termux.app.chrome.OnGlass.TARGET_BODY_TEXT);
    }

    @Test
    public void alphaWeightedCenter_followsTheMajorityOfVisibleInk() {
        int[] pixels = {
            0x20000000, 0x00000000, 0xFF000000,
            0x00000000, 0x00000000, 0xFF000000
        };

        float center = SessionsIndicatorView.alphaWeightedCenterX(pixels, 3, 0f);

        assertTrue(center > 2f);
        assertEquals((32f * .5f + 255f * 2.5f + 255f * 2.5f) / (32f + 255f + 255f),
            center, .001f);
    }
}
