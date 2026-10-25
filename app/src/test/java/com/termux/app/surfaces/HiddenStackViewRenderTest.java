package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.View;

import com.termux.R;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * The hidden stack with every item hidden, as the real view lays it out: each card as tall as its
 * own wrapped label, so one-line and two-line cards differ, at font scale 1 and 1.3. Writes a PNG
 * of each to {@code build/reports/hidden-stack/}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HiddenStackViewRenderTest {

    private static final int COLUMN_TOP = 100;
    private static final int COLUMN_H = 1400;

    @After
    public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test
    public void mixedOneAndTwoLineCardsRenderAtEachTextSize() throws IOException {
        for (float scale : new float[] {1f, 1.3f}) {
            RuntimeEnvironment.setFontScale(scale);
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            ContextThemeWrapper themed = new ContextThemeWrapper(activity,
                R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            float density = themed.getResources().getDisplayMetrics().density;
            HiddenStackView view = new HiddenStackView(themed);
            HiddenStackPolicy.Measure measure = HiddenStackView.measure(themed);
            float base = HiddenStackView.fittedLabelPx(themed, measure);
            List<HiddenItem> items = Arrays.asList(HiddenItem.values());
            List<String> labels = new java.util.ArrayList<>();
            for (HiddenItem item : items)
                labels.add(themed.getString(item.labelRes));
            int width = HiddenStackPolicy.cardWidthPx(density);
            float size = HiddenStackPolicy.stackLabelPx(labels, measure, base, width, COLUMN_H,
                density);
            int[] heights = HiddenStackPolicy.cardHeightsPx(labels, measure, size, width, density);
            HiddenStackPolicy.Rail rail = HiddenStackPolicy.rail(heights,
                HiddenStackPolicy.slotHeightPx(measure, size, density),
                Math.round(HiddenStackPolicy.SIDE_DP * density), COLUMN_TOP, COLUMN_H, density);
            view.set(items, rail, size);
            view.setZone(true, false);
            int w = HiddenStackPolicy.reservePx(density);
            int h = COLUMN_TOP + COLUMN_H;
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, w, h);

            boolean differ = false;
            for (int i = 1; i < heights.length; i++)
                differ |= heights[i] != heights[0];
            assertTrue("one- and two-line cards differ at " + scale, differ);
            for (int i = 0; i < items.size(); i++) {
                View card = view.getChildAt(i + 1);
                if (card.getVisibility() != View.VISIBLE) continue;
                assertEquals(heights[i], card.getHeight());
            }

            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(0xFF202124);
            view.draw(canvas);
            File dir = new File("build/reports/hidden-stack");
            assertTrue(dir.isDirectory() || dir.mkdirs());
            File png = new File(dir, "hidden-stack-font-" + scale + ".png");
            try (FileOutputStream out = new FileOutputStream(png)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            System.out.println("hidden-stack render: " + png.getAbsolutePath());
        }
    }
}
