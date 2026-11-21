package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.graphics.PathParser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BatteryIconDrawableTest {

    private static final Pattern PATH_DATA = Pattern.compile("android:pathData=\"([^\"]*)\"");

    private static List<String> drawablePaths(String name) throws IOException {
        File file = new File("src/main/res/drawable/" + name + ".xml");
        if (!file.exists()) file = new File("app/src/main/res/drawable/" + name + ".xml");
        Matcher m = PATH_DATA.matcher(new String(Files.readAllBytes(file.toPath()),
            StandardCharsets.UTF_8));
        List<String> out = new ArrayList<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static String join(String... parts) {
        return String.join(" ", parts);
    }

    @Test public void theGeometryIsTheDrawablesPathData() throws IOException {
        String body = join(BatteryIconDrawable.BODY, BatteryIconDrawable.NUB);
        assertEquals(Arrays.asList(body), drawablePaths("ic_rl_battery"));
        assertEquals(Arrays.asList(join(body, BatteryIconDrawable.LOW_MARK),
            BatteryIconDrawable.LOW_DOT), drawablePaths("ic_rl_battery_low"));
        assertEquals(Arrays.asList(join(body, BatteryIconDrawable.CHARGING_MARK)),
            drawablePaths("ic_rl_battery_charging"));
        assertEquals(Arrays.asList(join(body, BatteryIconDrawable.FULL_MARK)),
            drawablePaths("ic_rl_battery_full"));
        assertEquals(Arrays.asList(join(BatteryIconDrawable.HOT_BODY, BatteryIconDrawable.HOT_NUB,
            BatteryIconDrawable.HOT_MARK), BatteryIconDrawable.HOT_DOT),
            drawablePaths("ic_rl_battery_hot"));
    }

    private static void assertBodyRect(String data, float[] rect) {
        RectF r = new RectF();
        PathParser.createPathFromPathData(data).computeBounds(r, true);
        assertEquals(rect[0], r.left, 1e-3f);
        assertEquals(rect[1], r.top, 1e-3f);
        assertEquals(rect[2], r.right, 1e-3f);
        assertEquals(rect[3], r.bottom, 1e-3f);
    }

    @Test public void theBodyRectIsTheBody() {
        assertBodyRect(BatteryIconDrawable.BODY, BatteryIconDrawable.BODY_RECT);
    }

    @Test public void theBarStandsClearOfTheStroke() {
        float[] bar = new float[4];
        float[] body = BatteryIconDrawable.BODY_RECT;
        BatteryIconDrawable.barInside(body, bar);
        float clear = BatteryIconDrawable.STROKE / 2f + BatteryIconDrawable.CLEAR;
        assertEquals(body[0] + clear, bar[0], 1e-6f);
        assertEquals(body[1] + clear, bar[1], 1e-6f);
        assertEquals(body[2] - clear, bar[2], 1e-6f);
        assertEquals(body[3] - clear, bar[3], 1e-6f);
    }

    private static final class Counter implements Drawable.Callback {
        int invalidations;
        @Override public void invalidateDrawable(@NonNull Drawable who) { invalidations++; }
        @Override public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {}
        @Override public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {}
    }

    private static BatteryChipState level(int percent) {
        return BatteryChipState.of(percent, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0, -1, 300);
    }

    @Test public void theBarFollowsTheLevelAndRedrawsOnlyOnAWholePixel() {
        BatteryIconDrawable icon = new BatteryIconDrawable();
        Counter counter = new Counter();
        icon.setCallback(counter);
        icon.setBounds(0, 0, 41, 41);
        icon.setState(level(50));
        int half = icon.fillPixels();
        icon.setState(level(100));
        int full = icon.fillPixels();
        assertTrue(full > half && half > 0);
        float[] bar = new float[4];
        BatteryIconDrawable.barInside(BatteryIconDrawable.BODY_RECT, bar);
        assertEquals(Math.round((bar[2] - bar[0]) * 41f / 24f), full);
        int before = counter.invalidations;
        icon.setState(level(100));
        assertEquals(before, counter.invalidations);
    }

    @Test public void everyStateDrawsAndHotHasNoBar() {
        BatteryIconDrawable icon = new BatteryIconDrawable();
        icon.setBounds(0, 0, 41, 41);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(41, 41,
            android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        icon.setState(level(5));
        icon.draw(canvas);
        icon.setState(BatteryChipState.of(50, 100, BatteryManager.BATTERY_STATUS_CHARGING, 1, -1, 300));
        icon.draw(canvas);
        icon.setState(BatteryChipState.of(100, 100, BatteryManager.BATTERY_STATUS_FULL, 1, -1, 300));
        icon.draw(canvas);
        icon.setState(BatteryChipState.of(50, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0,
            BatteryManager.BATTERY_HEALTH_OVERHEAT, 500));
        icon.draw(canvas);
        // Hot shows its drawable alone, no bar: a bar in its short body reads as a zero.
        assertEquals(0, icon.fillPixels());
    }
}
