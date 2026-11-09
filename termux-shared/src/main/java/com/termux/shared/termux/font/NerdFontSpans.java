package com.termux.shared.termux.font;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.text.style.ReplacementSpan;
import android.util.SparseIntArray;

import androidx.annotation.DrawableRes;
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

    /**
     * Code points that stand for a drawable rather than a font glyph (an icon the symbols face
     * lacks). Registered once at startup by the app; read on the UI thread when a label is set.
     */
    private static final SparseIntArray sDrawableSymbols = new SparseIntArray();

    private NerdFontSpans() {}

    /**
     * Makes {@code codePoint} (keep it outside the Nerd Font ranges, e.g. plane 16) draw as the
     * drawable wherever a label goes through {@link #span}: sized to the text and tinted with the
     * text's current colour, so it follows the key ink and the pressed state like a font glyph.
     */
    public static void registerDrawableSymbol(int codePoint, @DrawableRes int drawableRes) {
        synchronized (sDrawableSymbols) {
            sDrawableSymbols.put(codePoint, drawableRes);
        }
    }

    private static int drawableFor(int codePoint) {
        synchronized (sDrawableSymbols) {
            return sDrawableSymbols.get(codePoint, 0);
        }
    }

    /** {@code spanned} with a tinted-drawable span over every registered code point of the label. */
    private static void spanDrawables(@NonNull Context context, @NonNull CharSequence label,
                                      @NonNull SpannableString spanned) {
        int length = label.length();
        int index = 0;
        while (index < length) {
            int codePoint = Character.codePointAt(label, index);
            int count = Character.charCount(codePoint);
            int res = drawableFor(codePoint);
            if (res != 0) {
                Drawable drawable = context.getDrawable(res);
                if (drawable != null) {
                    spanned.setSpan(new TintedDrawableSpan(drawable.mutate()), index, index + count,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            index += count;
        }
    }

    private static boolean hasDrawableSymbol(@NonNull CharSequence label) {
        int length = label.length();
        int index = 0;
        while (index < length) {
            int codePoint = Character.codePointAt(label, index);
            if (drawableFor(codePoint) != 0) return true;
            index += Character.charCount(codePoint);
        }
        return false;
    }

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
        boolean hasDrawable = hasDrawableSymbol(label);
        if (!hasSymbol && !hasDrawable) return label;
        Typeface typeface = hasSymbol ? typeface(context) : null;
        if (typeface == null && !hasDrawable) return label;

        SpannableString spanned = new SpannableString(label);
        if (hasDrawable) spanDrawables(context, label, spanned);
        if (typeface == null) return spanned;
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
     * Draws a drawable in place of one code point: a square the size of the text, centred on the
     * capital height, filled with the paint's colour so it follows the text colour and its state.
     * The tint is only re-applied when the colour changes, so a draw allocates nothing.
     */
    static final class TintedDrawableSpan extends ReplacementSpan {

        @NonNull private final Drawable mDrawable;
        private int mTint;
        private boolean mTinted;

        TintedDrawableSpan(@NonNull Drawable drawable) {
            mDrawable = drawable;
        }

        @Override public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                                     @Nullable Paint.FontMetricsInt fm) {
            return Math.round(paint.getTextSize());
        }

        @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                                   float x, int top, int y, int bottom, @NonNull Paint paint) {
            int size = Math.round(paint.getTextSize());
            int color = paint.getColor();
            if (!mTinted || color != mTint) {
                mDrawable.setTint(color | 0xFF000000);
                mTint = color;
                mTinted = true;
            }
            mDrawable.setAlpha(color >>> 24);
            // The middle of a capital sits about 0.36 em above the baseline, which is where the
            // Nerd glyphs centre their icons.
            int centre = Math.round(y - 0.36f * size);
            int left = Math.round(x);
            mDrawable.setBounds(left, centre - size / 2, left + size, centre - size / 2 + size);
            mDrawable.draw(canvas);
        }
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
