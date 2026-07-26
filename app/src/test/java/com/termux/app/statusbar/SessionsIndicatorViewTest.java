package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.graphics.drawable.GradientDrawable;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SessionsIndicatorViewTest {

    @Test
    public void sessionLabel_usesNameOrOneBasedNumberWithoutAnIconChild() {
        SessionsIndicatorView view = new SessionsIndicatorView(
            ApplicationProvider.getApplicationContext(), null);

        view.setSession("main", 3, 1);
        assertEquals(1, view.getChildCount());
        assertEquals("main", ((TextView) view.getChildAt(0)).getText().toString());

        view.setSession(null, 3, 1);
        assertEquals("2", ((TextView) view.getChildAt(0)).getText().toString());
        assertEquals(18, Math.round(view.getMinimumWidth()
            / view.getResources().getDisplayMetrics().density));

        view.setSession("7", 3, 1);
        assertEquals("7", ((TextView) view.getChildAt(0)).getText().toString());
        assertEquals(true, view.isNumericSession());
    }

    @Test
    public void surfaceStyle_matchesSquareOrCapsuleStatusBar() {
        SessionsIndicatorView view = new SessionsIndicatorView(
            ApplicationProvider.getApplicationContext(), null);
        view.setSurfaceStyle(false, 42f);
        assertEquals(0f, ((GradientDrawable) view.getBackground()).getCornerRadius(), .01f);

        view.setSurfaceStyle(true, 42f);
        assertEquals(42f, ((GradientDrawable) view.getBackground()).getCornerRadius(), .01f);
    }

    @Test
    public void numericSession_tracksExpandedRowHeightAsSquare() {
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
        assertEquals(height, view.getLayoutParams().width);
        view.measure(widthSpec, heightSpec);
        assertEquals(height, view.getMeasuredWidth());
        assertEquals(height, view.getMeasuredHeight());

        view.setSession("main", 3, 0);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, view.getLayoutParams().width);
    }
}
