package com.termux.app.chrome.wallpaper;

import android.os.SystemClock;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.scene.WallpaperMotion;

import java.lang.ref.WeakReference;

/**
 * A door from outside the launcher's window into the shader editor, open on one shader: Send to
 * AI's notification uses it for Edit (the saved shader) and its body tap (the shader being made,
 * in its photo flow). The intent carries it as one string ({@link #encode}); the launcher hands it
 * to {@link #send} and opens the wallpaper picker, whose {@link WallpaperLiveHost} opens the
 * shader, at once when it is up, else when it is built.
 *
 * <pre>
 *   AppearanceEditorEntry.shaderIntent(context, id, true);   // the notification's side
 *   if (ShaderEditorRequest.send(value)) resumeWallpaper();   // the launcher's side
 * </pre>
 */
public final class ShaderEditorRequest {

    /** Marks the photo flow; a shader id never holds a colon ({@code WallpaperMotion.safeName}). */
    static final String PHOTO_FLOW_PREFIX = "photo:";
    /**
     * How long a request waits for a picker to be built. The picker is built while the editor
     * opens, well inside this; a request older than this was for a picker that never came.
     */
    static final long WAIT_MS = 15_000L;

    @NonNull public final String id;
    /** Opens the editor as a new shader from a photo is opened (Send to AI and its note). */
    public final boolean photoFlow;

    ShaderEditorRequest(@NonNull String id, boolean photoFlow) {
        this.id = id;
        this.photoFlow = photoFlow;
    }

    /** The picker side that opens a request. */
    interface Target {
        void openRequested(@NonNull ShaderEditorRequest request);
    }

    @NonNull private static WeakReference<Target> sTarget = new WeakReference<>(null);
    @Nullable private static ShaderEditorRequest sPending;
    private static long sPendingAt;

    /** The intent extra's value for shader {@code id}. */
    @NonNull
    public static String encode(@NonNull String id, boolean photoFlow) {
        return photoFlow ? PHOTO_FLOW_PREFIX + id : id;
    }

    /** The request an extra names, or null when it names no shader. */
    @Nullable
    static ShaderEditorRequest parse(@Nullable String value) {
        if (value == null) return null;
        boolean photoFlow = value.startsWith(PHOTO_FLOW_PREFIX);
        String id = photoFlow ? value.substring(PHOTO_FLOW_PREFIX.length()) : value;
        if (WallpaperMotion.shader(id).kind != WallpaperMotion.Kind.SHADER) return null;
        return new ShaderEditorRequest(id, photoFlow);
    }

    /** Whether a request made at {@code madeAt} is still for the picker being built at {@code now}. */
    static boolean stillWaiting(long madeAt, long now) {
        return now - madeAt >= 0 && now - madeAt <= WAIT_MS;
    }

    /** Where a request lands in the picker. */
    enum Landing {
        /** The editor is open on that shader already: it stays as it is. */
        STAYS,
        /** The editor is open on another shader with changes: they are not thrown away, it stays. */
        KEEPS_EDITS,
        /** The shader is gone: the Shader list. */
        LIST,
        /** The shader's editor. */
        EDITOR
    }

    /**
     * @param editorOpen   the picker's shader editor is up
     * @param sameShader   on the requested shader
     * @param editorEdited with changes since it opened ({@link ShaderEditorPage#hasUnsavedEdits})
     * @param exists       the requested shader is on disk (saved, or its photo flow's folder)
     */
    @NonNull
    static Landing landing(boolean editorOpen, boolean sameShader, boolean editorEdited, boolean exists) {
        if (editorOpen && sameShader) return Landing.STAYS;
        if (editorOpen && editorEdited) return Landing.KEEPS_EDITS;
        return exists ? Landing.EDITOR : Landing.LIST;
    }

    /**
     * Hands the extra's request to the open picker, or keeps it for the next one built.
     *
     * @return true when it named a shader: the caller then opens the wallpaper picker
     */
    @MainThread
    public static boolean send(@Nullable String value) {
        ShaderEditorRequest request = parse(value);
        if (request == null) return false;
        Target target = sTarget.get();
        if (target != null) {
            sPending = null;
            target.openRequested(request);
        } else {
            sPending = request;
            sPendingAt = SystemClock.uptimeMillis();
        }
        return true;
    }

    /** A picker is up: later requests go to it; the one waiting for it, if any, is returned. */
    @MainThread
    @Nullable
    static ShaderEditorRequest attach(@NonNull Target target) {
        sTarget = new WeakReference<>(target);
        ShaderEditorRequest pending = sPending;
        sPending = null;
        return pending != null && stillWaiting(sPendingAt, SystemClock.uptimeMillis()) ? pending : null;
    }

    @MainThread
    static void detach(@NonNull Target target) {
        if (sTarget.get() == target) sTarget = new WeakReference<>(null);
    }
}
