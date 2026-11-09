package com.termux.app.screenshot;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.github.takahirom.roborazzi.RoborazziKt;
import com.github.takahirom.roborazzi.RoborazziOptions;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.chrome.LiveBackdrop;
import com.termux.app.chrome.LiveBackdropFrameLayout;
import com.termux.app.chrome.LivePicture;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

/**
 * Prototype: does a Robolectric + Roborazzi capture stand in for an emulator look at static
 * layout? Settings (one-pane / two-pane, light / dark) and one blurred glass panel over a striped
 * backdrop, in the default pixel-copy mode and in hardware mode. PNGs land in
 * app/src/test/screenshots; timings go to stdout as "SHOT".
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotPrototypeTest {

    private static final String PHONE = "w411dp-h891dp-%s-xxhdpi";
    private static final String TABLET = "sw700dp-w900dp-h700dp-%s-xhdpi";
    private static final String DIR = "src/test/screenshots/";

    private void shoot(View view, String name) {
        long start = System.nanoTime();
        RoborazziKt.captureRoboImage(view, DIR + name + ".png", new RoborazziOptions());
        System.out.println("SHOT " + name + " " + (System.nanoTime() - start) / 1_000_000 + " ms");
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).pause();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void settings(String qualifiers, String name) {
        long start = System.nanoTime();
        RuntimeEnvironment.setQualifiers(qualifiers);
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class,
            new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class)).create().start().resume().visible();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        settle();
        System.out.println("SHOT " + name + " build " + (System.nanoTime() - start) / 1_000_000 + " ms");
        shoot(activity.getWindow().getDecorView(), name);
        controller.pause().stop().destroy();
    }

    @Test @Config(sdk = Build.VERSION_CODES.P, application = Application.class) public void settingsOnePaneLight() { settings(String.format(PHONE, "notnight"), "settings_onepane_light"); }
    @Test @Config(sdk = Build.VERSION_CODES.P, application = Application.class) public void settingsOnePaneDark() { settings(String.format(PHONE, "night"), "settings_onepane_dark"); }
    @Test @Config(sdk = Build.VERSION_CODES.P, application = Application.class) public void settingsTwoPaneLight() { settings(String.format(TABLET, "notnight"), "settings_twopane_light"); }
    @Test @Config(sdk = Build.VERSION_CODES.P, application = Application.class) public void settingsTwoPaneDark() { settings(String.format(TABLET, "night"), "settings_twopane_dark"); }

    /** A busy backdrop: a gradient with hard stripes, so blur is easy to see. */
    private static final class Busy extends View {
        private final Paint mPaint = new Paint();
        Busy(Activity a) { super(a); }
        @Override protected void onDraw(Canvas c) {
            mPaint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(), Color.rgb(255, 80, 40),
                Color.rgb(30, 90, 255), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, getWidth(), getHeight(), mPaint);
            mPaint.setShader(null);
            for (int i = 0; i < getHeight(); i += 48) {
                mPaint.setColor(((i / 48) & 1) == 0 ? Color.BLACK : Color.WHITE);
                c.drawRect(0, i, getWidth(), i + 12, mPaint);
            }
            for (int i = 0; i < getWidth(); i += 96) {
                mPaint.setColor(Color.YELLOW);
                c.drawRect(i, 0, i + 8, getHeight(), mPaint);
            }
        }
    }

    private void glass(String name, boolean hardware) {
        if (hardware) System.setProperty("robolectric.pixelCopyRenderMode", "hardware");
        else System.clearProperty("robolectric.pixelCopyRenderMode");
        RuntimeEnvironment.setQualifiers(String.format(PHONE, "notnight"));
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).create();
        Activity activity = controller.get();
        LiveBackdropFrameLayout root = new LiveBackdropFrameLayout(activity);
        root.addView(new Busy(activity), new FrameLayout.LayoutParams(-1, -1));
        ImageView surface = new ImageView(activity);
        surface.setScaleType(ImageView.ScaleType.FIT_XY);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(700, 500);
        lp.leftMargin = 190;
        lp.topMargin = 700;
        root.addView(surface, lp);
        activity.setContentView(root);
        LiveBackdrop recorder = new LiveBackdrop(root);
        controller.start().resume().visible();
        LivePicture picture = recorder.attach(surface, 60f, true);
        System.out.println("SHOT " + name + " picture=" + picture);
        if (picture != null) surface.setImageDrawable(picture.drawable());
        settle();
        shoot(activity.getWindow().getDecorView(), name);
        controller.pause().stop().destroy();
    }

    @Test public void glassDefaultPixelCopy() { glass("glass_default", false); }
    @Test public void glassHardwarePixelCopy() { glass("glass_hardware", true); }

    /** Control: a plain View.setRenderEffect blur on the stripes themselves, no app code. */
    private void direct(String name, boolean hardware) {
        if (hardware) System.setProperty("robolectric.pixelCopyRenderMode", "hardware");
        else System.clearProperty("robolectric.pixelCopyRenderMode");
        RuntimeEnvironment.setQualifiers(String.format(PHONE, "notnight"));
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).create();
        Activity activity = controller.get();
        FrameLayout root = new FrameLayout(activity);
        Busy left = new Busy(activity);
        root.addView(left, new FrameLayout.LayoutParams(-1, -1));
        Busy blurred = new Busy(activity);
        blurred.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(30f, 30f,
            android.graphics.Shader.TileMode.CLAMP));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(700, 500);
        lp.leftMargin = 190;
        lp.topMargin = 700;
        root.addView(blurred, lp);
        activity.setContentView(root);
        controller.start().resume().visible();
        settle();
        shoot(activity.getWindow().getDecorView(), name);
        controller.pause().stop().destroy();
    }

    @Test public void directDefault() { direct("direct_default", false); }
    @Test public void directHardware() { direct("direct_hardware", true); }
}
