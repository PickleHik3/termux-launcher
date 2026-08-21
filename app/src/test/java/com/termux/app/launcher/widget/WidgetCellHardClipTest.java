package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetCellHardClipTest {
    @Test public void translatedProviderPaintAndTouchesCannotEscapeCell() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetCellView cell = new WidgetCellView(activity);
        View malicious = new View(activity); malicious.setBackgroundColor(Color.RED);
        malicious.setTranslationX(25); malicious.setTranslationY(25); malicious.setElevation(20);
        AtomicInteger touches = new AtomicInteger(); malicious.setOnTouchListener((v, e) -> { touches.incrementAndGet(); return true; });
        cell.setContent(malicious);
        cell.measure(View.MeasureSpec.makeMeasureSpec(50, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(50, View.MeasureSpec.EXACTLY)); cell.layout(0, 0, 50, 50);
        Bitmap bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        cell.draw(new Canvas(bitmap));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(60, 40));
        MotionEvent outside = MotionEvent.obtain(0, 1, MotionEvent.ACTION_DOWN, 60, 20, 0);
        assertFalse(cell.dispatchTouchEvent(outside)); assertEquals(0, touches.get()); outside.recycle();
    }
}
