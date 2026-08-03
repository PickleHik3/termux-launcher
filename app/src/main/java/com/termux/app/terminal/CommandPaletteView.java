package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

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
        /** Leading artwork, for app rows; text rows leave the gutter out entirely. */
        @Nullable final Drawable icon;

        private Row(int kind, @NonNull String primary, @Nullable String description,
                    @NonNull String shortcut, boolean enabled, @Nullable Drawable icon) {
            this.kind = kind;
            this.primary = primary;
            this.description = description;
            this.shortcut = shortcut;
            this.enabled = enabled;
            this.icon = icon;
        }

        @NonNull
        public static Row category(@NonNull String label) {
            return new Row(KIND_CATEGORY, label, null, "", true, null);
        }

        @NonNull
        public static Row notice(@NonNull String text) {
            return new Row(KIND_NOTICE, text, null, "", true, null);
        }

        @NonNull
        public static Row entry(@NonNull String title, @Nullable String description,
                                @NonNull String shortcut, boolean enabled) {
            return entry(title, description, shortcut, enabled, null);
        }

        @NonNull
        public static Row entry(@NonNull String title, @Nullable String description,
                                @NonNull String shortcut, boolean enabled,
                                @Nullable Drawable icon) {
            return new Row(KIND_ENTRY, title, description, shortcut, enabled, icon);
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
    private static final float FILTER_ROW_H = 34f;
    private static final float ARG_ROW_H = 30f;
    private static final float CATEGORY_H = 22f;
    private static final float ROW_PAD_V = 6f;
    private static final float ROW_PAD_LEFT = 14f;
    private static final float ROW_PAD_RIGHT = 12f;
    private static final float CATEGORY_PAD_LEFT = 12f;
    private static final float ICON_SIZE = 16f;
    private static final float ICON_GAP = 7f;
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

    // Drop shadow, drawn here rather than taken from View elevation: the caster would have to be
    // the full-screen glass pane, whose platform shadow came out as a flat band under the bottom
    // edge with square ends that ignored the rounded corners. These rings are concentric with the
    // animated rounded rect, so the corners stay round and the falloff is smooth. Each ring is
    // faint and they accumulate outward-to-inward into a ramp.
    private static final float SHADOW_SPREAD = 14f;
    private static final float SHADOW_DROP = 5f;
    // Many thin rings rather than a few thick ones: at 7 rings the accumulation stepped in
    // measurable ~4-level plateaus, which is the banding that makes a faked shadow look faked.
    private static final int SHADOW_RINGS = 14;
    private static final int SHADOW_RING_ALPHA = 4;

    /** Fling decay per frame, and the speeds at which a fling starts and stops. */
    private static final float FLING_DECAY = 0.90f;
    private static final float FLING_MIN_START_DP = 60f;
    private static final float FLING_MIN_KEEP_DP = 24f;

    // Type sizes, in dp for a stable ledger grid under any font scale.
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
    private final RectF mRect = new RectF();
    private final RectF mFrame = new RectF();
    private final Path mShadowClip = new Path();
    /**
     * The rounded frame as a clip path. Every body and list fill is a square-cornered drawRect, so
     * clipping them to a plain rect let anything flush with the frame's bottom paint into the
     * corner arcs the surface's drawRoundRect leaves empty. Cached because it is rebuilt only when
     * the animated frame moves, not once per fill.
     */
    private final Path mFramePath = new Path();
    private boolean mFramePathDirty = true;
    /**
     * Region the overlay is modal over. The view fills the activity so the sprout can start at
     * the space bar, but only this rectangle may swallow touches — everything below it is the
     * in-app keyboard, and taps there have to reach the keys or the palette could never be typed
     * into.
     */
    private final RectF mModalBounds = new RectF();
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
    /** Caret index into the active buffer (query or argument value); values past the end clamp. */
    private int mQueryCursor = Integer.MAX_VALUE;
    private boolean mArgumentMode;
    private String mArgumentPlaceholder = "";
    private String mArgumentValue = "";
    @Nullable private String mConfirmationText;
    private float mConfirmationLeft;
    private float mConfirmationBaseline;
    private float mScrollOffset;

    private final float mTouchSlop;
    /** True while the offset is the finger's rather than the focused row's. */
    private boolean mUserScrolled;
    private boolean mDragging;
    private float mDownX;
    private float mDownY;
    private float mLastTouchY;
    @Nullable private VelocityTracker mVelocity;
    private float mFlingVelocity;
    private final Runnable mFlingStep = this::stepFling;

    public CommandPaletteView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mMono.setTypeface(Typeface.MONOSPACE);
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
        mMeta = ColorUtils.setAlphaComponent(mOnSurfaceVariant, 212);
        mPrimary = InAppKeyboardPaletteFactory.ensureContrast(mPrimary, overGlass);
        mConfirmation = InAppKeyboardPaletteFactory.ensureContrast(
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary,
                mPrimary), overGlass);

        // Keycaps use the keyboard's chip recipe, so their labels are checked against the
        // chip composited over the glass rather than over the glass alone.
        mChipFill = ColorUtils.setAlphaComponent(surfaceContainerHigh, 160);
        int capOverGlass = ColorUtils.compositeColors(mChipFill, overGlass);
        mCapLabel = InAppKeyboardPaletteFactory.ensureContrast(onSurface, capOverGlass);
        invalidate();
    }

    /** Sets the animated rectangle and the fades driven by the sprout progress. */
    public void setFrame(@NonNull RectF frame, float radius, float bodyAlpha, float stripAlpha,
                         float stripOffsetPx, float progress) {
        mFrame.set(frame);
        mRadius = radius;
        mFramePathDirty = true;
        mBodyAlpha = clamp01(bodyAlpha);
        mStripAlpha = clamp01(stripAlpha);
        mStripOffset = stripOffsetPx;
        mProgress = clamp01(progress);
        invalidate();
    }

    /**
     * Sets the rectangle inside which taps belong to the palette. Taps outside it fall through to
     * whatever is underneath — in practice the in-app keyboard.
     */
    public void setModalBounds(@NonNull RectF bounds) {
        mModalBounds.set(bounds);
    }

    public void setRows(@NonNull List<Row> rows, int focusIndex) {
        // Any focus move or result change hands the viewport back to the focused row.
        if (focusIndex != mFocusIndex || rows != mRows) resetScroll();
        mRows = rows;
        mFocusIndex = focusIndex;
        invalidate();
    }

    /** Drops a manual scroll and any fling, so the next draw follows the focused row again. */
    public void resetScroll() {
        abortFling();
        mUserScrolled = false;
        mScrollOffset = 0f;
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

    public void setQueryCursor(int cursor) {
        mQueryCursor = cursor;
        invalidate();
    }

    /** Advance of the text before the caret, so the caret can sit mid-string. */
    private float measureToCursor(@NonNull TextPaint paint, @NonNull String text) {
        int cursor = Math.max(0, Math.min(mQueryCursor, text.length()));
        return paint.measureText(text.substring(0, cursor));
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
        float height = chromeHeight() + listContentHeight();
        if (mArgumentMode) height += dp(ARG_ROW_H);
        return height;
    }

    /**
     * The filter row: what the palette is with no rows under it, and so the floor the height
     * spring collapses to when the query is empty.
     */
    public float chromeHeight() {
        return dp(FILTER_ROW_H);
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

        drawSurface(canvas);
        if (mBodyAlpha > 0.004f) drawBody(canvas);
        drawRim(canvas);
        if (mStripAlpha > 0.004f) drawStrip(canvas);
        if (mConfirmationText != null) drawConfirmation(canvas);
    }

    // The spec's `0 18px 40px` drop shadow used to be faked with three concentric black rounded
    // rects at 30/18/7dp spread, because hardware canvases only honour setShadowLayer for text.
    // At 30dp the outermost one reached past the keycap strip, so the palette read as three
    // stacked panels behind the search box rather than one floating rectangle. The rim and the
    // backdrop blur carry the separation on their own.

    private void drawSurface(@NonNull Canvas canvas) {
        drawDropShadow(canvas);
        // Glass base at the dock's own tint alpha, then the vertical light model over it.
        mFill.setShader(null);
        mFill.setColor(ColorUtils.setAlphaComponent(mGlassBase, 158));
        canvas.drawRoundRect(mFrame, mRadius, mRadius, mFill);

        mFill.setShader(new LinearGradient(0f, mFrame.top, 0f, mFrame.bottom,
            ColorUtils.setAlphaComponent(Color.WHITE, 20),
            ColorUtils.setAlphaComponent(Color.BLACK, 46), Shader.TileMode.CLAMP));
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
    }

    /**
     * Accent-tinted rim: the palette rim is primary, unlike the keycaps' white hairline. Drawn
     * after the body, not as part of the surface — the rim belongs to the surface, so it has to
     * survive the body's fills. Stroked before them, the bottom fade and the last row's focus wash
     * painted over its bottom segment, which is what made the edge read as a band rather than a rim.
     */
    private void drawRim(@NonNull Canvas canvas) {
        mStroke.setColor(ColorUtils.setAlphaComponent(mPrimary, 150));
        float inset = mStroke.getStrokeWidth() / 2f;
        mRect.set(mFrame.left + inset, mFrame.top + inset,
            mFrame.right - inset, mFrame.bottom - inset);
        canvas.drawRoundRect(mRect, mRadius, mRadius, mStroke);
    }

    private void clipToFrame(@NonNull Canvas canvas) {
        canvas.clipPath(framePath());
    }

    /**
     * minSdk is 26 and Canvas has no clipRoundRect, so clipPath is the only option. A
     * uniform-radius addRoundRect is recognised by Skia as an rrect and clipped analytically, so
     * this costs about what the existing clipOutPath in the shadow pass does. Hardware rrect clips
     * are not always antialiased, which can leave the focus wash's corner marginally jaggy —
     * invisible at its alpha of 28.
     */
    @NonNull
    private Path framePath() {
        if (mFramePathDirty) {
            mFramePath.rewind();
            mFramePath.addRoundRect(mFrame, mRadius, mRadius, Path.Direction.CW);
            mFramePathDirty = false;
        }
        return mFramePath;
    }

    /**
     * Soft shadow around the palette, clipped out of the frame itself so it darkens only what the
     * glass floats over. Spread and drop scale with the sprout, so the panel lifts off the
     * terminal as it opens.
     */
    private void drawDropShadow(@NonNull Canvas canvas) {
        if (mProgress <= 0.02f) return;
        int save = canvas.save();
        mShadowClip.rewind();
        mShadowClip.addRoundRect(mFrame, mRadius, mRadius, Path.Direction.CW);
        canvas.clipOutPath(mShadowClip);
        mFill.setShader(null);
        mFill.setColor(ColorUtils.setAlphaComponent(Color.BLACK,
            Math.max(1, Math.round(SHADOW_RING_ALPHA * mProgress))));
        for (int ring = SHADOW_RINGS; ring >= 1; ring--) {
            float scale = ring / (float) SHADOW_RINGS;
            float spread = dp(SHADOW_SPREAD) * scale * mProgress;
            float drop = dp(SHADOW_DROP) * scale * mProgress;
            mRect.set(mFrame.left - spread, mFrame.top - spread + drop,
                mFrame.right + spread, mFrame.bottom + spread + drop);
            canvas.drawRoundRect(mRect, mRadius + spread, mRadius + spread, mFill);
        }
        canvas.restoreToCount(save);
    }

    private void drawBody(@NonNull Canvas canvas) {
        int save = canvas.save();
        clipToFrame(canvas);
        int alpha = Math.round(255f * mBodyAlpha);

        // One surface, no title bar: the filter row is the top edge, with the crumb and result
        // meta right-aligned inside it instead of on a row of their own.
        float filterBottom = mFrame.top + dp(FILTER_ROW_H);
        drawHairline(canvas, filterBottom, ColorUtils.setAlphaComponent(Color.WHITE, 26), alpha);
        mMono.setTextSize(dp(SIZE_FILTER));
        mMono.setLetterSpacing(0f);
        mMono.setColor(withBodyAlpha(mPrimary, alpha));
        float promptBaseline = baseline(mFrame.top, dp(FILTER_ROW_H), mMono);
        canvas.drawText("❯", mFrame.left + dp(ROW_PAD_LEFT), promptBaseline, mMono);
        float queryStart = mFrame.left + dp(ROW_PAD_LEFT) + mMono.measureText("❯ ");

        mMono.setTextSize(dp(SIZE_META));
        mMono.setColor(withBodyAlpha(mMeta, alpha));
        float metaWidth = mMono.measureText(mMetaText);
        canvas.drawText(mMetaText, mFrame.right - dp(ROW_PAD_RIGHT) - metaWidth,
            promptBaseline, mMono);
        float queryEnd = mFrame.right - dp(ROW_PAD_RIGHT)
            - (metaWidth > 0f ? metaWidth + dp(8f) : 0f);

        if (!mCrumb.isEmpty()) {
            mMono.setTextSize(dp(SIZE_CRUMB));
            mMono.setColor(withBodyAlpha(mOnSurfaceVariant, alpha));
            String crumb = ellipsize(mMono, "/ " + mCrumb, (queryEnd - queryStart) * 0.5f);
            float crumbWidth = mMono.measureText(crumb);
            canvas.drawText(crumb, queryEnd - crumbWidth, promptBaseline, mMono);
            queryEnd -= crumbWidth + dp(8f);
        }

        mMono.setTextSize(dp(SIZE_FILTER));
        boolean showPlaceholder = mQuery.isEmpty();
        mMono.setColor(withBodyAlpha(showPlaceholder ? mMeta : mOnSurface, alpha));
        String queryText = showPlaceholder ? mQueryPlaceholder : mQuery;
        canvas.drawText(ellipsizeStart(mMono, queryText, queryEnd - queryStart),
            queryStart, promptBaseline, mMono);
        if (!showPlaceholder) {
            float caretX = queryStart + Math.min(measureToCursor(mMono, mQuery),
                queryEnd - queryStart);
            mFill.setColor(withBodyAlpha(mPrimary, alpha));
            canvas.drawRect(caretX + dp(1f), promptBaseline - lineHeightOf(mMono) * 0.78f,
                caretX + dp(1f) + mDensity, promptBaseline + dp(2f), mFill);
        }

        float listBottom = listBottom();
        drawList(canvas, filterBottom, listBottom, alpha);
        if (mArgumentMode) drawArgumentRow(canvas, listBottom, alpha);
        canvas.restoreToCount(save);
    }

    private float listTop() {
        return mFrame.top + dp(FILTER_ROW_H);
    }

    private float listBottom() {
        return mArgumentMode ? mFrame.bottom - dp(ARG_ROW_H) : mFrame.bottom;
    }

    private float maxScroll() {
        return Math.max(0f, listContentHeight() - (listBottom() - listTop()));
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
        if (mUserScrolled) {
            // The finger owns the viewport; only re-clamp in case the content shrank under it.
            mScrollOffset = Math.max(0f, Math.min(mScrollOffset, contentHeight - viewportHeight));
            return;
        }
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
        float textLeft = mFrame.left + dp(ROW_PAD_LEFT);
        if (row.icon != null) {
            drawRowIcon(canvas, row.icon, textLeft, top, row.enabled ? alpha : alpha / 2);
            textLeft += dp(ICON_SIZE) + dp(ICON_GAP);
        }

        mMono.setTextSize(dp(SIZE_SHORTCUT));
        float shortcutWidth = row.shortcut.isEmpty() ? 0f
            : mMono.measureText(row.shortcut) + dp(10f);
        mMono.setTextSize(dp(SIZE_ROW));
        float titleWidth = mFrame.right - dp(ROW_PAD_RIGHT) - shortcutWidth - textLeft;
        canvas.drawText(ellipsize(mMono, row.primary, titleWidth), textLeft, titleBaseline, mMono);

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
            float width = mFrame.right - dp(ROW_PAD_RIGHT) - textLeft;
            canvas.drawText(ellipsize(mMono, "↳ " + row.description, width),
                textLeft, descriptionBaseline, mMono);
        }
    }

    /**
     * Draws row artwork centred on the title line. The alpha is set on the drawable and put back
     * immediately: these are the launcher's own cached icon instances, shared with the app grid,
     * and {@code mutate()} per frame would copy artwork on every scroll frame.
     */
    private void drawRowIcon(@NonNull Canvas canvas, @NonNull Drawable icon, float left, float top,
                            int alpha) {
        float size = dp(ICON_SIZE);
        float iconTop = top + dp(ROW_PAD_V) + (lineHeight(SIZE_ROW) - size) / 2f;
        icon.setBounds(Math.round(left), Math.round(iconTop),
            Math.round(left + size), Math.round(iconTop + size));
        int previous = icon.getAlpha();
        icon.setAlpha(alpha);
        icon.draw(canvas);
        icon.setAlpha(previous);
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
            float caretX = valueStart + Math.min(measureToCursor(mMono, mArgumentValue), valueWidth);
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
            mStroke.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(Color.WHITE, 88), alpha));
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
     * Lays the caps out in a row centered under the palette's bottom border, scaling their
     * padding down if the six of them would otherwise pass the palette's own width.
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
        float x = mFrame.left + (mFrame.width() - total * scale) / 2f;
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
        if (mProgress <= 0.01f || mCallbacks == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isModalAt(event.getX(), event.getY())) return false;
                abortFling();
                mDragging = false;
                mDownX = event.getX();
                mDownY = event.getY();
                mLastTouchY = mDownY;
                if (mVelocity != null) mVelocity.clear();
                else mVelocity = VelocityTracker.obtain();
                mVelocity.addMovement(event);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (mVelocity != null) mVelocity.addMovement(event);
                float y = event.getY();
                if (!mDragging && Math.abs(y - mDownY) > mTouchSlop
                    && maxScroll() > 0f && inList(mDownX, mDownY))
                    mDragging = true;
                if (mDragging) {
                    scrollByPx(mLastTouchY - y);
                    mLastTouchY = y;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (mDragging) {
                    startFling();
                    return true;
                }
                releaseVelocity();
                handleTap(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                releaseVelocity();
                return true;
            default:
                return true;
        }
    }

    /** Whether the palette owns this point, or it belongs to whatever is underneath. */
    private boolean isModalAt(float x, float y) {
        if (mFrame.contains(x, y)) return true;
        for (RectF cap : mCapRects)
            if (x >= cap.left && x <= cap.right
                && y >= cap.top + mStripOffset && y <= cap.bottom + mStripOffset) return true;
        // No bounds set yet: behave as a full-screen scrim rather than leaking every tap.
        return mModalBounds.isEmpty() || mModalBounds.contains(x, y);
    }

    private boolean inList(float x, float y) {
        return mFrame.contains(x, y) && y >= listTop() && y <= listBottom();
    }

    private void handleTap(float x, float y) {
        if (mCallbacks == null) return;
        for (int i = 0; i < mCapRects.size(); i++) {
            RectF cap = mCapRects.get(i);
            if (x >= cap.left && x <= cap.right
                && y >= cap.top + mStripOffset && y <= cap.bottom + mStripOffset) {
                mCallbacks.onKeycapTapped(i);
                return;
            }
        }
        if (!mFrame.contains(x, y)) {
            mCallbacks.onOutsideTapped();
            return;
        }
        if (y < listTop() || y > listBottom()) return;
        float rowY = listTop() - mScrollOffset;
        for (int i = 0; i < mRows.size(); i++) {
            float height = rowHeight(i);
            if (y >= rowY && y < rowY + height) {
                if (mRows.get(i).isSelectable()) mCallbacks.onRowTapped(i);
                return;
            }
            rowY += height;
        }
    }

    /** @return true when the offset actually moved, so a fling knows it has not hit an edge. */
    private boolean scrollByPx(float dy) {
        float max = maxScroll();
        float next = Math.max(0f, Math.min(mScrollOffset + dy, max));
        mUserScrolled = true;
        if (next == mScrollOffset) return false;
        mScrollOffset = next;
        invalidate();
        return true;
    }

    private void startFling() {
        mDragging = false;
        float velocityY = 0f;
        if (mVelocity != null) {
            mVelocity.computeCurrentVelocity(1000);
            velocityY = mVelocity.getYVelocity();
        }
        releaseVelocity();
        // A flick up (negative y velocity) walks the list forward, so the offset grows.
        mFlingVelocity = -velocityY;
        if (Math.abs(mFlingVelocity) < dp(FLING_MIN_START_DP)) {
            mFlingVelocity = 0f;
            return;
        }
        postOnAnimation(mFlingStep);
    }

    private void stepFling() {
        if (mFlingVelocity == 0f) return;
        boolean moved = scrollByPx(mFlingVelocity / 60f);
        mFlingVelocity *= FLING_DECAY;
        if (moved && Math.abs(mFlingVelocity) > dp(FLING_MIN_KEEP_DP)) postOnAnimation(mFlingStep);
        else mFlingVelocity = 0f;
    }

    private void abortFling() {
        mFlingVelocity = 0f;
        removeCallbacks(mFlingStep);
    }

    private void releaseVelocity() {
        if (mVelocity == null) return;
        mVelocity.recycle();
        mVelocity = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        abortFling();
        releaseVelocity();
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
