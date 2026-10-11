package com.termux.app.statusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertTrue;

/** The robot drawn as a gauge: grey above the fill line, its own colour below it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = android.app.Application.class, qualifiers = "w411dp-h900dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StatusBarWidgetViewGaugeTest {

    @Test public void halfLeftIsGreyOnTopAndFilledBelow() {
        View icon = robot(0.5f);
        Bitmap pixels = draw(icon);
        int x = icon.getWidth() / 2;
        // The antenna knob at the top of the head, and the band along the body's bottom edge.
        int top = pixels.getPixel(x, Math.round(icon.getHeight() * 140f / 960f));
        int bottom = pixels.getPixel(x, Math.round(icon.getHeight() * 800f / 960f));
        assertTrue("the drained top is the empty grey: " + Integer.toHexString(top),
            Color.blue(top) > 200 && Color.red(top) < 60);
        assertTrue("the bottom is still filled: " + Integer.toHexString(bottom),
            Color.red(bottom) > 200 && Color.blue(bottom) < 60);
    }

    @Test public void fullIsFilledEverywhereAndNoGaugeIsThePlainIcon() {
        for (float fraction : new float[] {1f, -1f}) {
            View icon = robot(fraction);
            Bitmap pixels = draw(icon);
            int top = pixels.getPixel(icon.getWidth() / 2,
                Math.round(icon.getHeight() * 140f / 960f));
            assertTrue("filled at the top for " + fraction + ": " + Integer.toHexString(top),
                Color.red(top) > 200 && Color.blue(top) < 60);
        }
    }

    private static View robot(float fraction) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        StatusBarWidgetView widget = new StatusBarWidgetView(activity);
        widget.setIconResource(R.drawable.ic_symbol_smart_toy);
        widget.setInk(Color.RED, Color.RED, Color.BLUE);
        widget.setValue("");
        activity.setContentView(widget);
        int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        widget.measure(spec, spec);
        widget.layout(0, 0, widget.getMeasuredWidth(), widget.getMeasuredHeight());
        widget.setIconGauge(fraction, AiUnloadGauge.ROBOT_INK_TOP,
            AiUnloadGauge.ROBOT_INK_BOTTOM);
        return widget.getChildAt(0);
    }

    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
            Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
}
