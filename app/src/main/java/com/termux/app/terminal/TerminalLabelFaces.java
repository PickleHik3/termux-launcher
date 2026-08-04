package com.termux.app.terminal;

import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.termux.TermuxConstants;
import com.termux.view.TerminalRenderer;

import java.io.File;

/**
 * The faces app-owned chrome draws terminal text with: the regular face plus the configured
 * {@code symbol_map} ranges, as one value taken from the same font config the panes are given.
 *
 * <p>One holder rather than a load per surface. {@link #publish} lets the side that already resolved
 * the config hand the result over, and until it does {@link #current} resolves the very same config
 * itself — the window row must never end up on a face the terminal is not using, because that is
 * precisely how an icon code point becomes tofu.
 */
public final class TerminalLabelFaces {

    private static final TerminalRenderer.SymbolMap[] NO_SYMBOL_MAPS =
        new TerminalRenderer.SymbolMap[0];

    /** Config files are stat'ed at most this often; a font change is not worth a syscall per frame. */
    private static final long RECHECK_INTERVAL_NANOS = 250L * 1000L * 1000L;

    @NonNull public final Typeface regular;
    @NonNull public final TerminalRenderer.SymbolMap[] symbolMaps;

    @Nullable private static TerminalLabelFaces sCurrent;
    /** Once the loading side has spoken, this holder stops resolving anything on its own. */
    private static boolean sPublished;
    private static long sSignature;
    private static long sCheckedAtNanos;

    private TerminalLabelFaces(@NonNull Typeface regular,
                               @NonNull TerminalRenderer.SymbolMap[] symbolMaps) {
        this.regular = regular;
        this.symbolMaps = symbolMaps;
    }

    /**
     * Hand over already-resolved faces, e.g. from the font reload that pushes them into the panes.
     * The instance is shared, so callers comparing identity see a change exactly when there is one.
     */
    public static synchronized void publish(@NonNull Typeface regular,
                                            @NonNull TerminalRenderer.SymbolMap[] symbolMaps) {
        TerminalLabelFaces current = sCurrent;
        if (current != null && current.regular == regular
            && sameMaps(current.symbolMaps, symbolMaps)) {
            sPublished = true;
            return;
        }
        sCurrent = new TerminalLabelFaces(regular,
            symbolMaps.length == 0 ? NO_SYMBOL_MAPS : symbolMaps.clone());
        sPublished = true;
    }

    /**
     * The faces to draw with now. The same instance is returned while nothing has changed, so a
     * caller can treat identity as "still current" and skip rebuilding its views.
     */
    @NonNull
    public static synchronized TerminalLabelFaces current() {
        TerminalLabelFaces current = sCurrent;
        if (sPublished && current != null) return current;
        long now = System.nanoTime();
        if (current != null && now - sCheckedAtNanos < RECHECK_INTERVAL_NANOS) return current;
        sCheckedAtNanos = now;
        long signature = configSignature();
        if (current != null && signature == sSignature) return current;
        sSignature = signature;
        current = resolve();
        sCurrent = current;
        return current;
    }

    @NonNull
    private static TerminalLabelFaces resolve() {
        try {
            TerminalFontLoader.Faces faces = TerminalFontLoader.load(TerminalFontConfig.load());
            return new TerminalLabelFaces(faces.regular, faces.symbolMaps);
        } catch (Exception ignored) {
            // A font file the loader cannot read must cost the row its icons, never its labels.
            return new TerminalLabelFaces(Typeface.MONOSPACE, NO_SYMBOL_MAPS);
        }
    }

    /**
     * Cheap stand-in for "the font configuration changed": the mtime and size of every file a load
     * would read. Drop-ins are combined additively so the order listFiles happens to return does not
     * look like an edit.
     */
    private static long configSignature() {
        long signature = mix(17L, new File(TerminalFontConfig.FILE_PATH));
        File dropInDir = new File(TerminalFontConfig.DROP_IN_DIR_PATH);
        signature = mix(signature, dropInDir);
        File[] dropIns = dropInDir.listFiles();
        if (dropIns != null) {
            for (File dropIn : dropIns) signature += mix(31L, dropIn);
        }
        signature = mix(signature, TermuxConstants.TERMUX_FONT_FILE);
        return mix(signature, TermuxConstants.TERMUX_ITALIC_FONT_FILE);
    }

    private static long mix(long signature, @NonNull File file) {
        return signature * 31L + file.lastModified() * 31L + file.length();
    }

    private static boolean sameMaps(@NonNull TerminalRenderer.SymbolMap[] left,
                                    @NonNull TerminalRenderer.SymbolMap[] right) {
        if (left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) {
            TerminalRenderer.SymbolMap a = left[i];
            TerminalRenderer.SymbolMap b = right[i];
            if (a.firstCodePoint != b.firstCodePoint || a.lastCodePoint != b.lastCodePoint
                || a.typeface != b.typeface) return false;
        }
        return true;
    }

    /** Static state outlives a test, so a test that publishes has to hand the holder back. */
    @VisibleForTesting
    public static synchronized void resetForTests() {
        sCurrent = null;
        sPublished = false;
        sSignature = 0L;
        sCheckedAtNanos = 0L;
    }
}
