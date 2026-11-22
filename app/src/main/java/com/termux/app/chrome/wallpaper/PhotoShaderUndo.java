package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;

/**
 * What Send to AI's Undo puts back, and how long the code it puts back is kept. A paste over a
 * saved shader keeps the source it replaces beside it ({@code ShaderStore.previousSourceFile});
 * Undo puts that source and the wallpaper from before the paste back, then asks again. A shader
 * that was new keeps its pasted source on Undo: it stays in the Shader list for the person to
 * keep or delete. At most one kept source exists per shader.
 */
final class PhotoShaderUndo {

    private PhotoShaderUndo() {}

    /** What Undo restores. */
    enum Restores {
        /** The source the paste replaced, and the wallpaper from before it. */
        SOURCE_AND_WALLPAPER,
        /** The shader was new: the wallpaper from before; the shader stays saved. */
        WALLPAPER
    }

    /** How the applied notification ends. */
    enum End {
        /** Undo: the kept source became the source again. */
        UNDO,
        /** Done, or the applied notification swiped away. */
        DONE,
        /** Close, or the asking or failed notification swiped away. */
        CLOSE,
        /** Send to AI asked again, replacing the notification and its Undo. */
        NEW_ASK
    }

    /**
     * A paste is about to save its source: whether the one there now is kept for Undo. A shader
     * with no source keeps nothing, and any old copy goes, so Undo never restores a stale one.
     */
    static boolean pasteKeeps(boolean hadSource) {
        return hadSource;
    }

    @NonNull
    static Restores undoRestores(boolean hasKeptSource) {
        return hasKeptSource ? Restores.SOURCE_AND_WALLPAPER : Restores.WALLPAPER;
    }

    /** Whether the kept source is dropped when the notification ends this way. Undo has used it up. */
    static boolean dropsKept(@NonNull End end) {
        return end != End.UNDO;
    }
}
