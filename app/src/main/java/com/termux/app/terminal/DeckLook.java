package com.termux.app.terminal;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * The deck look's colours: while a terminal effect is drawn on the chrome, the strip and the
 * in-app keyboard are the CRT's accessories in opaque plastic (the bezel's tone) with lights in
 * the effect's palette. Pure data; the surfaces draw it. See wave 8 of
 * {@code project-docs/active/agsl-crt-trail-effects-2026-11-02.md}.
 */
public final class DeckLook {

    /** The bed: the panel colour, opaque. */
    @ColorInt public final int bed;
    /** The bed's lit top: the bed and 10 % white. */
    @ColorInt public final int bedTop;
    /** A key's fill: one step lighter than the bed (12 % white). */
    @ColorInt public final int key;
    /** A key's 1 dp top edge: the key and 14 % white. */
    @ColorInt public final int keyTop;
    /** A key's 2 dp foot: the key 55 % toward black. */
    @ColorInt public final int keyFoot;
    /** The metrics' display colour. */
    @ColorInt public final int display;
    /** A light: a latched modifier, the active mode, an LED, the return key. */
    @ColorInt public final int lit;
    /** Ink on a lit fill. */
    @ColorInt public final int litInk;
    /** A lit key's foot: the light 45 % toward black. */
    @ColorInt public final int litFoot;

    private DeckLook(@ColorInt int panel, @ColorInt int display, @ColorInt int lit,
                     @ColorInt int litInk) {
        bed = opaque(panel);
        bedTop = ColorUtils.blendARGB(bed, 0xFFFFFFFF, 0.10f);
        key = ColorUtils.blendARGB(bed, 0xFFFFFFFF, 0.12f);
        keyTop = ColorUtils.blendARGB(key, 0xFFFFFFFF, 0.14f);
        keyFoot = ColorUtils.blendARGB(key, 0xFF000000, 0.55f);
        this.display = display;
        this.lit = lit;
        this.litInk = litInk;
        litFoot = ColorUtils.blendARGB(lit, 0xFF000000, 0.45f);
    }

    /**
     * The deck for {@code style} on a panel of {@code panelColour}, or null for
     * {@link PaneRetroStyle#NONE} (no effect, no deck). The Material CRT lights in
     * {@code materialPrimary}, made opaque.
     */
    @Nullable
    public static DeckLook of(@NonNull PaneRetroStyle style, @ColorInt int panelColour,
                              @ColorInt int materialPrimary) {
        switch (style) {
            case CRT:
            case TFT:
                return new DeckLook(panelColour, 0xFF7CF5D8, 0xFFFFB020, 0xFF1A1200);
            case CRT_GREEN:
                return new DeckLook(panelColour, 0xFF8CF0A4, 0xFF6EF08E, 0xFF04200C);
            case CRT_AMBER:
                return new DeckLook(panelColour, 0xFFFFBE5C, 0xFFFFB020, 0xFF1A1200);
            case CRT_MATERIAL: {
                int primary = opaque(materialPrimary);
                return new DeckLook(panelColour, primary, primary, inkOn(primary));
            }
            default:
                return null;
        }
    }

    /** Black on a light fill, white on a dark one, by the Rec.601 luma at 0.5. */
    @ColorInt
    static int inkOn(@ColorInt int fill) {
        double luma = (0.299 * ((fill >> 16) & 0xFF) + 0.587 * ((fill >> 8) & 0xFF)
            + 0.114 * (fill & 0xFF)) / 255d;
        return luma > 0.5 ? 0xFF000000 : 0xFFFFFFFF;
    }

    @ColorInt
    private static int opaque(@ColorInt int colour) {
        return colour | 0xFF000000;
    }

    /** By colours, so a surface re-dressed with the same deck can skip its repaint. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeckLook)) return false;
        DeckLook that = (DeckLook) o;
        return bed == that.bed && display == that.display && lit == that.lit
            && litInk == that.litInk;
    }

    @Override
    public int hashCode() {
        return ((bed * 31 + display) * 31 + lit) * 31 + litInk;
    }
}
