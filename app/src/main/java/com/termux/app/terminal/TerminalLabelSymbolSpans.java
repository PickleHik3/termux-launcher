package com.termux.app.terminal;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.view.TerminalRenderer;

/**
 * Draws an app-owned label the way the terminal draws a line of cells: ordinary text in the regular
 * face, and every code point a {@code symbol_map} claims in that map's own face.
 *
 * <p>Window names carry Nerd Font icons. While {@code font.ttf} was a patched build one face covered
 * both, but a config that routes the icon ranges to a dedicated symbols font leaves the regular face
 * with no glyph for them — tofu. A text view can only reach a second face through spans, so a run of
 * mapped code points gets one span carrying the mapped typeface.
 */
final class TerminalLabelSymbolSpans {

    private TerminalLabelSymbolSpans() {}

    /**
     * The label with one {@link SymbolTypefaceSpan} per run of code points sharing a mapped face, or
     * the label itself — same instance, nothing allocated — when no run is mapped.
     */
    @NonNull
    static CharSequence apply(@NonNull CharSequence label,
                              @Nullable TerminalRenderer.SymbolMap[] symbolMaps) {
        int length = label.length();
        if (length == 0 || symbolMaps == null || symbolMaps.length == 0) return label;
        // The common case is a pure-ASCII label, and no icon range can reach it. Only a map that
        // actually claims an ASCII code point makes the scan below worth running.
        if (!mapsAscii(symbolMaps) && isAscii(label)) return label;

        SpannableString spanned = null;
        Typeface runFace = null;
        int runStart = 0;
        int index = 0;
        while (index < length) {
            // By code point, not by char: the Material Design Nerd ranges are astral, so a char walk
            // would see two unmapped surrogates instead of one mapped icon.
            int codePoint = Character.codePointAt(label, index);
            Typeface face = faceFor(symbolMaps, codePoint);
            if (face != runFace) {
                spanned = closeRun(spanned, label, runFace, runStart, index);
                runFace = face;
                runStart = index;
            }
            index += Character.charCount(codePoint);
        }
        spanned = closeRun(spanned, label, runFace, runStart, length);
        return spanned == null ? label : spanned;
    }

    /** Spans the finished run, allocating the spannable only once a run needs one. */
    @Nullable
    private static SpannableString closeRun(@Nullable SpannableString spanned,
                                            @NonNull CharSequence label, @Nullable Typeface face,
                                            int start, int end) {
        if (face == null || end <= start) return spanned;
        // Copy construction, so a label that already carries spans keeps them.
        SpannableString target = spanned == null ? new SpannableString(label) : spanned;
        target.setSpan(new SymbolTypefaceSpan(face), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return target;
    }

    /** The face a code point draws from, resolved like the renderer: a later range wins. */
    @Nullable
    private static Typeface faceFor(@NonNull TerminalRenderer.SymbolMap[] symbolMaps,
                                    int codePoint) {
        for (int i = symbolMaps.length - 1; i >= 0; i--) {
            TerminalRenderer.SymbolMap map = symbolMaps[i];
            if (codePoint >= map.firstCodePoint && codePoint <= map.lastCodePoint)
                return map.typeface;
        }
        return null;
    }

    private static boolean mapsAscii(@NonNull TerminalRenderer.SymbolMap[] symbolMaps) {
        for (TerminalRenderer.SymbolMap map : symbolMaps) {
            if (map.firstCodePoint < 0x80) return true;
        }
        return false;
    }

    private static boolean isAscii(@NonNull CharSequence label) {
        for (int i = 0; i < label.length(); i++) {
            if (label.charAt(i) >= 0x80) return false;
        }
        return true;
    }

    /**
     * A typeface swap for a span of text. {@code TypefaceSpan(Typeface)} would do this but needs API
     * 28, and this app runs from 26, so the paints are touched directly — both of them, or the run
     * would be measured with one face and drawn with another.
     */
    static final class SymbolTypefaceSpan extends MetricAffectingSpan {

        @NonNull private final Typeface mTypeface;

        SymbolTypefaceSpan(@NonNull Typeface typeface) {
            mTypeface = typeface;
        }

        @NonNull
        Typeface getTypeface() {
            return mTypeface;
        }

        @Override public void updateDrawState(@NonNull TextPaint paint) {
            apply(paint);
        }

        @Override public void updateMeasureState(@NonNull TextPaint paint) {
            apply(paint);
        }

        /**
         * The style already on the paint is carried over rather than replaced: the tab sets
         * {@code Typeface.BOLD} on the selected window, and a bare setTypeface here would quietly
         * un-bold the icon while the text beside it stayed bold. Where the symbols face has no such
         * style of its own, the paint synthesizes it, exactly as a styled TextView would.
         */
        private void apply(@NonNull TextPaint paint) {
            Typeface current = paint.getTypeface();
            int style = current == null ? Typeface.NORMAL : current.getStyle();
            Typeface styled = style == Typeface.NORMAL
                ? mTypeface : Typeface.create(mTypeface, style);
            int synthesize = style & ~styled.getStyle();
            if ((synthesize & Typeface.BOLD) != 0) paint.setFakeBoldText(true);
            if ((synthesize & Typeface.ITALIC) != 0) paint.setTextSkewX(-0.25f);
            paint.setTypeface(styled);
        }
    }
}
