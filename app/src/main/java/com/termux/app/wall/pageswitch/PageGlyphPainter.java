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
 * glyphs' slot. Owns its paint and drawables, loaded once; {@link #draw} allocates nothing.
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
        mText.setTypeface(Typeface.DEFAULT_BOLD);
        mText.setTextAlign(Paint.Align.CENTER);
    }

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
        // Fit the digits' height to the slot, then shrink until the string fits the slot's width.
        mText.setTextSize(slotH * unit);
        float width = mText.measureText(text);
        float maxWidth = slotW * unit;
        if (width > maxWidth) mText.setTextSize(mText.getTextSize() * maxWidth / width);
        mText.setColor(color);
        // Digits are cap-height tall, about 0.72 of the text size: centre them on the slot.
        float baseline = top + cy * unit + mText.getTextSize() * 0.36f;
        canvas.drawText(text, left + cx * unit, baseline, mText);
    }
}
