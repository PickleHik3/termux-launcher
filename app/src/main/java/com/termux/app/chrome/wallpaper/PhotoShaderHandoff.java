package com.termux.app.chrome.wallpaper;

import androidx.annotation.Nullable;

/**
 * Who keeps a new photo shader's folder (its photo and depth image) while it has no saved
 * source: the open editor, and the Send to AI notification while it is live. The folder goes
 * only when neither holds it any more and nothing was ever applied, as the editor alone did.
 */
final class PhotoShaderHandoff {

    private PhotoShaderHandoff() {}

    /** Send to AI shows once the draft has a photo: before that there is nothing to send. */
    static boolean sendShows(boolean photoFlow, @Nullable String texture) {
        return photoFlow && texture != null;
    }

    /**
     * The editor closes without applying: whether its unsaved files go. Not while the
     * notification still holds the shader; the notification's close drops them then.
     */
    static boolean editorDrops(boolean editorSaved, boolean notificationHolds) {
        return !editorSaved && !notificationHolds;
    }

    /**
     * The notification closes, or another shader takes its place: whether the folder goes.
     * Not once a source was saved (applied from anywhere), nor while the editor still has it open.
     */
    static boolean notificationDrops(boolean hasSource, boolean editorHolds) {
        return !hasSource && !editorHolds;
    }
}
