package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.io.ExtraKeyActionLabels;
import com.termux.shared.termux.extrakeys.ExtraKeyIcons;

import java.util.HashMap;

import juloo.keyboard2.SymbolIcons;

/**
 * The in-app keyboard's key symbols that draw as Roundline icons, the same drawings the extra-keys
 * bar wears. One table per source of a label: the bundled key font's code points, and the code
 * points that are not in it (the paste picture, the bar's own icon code points). A symbol with no
 * Roundline drawing yet is simply not listed, and the keyboard keeps drawing it from its font:
 * adding one is one line in the matching table.
 *
 * <p>Accent and combining marks (E050+, E150+, E200+) are never listed.
 */
public final class KeyboardSymbolIcons implements SymbolIcons {

    /** Key-font code points ({@code KeyValue.FLAG_KEY_FONT}) and the drawable that replaces each. */
    static final int[][] KEY_FONT_ICONS = {
        {0xE00E, R.drawable.ic_rl_enter},
        {0xE011, R.drawable.ic_rl_backspace},
        {0xE010, R.drawable.ic_rl_delete_forward},
        {0xE00F, R.drawable.ic_rl_tab},
        {0xE00D, R.drawable.ic_rl_space},
        {0xE005, R.drawable.ic_rl_arrow_up},
        {0xE006, R.drawable.ic_rl_arrow_right},
        {0xE007, R.drawable.ic_rl_arrow_down},
        {0xE008, R.drawable.ic_rl_arrow_left},
        {0xE002, R.drawable.ic_rl_page_up},
        {0xE003, R.drawable.ic_rl_page_down},
        {0xE00B, R.drawable.ic_rl_key_home},
        {0xE00C, R.drawable.ic_rl_key_end},
        {0xE032, R.drawable.ic_rl_paste},
        {0xE036, R.drawable.ic_rl_undo},
        {0xE015, R.drawable.ic_rl_mic},
        {0xE038, R.drawable.ic_rl_assistant},
    };

    /**
     * Other code points. Empty: the paste picture glyph (U+F02E9) stays a Nerd glyph so the
     * picture state differs from plain paste; it takes ic_rl_image once that drawable is here.
     */
    static final int[][] OTHER_ICONS = {};

    /**
     * Tool keys the keyboard's own layouts ship with a Nerd glyph the bar's {@code ExtraKeyIcons}
     * table does not know: {tool id, code point, icon}. The bar's table answers for the rest.
     */
    static final Object[][] SHIPPED_TOOL_GLYPHS = {
        {"app.command_palette", 0xF13B1, ExtraKeyIcons.COMMAND},
    };

    private final Context mContext;
    private final HashMap<String, HashMap<String, String>> mToolLabels = new HashMap<>();

    public KeyboardSymbolIcons(@NonNull Context context) {
        Context app = context.getApplicationContext();
        mContext = app != null ? app : context;
    }

    /** The drawable resource for a one-code-point label, or 0 when the font keeps it. */
    static int drawableFor(int codePoint, boolean keyFont) {
        for (int[] row : keyFont ? KEY_FONT_ICONS : OTHER_ICONS) {
            if (row[0] == codePoint) return row[1];
        }
        if (keyFont) return 0;
        for (int[] row : ExtraKeyActionLabels.ICON_DRAWABLES) {
            if (row[0] == codePoint && codePoint != ExtraKeyIcons.PAGE_SWITCHER) return row[1];
        }
        return 0;
    }

    @Nullable
    @Override
    public Drawable iconFor(@NonNull String label, boolean keyFont) {
        if (label.isEmpty() || label.codePointCount(0, label.length()) != 1) return null;
        int res = drawableFor(label.codePointAt(0), keyFont);
        if (res == 0) return null;
        try {
            return mContext.getDrawable(res);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public String toolLabel(@NonNull String toolId, @NonNull String symbol) {
        HashMap<String, String> bySymbol = mToolLabels.get(toolId);
        if (bySymbol == null) {
            bySymbol = new HashMap<>();
            mToolLabels.put(toolId, bySymbol);
        }
        String label = bySymbol.get(symbol);
        if (label == null) {
            label = resolveTool(toolId, symbol);
            bySymbol.put(symbol, label);
        }
        return label;
    }

    /** The tool's icon label (a code point {@link #drawableFor} knows), else the symbol itself. */
    @NonNull
    static String resolveTool(@NonNull String toolId, @NonNull String symbol) {
        String effective = ExtraKeyIcons.effectiveDisplay("tool:" + toolId, symbol);
        if (effective != null && !effective.equals(symbol)) return effective;
        if (symbol.codePointCount(0, symbol.length()) == 1) {
            int codePoint = symbol.codePointAt(0);
            for (Object[] row : SHIPPED_TOOL_GLYPHS) {
                if (row[0].equals(toolId) && (Integer) row[1] == codePoint) {
                    return ExtraKeyIcons.string((Integer) row[2]);
                }
            }
        }
        return symbol;
    }
}
