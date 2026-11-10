package com.termux.app.chrome.scene;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;

/**
 * What the stored wallpaper choice plays: the one place that turns a {@link WallpaperMotion} into
 * the files, the layer identity and the still. {@link SceneController} runs {@link #spec} and
 * {@link SceneStill#renderActive} puts {@link #still} on the phone's wallpaper, so the two cannot
 * disagree. Resolving touches the disk only for a video (which file is playable); a shader's meta
 * is passed in, as the controller already caches it.
 */
final class SceneSource {

    @NonNull private final String mKey;
    private final int mRateHz;
    @NonNull private final File mFile;
    @Nullable private final File mChannel0;
    @NonNull private final SceneLayerView.Spec mSpec;
    private final boolean mVideo;

    private SceneSource(@NonNull String key, int rateHz, @NonNull File file, @Nullable File channel0,
                        @NonNull SceneLayerView.Spec spec, boolean video) {
        mKey = key;
        mRateHz = rateHz;
        mFile = file;
        mChannel0 = channel0;
        mSpec = spec;
        mVideo = video;
    }

    /**
     * The source of {@code motion}, or null for a photo, a video whose file is missing, or a shader
     * without its {@code meta}. {@code videoRateHz} is the chosen video rate; {@code meta} is the
     * shader's, as read by the caller.
     */
    @Nullable
    static SceneSource resolve(@NonNull Context context, @NonNull WallpaperMotion motion, int videoRateHz,
                               @Nullable ShaderStore.Meta meta) {
        if (motion.id == null) return null;
        if (motion.kind == WallpaperMotion.Kind.VIDEO) {
            File video = VideoVariants.playable(context, videoRateHz);
            // An import from before frame-rate versions is the file named after the choice.
            if (video == null) video = VideoImport.file(context, motion.id);
            if (!video.isFile()) return null;
            // The file name is in the key: a version finishing swaps the layer to it.
            String key = motion.format() + "@" + video.getName();
            return new SceneSource(key, videoRateHz, video, null,
                new SceneLayerView.Spec(key, video, false), true);
        }
        if (motion.kind == WallpaperMotion.Kind.SHADER && meta != null) {
            File source = ShaderStore.sourceFile(context, motion.id);
            File channel0 = ShaderStore.textureFile(context, motion.id, meta);
            String key = motion.format();
            return new SceneSource(key, meta.rateHz, source, channel0,
                new SceneLayerView.Spec(key, source, channel0, meta.quality, false), false);
        }
        return null;
    }

    /** The layer's identity: a different key is a different layer. */
    @NonNull
    String key() {
        return mKey;
    }

    /** The frame rate the choice asks for: the chosen video rate, or the shader's own. */
    int rateHz() {
        return mRateHz;
    }

    @NonNull
    SceneLayerView.Spec spec() {
        return mSpec;
    }

    /**
     * A frame to set as the phone's wallpaper: a video frame (API 29+), or a shader at
     * {@code iTime = 0} with {@code look} (null leaves the colours 0). Null when the device cannot,
     * the shader source cannot be read, or the shader does not build.
     *
     * @throws IllegalStateException when the GPU could not draw it
     */
    @WorkerThread
    @Nullable
    Bitmap still(int widthPx, int heightPx, @Nullable ShaderLook look) {
        if (mVideo) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? VideoStill.render(mFile, widthPx, heightPx) : null;
        }
        String text = ShaderStore.readText(mFile);
        return text == null ? null : SceneStill.render(text, mChannel0, widthPx, heightPx, look);
    }
}
