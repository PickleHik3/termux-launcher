package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;

import com.termux.app.chrome.scene.WallpaperMotion;

/**
 * The pure rules of the Video and Shader pages' preview model, the Photo page's own: a pick only
 * writes the motion choice (the scene behind the sheet shows it), Done applies it, and closing
 * any other way puts back what was there when the sheet opened.
 */
public final class LivePreviewLogic {

    private LivePreviewLogic() {}

    /**
     * Whether Done has anything to apply: a moving choice that is not the one the sheet opened
     * with, or a shader whose source was just saved again under the same id.
     */
    public static boolean commitHasWork(@NonNull WallpaperMotion openedWith, @NonNull WallpaperMotion current,
                                        boolean draftApplied) {
        return current.moves() && (draftApplied || !openedWith.equals(current));
    }

    /**
     * Whether closing without Done has to write the opened-with choice and rate back: nothing was
     * committed and the choice or the video rate moved.
     */
    public static boolean mustRevert(boolean committed, @NonNull WallpaperMotion openedWith,
                                     @NonNull WallpaperMotion current, int openedRate, int currentRate) {
        return !committed && (!openedWith.equals(current) || openedRate != currentRate);
    }

    /**
     * Whether a finished video import is committed at once: importing deletes the previous
     * video's files, so with a video opened-with there is nothing to go back to.
     */
    public static boolean importCommitsAtOnce(@NonNull WallpaperMotion openedWith) {
        return openedWith.kind == WallpaperMotion.Kind.VIDEO;
    }

    /** Whether a preview-only shader copy is deleted: it is not the choice Done keeps. */
    public static boolean dropsCopy(@NonNull WallpaperMotion current, @NonNull String copyId) {
        return !(current.kind == WallpaperMotion.Kind.SHADER && copyId.equals(current.id));
    }
}
