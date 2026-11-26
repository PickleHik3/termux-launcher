package com.termux.app.surfaces.cards;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
import com.termux.app.surfaces.rows.PreviewTilesRow;
import com.termux.app.surfaces.rows.RowKit;
import com.termux.app.terminal.PaneRetroStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Terminal card (appearance-one-look spec §3, Terminal; the old Workspace card merged into it; no
 * eye, the terminal cannot be hidden) in the condensed kinds (spec #326): the sliders in pairs
 * (Font size with Inner gap, the panes' Opacity with Frost; the Wallpaper dim of the Terminal's
 * pages, which does not follow Global, stands alone), Contrast beside its label, Cursor trail
 * folded into a menu, Screen effect as preview tiles (only where its shader runs), the switches
 * as chips, the Layout list, and the Font, Pane borders and Pages links on one row. Order does
 * the grouping in place of headings; every knob of the earlier card is still here.
 *
 * <p>{@link #rows} is the card's table and the pure policy below it maps stored values to the
 * tiles, so JVM tests hold both; {@code CardBody} draws the table, and {@link #view} builds the
 * one row it does not, the Screen effect tiles, over a {@link Host}.</p>
 */
public final class TerminalCard {

    private TerminalCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .pair(1, Row.FONT_SIZE, Row.SPLIT_GAP)
            // The Terminal's Background (ADR 0031 §4): never Global's, no preset, no reset arrow.
            .pair(1, Row.TERMINAL_OPACITY, Row.TERMINAL_BLUR)
            .tier(1, Row.WALLPAPER_DIM)
            .choice(3, Row.CONTRAST)
            .fold(3, Row.CURSOR_TRAIL);
        if (offers.terminalEffect)
            rows.tier(3, Row.EFFECT);
        rows.chips(3, Row.ADAPTIVE_LEGIBILITY, Row.MULTIPLEXER, Row.AUTO_TILING, Row.FOCUS_GROWS);
        // Screen events and Composite signal stand only while a Screen effect is on (shown).
        if (offers.terminalEffect)
            rows.chips(3, Row.SCREEN_EVENTS, Row.COMPOSITE_SIGNAL);
        rows.chips(3, Row.MINIMAL, Row.SEARCH_FIELD, Row.DRAWER_ICON);
        rows.links(3, Row.LINK_FONT, Row.PANE_BORDERS);
        return rows.build();
    }

    // ------------------------------------------------------------------------------ policy

    /**
     * The Cursor trail tiles' ids, in order: Off, which is the trail switch and never a stored
     * style, then the styles {@code terminal_cursor_trail_style} stores.
     */
    public static final List<String> TRAILS = Collections.unmodifiableList(Arrays.asList(
        "off", "default", "motion_blur", "railgun", "torpedo", "pixiedust", "comet"));

    @StringRes
    public static final int[] TRAIL_NAMES = {R.string.editor_st_trail_off,
        R.string.editor_st_trail_default, R.string.editor_st_trail_motion,
        R.string.editor_st_trail_railgun, R.string.editor_st_trail_torpedo,
        R.string.editor_st_trail_pixie, R.string.editor_st_trail_comet};

    /** The tile of the stored trail: Off while it is switched off, Default for an unknown style. */
    public static int trailIndex(boolean enabled, @Nullable String style) {
        if (!enabled)
            return 0;
        int index = style == null ? -1 : TRAILS.indexOf(style);
        return index <= 0 ? 1 : index;
    }

    /**
     * The Screen effect tiles' ids, {@code terminal_retro_effect}'s values, in order. Material is
     * the CRT in the theme's own primary ({@link PaneRetroStyle#CRT_MATERIAL}).
     */
    public static final List<String> EFFECTS = Collections.unmodifiableList(Arrays.asList(
        PaneRetroStyle.NONE.id(), PaneRetroStyle.CRT.id(), PaneRetroStyle.CRT_GREEN.id(),
        PaneRetroStyle.CRT_AMBER.id(), PaneRetroStyle.CRT_MATERIAL.id(), PaneRetroStyle.TFT.id()));

    @StringRes
    static final int[] EFFECT_NAMES = {R.string.editor_st_effect_none,
        R.string.editor_st_effect_crt, R.string.editor_st_effect_green,
        R.string.editor_st_effect_amber, R.string.editor_st_effect_material,
        R.string.editor_st_effect_tft};

    /** The tile of the stored effect; None for an unknown one, as the panes read it. */
    public static int effectIndex(@Nullable String id) {
        int index = id == null ? -1 : EFFECTS.indexOf(id);
        return Math.max(0, index);
    }

    // ------------------------------------------------------------------------------- views

    /** What the card's own row reads and writes: the editor's side. */
    public interface Host {
        /** The Screen effect tile ({@link #effectIndex}). */
        int effect();
        void setEffect(int index);
    }

    /** The row the card draws itself, the Screen effect tiles; the rest are the body's. */
    public static boolean owns(@NonNull Row row) {
        return row == Row.EFFECT;
    }

    /** The card's own row for {@code row}, or null for a row the body draws. */
    @Nullable
    public static CardRowView view(@NonNull Context context, @NonNull Row row, int widthPx,
                                   @NonNull Host host) {
        return row == Row.EFFECT ? effects(context, host) : null;
    }

    @NonNull
    private static CardRowView effects(@NonNull Context context, @NonNull Host host) {
        List<PreviewTilesRow.Tile> tiles = new ArrayList<>(EFFECTS.size());
        for (int i = 0; i < EFFECTS.size(); i++)
            tiles.add(new PreviewTilesRow.Tile(context.getString(EFFECT_NAMES[i]),
                new EffectPreview(context, PaneRetroStyle.fromId(EFFECTS.get(i)))));
        PreviewTilesRow row = PreviewTilesRow.build(context, tiles, host.effect(),
            host::setEffect);
        View section = CardRowView.section(context,
            context.getString(R.string.editor_st_section_screen_effect), null, true, row.root);
        return new CardRowView(section, () -> row.select(host.effect()));
    }

    // ---------------------------------------------------------------------------- previews

    /**
     * A screen effect drawn small: a terminal screen in the effect's colour, scanlines for the CRT
     * family, the grid for TFT. None is the plain terminal; Material glows in the theme's primary,
     * the phosphor the effect itself takes ({@link PaneRetroStyle#materialPhosphor}).
     */
    static final class EffectPreview extends View {
        @NonNull final PaneRetroStyle style;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        @ColorInt private final int mPrimary;
        @ColorInt private final int mTertiary;

        /** The plain screen and the CRT's grey glass, as the review's drawing has them. */
        @ColorInt static final int SCREEN = 0xFF1B1B21;
        @ColorInt static final int CRT_GLASS = 0xFF9AA3B2;
        @ColorInt static final int TFT_PANEL = 0xFF4A5A95;

        EffectPreview(@NonNull Context context, @NonNull PaneRetroStyle style) {
            super(context);
            this.style = style;
            mPrimary = RowKit.color(context, androidx.appcompat.R.attr.colorPrimary);
            mTertiary = RowKit.color(context, com.google.android.material.R.attr.colorTertiary);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f)
                return;
            float d = getResources().getDisplayMetrics().density;
            float sw = Math.min(w - 8f * d, 36f * d);
            float sh = Math.min(h - 6f * d, 22f * d);
            float left = (w - sw) / 2f;
            float top = (h - sh) / 2f;
            mRect.set(left, top, left + sw, top + sh);
            float corner = 4f * d;
            mPaint.setShader(null);
            mPaint.setStyle(Paint.Style.FILL);
            switch (style) {
                case CRT_GREEN:
                case CRT_AMBER:
                    mPaint.setColor(phosphor(style.tint()));
                    break;
                case CRT_MATERIAL:
                    mPaint.setShader(new LinearGradient(mRect.left, mRect.top, mRect.right,
                        mRect.bottom, phosphor(PaneRetroStyle.materialPhosphor(mPrimary)),
                        phosphor(PaneRetroStyle.materialPhosphor(mTertiary)),
                        Shader.TileMode.CLAMP));
                    break;
                case CRT:
                    mPaint.setColor(CRT_GLASS);
                    break;
                case TFT:
                    mPaint.setColor(TFT_PANEL);
                    break;
                case NONE:
                default:
                    mPaint.setColor(SCREEN);
                    break;
            }
            canvas.drawRoundRect(mRect, corner, corner, mPaint);
            mPaint.setShader(null);
            canvas.save();
            canvas.clipRect(mRect);
            if (style.usesCrt()) {
                mPaint.setColor(0x59000000);
                float line = Math.max(1f, d);
                for (float y = mRect.top; y < mRect.bottom; y += 3f * line)
                    canvas.drawRect(mRect.left, y, mRect.right, y + line, mPaint);
            } else if (style == PaneRetroStyle.TFT) {
                mPaint.setColor(0x4DFFFFFF);
                float step = 5f * d / 2f;
                float line = Math.max(1f, d / 2f);
                for (float x = mRect.left; x < mRect.right; x += step)
                    canvas.drawRect(x, mRect.top, x + line, mRect.bottom, mPaint);
                for (float y = mRect.top; y < mRect.bottom; y += step)
                    canvas.drawRect(mRect.left, y, mRect.right, y + line, mPaint);
            } else {
                // The plain terminal: a prompt and its cursor.
                mPaint.setColor(0xFFC9C9D1);
                canvas.drawRect(mRect.left + 4f * d, mRect.top + 5f * d, mRect.left + 14f * d,
                    mRect.top + 7f * d, mPaint);
                canvas.drawRect(mRect.left + 16f * d, mRect.top + 4f * d, mRect.left + 19f * d,
                    mRect.top + 8f * d, mPaint);
            }
            canvas.restore();
        }

        @ColorInt
        static int phosphor(@NonNull float[] tint) {
            return 0xFF000000 | (Math.round(tint[0] * 255f) << 16)
                | (Math.round(tint[1] * 255f) << 8) | Math.round(tint[2] * 255f);
        }
    }

    @ColorInt
    static int withAlpha(@ColorInt int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
