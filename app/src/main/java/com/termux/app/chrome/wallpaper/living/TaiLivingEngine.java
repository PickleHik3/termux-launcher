package com.termux.app.chrome.wallpaper.living;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.ai.TaiManager;

import java.io.File;
import java.io.IOException;

/**
 * {@link LivingStillJob.Engine} on the phone: the vision analysis through
 * {@link TaiManager#analyzeWallpaper} (the {@code :tai_runtime} process, one graph at a time) and
 * the build through {@link LivingStillBuilder}, which asks Gemma too when it is installed.
 */
final class TaiLivingEngine implements LivingStillJob.Engine {

    @NonNull private final Context mContext;

    TaiLivingEngine(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public LivingStillJob.Outcome analyze(@NonNull File photo, @NonNull File outDir,
                                          @NonNull LivingStillJob.StageSink sink) {
        TaiManager.WallpaperAnalysisResult r = TaiManager.getInstance(mContext).analyzeWallpaper(
            new TaiManager.WallpaperAnalysisRequest(photo.getAbsolutePath(), outDir.getAbsolutePath(), null),
            sink::onStage);
        return new LivingStillJob.Outcome(r.ok, r.cancelled, r.error, r.message);
    }

    @NonNull
    @Override
    public Manifest build(@NonNull File photo, @NonNull File outDir, @NonNull LivingStillBuilder.Progress progress)
        throws IOException {
        return LivingStillBuilder.build(mContext, photo, outDir, progress);
    }

    @Override
    public void cancelAnalysis() {
        TaiManager.getInstance(mContext).cancelWallpaperAnalysis();
    }

    @Override
    public boolean usesGemma() {
        return TaiGemmaChat.installed(mContext);
    }
}
