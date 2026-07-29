package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The command palette's drawn surface: a thin bordered glass rectangle reading as a tmux pane —
 * monospace rows, {@code ── CATEGORY} rules, a right-aligned shortcut column — with a strip of
 * frequent-action keycaps under its bottom border.
 *
 * <p>Everything is drawn on one canvas rather than composed from child views because the whole
 * rectangle is spring-animated from the space bar every frame: a view tree would need a measure
 * and layout pass per frame, and the clipped, bottom-anchored viewport with its focus wash and
 * bottom fade has no natural widget anyway.
 *
 * <p>The backdrop blur is not drawn here. The host stacks a {@code RealtimeBlurView} clipped to
 * this rectangle's outline underneath, exactly as the dock does; this view draws the tint,
 * gradient, specular, rim and content over it.
 *
 * <p>Colors resolve through Material roles and every label is contrast-checked against the
 * composited glass with {@link InAppKeyboardPaletteFactory#ensureContrast}, because the live
 * wallpaper shows through the blur.
 */
public final class CommandPaletteView extends View {

    /** Frequent-action keycaps, per the handoff's default six. */
    public static final int KEYCAP_COUNT = 6;

    public interface Callbacks {
        /** A tap landed on the row at {@code index} in the current row list. */
        void onRowTapped(int index);

        /** A tap landed on the keycap at {@code index} in the frequent-action strip. */
        void onKeycapTapped(int index);

        /** A tap landed outside the palette and its strip. */
        void onOutsideTapped();
    }

    public static final int KIND_CATEGORY = 0;
    public static final int KIND_ENTRY = 1;
    public static final int KIND_NOTICE = 2;

    /** One line of the ledger: a category rule, an action row, or a full-width notice. */
    public static final class Row {
        final int kind;
        final String primary;
        @Nullable final String description;
        final String shortcut;
        final boolean enabled;

        private Row(int kind, @NonNull String primary, @Nullable String description,
                    @NonNull String shortcut, boolean enabled) {
            this.kind = kind;
            this.primary = primary;
            this.description = description;
            this.shortcut = shortcut;
            this.enabled = enabled;
        }

        @NonNull
        public static Row category(@NonNull String label) {
            return new Row(KIND_CATEGORY, label, null, "", true);
        }

        @NonNull
        public static Row notice(@NonNull String text) {
            return new Row(KIND_NOTICE, text, null, "", true);
        }

        @NonNull
        public static Row entry(@NonNull String title, @Nullable String description,
                                @NonNull String shortcut, boolean enabled) {
            return new Row(KIND_ENTRY, title, description, shortcut, enabled);
        }

        public boolean isSelectable() {
            return kind == KIND_ENTRY;
        }
    }

    /** One keycap of the frequent-action strip. */
    public static final class Keycap {
        final String glyph;
        final String label;

        public Keycap(@NonNull String glyph, @NonNull String label) {
            this.glyph = glyph;
            this.label = label;
        }
    }

    // Geometry, in dp, from the handoff's 412 x 920 dp reference frame.
    private static final float TITLE_BAR_H = 33f;
    private static final float FILTER_ROW_H = 34f;
    private static final float ARG_ROW_H = 30f;
    private static final float CATEGORY_H = 22f;
    private static final float ROW_PAD_V = 6f;
    private static final float ROW_PAD_LEFT = 14f;
    private static final float ROW_PAD_RIGHT = 12f;
    private static final float CATEGORY_PAD_LEFT = 12f;
    private static final float FOCUS_BAR_W = 2f;
    private static final float VIEWPORT_BOTTOM_SLACK = 26f;
    private static final float BOTTOM_FADE_H = 28f;
    private static final float STRIP_GAP = 8f;
    private static final float STRIP_LEFT_EXTRA = 2f;
    private static final float CAP_GAP = 5f;
    private static final float CAP_PAD_H = 8f;
    private static final float CAP_PAD_V = 6f;
    private static final float CAP_RADIUS = 6f;
    private static final float HAIRLINE = 1f;
    private static final float SPECULAR_RADIUS = 320f;

    // Type sizes, in dp for a stable ledger grid under any font scale.
    private static final float SIZE_TITLE = 10f;
    private static final float SIZE_CRUMB = 10.5f;
    private static final float SIZE_META = 10f;
    private static final float SIZE_FILTER = 13f;
    private static final float SIZE_CATEGORY = 9.5f;
    private static final float SIZE_ROW = 12.5f;
    private static final float SIZE_DESCRIPTION = 10.5f;
    private static final float SIZE_SHORTCUT = 10f;
    private static final float SIZE_CAP_GLYPH = 11f;
    private static final float SIZE_CAP_LABEL = 10.5f;

    private final float mDensity;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mMono = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mMonoBold = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final RectF mFrame = new RectF();
    private final List<RectF> mCapRects = new ArrayList<>();

    @Nullable private Callbacks mCallbacks;

    private int mGlassBase;
    private int mPrimary;
    private int mOnSurface;
    private int mOnSurfaceVariant;
    private int mMeta;
    private int mChipFill;
    private int mCapLabel;
    private int mConfirmation;

    private float mRadius;
    private float mBodyAlpha;
    private float mStripAlpha;
    private float mStripOffset;
    private float mProgress;

    private List<Row> mRows = new ArrayList<>();
    private List<Keycap> mKeycaps = new ArrayList<>();
    private int mFocusIndex = -1;
    private String mMetaText = "";
    private String mCrumb = "";
    private String mQuery = "";
    private String mQueryPlaceholder = "";
    private boolean mArgumentMode;
    private String mArgumentPlaceholder = "";
    private String mArgumentValue = "";
    @Nullable private String mConfirmationText;
    private float mConfirmationLeft;
    private float mConfirmationBaseline;
    private float mScrollOffset;

    public CommandPaletteView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        Typeface mono = Typeface.MONOSPACE;
        mMono.setTypeface(mono);
        mMonoBold.setTypeface(Typeface.create(mono, Typeface.BOLD));
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(HAIRLINE * mDensity);
        setClickable(true);
        refreshPalette();
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /** Re-resolves every role. Called on theme, wallpaper-color and configuration changes. */
    public void refreshPalette() {
        Context context = getContext();
        mGlassBase = InAppKeyboardPaletteFactory.resolveDockGlassBaseColor(context);
        mPrimary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int onSurface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        int onSurfaceVariant = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            ColorUtils.blendARGB(onSurface, mGlassBase, 0.28f));
        int surfaceContainerHigh = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            ColorUtils.blendARGB(mGlassBase, onSurface, 0.10f));

        // Reference background for the contrast pass: the glass tint plus its darkening
        // gradient, composited down to something opaque to measure against.
        int overGlass = ColorUtils.compositeColors(
            ColorUtils.setAlphaComponent(Color.BLACK, 40), mGlassBase);
        mOnSurface = InAppKeyboardPaletteFactory.ensureContrast(onSurface, overGlass);
        mOnSurfaceVariant = InAppKeyboardPaletteFactory.ensureContrast(onSurfaceVariant, overGlass);
        mMeta = ColorUtils.setAlphaComponent(mOnSurfaceVariant, 190);
        mPrimary = InAppKeyboardPaletteFactory.ensureContrast(mPrimary, overGlass);
        mConfirmation = InAppKeyboardPaletteFactory.ensureContrast(
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary,
                mPrimary), overGlass);

        // Keycaps use the keyboard's chip recipe, so their labels are checked against the
        // chip composited over the glass rather than over the glass alone.
        mChipFill = ColorUtils.setAlphaComponent(surfaceContainerHigh, 128);
        int capOverGlass = ColorUtils.compositeColors(mChipFill, overGlass);
        mCapLabel = InAppKeyboardPaletteFactory.ensureContrast(onSurface, capOverGlass);
        invalidate();
    }

    /** Sets the animated rectangle and the fades driven by the sprout progress. */
    public void setFrame(@NonNull RectF frame, float radius, float bodyAlpha, float stripAlpha,
                         float stripOffsetPx, float progress) {
        mFrame.set(frame);
        mRadius = radius;
        mBodyAlpha = clamp01(bodyAlpha);
        mStripAlpha = clamp01(stripAlpha);
        mStripOffset = stripOffsetPx;
        mProgress = clamp01(progress);
        invalidate();
    }

    public void setRows(@NonNull List<Row> rows, int focusIndex) {
        mRows = rows;
        mFocusIndex = focusIndex;
        invalidate();
    }

    public void setKeycaps(@NonNull List<Keycap> keycaps) {
        mKeycaps = keycaps;
        invalidate();
    }

    public void setHeader(@NonNull String metaText, @NonNull String crumb) {
        mMetaText = metaText;
        mCrumb = crumb;
        invalidate();
    }

    public void setQuery(@NonNull String query, @NonNull String placeholder) {
        mQuery = query;
        mQueryPlaceholder = placeholder;
        invalidate();
    }

    /**
     * Argument mode: the rows collapse to one instruction and the value being typed moves from
     * the filter field down to its own row, so the field never shows the argument buffer.
     */
    public void setArgumentMode(boolean argumentMode, @NonNull String placeholder,
                                @NonNull String value) {
        mArgumentMode = argumentMode;
        mArgumentPlaceholder = placeholder;
        mArgumentValue = value;
        invalidate();
    }

    /**
     * Transient confirmation line, drawn on its own at {@code left}/{@code baselineY} once the
     * palette has collapsed — the handoff prints it at the prompt, but writing into the shell
     * would land in whatever the user is composing, so it is drawn over the terminal instead.
     */
    public void setConfirmation(@Nullable String text, float left, float baselineY) {
        mConfirmationText = text;
        mConfirmationLeft = left;
        mConfirmationBaseline = baselineY;
        invalidate();
    }

    public boolean hasConfirmation() {
        return mConfirmationText != null;
    }

    /**
     * Height the current content wants, before the host clamps it. Includes the chrome rows so
     * the caller can target the height spring directly.
     */
    public float measuredContentHeight() {
        float height = dp(TITLE_BAR_H) + dp(FILTER_ROW_H) + listContentHeight();
        if (mArgumentMode) height += dp(ARG_ROW_H);
        return height;
    }

    private float listContentHeight() {
        float total = 0f;
        for (int i = 0; i < mRows.size(); i++) total += rowHeight(i);
        return total;
    }

    private float rowHeight(int index) {
        Row row = mRows.get(index);
        if (row.kind != KIND_ENTRY) return dp(CATEGORY_H);
        float height = dp(ROW_PAD_V) * 2f + lineHeight(SIZE_ROW);
        if (index == mFocusIndex && row.description != null && !row.description.isEmpty())
            height += lineHeight(SIZE_DESCRIPTION);
        return height;
    }

    private float lineHeight(float sizeDp) {
        mMono.setTextSize(dp(sizeDp));
        Paint.FontMetrics metrics = mMono.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mConfirmationText != null && mProgress <= 0.01f) {
            drawConfirmation(canvas);
            return;
        }
        if (mFrame.isEmpty() || mProgress <= 0.001f) return;

        drawShadow(canvas);
        drawSurface(canvas);
        if (mBodyAlpha > 0.004f) drawBody(canvas);
        if (mStripAlpha > 0.004f) drawStrip(canvas);
        if (mConfirmationText != null) drawConfirmation(canvas);
    }

    /**
     * Layered stand-in for the spec's {@code 0 18px 40px} drop shadow: hardware canvases only
     * honor {@code setShadowLayer} for text, and forcing a software layer here would defeat the
     * blur underneath.
     */
    private void drawShadow(@NonNull Canvas canvas) {
        float[] spreads = {dp(30f), dp(18f), dp(7f)};
        int[] alphas = {22, 38, 58};
        float lift = dp(18f) * mProgress;
        mFill.setShader(null);
        for (int i = 0; i < spreads.length; i++) {
            mFill.setColor(ColorUtils.setAlphaComponent(Color.BLACK,
                Math.round(alphas[i] * mProgress)));
            mRect.set(mFrame.left - spreads[i], mFrame.top - spreads[i] + lift,
                mFrame.right + spreads[i], mFrame.bottom + spreads[i] + lift);
            canvas.drawRoundRect(mRect, mRadius + spreads[i], mRadius + spreads[i], mFill);
        }
    }

    private void drawSurface(@NonNull Canvas canvas) {
        // Glass base at the dock's own tint alpha, then the vertical light model over it.
        mFill.setShader(null);
        mFill.setColor(ColorUtils.setAlphaComponent(mGlassBase, 158));
        canvas.drawRoundRect(mFrame, mRadius, mRadius, mFill);

        mFill.setShader(new LinearGradient(0f, mFrame.top, 0f, mFrame.bottom,
            ColorUtils.setAlphaComponent(Color.WHITE, 20),
            ColorUtils.setAlphaComponent(Color.BLACK, 61), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(mFrame, mRadius, mRadius, mFill);
        mFill.setShader(null);

        int save = canvas.save();
        clipToFrame(canvas);
        float specularAlpha = 0.35f + 0.5f * mProgress;
        int specular = ColorUtils.setAlphaComponent(Color.WHITE, Math.round(33f * specularAlpha));
        mFill.setShader(new RadialGradient(
            mFrame.left + mFrame.width() * 0.62f, mFrame.top + mFrame.height() * 0.18f,
            dp(SPECULAR_RADIUS),
            new int[] {specular, ColorUtils.setAlphaComponent(specular, 0), Color.TRANSPARENT},
            new float[] {0f, 0.68f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(mFrame, mFill);
        mFill.setShader(null);
        canvas.restoreToCount(save);

        // Accent-tinted rim: the palette rim is primary, unlike the keycaps' white hairline.
        mStroke.setColor(ColorUtils.setAlphaComponent(mPrimary, 107));
        float inset = mStroke.getStrokeWidth() / 2f;
        mRect.set(mFrame.left + inset, mFrame.top + inset,
            mFrame.right - inset, mFrame.bottom - inset);
        canvas.drawRoundRect(mRect, mRadius, mRadius, mStroke);
    }

    private void clipToFrame(@NonNull Canvas canvas) {
        canvas.clipRect(mFrame);
    }

    private void drawBody(@NonNull Canvas canvas) {
        int save = canvas.save();
        clipToFrame(canvas);
        int alpha = Math.round(255f * mBodyAlpha);

        float titleBottom = mFrame.top + dp(TITLE_BAR_H);
        mFill.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(mPrimary, 18), alpha));
        canvas.drawRect(mFrame.left, mFrame.top, mFrame.right, titleBottom, mFill);
        drawHairline(canvas, titleBottom, ColorUtils.setAlphaComponent(mPrimary, 56), alpha);

        mMonoBold.setTextSize(dp(SIZE_TITLE));
        mMonoBold.setLetterSpacing(0.1f);
        mMonoBold.setColor(withBodyAlpha(mPrimary, alpha));
        float titleBaseline = baseline(mFrame.top, dp(TITLE_BAR_H), mMonoBold);
        canvas.drawText("PALETTE", mFrame.left + dp(ROW_PAD_LEFT), titleBaseline, mMonoBold);
        float crumbStart = mFrame.left + dp(ROW_PAD_LEFT)
            + mMonoBold.measureText("PALETTE") + dp(6f);
        mMonoBold.setLetterSpacing(0f);

        mMono.setTextSize(dp(SIZE_META));
        mMono.setLetterSpacing(0f);
        mMono.setColor(withBodyAlpha(mMeta, alpha));
        float metaWidth = mMono.measureText(mMetaText);
        canvas.drawText(mMetaText, mFrame.right - dp(ROW_PAD_RIGHT) - metaWidth,
            titleBaseline, mMono);

        if (!mCrumb.isEmpty()) {
            mMono.setTextSize(dp(SIZE_CRUMB));
            mMono.setColor(withBodyAlpha(mOnSurfaceVariant, alpha));
            float available = mFrame.right - dp(ROW_PAD_RIGHT) - metaWidth - dp(8f) - crumbStart;
            canvas.drawText(ellipsize(mMono, "/ " + mCrumb, available), crumbStart,
                titleBaseline, mMono);
        }

        float filterBottom = titleBottom + dp(FILTER_ROW_H);
        drawHairline(canvas, filterBottom, ColorUtils.setAlphaComponent(Color.WHITE, 26), alpha);
        mMono.setTextSize(dp(SIZE_FILTER));
        mMono.setColor(withBodyAlpha(mPrimary, alpha));
        float promptBaseline = baseline(titleBottom, dp(FILTER_ROW_H), mMono);
        canvas.drawText("❯", mFrame.left + dp(ROW_PAD_LEFT), promptBaseline, mMono);
        float queryStart = mFrame.left + dp(ROW_PAD_LEFT) + mMono.measureText("❯ ");
        boolean showPlaceholder = mQuery.isEmpty();
        mMono.setColor(withBodyAlpha(showPlaceholder ? mMeta : mOnSurface, alpha));
        String queryText = showPlaceholder ? mQueryPlaceholder : mQuery;
        canvas.drawText(ellipsizeStart(mMono, queryText,
            mFrame.right - dp(ROW_PAD_RIGHT) - queryStart), queryStart, promptBaseline, mMono);
        if (!showPlaceholder) {
            float caretX = queryStart + Math.min(mMono.measureText(mQuery),
                mFrame.right - dp(ROW_PAD_RIGHT) - queryStart);
            mFill.setColor(withBodyAlpha(mPrimary, alpha));
            canvas.drawRect(caretX + dp(1f), promptBaseline - lineHeightOf(mMono) * 0.78f,
                caretX + dp(1f) + mDensity, promptBaseline + dp(2f), mFill);
        }

        float listBottom = mArgumentMode ? mFrame.bottom - dp(ARG_ROW_H) : mFrame.bottom;
        drawList(canvas, filterBottom, listBottom, alpha);
        if (mArgumentMode) drawArgumentRow(canvas, listBottom, alpha);
        canvas.restoreToCount(save);
    }

    private void drawList(@NonNull Canvas canvas, float top, float bottom, int alpha) {
        int save = canvas.save();
        canvas.clipRect(mFrame.left, top, mFrame.right, bottom);
        float viewportHeight = bottom - top;
        updateScrollOffset(viewportHeight);
        float y = top - mScrollOffset;
        for (int i = 0; i < mRows.size(); i++) {
            float height = rowHeight(i);
            if (y + height >= top && y <= bottom) drawRow(canvas, mRows.get(i), i, y, height, alpha);
            y += height;
        }
        // Bottom fade so a clipped last row never cuts hard.
        if (listContentHeight() > viewportHeight) {
            mFill.setShader(new LinearGradient(0f, bottom - dp(BOTTOM_FADE_H), 0f, bottom,
                Color.TRANSPARENT,
                withBodyAlpha(ColorUtils.setAlphaComponent(mGlassBase, 230), alpha),
                Shader.TileMode.CLAMP));
            canvas.drawRect(mFrame.left, bottom - dp(BOTTOM_FADE_H), mFrame.right, bottom, mFill);
            mFill.setShader(null);
        }
        canvas.restoreToCount(save);
    }

    /** Keeps the focused row inside the clipped viewport, with the spec's bottom slack. */
    private void updateScrollOffset(float viewportHeight) {
        float contentHeight = listContentHeight();
        if (contentHeight <= viewportHeight || mFocusIndex < 0) {
            mScrollOffset = 0f;
            return;
        }
        float focusTop = 0f;
        for (int i = 0; i < mFocusIndex && i < mRows.size(); i++) focusTop += rowHeight(i);
        float focusBottom = focusTop
            + (mFocusIndex < mRows.size() ? rowHeight(mFocusIndex) : 0f) + dp(VIEWPORT_BOTTOM_SLACK);
        float offset = mScrollOffset;
        if (focusTop < offset) offset = focusTop;
        if (focusBottom > offset + viewportHeight) offset = focusBottom - viewportHeight;
        mScrollOffset = Math.max(0f, Math.min(offset, contentHeight - viewportHeight));
    }

    private void drawRow(@NonNull Canvas canvas, @NonNull Row row, int index, float top,
                         float height, int alpha) {
        if (row.kind != KIND_ENTRY) {
            drawRule(canvas, row.primary, top, alpha,
                row.kind == KIND_NOTICE ? mOnSurfaceVariant : mPrimary);
            return;
        }
        boolean focused = index == mFocusIndex;
        if (focused) {
            mFill.setShader(null);
            mFill.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(mPrimary, 28), alpha));
            canvas.drawRect(mFrame.left, top, mFrame.right, top + height, mFill);
            mFill.setColor(withBodyAlpha(mPrimary, alpha));
            canvas.drawRect(mFrame.left, top, mFrame.left + dp(FOCUS_BAR_W), top + height, mFill);
        }
        int titleColor = row.enabled ? mOnSurface : mMeta;
        mMono.setTextSize(dp(SIZE_ROW));
        mMono.setLetterSpacing(0f);
        mMono.setColor(withBodyAlpha(titleColor, row.enabled ? alpha : alpha / 2));
        float titleBaseline = top + dp(ROW_PAD_V) - mMono.getFontMetrics().ascent;

        mMono.setTextSize(dp(SIZE_SHORTCUT));
        float shortcutWidth = row.shortcut.isEmpty() ? 0f
            : mMono.measureText(row.shortcut) + dp(10f);
        mMono.setTextSize(dp(SIZE_ROW));
        float titleWidth = mFrame.width() - dp(ROW_PAD_LEFT) - dp(ROW_PAD_RIGHT) - shortcutWidth;
        canvas.drawText(ellipsize(mMono, row.primary, titleWidth),
            mFrame.left + dp(ROW_PAD_LEFT), titleBaseline, mMono);

        if (!row.shortcut.isEmpty()) {
            mMono.setTextSize(dp(SIZE_SHORTCUT));
            mMono.setColor(withBodyAlpha(mOnSurfaceVariant, alpha));
            canvas.drawText(row.shortcut,
                mFrame.right - dp(ROW_PAD_RIGHT) - mMono.measureText(row.shortcut),
                titleBaseline, mMono);
        }
        if (focused && row.description != null && !row.description.isEmpty()) {
            mMono.setTextSize(dp(SIZE_DESCRIPTION));
            mMono.setColor(withBodyAlpha(mMeta, alpha));
            float descriptionBaseline = titleBaseline + lineHeightOf(mMono);
            float width = mFrame.width() - dp(ROW_PAD_LEFT) - dp(ROW_PAD_RIGHT);
            canvas.drawText(ellipsize(mMono, "↳ " + row.description, width),
                mFrame.left + dp(ROW_PAD_LEFT), descriptionBaseline, mMono);
        }
    }

    /** {@code ── LABEL} followed by a hairline filling the remaining width. */
    private void drawRule(@NonNull Canvas canvas, @NonNull String label, float top, int alpha,
                          @ColorInt int color) {
        mMono.setTextSize(dp(SIZE_CATEGORY));
        mMono.setLetterSpacing(0.1f);
        mMono.setColor(withBodyAlpha(color, alpha));
        float baseline = top + dp(8f) - mMono.getFontMetrics().ascent;
        String text = "── " + label;
        float left = mFrame.left + dp(CATEGORY_PAD_LEFT);
        float available = mFrame.right - dp(ROW_PAD_RIGHT) - left;
        String drawn = ellipsize(mMono, text, available);
        canvas.drawText(drawn, left, baseline, mMono);
        float ruleStart = left + mMono.measureText(drawn) + dp(6f);
        mMono.setLetterSpacing(0f);
        float ruleEnd = mFrame.right - dp(ROW_PAD_RIGHT);
        if (ruleEnd > ruleStart) {
            mFill.setShader(null);
            mFill.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(mPrimary, 56), alpha));
            float ruleY = baseline + mMono.getFontMetrics().descent * 0.4f;
            canvas.drawRect(ruleStart, ruleY, ruleEnd, ruleY + mDensity, mFill);
        }
    }

    private void drawArgumentRow(@NonNull Canvas canvas, float top, int alpha) {
        mFill.setShader(null);
        mFill.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(mPrimary, 26), alpha));
        canvas.drawRect(mFrame.left, top, mFrame.right, mFrame.bottom, mFill);
        drawHairline(canvas, top, ColorUtils.setAlphaComponent(mPrimary, 56), alpha);

        mMono.setTextSize(dp(SIZE_FILTER));
        mMono.setLetterSpacing(0f);
        float argBaseline = baseline(top, dp(ARG_ROW_H), mMono);
        mMono.setColor(withBodyAlpha(mPrimary, alpha));
        canvas.drawText("arg ❯", mFrame.left + dp(ROW_PAD_LEFT), argBaseline, mMono);
        float valueStart = mFrame.left + dp(ROW_PAD_LEFT) + mMono.measureText("arg ❯ ");
        boolean empty = mArgumentValue.isEmpty();
        mMono.setColor(withBodyAlpha(empty ? mMeta : mOnSurface, alpha));
        float enterWidth = mMono.measureText("⏎") + dp(8f);
        float valueWidth = mFrame.right - dp(ROW_PAD_RIGHT) - enterWidth - valueStart;
        canvas.drawText(ellipsizeStart(mMono, empty ? mArgumentPlaceholder : mArgumentValue,
            valueWidth), valueStart, argBaseline, mMono);
        if (!empty) {
            float caretX = valueStart + Math.min(mMono.measureText(mArgumentValue), valueWidth);
            mFill.setShader(null);
            mFill.setColor(withBodyAlpha(mPrimary, alpha));
            canvas.drawRect(caretX + dp(1f), argBaseline - lineHeightOf(mMono) * 0.78f,
                caretX + dp(1f) + mDensity, argBaseline + dp(2f), mFill);
        }
        mMono.setColor(withBodyAlpha(mPrimary, alpha));
        canvas.drawText("⏎", mFrame.right - dp(ROW_PAD_RIGHT) - mMono.measureText("⏎"),
            argBaseline, mMono);
    }

    private void drawStrip(@NonNull Canvas canvas) {
        layoutKeycaps();
        if (mCapRects.isEmpty()) return;
        int alpha = Math.round(255f * mStripAlpha);
        int save = canvas.save();
        canvas.translate(0f, mStripOffset);
        for (int i = 0; i < mCapRects.size(); i++) {
            RectF cap = mCapRects.get(i);
            Keycap keycap = mKeycaps.get(i);
            float radius = dp(CAP_RADIUS);
            mFill.setShader(null);
            mFill.setColor(withBodyAlpha(mChipFill, alpha));
            canvas.drawRoundRect(cap, radius, radius, mFill);
            mFill.setShader(new LinearGradient(0f, cap.top, 0f, cap.bottom,
                ColorUtils.setAlphaComponent(Color.WHITE, 26),
                ColorUtils.setAlphaComponent(Color.BLACK, 56), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(cap, radius, radius, mFill);
            mFill.setShader(null);
            mStroke.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(Color.WHITE, 64), alpha));
            float inset = mStroke.getStrokeWidth() / 2f;
            mRect.set(cap.left + inset, cap.top + inset, cap.right - inset, cap.bottom - inset);
            canvas.drawRoundRect(mRect, radius, radius, mStroke);

            mMono.setTextSize(dp(SIZE_CAP_GLYPH));
            mMono.setLetterSpacing(0f);
            float baseline = baseline(cap.top, cap.height(), mMono);
            mMono.setColor(withBodyAlpha(mPrimary, alpha));
            canvas.drawText(keycap.glyph, cap.left + dp(CAP_PAD_H), baseline, mMono);
            float labelStart = cap.left + dp(CAP_PAD_H) + mMono.measureText(keycap.glyph) + dp(4f);
            mMono.setTextSize(dp(SIZE_CAP_LABEL));
            mMono.setColor(withBodyAlpha(mCapLabel, alpha));
            canvas.drawText(keycap.label, labelStart, baseline, mMono);
        }
        canvas.restoreToCount(save);
    }

    /**
     * Lays the caps out left to right under the palette's bottom border, scaling their padding
     * down if the six of them would otherwise pass the palette's own width.
     */
    private void layoutKeycaps() {
        mCapRects.clear();
        if (mKeycaps.isEmpty() || mFrame.isEmpty()) return;
        mMono.setTextSize(dp(SIZE_CAP_LABEL));
        float capHeight = dp(CAP_PAD_V) * 2f + lineHeight(SIZE_CAP_GLYPH);
        float top = mFrame.bottom + dp(STRIP_GAP);
        float available = mFrame.width() - dp(STRIP_LEFT_EXTRA);
        float[] widths = new float[mKeycaps.size()];
        float total = dp(CAP_GAP) * (mKeycaps.size() - 1);
        for (int i = 0; i < mKeycaps.size(); i++) {
            Keycap keycap = mKeycaps.get(i);
            mMono.setTextSize(dp(SIZE_CAP_GLYPH));
            float glyph = mMono.measureText(keycap.glyph);
            mMono.setTextSize(dp(SIZE_CAP_LABEL));
            widths[i] = dp(CAP_PAD_H) * 2f + glyph + dp(4f) + mMono.measureText(keycap.label);
            total += widths[i];
        }
        float scale = total > available ? available / total : 1f;
        float x = mFrame.left + dp(STRIP_LEFT_EXTRA);
        for (int i = 0; i < widths.length; i++) {
            float width = widths[i] * scale;
            mCapRects.add(new RectF(x, top, x + width, top + capHeight));
            x += width + dp(CAP_GAP) * scale;
        }
    }

    private void drawConfirmation(@NonNull Canvas canvas) {
        String text = mConfirmationText;
        if (text == null) return;
        mMono.setTextSize(dp(SIZE_ROW));
        mMono.setLetterSpacing(0f);
        mMono.setColor(mConfirmation);
        canvas.drawText(ellipsize(mMono, text, getWidth() - mConfirmationLeft - dp(ROW_PAD_LEFT)),
            mConfirmationLeft, mConfirmationBaseline, mMono);
    }

    private void drawHairline(@NonNull Canvas canvas, float y, @ColorInt int color, int alpha) {
        mFill.setShader(null);
        mFill.setColor(withBodyAlpha(color, alpha));
        canvas.drawRect(mFrame.left, y - mDensity, mFrame.right, y, mFill);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Once collapsed to a confirmation line the overlay stops being modal, so taps fall
        // through to the terminal underneath.
        if (mProgress <= 0.01f) return false;
        if (mCallbacks == null || event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = event.getX();
        float y = event.getY();
        for (int i = 0; i < mCapRects.size(); i++) {
            RectF cap = mCapRects.get(i);
            if (x >= cap.left && x <= cap.right
                && y >= cap.top + mStripOffset && y <= cap.bottom + mStripOffset) {
                mCallbacks.onKeycapTapped(i);
                return true;
            }
        }
        if (!mFrame.contains(x, y)) {
            mCallbacks.onOutsideTapped();
            return true;
        }
        float listTop = mFrame.top + dp(TITLE_BAR_H) + dp(FILTER_ROW_H);
        float listBottom = mArgumentMode ? mFrame.bottom - dp(ARG_ROW_H) : mFrame.bottom;
        if (y < listTop || y > listBottom) return true;
        float rowY = listTop - mScrollOffset;
        for (int i = 0; i < mRows.size(); i++) {
            float height = rowHeight(i);
            if (y >= rowY && y < rowY + height) {
                if (mRows.get(i).isSelectable()) mCallbacks.onRowTapped(i);
                return true;
            }
            rowY += height;
        }
        return true;
    }

    private float baseline(float top, float height, @NonNull Paint paint) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return top + (height - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent;
    }

    private static float lineHeightOf(@NonNull Paint paint) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    @NonNull
    private static String ellipsize(@NonNull TextPaint paint, @NonNull String text, float width) {
        if (width <= 0f) return "";
        return TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString();
    }

    /** Keeps the tail of a typed value visible once it outgrows its field. */
    @NonNull
    private static String ellipsizeStart(@NonNull TextPaint paint, @NonNull String text,
                                         float width) {
        if (width <= 0f) return "";
        return TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.START).toString();
    }

    private static int withBodyAlpha(@ColorInt int color, int alpha) {
        return ColorUtils.setAlphaComponent(color, Color.alpha(color) * alpha / 255);
    }

    private float dp(float value) {
        return value * mDensity;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }
}
