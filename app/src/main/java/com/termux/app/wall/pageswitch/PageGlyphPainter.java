package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.termux.R;

/**
 * Draws a page glyph tinted one colour, with the page number centred in the Home and Terminal
 * glyphs' slot (sized and placed by {@link PageGlyphSlots}). Owns its paint and drawables, loaded once; {@link #draw} allocates nothing.
 * Numbers above 99 draw as "99+".
 */
public final class PageGlyphPainter {

    private static final int MAX_NUMBER = 99;

    private final Drawable[] mGlyphs = new Drawable[PageSwitchModel.Kind.values().length];
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] mNumbers = new String[MAX_NUMBER + 1];
    private static final String OVERFLOW = "99+";

    public PageGlyphPainter(@NonNull Context context) {
        mGlyphs[PageSwitchModel.Kind.HOME.ordinal()] = load(context, R.drawable.ic_page_home);
        mGlyphs[PageSwitchModel.Kind.TERMINAL.ordinal()] = load(context, R.drawable.ic_page_terminal);
        mGlyphs[PageSwitchModel.Kind.ASSISTANT.ordinal()] = load(context, R.drawable.ic_page_assistant);
        mGlyphs[PageSwitchModel.Kind.DISPLAY.ordinal()] = load(context, R.drawable.ic_page_display);
        mText.setTypeface(Typeface.create(PageGlyphSlots.DIGIT_FAMILY, PageGlyphSlots.DIGIT_STYLE));
        mText.setTextAlign(Paint.Align.CENTER);
        // Measured once at a reference size; both scale with the text size.
        mText.setTextSize(REFERENCE_SIZE);
        float widest = 0f;
        for (int n = 1; n <= MAX_NUMBER; n++) widest = Math.max(widest, mText.measureText(numberString(n)));
        mWidestPerSize = widest / REFERENCE_SIZE;
        android.graphics.Rect bounds = new android.graphics.Rect();
        mText.getTextBounds("0123456789", 0, 10, bounds);
        mDigitTopPerSize = bounds.top / REFERENCE_SIZE;
        mDigitHeightPerSize = bounds.height() / REFERENCE_SIZE;
    }

    private static final float REFERENCE_SIZE = 100f;
    /** The widest of "1".."99", the digits' top (negative, above the baseline) and height, per unit of text size. */
    private final float mWidestPerSize;
    private final float mDigitTopPerSize;
    private final float mDigitHeightPerSize;

    private static Drawable load(Context context, int res) {
        Drawable d = AppCompatResources.getDrawable(context, res);
        return d == null ? null : d.mutate();
    }

    private String numberString(int number) {
        if (number > MAX_NUMBER) return OVERFLOW;
        String s = mNumbers[number];
        if (s == null) {
            s = Integer.toString(number);
            mNumbers[number] = s;
        }
        return s;
    }

    /** Draws {@code kind}'s glyph in the square at ({@code left}, {@code top}) of side {@code size}. */
    public void draw(@NonNull Canvas canvas, @NonNull PageSwitchModel.Kind kind, int number,
                     float left, float top, float size, int color) {
        Drawable d = mGlyphs[kind.ordinal()];
        if (d == null) return;
        d.setTint(color);
        int l = Math.round(left);
        int t = Math.round(top);
        int s = Math.round(size);
        d.setBounds(l, t, l + s, t + s);
        d.draw(canvas);

        if (number <= 0) return;
        float cx, cy, slotW, slotH;
        if (kind == PageSwitchModel.Kind.HOME) {
            cx = PageGlyphSlots.HOME_SLOT_CX;
            cy = PageGlyphSlots.HOME_SLOT_CY;
            slotW = PageGlyphSlots.HOME_SLOT_W;
            slotH = PageGlyphSlots.HOME_SLOT_H;
        } else if (kind == PageSwitchModel.Kind.TERMINAL) {
            cx = PageGlyphSlots.TERMINAL_SLOT_CX;
            cy = PageGlyphSlots.TERMINAL_SLOT_CY;
            slotW = PageGlyphSlots.TERMINAL_SLOT_W;
            slotH = PageGlyphSlots.TERMINAL_SLOT_H;
        } else {
            return;
        }
        float unit = size / PageGlyphSlots.VIEWPORT;
        String text = numberString(number);
        // One size for every number: the digits fill the slot's height, unless the widest
        // two-digit number would then overrun its width.
        float byHeight = PageGlyphSlots.NUMBER_FILL * slotH * unit / Math.max(1e-3f, mDigitHeightPerSize);
        float byWidth = slotW * unit / Math.max(1e-3f, mWidestPerSize);
        float textSize = Math.min(byHeight, byWidth);
        mText.setTextSize(textSize);
        mText.setColor(color);
        // Centre the digits' measured box on the slot.
        float baseline = top + cy * unit
            - (mDigitTopPerSize + mDigitHeightPerSize / 2f) * textSize;
        canvas.drawText(text, left + cx * unit, baseline, mText);
    }
}
