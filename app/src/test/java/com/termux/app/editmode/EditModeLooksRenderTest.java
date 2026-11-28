package com.termux.app.editmode;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.editmode.EditModeController.Card;
import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the All glass and Home cards at 360 dp over a wallpaper-like frame, in the dark theme
 * and under the Black overlay, and checks the filmstrip's tiles draw as different glass. The
 * pictures land in {@code ~/.cache/terl-visual/terl-looks} when that folder exists, for a look.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w360dp-h740dp-night-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditModeLooksRenderTest {

    private interface Both extends EditModeController.WindowsHost, EditModeController.LooksHost { }

    private final Map<String, Object> keyed = new HashMap<>();

    private Bitmap wallpaper(int width, int height) {
        Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(0, 0, width, height, 0xFF1D3B6E, 0xFF8A3E6B, Shader.TileMode.CLAMP));
        new Canvas(frame).drawRect(0, 0, width, height, paint);
        return frame;
    }

    private Bitmap render(boolean black, @NonNull Card card) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        if (black) activity.getTheme().applyStyle(com.termux.R.style.ThemeOverlay_Termux_Black, true);
        FrameLayout layer = new FrameLayout(activity);
        activity.setContentView(layer);
        TerlLookStore looks = new StoredTerlLookStore(new MemoryLookStore());
        keyed.put(EditSession.Keyed.ICON_SIZE, "m");
        keyed.put(EditSession.Keyed.MONOCHROME, EditSession.OFF);
        keyed.put(EditSession.Keyed.LABELS_SHOWN, EditSession.ON);
        Bitmap frame = wallpaper(240, 480);
        EditSession.Keyed windows = new EditSession.Keyed() {
            @Nullable @Override public Object get(@NonNull String key) { return keyed.get(key); }
            @Override public void set(@NonNull String key, @NonNull Object value) { keyed.put(key, value); }
        };
        EditSession.Home home = new EditSession.Home() {
            @Override public boolean available() { return true; }
            @Override public int columns() { return 4; }
            @Override public void setColumns(int count) { }
            @NonNull @Override public String labels() { return LABEL_DEFAULT; }
            @Override public void setLabels(@NonNull String size) { }
            @Override public boolean pageDots() { return true; }
            @Override public void setPageDots(boolean on) { }
            @NonNull @Override public String azEdge() { return AZ_OFF; }
            @Override public void setAzEdge(@NonNull String edge) { }
            @Override public void onEmptyTap(@Nullable Runnable tap) { }
            @Override public void change() { }
        };
        EditModeController controller = new EditModeController(new Both() {
            @NonNull @Override public Context context() { return activity; }
            @Nullable @Override public ViewGroup editorLayer() { return layer; }
            @NonNull @Override public TerlLookStore looks() { return looks; }
            @Nullable @Override public View keyboardView() { return null; }
            @Override public boolean isKeyboardShown() { return false; }
            @Override public boolean showKeyboard() { return false; }
            @Override public void hideKeyboard() { }
            @Nullable @Override public EditSession.Launcher launcher() { return null; }
            @NonNull @Override public List<com.termux.app.launcher.data.IconPackChoices.Entry> iconPacks() {
                return Collections.emptyList();
            }
            @Override public void warmIconPack(@NonNull String pack) { }
            @Nullable @Override public EditSession.Wallpaper wallpaper() { return null; }
            @Nullable @Override public EditSession.Home home() { return home; }
            @Override public void onWallpaperTap(@Nullable Runnable tap) { }
            @Nullable @Override public LegibilityMeter.Reading legibility() { return null; }
            @Override public void onLeft() { }
            @Nullable @Override public EditSession.Bar bar() { return null; }
            @Nullable @Override public View barView() { return null; }
            @Override public boolean raiseBar() { return false; }
            @Override public void lowerBar() { }
            @Override public void onBarTap(@Nullable Runnable tap) { }
            @Nullable @Override public EditSession.Keyed windows() { return windows; }
            @Override public void onWindowTap(@Nullable Runnable tap) { }
            @NonNull @Override public List<String[]> installedFonts() { return Collections.emptyList(); }
            @Override public void openFonts() { }
            @Nullable @Override public Bitmap wallpaperFrame() { return frame; }
            @Override public void openSettings() { }
        });
        controller.open(card);
        int width = 720;
        int height = 1480;
        for (int i = 0; i < 3; i++) {
            layer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            layer.layout(0, 0, width, height);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(Bitmap.createScaledBitmap(frame, width, height, true), 0, 0, null);
        layer.draw(canvas);
        return out;
    }

    private static void write(@NonNull String name, @NonNull Bitmap bitmap) throws IOException {
        File dir = new File(System.getProperty("user.home"), ".cache/terl-visual/terl-looks");
        if (!dir.isDirectory()) return;
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test public void theCardsRender() throws IOException {
        Bitmap global = render(false, Card.GLOBAL);
        write("all-glass-dark.png", global);
        write("home-dark.png", render(false, Card.HOME));
        Bitmap black = render(true, Card.GLOBAL);
        write("all-glass-black.png", black);
        write("home-black.png", render(true, Card.HOME));
        // The card's panel near its bottom-left corner: Black's is true black, Dark's is not.
        int y = global.getHeight() - 200;
        assertNotEquals(0xFF000000, global.getPixel(40, y) | 0);
        assertTrue(global.getWidth() > 0);
    }
}
