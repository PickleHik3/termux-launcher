package com.termux.app.chrome;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** A window-less stand-in for the Activity, so the chrome's ordering can be driven by hand. */
final class FakeChromeSurfaces implements ChromeRenderer.Surfaces {

    @NonNull private final Context context;

    FakeChromeSurfaces(@NonNull Context context) {
        this.context = context;
    }

    // ---- state the test drives
    boolean visible = true;
    boolean passthrough = true;
    boolean fullStatusBar;
    boolean rounded;
    int dockBlurRadiusDp = 12;
    int statusBlurRadiusDp = 12;
    int orientation = Configuration.ORIENTATION_PORTRAIT;
    @NonNull final Rect frameRect = new Rect(0, 0, 100, 200);
    int systemWallpaperId = 3;
    boolean managedSource;
    boolean blurHealthy = true;
    @NonNull ChromeSpec spec = new ChromeSpec(true, false, 0, true, true, false, true, 1f, 12);

    // ---- what the test observes
    @NonNull final List<ChromeSpec> applied = new ArrayList<>();
    int invariantsEnforced;
    int terminalGlassFrostUpdates;
    int captureCount;
    int cacheClearedCallbacks;
    @NonNull final List<Bitmap> inUse = new ArrayList<>();

    @NonNull
    @Override
    public Context context() {
        return context;
    }

    @Nullable
    @Override
    public View findChromeView(int viewId) {
        return null;   // no inflated layout: every view-touching path must no-op safely
    }

    @Nullable
    @Override
    public TermuxAppSharedPreferences preferences() {
        return null;
    }

    @Override
    public int orientation() {
        return orientation;
    }

    @Override
    public float dpToPx(float dp) {
        return dp;
    }

    @Override
    public int glassBaseColor() {
        return 0xFF1C1B1F;
    }

    @Override
    public int accentColor() {
        return 0xFF3366FF;
    }

    @Override
    public int outlineColor() {
        return 0xFF808080;
    }

    @Override
    public boolean roundedDockStyle() {
        return rounded;
    }

    @Override
    public float statusBarRimCornerRadiusPx() {
        return 24f;
    }

    @NonNull
    @Override
    public Rect wallpaperFrameRect() {
        return new Rect(frameRect);
    }

    @Override
    public boolean useManagedWallpaperSource() {
        return managedSource;
    }

    @Override
    public int systemWallpaperId() {
        return systemWallpaperId;
    }

    @NonNull
    @Override
    public File managedWallpaperExactFile() {
        return new File("/nonexistent/managed-wallpaper.png");
    }

    @Nullable
    @Override
    public Bitmap captureWallpaperFrame(@NonNull Rect frameRect, @NonNull View wallpaperFrame) {
        captureCount++;
        return Bitmap.createBitmap(Math.max(1, frameRect.width()), Math.max(1, frameRect.height()),
            Bitmap.Config.ARGB_8888);
    }

    @Nullable
    @Override
    public Bitmap preBlur(@NonNull Bitmap sourceBitmap, int blurRadiusDp) {
        // No renderer here: the cache's own frame bookkeeping is what these tests drive.
        return blurRadiusDp <= 0 ? sourceBitmap : sourceBitmap.copy(Bitmap.Config.ARGB_8888, false);
    }

    @Override
    public boolean isFrameInUse(@Nullable Bitmap frame) {
        return frame != null && inUse.contains(frame);
    }

    @Override
    public void onCacheCleared() {
        cacheClearedCallbacks++;
    }

    @Override
    public boolean isActivityVisible() {
        return visible;
    }

    @Override
    public boolean wallpaperPassthroughEnabled() {
        return passthrough;
    }

    @Override
    public boolean fullStatusBarEngaged() {
        return fullStatusBar;
    }

    @Override
    public int effectiveDockBlurRadiusDp() {
        return dockBlurRadiusDp;
    }

    @Override
    public int effectiveStatusBarBlurRadiusDp() {
        return statusBlurRadiusDp;
    }

    @NonNull
    @Override
    public ChromeSpec buildChromeSpec() {
        return spec;
    }

    @Override
    public void applyChromeSpec(@NonNull ChromeSpec spec) {
        applied.add(spec);
    }

    @Override
    public void enforceAccessoryFxInvariants() {
        invariantsEnforced++;
    }

    @Override
    public void updateTerminalGlassFrost() {
        terminalGlassFrostUpdates++;
    }

    @Override
    public boolean isBlurHealthy(@NonNull ChromeSpec spec) {
        return blurHealthy;
    }
}
