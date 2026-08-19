package com.termux.shared.termux.font;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Renders Nerd Font icon code points with the bundled symbols face on app-owned chrome that
 * otherwise draws with a system font.
 *
 * <p>Session and window names routinely carry Nerd Font icons, but those live in the Private Use
 * Areas, which no Android system font covers — so a status-bar label or an extra-keys cap shows
 * tofu unless the user installed a patched font AND the surface happens to use it. The Nerd Fonts
 * patcher injects one shared symbol set (same code points, same outlines) into every patched font,
 * so the bundled symbols-only face draws exactly the glyph any user font would.
 *
 * <p>{@link #span} wraps each run of PUA code points in a typeface span pointing at the bundled
 * face and leaves everything else on the label's own font — the same trick the terminal window bar
 * plays with {@code symbol_map} faces.
 */
public final class NerdFontSpans {

    /** The symbols-only Nerd Font the app ships; also extracted for the terminal font config. */
    public static final String ASSET_PATH = "fonts/SymbolsNerdFontMono.ttf";

    @Nullable private static volatile Typeface sTypeface;
    private static volatile boolean sLoadFailed;

    private NerdFontSpans() {}

    /** The bundled symbols face, or null when the asset is missing (e.g. bare-module tests). */
    @Nullable
    public static Typeface typeface(@NonNull Context context) {
        Typeface typeface = sTypeface;
        if (typeface != null || sLoadFailed) return typeface;
        synchronized (NerdFontSpans.class) {
            typeface = sTypeface;
            if (typeface != null || sLoadFailed) return typeface;
            try {
                typeface = Typeface.createFromAsset(context.getApplicationContext().getAssets(),
                    ASSET_PATH);
                // createFromAsset returns DEFAULT rather than throwing on some devices.
                if (typeface == null || Typeface.DEFAULT.equals(typeface)) {
                    sLoadFailed = true;
                    return null;
                }
                sTypeface = typeface;
            } catch (RuntimeException e) {
                sLoadFailed = true;
                return null;
            }
            return typeface;
        }
    }

    /**
     * Whether a code point belongs to the Private Use Areas Nerd Fonts populate: the BMP PUA and
     * the plane-15 block carrying the Material Design icons. A PUA code point the bundled face
     * lacks falls through to the system fallback, which was tofu already — never worse.
     */
    public static boolean isNerdSymbol(int codePoint) {
        return (codePoint >= 0xE000 && codePoint <= 0xF8FF)
            || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD);
    }

    /**
     * The label with each run of Nerd Font code points spanned onto the bundled symbols face, or
     * the label itself — same instance, nothing allocated — when it carries none, or when the
     * bundled face is unavailable.
     */
    @NonNull
    public static CharSequence span(@NonNull Context context, @NonNull CharSequence label) {
        int length = label.length();
        if (length == 0) return label;
        // By code point, not by char: the Material Design ranges are astral, so a char walk would
        // see two non-PUA surrogates instead of one mapped icon.
        int index = 0;
        boolean hasSymbol = false;
        while (index < length) {
            int codePoint = Character.codePointAt(label, index);
            if (isNerdSymbol(codePoint)) {
                hasSymbol = true;
                break;
            }
            index += Character.charCount(codePoint);
        }
        if (!hasSymbol) return label;
        Typeface typeface = typeface(context);
        if (typeface == null) return label;

        SpannableString spanned = new SpannableString(label);
        int runStart = -1;
        index = 0;
        while (index < length) {
            int codePoint = Character.codePointAt(label, index);
            if (isNerdSymbol(codePoint)) {
                if (runStart < 0) runStart = index;
            } else if (runStart >= 0) {
                spanned.setSpan(new SymbolTypefaceSpan(typeface), runStart, index,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                runStart = -1;
            }
            index += Character.charCount(codePoint);
        }
        if (runStart >= 0)
            spanned.setSpan(new SymbolTypefaceSpan(typeface), runStart, length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spanned;
    }

    /**
     * Re-spans an editable in place: every span this class owns is dropped and the Nerd Font runs
     * of the current text are marked again. Editing a key label is the one place the text changes
     * under the spans, and rewriting the text to re-span it would fight the input connection.
     */
    public static void applyTo(@NonNull Context context, @NonNull android.text.Editable editable) {
        for (SymbolTypefaceSpan span : editable.getSpans(0, editable.length(),
                SymbolTypefaceSpan.class)) {
            editable.removeSpan(span);
        }
        int length = editable.length();
        if (length == 0) return;
        Typeface typeface = typeface(context);
        if (typeface == null) return;
        int runStart = -1;
        int index = 0;
        while (index < length) {
            int codePoint = Character.codePointAt(editable, index);
            if (isNerdSymbol(codePoint)) {
                if (runStart < 0) runStart = index;
            } else if (runStart >= 0) {
                editable.setSpan(new SymbolTypefaceSpan(typeface), runStart, index,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                runStart = -1;
            }
            index += Character.charCount(codePoint);
        }
        if (runStart >= 0)
            editable.setSpan(new SymbolTypefaceSpan(typeface), runStart, length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * A typeface swap for a span of text that carries the surrounding style over rather than
     * replacing it: a bold label keeps a (synthesized) bold icon, exactly as a styled TextView
     * would render it. {@code TypefaceSpan(Typeface)} would do this but needs API 28.
     */
    static final class SymbolTypefaceSpan extends MetricAffectingSpan {

        @NonNull private final Typeface mTypeface;

        SymbolTypefaceSpan(@NonNull Typeface typeface) {
            mTypeface = typeface;
        }

        @Override public void updateDrawState(@NonNull TextPaint paint) {
            apply(paint);
        }

        @Override public void updateMeasureState(@NonNull TextPaint paint) {
            apply(paint);
        }

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
