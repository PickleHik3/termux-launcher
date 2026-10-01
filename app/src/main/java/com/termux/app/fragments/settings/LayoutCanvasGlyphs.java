package com.termux.app.fragments.settings;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

import java.util.HashMap;
import java.util.Map;

/**
 * The layout assets pack's eleven glyphs (project-docs/reference/layout-assets/glyphs), as the
 * paths of its 24x24 viewBox. Each is stroked 1.8 wide with round caps and joins and never filled,
 * exactly as the pack draws it, and drawn at whatever size the caller wants: the whole glyph is
 * scaled by one factor, so it keeps its proportions in any bounds and nothing is a stretched
 * bitmap.
 */
final class LayoutCanvasGlyphs {

    private LayoutCanvasGlyphs() {}

    /** The pack's viewBox edge, and the stroke it draws every glyph with, in viewBox units. */
    static final float VIEWBOX = 24f;
    static final float STROKE = 1.8f;

    /** The glyphs' ids, which are the pack's file names. */
    static final String CHAT = "chat";
    static final String GLOBE = "globe";
    static final String GRID = "grid";
    static final String HOME = "home";
    static final String KEYBOARD = "keyboard";
    static final String LIST = "list";
    static final String MONITOR = "monitor";
    static final String MOUSE = "mouse";
    static final String PHONE = "phone";
    static final String PLAY = "play";
    static final String TERMINAL = "terminal";

    /** All eleven, in the pack's alphabetical order. */
    static final String[] IDS = {CHAT, GLOBE, GRID, HOME, KEYBOARD, LIST, MONITOR, MOUSE, PHONE,
        PLAY, TERMINAL};

    private static final Map<String, String> PATH_DATA = new HashMap<>();
    private static final Map<String, Path> PATHS = new HashMap<>();

    static {
        PATH_DATA.put(CHAT, "M5 5h14v11H10l-5 4V5ZM9 9h6M9 12h4");
        PATH_DATA.put(GLOBE,
            "M3 12h18M12 3c-5 5-5 13 0 18M12 3c5 5 5 13 0 18M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0");
        PATH_DATA.put(GRID, "M5 5h5v5H5ZM14 5h5v5h-5ZM5 14h5v5H5ZM14 14h5v5h-5Z");
        PATH_DATA.put(HOME, "M3 11l9-8 9 8M6 9v11h4v-6h4v6h4V9");
        PATH_DATA.put(KEYBOARD,
            "M4 5h16q2 0 2 2v10q0 2-2 2H4q-2 0-2-2V7q0-2 2-2ZM6 9h1M10 9h1M14 9h1M18 9h1M6 12h1"
                + "M10 12h1M14 12h1M18 12h1M7 16h10");
        PATH_DATA.put(LIST, "M9 6h11M9 12h11M9 18h11M4 6h.1M4 12h.1M4 18h.1");
        PATH_DATA.put(MONITOR, "M3 5h18v12H3ZM9 21h6M12 17v4");
        PATH_DATA.put(MOUSE, "M6 9a6 6 0 0 1 12 0v6a6 6 0 0 1-12 0ZM6 10h12M12 3v7");
        PATH_DATA.put(PHONE,
            "M7 4l3 5-2 2c1.5 3 2.5 4 5 5l2-2 5 3c-1 4-4 4-7 2C7 16 4 12 4 8c0-2 1-3 3-4Z");
        PATH_DATA.put(PLAY, "M9 6l10 6-10 6Z");
        PATH_DATA.put(TERMINAL, "M5 7l5 5-5 5M13 17h6");
    }

    /** The SVG path data of one glyph, or null for an id the pack does not have. */
    @Nullable
    static String pathData(@NonNull String id) {
        return PATH_DATA.get(id);
    }

    /** One glyph's path in viewBox units, parsed once; null for an unknown id. */
    @Nullable
    static Path path(@NonNull String id) {
        if (PATHS.containsKey(id)) return PATHS.get(id);
        String data = PATH_DATA.get(id);
        Path path = null;
        if (data != null) {
            try {
                path = PathParser.createPathFromPathData(data);
            } catch (RuntimeException ignored) {
                path = null;
            }
        }
        PATHS.put(id, path);
        return path;
    }

    /**
     * Draws a glyph centred on {@code (cx, cy)}, {@code size} wide and tall. The paint's own style,
     * width, cap and join are put back afterwards.
     */
    static void draw(@NonNull Canvas canvas, @NonNull Paint paint, @NonNull String id, float cx,
                     float cy, float size, @ColorInt int color) {
        Path path = path(id);
        if (path == null || size <= 0f) return;
        Paint.Style style = paint.getStyle();
        float width = paint.getStrokeWidth();
        Paint.Cap cap = paint.getStrokeCap();
        Paint.Join join = paint.getStrokeJoin();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(STROKE);
        paint.setColor(color);
        int saved = canvas.save();
        canvas.translate(cx - size / 2f, cy - size / 2f);
        float scale = size / VIEWBOX;
        canvas.scale(scale, scale);
        canvas.drawPath(path, paint);
        canvas.restoreToCount(saved);
        paint.setStyle(style);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(cap);
        paint.setStrokeJoin(join);
    }
}
