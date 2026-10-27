package com.termux.app.surfaces.cards;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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
import com.termux.app.surfaces.rows.ChipsRow;
import com.termux.app.surfaces.rows.PreviewTilesRow;
import com.termux.app.surfaces.rows.RowKit;
import com.termux.app.surfaces.rows.SegmentRow;
import com.termux.app.terminal.PaneRetroStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Terminal card (editor-redesign §2, Terminal; no eye, the terminal cannot be hidden): the
 * glass and shape strip under its Glass header (Text size is the terminal's and the keyboard
 * labels', {@code text_size_percent}), Contrast as words, Cursor trail and Screen effect as preview
 * tiles (Screen effect only where its shader runs), and the Font chip.
 *
 * <p>{@link #rows} is the card's table and the pure policy below it maps stored values to the
 * tiles, so JVM tests hold both; {@link #view} builds the card's own rows over a {@link Host}.</p>
 */
public final class TerminalCard {

    private TerminalCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .tier(1, Row.GLASS_SECTION, Row.FROST, Row.OPACITY, Row.TINT, Row.TEXT_SIZE,
                Row.CORNERS, Row.MARGIN, Row.SPLIT_GAP)
            .tier(2, Row.CONTRAST, Row.CURSOR_TRAIL);
        if (offers.terminalEffect)
            rows.tier(2, Row.EFFECT);
        return rows.tier(3, Row.LINK_FONT).build();
    }

    // ------------------------------------------------------------------------------ policy

    /**
     * The Cursor trail tiles' ids, in order: Off, which is the trail switch and never a stored
     * style, then the styles {@code terminal_cursor_trail_style} stores.
     */
    public static final List<String> TRAILS = Collections.unmodifiableList(Arrays.asList(
        "off", "default", "motion_blur", "railgun", "torpedo", "pixiedust", "comet"));

    @StringRes
    static final int[] TRAIL_NAMES = {R.string.editor_st_trail_off,
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

    /** What the card's own rows read and write: the editor's side. */
    public interface Host {
        /** Contrast's segment: Softer, Default, Harder. */
        int contrast();
        void setContrast(int index);
        /** The Cursor trail tile ({@link #trailIndex}). */
        int trail();
        void setTrail(int index);
        /** The Screen effect tile ({@link #effectIndex}). */
        int effect();
        void setEffect(int index);
        /** The Font chip was tapped. */
        void openFont();
    }

    /**
     * The card's own row for {@code row}, {@code widthPx} wide, or null for a row the sheet
     * draws (the strips).
     */
    @Nullable
    public static CardRowView view(@NonNull Context context, @NonNull Row row, int widthPx,
                                   @NonNull Host host) {
        switch (row) {
            case GLASS_SECTION: return StatusBarCard.glassHeader(context, false);
            case CONTRAST: return contrast(context, widthPx, host);
            case CURSOR_TRAIL: return trails(context, host);
            case EFFECT: return effects(context, host);
            case LINK_FONT: return more(context, host);
            default: return null;
        }
    }

    @NonNull
    private static CardRowView contrast(@NonNull Context context, int widthPx,
                                        @NonNull Host host) {
        SegmentRow row = SegmentRow.build(context, R.drawable.ic_editor_contrast,
            context.getString(R.string.appearance_row_contrast),
            Arrays.asList(
                SegmentRow.Segment.word(context.getString(R.string.appearance_contrast_softer)),
                SegmentRow.Segment.word(context.getString(R.string.appearance_contrast_default)),
                SegmentRow.Segment.word(context.getString(R.string.appearance_contrast_harder))),
            host.contrast(), widthPx, host::setContrast);
        // The sheet's tier divider stands over the Contrast section.
        View section = CardRowView.section(context,
            context.getString(R.string.appearance_row_contrast), null, false, row.root);
        return new CardRowView(section, () -> row.select(host.contrast()));
    }

    @NonNull
    private static CardRowView trails(@NonNull Context context, @NonNull Host host) {
        List<PreviewTilesRow.Tile> tiles = new ArrayList<>(TRAILS.size());
        for (int i = 0; i < TRAILS.size(); i++)
            tiles.add(new PreviewTilesRow.Tile(context.getString(TRAIL_NAMES[i]),
                new TrailPreview(context, TRAILS.get(i))));
        PreviewTilesRow row = PreviewTilesRow.build(context, tiles, host.trail(), host::setTrail);
        View section = CardRowView.section(context,
            context.getString(R.string.editor_st_section_cursor_trail), null, true, row.root);
        return new CardRowView(section, () -> row.select(host.trail()));
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

    @NonNull
    private static CardRowView more(@NonNull Context context, @NonNull Host host) {
        ChipsRow chips = ChipsRow.build(context, Collections.singletonList(
            new ChipsRow.Link(R.drawable.ic_ms_font_download,
                context.getString(R.string.appearance_row_font))), index -> host.openFont());
        // The sheet's tier divider stands over More.
        View section = CardRowView.section(context,
            context.getString(R.string.editor_st_section_more), null, false, chips.root);
        return new CardRowView(section, () -> {});
    }

    // ---------------------------------------------------------------------------- previews

    /**
     * A cursor trail drawn still: the block cursor at the end, and what each style leaves behind
     * it, in the theme's primary. Off is the cursor alone.
     */
    static final class TrailPreview extends View {
        @NonNull final String style;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mPath = new Path();
        private final RectF mRect = new RectF();
        @ColorInt private final int mInk;

        TrailPreview(@NonNull Context context, @NonNull String style) {
            super(context);
            this.style = style;
            mInk = RowKit.color(context, androidx.appcompat.R.attr.colorPrimary);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f)
                return;
            float d = getResources().getDisplayMetrics().density;
            float cursorW = 6f * d;
            float cursorH = Math.min(h - 4f * d, 16f * d);
            float right = w - 6f * d;
            float left = right - cursorW;
            float top = (h - cursorH) / 2f;
            float cy = h / 2f;
            float start = 4f * d;
            mPaint.setShader(null);
            mPaint.setStyle(Paint.Style.FILL);
            switch (style) {
                case "default":
                    // One smear from the old cell to the new.
                    mPath.reset();
                    mPath.moveTo(start, cy - 1.5f * d);
                    mPath.lineTo(left, top);
                    mPath.lineTo(left, top + cursorH);
                    mPath.lineTo(start, cy + 1.5f * d);
                    mPath.close();
                    mPaint.setShader(new LinearGradient(start, 0f, left, 0f,
                        withAlpha(mInk, 0f), withAlpha(mInk, 0.55f), Shader.TileMode.CLAMP));
                    canvas.drawPath(mPath, mPaint);
                    mPaint.setShader(null);
                    break;
                case "motion_blur":
                    // Ghost cells fading out behind it.
                    for (int i = 1; i <= 4; i++) {
                        float x = left - i * (cursorW + 2f * d);
                        if (x < start) break;
                        mPaint.setColor(withAlpha(mInk, 0.6f - i * 0.13f));
                        mRect.set(x, top, x + cursorW, top + cursorH);
                        canvas.drawRoundRect(mRect, d, d, mPaint);
                    }
                    break;
                case "railgun":
                    // A thin beam with rings along it.
                    mPaint.setColor(withAlpha(mInk, 0.7f));
                    mRect.set(start, cy - 0.75f * d, left, cy + 0.75f * d);
                    canvas.drawRect(mRect, mPaint);
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(1.2f * d);
                    for (int i = 1; i <= 3; i++) {
                        float x = start + (left - start) * i / 4f;
                        mPaint.setColor(withAlpha(mInk, 0.25f + i * 0.15f));
                        canvas.drawCircle(x, cy, 2.5f * d + i * 0.6f * d, mPaint);
                    }
                    mPaint.setStyle(Paint.Style.FILL);
                    break;
                case "torpedo":
                    // A tapered wake and a few bubbles.
                    mPath.reset();
                    mPath.moveTo(start, cy);
                    mPath.lineTo(left, top + cursorH * 0.2f);
                    mPath.lineTo(left, top + cursorH * 0.8f);
                    mPath.close();
                    mPaint.setShader(new LinearGradient(start, 0f, left, 0f,
                        withAlpha(mInk, 0.1f), withAlpha(mInk, 0.7f), Shader.TileMode.CLAMP));
                    canvas.drawPath(mPath, mPaint);
                    mPaint.setShader(null);
                    mPaint.setColor(withAlpha(mInk, 0.45f));
                    canvas.drawCircle(start + (left - start) * 0.3f, cy - 4f * d, 1.3f * d, mPaint);
                    canvas.drawCircle(start + (left - start) * 0.55f, cy + 4.5f * d, 1.1f * d,
                        mPaint);
                    break;
                case "pixiedust":
                    // Sparks scattered behind it.
                    float[][] sparks = {{0.15f, -0.30f, 0.9f}, {0.30f, 0.25f, 1.2f},
                        {0.45f, -0.10f, 1.0f}, {0.55f, 0.35f, 0.8f}, {0.68f, -0.32f, 1.3f},
                        {0.80f, 0.10f, 1.1f}, {0.38f, -0.42f, 0.7f}};
                    for (float[] spark : sparks) {
                        mPaint.setColor(withAlpha(mInk, 0.25f + spark[0] * 0.6f));
                        canvas.drawCircle(start + (left - start) * spark[0],
                            cy + cursorH * spark[1], spark[2] * d, mPaint);
                    }
                    break;
                case "comet":
                    // A round head with a tail running out to nothing.
                    float head = Math.min(cursorH, cursorW * 1.6f) / 2f;
                    float hx = left + cursorW / 2f;
                    mPath.reset();
                    mPath.moveTo(start, cy);
                    mPath.lineTo(hx, cy - head);
                    mPath.lineTo(hx, cy + head);
                    mPath.close();
                    mPaint.setShader(new LinearGradient(start, 0f, hx, 0f,
                        withAlpha(mInk, 0f), withAlpha(mInk, 0.6f), Shader.TileMode.CLAMP));
                    canvas.drawPath(mPath, mPaint);
                    mPaint.setShader(null);
                    mPaint.setColor(mInk);
                    canvas.drawCircle(hx, cy, head, mPaint);
                    return;
                case "off":
                default:
                    break;
            }
            mPaint.setColor(mInk);
            mRect.set(left, top, right, top + cursorH);
            canvas.drawRoundRect(mRect, d, d, mPaint);
        }
    }

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
