package com.termux.app.fragments.settings;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.PathParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The layout assets pack's artwork (project-docs/reference/layout-assets, {@code generate.py}),
 * ported as Canvas painters. Each painter fills the bounds the shape model gave one piece and
 * nothing else: the card behind it, its corners and its outline are the shape's, not drawn here.
 *
 * <p>Every painter works in pack units, about one dp each, by scaling the canvas by {@code k}
 * canvas pixels per unit, and places its shapes from the bounds it is given: a bar's symbols from
 * its length, the terminal's prompt from the pane's bottom edge. Sizes are the pack's own and are
 * never derived from the bounds except to fit them, so symbols, glyphs and keys keep their
 * proportions whatever the bounds are and nothing is a stretched bitmap. What does not fit is left
 * out, not squeezed.
 */
final class LayoutCanvasArtwork {

    /**
     * The pack's palette roles, resolved on the live Material scheme by the caller (SPEC section 5:
     * canvas colorSurface, surface colorSurfaceContainerHigh, terminal colorSurfaceContainerLow, key
     * colorSurfaceContainerHighest, line colorOutlineVariant, text colorOnSurface, muted
     * colorOnSurfaceVariant, accent colorPrimary, accent_bg colorPrimaryContainer, secondary
     * colorTertiary, secondary_bg colorTertiaryContainer).
     */
    static final class Palette {
        @ColorInt int canvas;
        @ColorInt int surface;
        @ColorInt int terminal;
        @ColorInt int key;
        @ColorInt int line;
        @ColorInt int text;
        @ColorInt int muted;
        @ColorInt int accent;
        @ColorInt int accentBg;
        @ColorInt int secondary;
        @ColorInt int secondaryBg;
    }

    /** The dock's seven symbols and the extra keys' seven, as the pack lists them. */
    static final String[] DOCK_GLYPHS = {LayoutCanvasGlyphs.PHONE, LayoutCanvasGlyphs.CHAT,
        LayoutCanvasGlyphs.GLOBE, LayoutCanvasGlyphs.TERMINAL, LayoutCanvasGlyphs.PLAY,
        LayoutCanvasGlyphs.MONITOR, LayoutCanvasGlyphs.GRID};
    static final String[] TOOL_GLYPHS = {LayoutCanvasGlyphs.KEYBOARD, LayoutCanvasGlyphs.MOUSE,
        LayoutCanvasGlyphs.HOME, LayoutCanvasGlyphs.TERMINAL, LayoutCanvasGlyphs.MONITOR,
        LayoutCanvasGlyphs.GRID, LayoutCanvasGlyphs.LIST};
    /** How many symbols the pack draws in a bar; what a bar draws when the real count is unknown. */
    static final int PACK_SLOTS = 7;
    /** The A-Z index's sample letters, with the one it is on lit. */
    static final String ALPHABET = "ADGJMPSVZ";
    static final int ALPHABET_LIT = 4;

    /** The pack's keyboard: side and bottom margin, top margin, the gap between keys, a key's corner. */
    private static final float KEY_MARGIN = 10f;
    private static final float KEY_TOP = 10f;
    private static final float KEY_GAP = 4f;
    private static final float KEY_CORNER = 4f;

    private static final Typeface MEDIUM =
        Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final String MOON = "M17 3a9 9 0 1 0 4 14A9 9 0 0 1 17 3Z";
    private static final String SHIFT = "M-4 3v-5h-3l7-5 7 5h-3v5";
    private static final String BACKSPACE = "M3 -4l-5 4 5 4";
    private static final String ENTER = "M7 -4v5h-14m4-4-4 4 4 4";
    private static final Map<String, Path> PATHS = new HashMap<>();

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    LayoutCanvasArtwork() {
        mFill.setStyle(Paint.Style.FILL);
        mStroke.setStyle(Paint.Style.STROKE);
        mText.setStyle(Paint.Style.FILL);
        mText.setTypeface(MEDIUM);
    }

    // ---------------------------------------------------------------------------- status bar

    /**
     * The status bar, collapsed or expanded from {@code status_compact}. Collapsed is one row: the
     * workspace count, the current tab, a plus, the two readings and the moon. Expanded puts the
     * clock and the media tile over that row. What does not fit the bar's length is left out.
     */
    void status(@NonNull Canvas canvas, @NonNull RectF box, boolean compact,
                @NonNull Palette p, float k) {
        float w = box.width() / k;
        float h = box.height() / k;
        if (w < 60f || h < 18f) return;
        int saved = begin(canvas, box, k);
        boolean expanded = !compact && h > 50f;
        // A collapsed bar's row stands in its middle; an expanded one's under the clock.
        float cy = expanded ? h - 17f : h / 2f;
        rr(canvas, 10f, cy - 9f, 18f, 18f, 5f, p.key);
        text(canvas, "2", 19f, cy + 3.5f, 10f, p.text, Paint.Align.CENTER);
        rr(canvas, 33f, cy - 9f, 38f, 18f, 5f, p.accentBg);
        text(canvas, "home", 52f, cy + 3.5f, 10f, p.accent, Paint.Align.CENTER);
        if (w >= 110f) {
            line(canvas, 80f, cy, 88f, cy, 1.6f, p.text);
            line(canvas, 84f, cy - 4f, 84f, cy + 4f, 1.6f, p.text);
        }
        if (w >= 200f) {
            line(canvas, w - 82f, cy - 2f, w - 70f, cy - 2f, 1.8f, p.accent);
            line(canvas, w - 61f, cy - 2f, w - 51f, cy - 2f, 1.8f, p.secondary);
            fillPath(canvas, MOON, w - 45f, cy - 6f, 0.5f, p.secondary);
        }
        if (expanded) {
            text(canvas, "08:24", 14f, 27f, 22f, p.text, Paint.Align.LEFT);
            if (w >= 230f) {
                rr(canvas, 121f, 10f, w - 134f, 28f, 5f, p.key);
                LayoutCanvasGlyphs.draw(canvas, mStroke, LayoutCanvasGlyphs.PLAY, w - 30f, 24f,
                    16f, p.text);
            }
        }
        canvas.restoreToCount(saved);
    }

    /**
     * A status bar standing as a column, which the pack has no art for: the hour over the minutes at
     * its top and the readings at its foot, upright, in a column's own width.
     */
    void statusColumn(@NonNull Canvas canvas, @NonNull RectF box, @NonNull Palette p, float k) {
        float kk = Math.max(0.01f, Math.min(k, box.width() / 40f));
        int saved = canvas.save();
        canvas.clipRect(box);
        float cx = box.centerX();
        float top = box.top + 12f * kk;
        mText.setColor(p.text);
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setTextSize(9f * kk);
        float lineH = 10f * kk;
        if (top + 2f * lineH < box.bottom - 24f * kk) {
            canvas.drawText("07", cx, top + lineH - 2f * kk, mText);
            canvas.drawText("34", cx, top + 2f * lineH - 2f * kk, mText);
        }
        if (box.height() > 40f * kk) readingsStacked(canvas, cx, box.bottom - 12f * kk, kk, p);
        canvas.restoreToCount(saved);
    }

    /** The battery over the signal's three rising bars, centred on {@code cx}, in pixels. */
    private void readingsStacked(@NonNull Canvas canvas, float cx, float cy, float kk,
                                 @NonNull Palette p) {
        mFill.setColor(ColorUtils.setAlphaComponent(p.muted, 115));
        float barW = 1.4f * kk;
        float barGap = 1.1f * kk;
        float batteryW = 7f * kk;
        float batteryH = 4f * kk;
        mRect.set(cx - batteryW / 2f, cy - batteryH - 2.5f * kk, cx + batteryW / 2f,
            cy - 2.5f * kk);
        canvas.drawRoundRect(mRect, kk, kk, mFill);
        float x = cx - (3f * barW + 2f * barGap) / 2f;
        float base = cy + 2.5f * kk + 5.5f * kk;
        for (int i = 0; i < 3; i++) {
            float h = (2.5f + 1.5f * i) * kk;
            mRect.set(x, base - h, x + barW, base);
            canvas.drawRoundRect(mRect, barW / 2f, barW / 2f, mFill);
            x += barW + barGap;
        }
    }

    // ----------------------------------------------------------------------------------- dock

    /**
     * The app icons bar (layout editor v2, DECISIONS item 8): always the pack's seven fixed
     * placeholders, tonal circles with generic glyphs (phone, chat, globe, terminal, play, display,
     * grid), along a row or down a column, upright either way, as in
     * {@code docked/app-icons-*.svg}. It never draws the user's pinned apps and never follows
     * their count; a bar too short for seven draws as many as fit ({@link #dockSlotsFor}).
     */
    void dock(@NonNull Canvas canvas, @NonNull RectF box, boolean vertical,
              @NonNull Palette p, float k) {
        float w = box.width() / k;
        float h = box.height() / k;
        float length = vertical ? h : w;
        float cross = vertical ? w : h;
        int n = dockSlotsFor(length);
        if (n <= 0) return;
        float pitch = (length - 16f) / n;
        float size = Math.min(32f, Math.min(cross - 12f, pitch - 4f));
        if (size < 6f) return;
        int saved = begin(canvas, box, k);
        for (int i = 0; i < n; i++) {
            int role = i % PACK_SLOTS;
            float along = 8f + (i + 0.5f) * pitch;
            float cx = vertical ? w / 2f : along;
            float cy = vertical ? along : h / 2f;
            boolean primary = role == 0 || role == 3 || role == 4;
            boolean tertiary = role == 1 || role == 2;
            circle(canvas, cx, cy, size / 2f,
                primary ? p.accentBg : tertiary ? p.secondaryBg : p.key);
            LayoutCanvasGlyphs.draw(canvas, mStroke, DOCK_GLYPHS[role], cx, cy, size * 0.62f,
                primary ? p.accent : p.text);
        }
        canvas.restoreToCount(saved);
    }

    /**
     * How many placeholder icons the app icons bar draws along {@code length} pack units: the
     * pack's seven, whatever the user has pinned, or as many as fit a bar too short for seven.
     */
    @VisibleForTesting
    static int dockSlotsFor(float length) {
        return slotsFor(PACK_SLOTS, length, 20f);
    }

    // ------------------------------------------------------------------------------------ A-Z

    /** Nine letters spread along a row or down a column, upright, with M on its highlight. */
    void alphabet(@NonNull Canvas canvas, @NonNull RectF box, boolean vertical,
                  @NonNull Palette p, float k) {
        float w = box.width() / k;
        float h = box.height() / k;
        float length = vertical ? h : w;
        if (length < 70f) return;
        int n = ALPHABET.length();
        float pitch = (length - (vertical ? 28f : 24f)) / (n - 1);
        // Where the letters stand too close, every second one is left out; M stays.
        int stride = pitch < 12f ? 2 : 1;
        int saved = begin(canvas, box, k);
        for (int i = 0; i < n; i += stride) {
            float along = (vertical ? 14f : 12f) + i * pitch;
            float cx = vertical ? w / 2f : along;
            float cy = vertical ? along : h / 2f;
            boolean lit = i == ALPHABET_LIT;
            if (lit) rr(canvas, cx - 9f, cy - 9f, 18f, 18f, 5f, p.accentBg);
            text(canvas, String.valueOf(ALPHABET.charAt(i)), cx, cy + 4f, 11f,
                lit ? p.accent : p.text, Paint.Align.CENTER);
        }
        canvas.restoreToCount(saved);
    }

    // ------------------------------------------------------------------------------ extra keys

    /**
     * The extra keys as glyphs along a row or down a column, one on its accent background. The
     * pack's seven repeat to the real {@code count}, as many as fit; negative draws the seven.
     */
    void extraKeys(@NonNull Canvas canvas, @NonNull RectF box, boolean vertical, int count,
                   @NonNull Palette p, float k) {
        float w = box.width() / k;
        float h = box.height() / k;
        float length = vertical ? h : w;
        float cross = vertical ? w : h;
        boolean labels = !mKeys.isEmpty();
        int n = slotsForContent(mKeys.size(), count, length, 18f);
        if (n <= 0) return;
        float pitch = (length - 16f) / n;
        float size = Math.min(22f, Math.min(cross - 12f, pitch - 4f));
        if (size < 6f) return;
        int active = Math.min(3, n - 1);
        int saved = begin(canvas, box, k);
        for (int i = 0; i < n; i++) {
            if (labels) {
                float along = 8f + (i + 0.5f) * pitch;
                float cx = vertical ? w / 2f : along;
                float cy = vertical ? along : h / 2f;
                boolean lit = i == active;
                if (lit) {
                    rr(canvas, cx - size / 2f - 3f, cy - size / 2f - 3f, size + 6f, size + 6f, 5f,
                        p.accentBg);
                }
                LayoutCanvasView.KeySlot key = mKeys.get(i);
                int ink = lit ? p.accent : p.text;
                if (key.hasIcon()) {
                    // The icon the real bar draws, in the slot's square and the canvas's ink.
                    android.graphics.drawable.Drawable icon = key.icon;
                    android.graphics.Rect old = icon.copyBounds();
                    icon.setBounds(Math.round(cx - size / 2f), Math.round(cy - size / 2f),
                        Math.round(cx + size / 2f), Math.round(cy + size / 2f));
                    icon.setColorFilter(new android.graphics.PorterDuffColorFilter(ink,
                        android.graphics.PorterDuff.Mode.SRC_IN));
                    icon.draw(canvas);
                    icon.setBounds(old);
                } else if (key.hasLabel()) {
                    int maxChars = Math.max(1, (int) ((vertical ? cross - 8f : pitch) / 6f));
                    text(canvas, fitLabel(key.label, maxChars), cx, cy + 3.5f, 10f, ink,
                        Paint.Align.CENTER);
                } else {
                    LayoutCanvasGlyphs.draw(canvas, mStroke, TOOL_GLYPHS[i % PACK_SLOTS], cx, cy,
                        size * 0.72f, ink);
                }
                continue;
            }
            float along = 8f + (i + 0.5f) * pitch;
            float cx = vertical ? w / 2f : along;
            float cy = vertical ? along : h / 2f;
            boolean lit = i == active;
            if (lit) {
                rr(canvas, cx - size / 2f - 3f, cy - size / 2f - 3f, size + 6f, size + 6f, 5f,
                    p.accentBg);
            }
            LayoutCanvasGlyphs.draw(canvas, mStroke, TOOL_GLYPHS[i % PACK_SLOTS], cx, cy,
                size * 0.72f, lit ? p.accent : p.text);
        }
        canvas.restoreToCount(saved);
    }

    // ------------------------------------------------------------------------------- terminal

    /**
     * The terminal: the live prompt with its cursor anchored to the pane's bottom edge wherever
     * that is, and above it the first prompt and a few lines of output, each drawn only while the
     * pane is tall enough to hold it, so they stop first when the pane gets short.
     */
    void terminal(@NonNull Canvas canvas, @NonNull RectF pane, @NonNull Palette p, float k) {
        float w = pane.width() / k;
        float h = pane.height() / k;
        if (w < 60f || h < 40f) return;
        int saved = begin(canvas, pane, k);
        LayoutCanvasGlyphs.draw(canvas, mStroke, LayoutCanvasGlyphs.TERMINAL, 22f, h - 27f, 16f,
            p.secondary);
        rr(canvas, 37f, h - 32f, 4f, 11f, 1f, p.accent);
        if (h >= 80f) {
            LayoutCanvasGlyphs.draw(canvas, mStroke, LayoutCanvasGlyphs.TERMINAL, 22f, 26f, 16f,
                p.secondary);
            rr(canvas, 37f, 24f, Math.min(70f, w - 55f), 3f, 1.5f, p.accent);
            float[] widths = {116f, 88f, 102f};
            for (int i = 0; i < widths.length; i++) {
                float y = 46f + i * 14f;
                // A line stops short of the live prompt's glyph, never runs into it.
                if (y + 3f > h - 41f) break;
                rr(canvas, 18f, y, Math.min(widths[i], w - 36f), 3f, 1.5f, p.key);
            }
        }
        canvas.restoreToCount(saved);
    }

    // ------------------------------------------------------------------------------- keyboard

    /**
     * The keys of one keyboard card (or one half of a split one): the real key rows laid across
     * {@code units} key units, the pitch taken from the card's width and the row pitch from its
     * height less the chin. The space bar wears the accent, the enter key the secondary, and shift,
     * backspace and enter carry their glyphs.
     */
    void keyboard(@NonNull Canvas canvas, @NonNull RectF card, float chinPx,
                  @NonNull List<List<LayoutCanvasGeometry.KeyCell>> rows, float units,
                  @NonNull Palette p, float k) {
        float w = card.width() / k;
        float h = (card.height() - chinPx) / k;
        float pitch = (w - 2f * KEY_MARGIN) / units;
        float rowPitch = (h - KEY_TOP - KEY_MARGIN) / rows.size();
        float keyH = rowPitch - KEY_GAP;
        if (keyH < 3f || pitch < 4f) return;
        int saved = begin(canvas, card, k);
        float radius = Math.min(KEY_CORNER, keyH / 3f);
        for (int r = 0; r < rows.size(); r++) {
            float y = KEY_TOP + r * rowPitch;
            for (LayoutCanvasGeometry.KeyCell cell : rows.get(r)) {
                key(canvas, cell, KEY_MARGIN + cell.x * pitch, y, cell.w * pitch - KEY_GAP, keyH,
                    radius, p);
            }
        }
        canvas.restoreToCount(saved);
    }

    /** One key in pack units: its tone by role, and the glyph the role carries if it fits. */
    private void key(@NonNull Canvas canvas, @NonNull LayoutCanvasGeometry.KeyCell cell, float x,
                     float y, float w, float h, float radius, @NonNull Palette p) {
        int fill;
        switch (cell.role) {
            case SPACE: fill = p.accentBg; break;
            case ENTER: fill = p.secondaryBg; break;
            default: fill = p.key; break;
        }
        rr(canvas, x, y, w, h, radius, fill);
        boolean roomy = w >= 18f && h >= 12f;
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        switch (cell.role) {
            case SHIFT:
                if (roomy) strokePath(canvas, SHIFT, cx, cy, 1.4f, p.text);
                break;
            case BACKSPACE:
                if (roomy) strokePath(canvas, BACKSPACE, cx, cy, 1.6f, p.text);
                break;
            case SPACE: {
                float bar = Math.min(w * 0.3f, 30f);
                rr(canvas, cx - bar / 2f, cy - 1f, bar, 2f, 1f, p.accent);
                break;
            }
            case ENTER:
                if (roomy) {
                    float gx = w >= 40f ? x + w - 22f : cx;
                    strokePath(canvas, ENTER, gx, cy, 1.8f, p.secondary);
                }
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------------------ primitives

    /** How many symbols a bar of this length shows: the real count, or the pack's, as many as fit. */
    @VisibleForTesting
    static int slotsFor(int count, float length, float minPitch) {
        int wanted = count < 0 ? PACK_SLOTS : count;
        int fit = Math.max(1, (int) Math.floor((length - 16f) / minPitch));
        return Math.min(wanted, fit);
    }

    /**
     * What a bar draws: the supplied real items capped by how many fit, or, with none supplied,
     * the pack's glyph count rule ({@link #slotsFor}).
     */
    static int slotsForContent(int supplied, int count, float length, float minPitch) {
        if (supplied <= 0) return slotsFor(count, length, minPitch);
        int fit = Math.max(1, (int) Math.floor((length - 16f) / minPitch));
        return Math.min(supplied, fit);
    }

    /** A key label cut to {@code maxChars} so it stays inside its slot. */
    @NonNull
    static String fitLabel(@NonNull String label, int maxChars) {
        if (maxChars < 1) return "";
        return label.length() <= maxChars ? label : label.substring(0, maxChars);
    }

    private java.util.List<LayoutCanvasView.KeySlot> mKeys = java.util.Collections.emptyList();

    /**
     * The extra keys' first row as the real bar shows it; an empty list keeps the pack's glyphs.
     * Extra keys stay live (DECISIONS item 8); the app icons bar never takes real content.
     */
    void setKeySlots(@NonNull java.util.List<LayoutCanvasView.KeySlot> keys) {
        mKeys = keys;
    }

    /** Opens a frame whose origin is the box's corner and whose unit is {@code k} pixels. */
    private static int begin(@NonNull Canvas canvas, @NonNull RectF box, float k) {
        int saved = canvas.save();
        canvas.clipRect(box);
        canvas.translate(box.left, box.top);
        canvas.scale(k, k);
        return saved;
    }

    private void rr(@NonNull Canvas canvas, float x, float y, float w, float h, float r,
                    @ColorInt int color) {
        if (w <= 0f || h <= 0f) return;
        mFill.setColor(color);
        mRect.set(x, y, x + w, y + h);
        float radius = Math.min(r, Math.min(w, h) / 2f);
        canvas.drawRoundRect(mRect, radius, radius, mFill);
    }

    private void circle(@NonNull Canvas canvas, float cx, float cy, float r, @ColorInt int color) {
        if (r <= 0f) return;
        mFill.setColor(color);
        canvas.drawCircle(cx, cy, r, mFill);
    }

    private void line(@NonNull Canvas canvas, float x1, float y1, float x2, float y2, float width,
                      @ColorInt int color) {
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
        mStroke.setStrokeWidth(width);
        mStroke.setColor(color);
        canvas.drawLine(x1, y1, x2, y2, mStroke);
    }

    private void text(@NonNull Canvas canvas, @NonNull String value, float x, float baseline,
                      float size, @ColorInt int color, @NonNull Paint.Align align) {
        mText.setColor(color);
        mText.setTextAlign(align);
        mText.setTextSize(size);
        canvas.drawText(value, x, baseline, mText);
    }

    /** An SVG path stroked round-capped, its origin moved to {@code (x, y)}. */
    private void strokePath(@NonNull Canvas canvas, @NonNull String data, float x, float y,
                            float width, @ColorInt int color) {
        Path path = path(data);
        if (path == null) return;
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
        mStroke.setStrokeJoin(Paint.Join.ROUND);
        mStroke.setStrokeWidth(width);
        mStroke.setColor(color);
        int saved = canvas.save();
        canvas.translate(x, y);
        canvas.drawPath(path, mStroke);
        canvas.restoreToCount(saved);
    }

    /** An SVG path filled, its origin moved to {@code (x, y)} and scaled by {@code scale}. */
    private void fillPath(@NonNull Canvas canvas, @NonNull String data, float x, float y,
                          float scale, @ColorInt int color) {
        Path path = path(data);
        if (path == null) return;
        mFill.setColor(color);
        int saved = canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        canvas.drawPath(path, mFill);
        canvas.restoreToCount(saved);
    }

    @Nullable
    private static Path path(@NonNull String data) {
        if (PATHS.containsKey(data)) return PATHS.get(data);
        Path path;
        try {
            path = PathParser.createPathFromPathData(data);
        } catch (RuntimeException e) {
            path = null;
        }
        PATHS.put(data, path);
        return path;
    }
}
