package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarWidgetViewTest {

    @Test
    public void iconAndValue_areVerticallyCenteredWithSymmetricPadding() {
        StatusBarWidgetView widget = new StatusBarWidgetView(
            ApplicationProvider.getApplicationContext(), null);
        widget.setValue("88%");
        widget.setIconGlyph("\uf4bc");   // nf-oct-cpu, the CPU widget's icon
        widget.measure(exact(100), exact(24));
        widget.layout(0, 0, 100, 24);

        assertEquals(widget.getPaddingTop(), widget.getPaddingBottom());
        for (int i = 0; i < widget.getChildCount(); i++) {
            View child = widget.getChildAt(i);
            // The icon comes in two mutually exclusive forms — a vector and a Nerd Font glyph —
            // so whichever one is unused sits GONE at the origin with nothing to centre.
            if (child.getVisibility() == View.GONE) continue;
            assertEquals(widget.getHeight() / 2f,
                (child.getTop() + child.getBottom()) / 2f, .51f);
            assertEquals(0f, child.getTranslationY(), .01f);
        }
    }

    private static int exact(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }
}
