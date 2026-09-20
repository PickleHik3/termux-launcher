package com.termux.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.az.AzBarFrame;
import com.termux.app.launcher.az.AzFloatingStripPolicy;
import com.termux.app.launcher.az.AzScrubGesture;
import com.termux.app.place.PlaceLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Glassmorphic renderer for AZ and icon-row drag interactions.
 *
 * <p>It also hosts the standalone index's floating strip of matches, for the places that put their
 * pinned apps on a rail or hid them: with no apps row to fill, the letters' matches are drawn here
 * instead. Every number the strip is drawn from comes from {@link AzFloatingStripPolicy}.
 */
public final class LauncherAzGestureFxView extends View {

    public enum InteractionMode {
        LETTER_TRACK,
        ICON_TRACK_LOCKED
    }

    public enum RenderLayer {
        UNDERLAY,
        OVERLAY
    }

    private final Paint glassStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeDwellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final FocusOutlineRenderer.RenderPaints focusedIconOutlinePaints =
        new FocusOutlineRenderer.RenderPaints();
    private final FocusOutlineRenderer.RenderPaints floatingStripOutlinePaints =
        new FocusOutlineRenderer.RenderPaints();
    private final TextPaint previewLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();
    private final RectF tmpShadowRect = new RectF();
    private final RectF focusDisplayRect = new RectF();
    private final RectF focusRawRect = new RectF();
    private final RectF previewRect = new RectF();
    private final RectF floatingStripOutlineRect = new RectF();
    private final RectF appsRowRawBounds = new RectF();
    private final int[] locationOnScreen = new int[2];
    private final int[] edgeDwellGradientColors = new int[3];
    private final float[] edgeDwellGradientStops = {0f, 0.48f, 1f};
    @Nullable private RadialGradient edgeDwellShader;
    private float edgeDwellShaderX = Float.NaN;
    private float edgeDwellShaderY = Float.NaN;
    private float edgeDwellShaderRadius = Float.NaN;
    private int edgeDwellShaderCoreColor;
    private int edgeDwellShaderOuterColor;
    private int edgeDwellShaderEndColor;

    private int glassTintColor = 0xFF86A7FF;
    private int edgeTintColor = 0xFF7CE2FF;

    private boolean dragActive;
    private float targetRawX;

    private boolean hasFocus;
    private float edgeDwellProgress;
    private float edgeDwellRawX;
    private float edgeDwellRawY;
    @Nullable private Drawable focusedAppPreviewIcon;
    private float focusedAppPreviewProgress;
    @Nullable private ValueAnimator focusedAppPreviewAnimator;
    private float focusedAppPreviewSettleProgress;
    @Nullable private ValueAnimator focusedAppPreviewSettleAnimator;
    private boolean hasPreviewPosition;
    private float previewDisplayRawX;
    private boolean focusedAppPreviewLaunchDismissing;
    private boolean focusedAppPreviewLabelEnabled;
    private boolean focusedIconRingEnabled = true;
    @Nullable private FocusOutlineRenderer.Visual focusedIconOutlineVisual;
    private final RectF focusedIconOutlineRawBounds = new RectF();
    @Nullable private FocusOutlineRenderer.Visual outgoingIconOutlineVisual;
    private final RectF outgoingIconOutlineRawBounds = new RectF();
    private float focusedIconOutlineAlpha = 1f;
    private float focusedIconOutlineScale = 1f;
    private float outgoingIconOutlineAlpha;
    private float outgoingIconOutlineScale = 0.96f;
    @Nullable private ValueAnimator focusedIconOutlineAnimator;
    @Nullable private String focusedAppPreviewLabel;
    @Nullable private StaticLayout focusedAppPreviewLabelLayout;
    @Nullable private String focusedAppPreviewLabelLayoutText;
    private int focusedAppPreviewLabelLayoutWidth = -1;
    private float focusedAppPreviewLabelLayoutTextSize = -1f;
    private boolean darkThemeActive = true;
    @NonNull private RenderLayer renderLayer = RenderLayer.OVERLAY;

    /** The standalone index's strip of matches: its geometry, its artwork and which slot has focus. */
    @Nullable private AzFloatingStripPolicy.Strip floatingStrip;
    /**
     * The edge the bar stands on, as the map between the frame the strip is laid out in and the
     * screen it is drawn on. A bottom bar is that frame already, so this rests as the identity.
     */
    @NonNull private AzBarFrame barFrame = AzBarFrame.bottom(0f, 0f);
    /** True while the band the matches fill stands between the letters and the screen's rim. */
    private boolean previewTrackOutward;
    @NonNull private final List<Drawable> floatingStripIcons = new ArrayList<>();
    /**
     * Per-slot contour visuals, parallel to {@link #floatingStripIcons}: resolved once by the caller
     * when the strip's artwork changes, never per frame. A slot with no visual (index out of range,
     * or a null entry) falls back to the rounded rect the focus ring always had.
     */
    @NonNull private final List<FocusOutlineRenderer.Visual> floatingStripVisuals = new ArrayList<>();
    private int floatingStripFocusedSlot = -1;
    private float floatingStripProgress;
    @Nullable private ValueAnimator floatingStripAnimator;

    /**
     * The focused icon's breath. Driven by a repeating animator that exists only while a finger is
     * down on a focused icon, so nothing here ever repaints at rest.
     */
    private float breathPhase;
    @Nullable private ValueAnimator breathAnimator;

    @NonNull private InteractionMode interactionMode = InteractionMode.LETTER_TRACK;

    public LauncherAzGestureFxView(Context context) {
        super(context);
        init();
    }

    public LauncherAzGestureFxView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LauncherAzGestureFxView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);

        glassStrokePaint.setStyle(Paint.Style.STROKE);
        glassStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        glassStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        edgePaint.setStyle(Paint.Style.FILL);
        previewFillPaint.setStyle(Paint.Style.FILL);
        previewLabelPaint.setTextAlign(Paint.Align.LEFT);
        previewLabelPaint.setSubpixelText(true);
        previewLabelPaint.setTextSize(dp(11f));
    }

    public void setColors(int glassTintColor, int edgeTintColor) {
        this.glassTintColor = enforceGlassVisibility(glassTintColor, 0.78f);
        this.edgeTintColor = enforceGlassVisibility(edgeTintColor, 0.84f);
        edgeDwellShader = null;
        invalidate();
    }

    public void updateDrag(
        boolean active,
        float rawX,
        @Nullable RectF focusedBoundsRaw,
        @NonNull InteractionMode mode
    ) {
        dragActive = active;
        targetRawX = rawX;
        interactionMode = mode;

        if (focusedBoundsRaw != null) {
            hasFocus = true;
            focusRawRect.set(focusedBoundsRaw);
            if (mode == InteractionMode.ICON_TRACK_LOCKED) {
                float nextPreviewRawX = focusRawRect.centerX();
                if (!hasPreviewPosition) {
                    previewDisplayRawX = nextPreviewRawX;
                    hasPreviewPosition = true;
                } else {
                    previewDisplayRawX = lerp(previewDisplayRawX, nextPreviewRawX, 0.42f);
                }
            }
        } else {
            hasFocus = false;
            if (!active || mode != InteractionMode.ICON_TRACK_LOCKED) {
                hasPreviewPosition = false;
            }
        }
        syncBreathing();
        refreshVisibility();
        invalidate();
    }

    /**
     * The edge the alphabets bar stands on. The band itself arrives as a screen rectangle, so all
     * this decides is which way it rises into place and which side of it the focused app's name
     * reads on — both of them "away from the bar", on whichever edge it is.
     */
    public void setBarFrame(@NonNull AzBarFrame frame) {
        barFrame = frame;
        invalidate();
    }

    public void setRowBounds(@Nullable RectF appsRowRaw) {
        if (appsRowRaw != null) {
            appsRowRawBounds.set(appsRowRaw);
        } else {
            appsRowRawBounds.setEmpty();
        }
    }

    /**
     * Which side of the letters the band being drawn stands on
     * ({@code AzPreviewTargetPolicy.Side}). The name reads on the far side of the band from the
     * letters, so a row the user ordered outside them turns the bubble over with it; without this
     * the name would be drawn into the gap it came from, over the very letters it is naming.
     */
    public void setPreviewTrackOutward(boolean outward) {
        if (previewTrackOutward == outward) return;
        previewTrackOutward = outward;
        invalidate();
    }

    public void setEdgeDwellProgress(float progress, float rawX, float rawY) {
        edgeDwellProgress = clamp01(progress);
        edgeDwellRawX = rawX;
        edgeDwellRawY = rawY;
        refreshVisibility();
        invalidate();
    }

    public void setRenderLayer(@NonNull RenderLayer renderLayer) {
        this.renderLayer = renderLayer;
        refreshVisibility();
        invalidate();
    }

    public void setFocusedAppPreviewIcon(@Nullable Drawable icon) {
        Drawable next = null;
        if (icon != null) {
            Drawable.ConstantState state = icon.getConstantState();
            next = state != null ? state.newDrawable(getResources()).mutate() : icon.mutate();
        }
        boolean same = (focusedAppPreviewIcon == null && next == null)
            || (focusedAppPreviewIcon != null && next != null && focusedAppPreviewIcon.getConstantState() == next.getConstantState());
        float target = next == null ? 0f : 1f;
        if (same && Math.abs(focusedAppPreviewProgress - target) < 0.01f) {
            return;
        }
        if (next != null) {
            focusedAppPreviewIcon = next;
            focusedAppPreviewLaunchDismissing = false;
        }
        animateFocusedAppPreviewTo(target, false);
        invalidate();
    }

    public void setFocusedAppPreviewLabel(@Nullable String label) {
        String next = label == null ? null : label.trim();
        if (next != null && next.isEmpty()) {
            next = null;
        }
        if (TextUtils.equals(focusedAppPreviewLabel, next)) {
            return;
        }
        focusedAppPreviewLabel = next;
        invalidate();
    }

    public void setFocusedAppPreviewLabelEnabled(boolean enabled) {
        if (focusedAppPreviewLabelEnabled == enabled) {
            return;
        }
        focusedAppPreviewLabelEnabled = enabled;
        invalidate();
    }

    public void setFocusedIconRingEnabled(boolean enabled) {
        if (focusedIconRingEnabled == enabled) {
            return;
        }
        focusedIconRingEnabled = enabled;
        invalidate();
    }

    public void setFocusedIconOutline(@Nullable FocusOutlineRenderer.Visual visual,
                                      @Nullable RectF rawBounds) {
        boolean valid = visual != null && rawBounds != null && !rawBounds.isEmpty();
        if (valid && focusedIconOutlineVisual == visual) {
            focusedIconOutlineRawBounds.set(rawBounds);
            invalidate();
            return;
        }
        if (!valid && focusedIconOutlineVisual == null) return;

        boolean reviveOutgoing = valid && outgoingIconOutlineVisual == visual;
        float revivedAlpha = outgoingIconOutlineAlpha;
        float revivedScale = outgoingIconOutlineScale;
        if (focusedIconOutlineAnimator != null) {
            ValueAnimator old = focusedIconOutlineAnimator;
            focusedIconOutlineAnimator = null;
            old.cancel();
        }
        outgoingIconOutlineVisual = focusedIconOutlineVisual;
        outgoingIconOutlineRawBounds.set(focusedIconOutlineRawBounds);
        outgoingIconOutlineAlpha = focusedIconOutlineAlpha;
        outgoingIconOutlineScale = focusedIconOutlineScale;

        focusedIconOutlineVisual = valid ? visual : null;
        if (valid) {
            focusedIconOutlineRawBounds.set(rawBounds);
            focusedIconOutlineAlpha = reviveOutgoing ? revivedAlpha : 0f;
            focusedIconOutlineScale = reviveOutgoing ? revivedScale : 1.04f;
        } else {
            focusedIconOutlineRawBounds.setEmpty();
            focusedIconOutlineAlpha = 0f;
            focusedIconOutlineScale = 1f;
        }

        if (!FocusOutlineRenderer.animationsEnabled(getContext())) {
            focusedIconOutlineAlpha = valid ? 1f : 0f;
            focusedIconOutlineScale = 1f;
            outgoingIconOutlineVisual = null;
            outgoingIconOutlineRawBounds.setEmpty();
            outgoingIconOutlineAlpha = 0f;
            invalidate();
            return;
        }

        final float incomingStartAlpha = focusedIconOutlineAlpha;
        final float incomingStartScale = focusedIconOutlineScale;
        final float outgoingStartAlpha = outgoingIconOutlineAlpha;
        final float outgoingStartScale = outgoingIconOutlineScale;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        focusedIconOutlineAnimator = animator;
        animator.setDuration(160L);
        animator.setInterpolator(new DecelerateInterpolator(1.45f));
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            if (focusedIconOutlineVisual != null) {
                focusedIconOutlineAlpha = lerp(incomingStartAlpha, 1f, progress);
                focusedIconOutlineScale = FocusOutlineRenderer.incomingScale(
                    incomingStartScale, progress);
            }
            if (outgoingIconOutlineVisual != null) {
                outgoingIconOutlineAlpha = lerp(outgoingStartAlpha, 0f, progress);
                outgoingIconOutlineScale = lerp(outgoingStartScale, 0.96f, progress);
            }
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (focusedIconOutlineAnimator != animation) return;
                focusedIconOutlineAnimator = null;
                focusedIconOutlineAlpha = focusedIconOutlineVisual == null ? 0f : 1f;
                focusedIconOutlineScale = 1f;
                outgoingIconOutlineVisual = null;
                outgoingIconOutlineRawBounds.setEmpty();
                outgoingIconOutlineAlpha = 0f;
                invalidate();
            }
        });
        animator.start();
    }

    /**
     * The standalone index's matches, as a strip floating above the letters.
     *
     * <p>{@code icons} are the drawables the row would have drawn — straight from the budgeted
     * {@code LauncherIconStore}, referenced rather than copied, and dropped again the moment the
     * strip is cleared, so the strip costs no pixels of its own.
     *
     * @param strip the band's geometry in raw screen coordinates, or null to fade the strip out
     */
    public void setFloatingStrip(@Nullable AzFloatingStripPolicy.Strip strip,
                                 @Nullable List<Drawable> icons) {
        setFloatingStrip(strip, icons, null);
    }

    /**
     * Same as {@link #setFloatingStrip(AzFloatingStripPolicy.Strip, List)}, plus the per-slot
     * contour visual the focused slot should wear — the same icon-shaped ring the apps row uses,
     * instead of a rounded rectangle. Resolving these is the caller's job (it holds the drawable
     * cache); this view only ever indexes into the list it is handed.
     *
     * @param visuals parallel to {@code icons}, or null/short to fall back to the rounded rect
     */
    public void setFloatingStrip(@Nullable AzFloatingStripPolicy.Strip strip,
                                 @Nullable List<Drawable> icons,
                                 @Nullable List<FocusOutlineRenderer.Visual> visuals) {
        boolean show = strip != null && !strip.isEmpty() && icons != null && !icons.isEmpty();
        floatingStrip = show ? strip : null;
        floatingStripIcons.clear();
        floatingStripVisuals.clear();
        if (show) {
            floatingStripIcons.addAll(icons);
            if (visuals != null) {
                floatingStripVisuals.addAll(visuals);
            }
        } else {
            floatingStripFocusedSlot = -1;
        }
        animateFloatingStripTo(show ? 1f : 0f);
        syncBreathing();
        invalidate();
    }

    /** Which slot the finger is on, or -1 for none. */
    public void setFloatingStripFocusedSlot(int slot) {
        int bounded = slot >= 0 && slot < floatingStripIcons.size() ? slot : -1;
        if (floatingStripFocusedSlot == bounded) {
            return;
        }
        floatingStripFocusedSlot = bounded;
        syncBreathing();
        invalidate();
    }

    public void setDarkThemeActive(boolean active) {
        if (darkThemeActive == active) {
            return;
        }
        darkThemeActive = active;
        invalidate();
    }

    public void clearDrag() {
        setFocusedIconOutline(null, null);
        dragActive = false;
        hasFocus = false;
        edgeDwellProgress = 0f;
        setFloatingStrip(null, null);
        if (!focusedAppPreviewLaunchDismissing) {
            setFocusedAppPreviewIcon(null);
        }
        hasPreviewPosition = false;
        interactionMode = InteractionMode.LETTER_TRACK;
        refreshVisibility();
        invalidate();
    }

    public void playFocusedAppPreviewSettle() {
        if (focusedAppPreviewIcon == null || focusedAppPreviewProgress <= 0.01f) {
            return;
        }
        if (focusedAppPreviewSettleAnimator != null) {
            focusedAppPreviewSettleAnimator.cancel();
        }
        focusedAppPreviewSettleProgress = 1f;
        focusedAppPreviewSettleAnimator = ValueAnimator.ofFloat(1f, 0f);
        focusedAppPreviewSettleAnimator.setDuration(170L);
        focusedAppPreviewSettleAnimator.setInterpolator(new DecelerateInterpolator(1.7f));
        focusedAppPreviewSettleAnimator.addUpdateListener(animation -> {
            focusedAppPreviewSettleProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        focusedAppPreviewSettleAnimator.start();
    }

    private void refreshVisibility() {
        boolean shouldDrawFocusRing = focusedIconRingEnabled
            && dragActive
            && interactionMode == InteractionMode.ICON_TRACK_LOCKED
            && hasFocus
            && !focusRawRect.isEmpty();
        setVisibility(edgeDwellProgress > 0.01f
            || focusedAppPreviewProgress > 0.01f || shouldDrawFocusRing
            || floatingStrip != null || floatingStripProgress > 0.01f ? VISIBLE : GONE);
    }

    public void dismissFocusedAppPreviewForLaunch() {
        focusedAppPreviewLaunchDismissing = true;
        animateFocusedAppPreviewTo(0f, true);
    }

    /**
     * The breath is the one thing here that repaints per frame, so it must not be able to outlive
     * the window it is drawing in — a gesture cut short by the launcher going away never releases.
     */
    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            stopBreathing();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (focusedAppPreviewAnimator != null) {
            focusedAppPreviewAnimator.cancel();
            focusedAppPreviewAnimator = null;
        }
        if (focusedAppPreviewSettleAnimator != null) {
            focusedAppPreviewSettleAnimator.cancel();
            focusedAppPreviewSettleAnimator = null;
        }
        if (focusedIconOutlineAnimator != null) {
            focusedIconOutlineAnimator.cancel();
            focusedIconOutlineAnimator = null;
        }
        if (floatingStripAnimator != null) {
            floatingStripAnimator.cancel();
            floatingStripAnimator = null;
        }
        stopBreathing();
        floatingStripIcons.clear();
        floatingStripVisuals.clear();
        floatingStrip = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        getLocationOnScreen(locationOnScreen);

        boolean drawFocusRing = focusedIconRingEnabled
            && dragActive
            && interactionMode == InteractionMode.ICON_TRACK_LOCKED
            && hasFocus
            && !focusRawRect.isEmpty();
        boolean drawStrip = floatingStripProgress > 0.01f && floatingStrip != null
            && renderLayer == RenderLayer.OVERLAY;
        if (edgeDwellProgress <= 0.01f && focusedAppPreviewProgress <= 0.01f
            && !drawFocusRing && !drawStrip) {
            return;
        }
        if (drawStrip) {
            drawFloatingStrip(canvas);
        }
        if (edgeDwellProgress > 0.01f && renderLayer == RenderLayer.OVERLAY) {
            drawEdgeDwellBloom(canvas);
        }
        if (drawFocusRing && renderLayer == RenderLayer.OVERLAY) {
            drawFocusedIconRing(canvas);
        }
        if (focusedAppPreviewProgress > 0.01f && renderLayer == RenderLayer.OVERLAY) {
            drawFocusedAppPreviewIcon(canvas);
        }
    }

    /** Accent outline around the visible artwork, not the icon button's larger touch target. */
    private void drawFocusedIconRing(Canvas canvas) {
        int accent = FocusOutlineRenderer.resolveAccent(this);
        // The ring the finger is holding breathes; the one it just left keeps its own fade out.
        float breathScale = AzFloatingStripPolicy.breathScale(breathPhase);
        float breathAlpha = AzFloatingStripPolicy.breathAlpha(breathPhase);
        if (outgoingIconOutlineVisual != null && !outgoingIconOutlineRawBounds.isEmpty()) {
            focusDisplayRect.set(outgoingIconOutlineRawBounds);
            focusDisplayRect.offset(-locationOnScreen[0], -locationOnScreen[1]);
            FocusOutlineRenderer.draw(canvas, outgoingIconOutlineVisual, focusDisplayRect, accent,
                outgoingIconOutlineAlpha, outgoingIconOutlineScale, focusedIconOutlinePaints);
        }
        if (focusedIconOutlineVisual != null && !focusedIconOutlineRawBounds.isEmpty()) {
            focusDisplayRect.set(focusedIconOutlineRawBounds);
            focusDisplayRect.offset(-locationOnScreen[0], -locationOnScreen[1]);
            FocusOutlineRenderer.draw(canvas, focusedIconOutlineVisual, focusDisplayRect, accent,
                focusedIconOutlineAlpha * breathAlpha, focusedIconOutlineScale * breathScale,
                focusedIconOutlinePaints);
        } else if (outgoingIconOutlineVisual == null && hasFocus && !focusRawRect.isEmpty()) {
            // No artwork mask available (folder previews, views not yet measured): keep the ring
            // present with the sibling rounded-rect treatment instead of showing nothing.
            focusDisplayRect.set(focusRawRect);
            focusDisplayRect.offset(-locationOnScreen[0], -locationOnScreen[1]);
            float density = getResources().getDisplayMetrics().density;
            FocusOutlineRenderer.drawRoundRectFallback(canvas, focusDisplayRect,
                Math.min(focusDisplayRect.width(), focusDisplayRect.height()) * 0.28f, accent,
                (focusedIconOutlineAlpha > 0f ? focusedIconOutlineAlpha : 1f) * breathAlpha,
                (focusedIconOutlineScale > 0f ? focusedIconOutlineScale : 1f) * breathScale,
                density);
        }
    }

    /**
     * The standalone index's matches: one glass plank carrying the page's icons, with the focused
     * one lifted slightly and wearing the breathing ring. Every position comes from the strip the
     * policy laid out, so what is drawn and what the gesture hit-tests cannot drift apart.
     */
    private void drawFloatingStrip(Canvas canvas) {
        AzFloatingStripPolicy.Strip strip = floatingStrip;
        if (strip == null || floatingStripIcons.isEmpty()) {
            return;
        }
        float progress = clamp01(floatingStripProgress);
        float alpha = progress;
        float offsetX = -locationOnScreen[0];
        float offsetY = -locationOnScreen[1];
        float iconSize = strip.iconSizePx;
        float plankPadding = dp(9f);
        float plankRadius = (iconSize * 0.5f) + plankPadding;
        // The band is already a screen rectangle — one row of icons, whichever edge it grew out
        // of — so the only thing the edge still decides here is which way it rises into place.
        tmpRect.set(strip.left - plankPadding + offsetX, strip.top - plankPadding + offsetY,
            strip.right + plankPadding + offsetX, strip.bottom + plankPadding + offsetY);

        int save = canvas.save();
        // Rises the last few pixels into place, like the preview bubble beside it — away from the
        // bar, which is up off a bottom one and sideways off a column.
        float rise = lerp(dp(7f), 0f, progress);
        canvas.translate(rise * barFrame.awayDirectionX(), rise * barFrame.awayDirectionY());
        // Scaled about the face nearest the bar, so the band grows out of the letters.
        canvas.scale(lerp(0.94f, 1f, progress), lerp(0.94f, 1f, progress),
            tmpRect.centerX() + (barFrame.awayDirectionX() * tmpRect.width() * 0.5f),
            tmpRect.centerY() + (barFrame.awayDirectionY() * tmpRect.height() * 0.5f));

        previewFillPaint.setColor(withAlpha(Color.BLACK,
            Math.round((darkThemeActive ? 66f : 30f) * alpha)));
        tmpShadowRect.set(tmpRect);
        tmpShadowRect.offset(0f, dp(2.5f));
        canvas.drawRoundRect(tmpShadowRect, plankRadius, plankRadius, previewFillPaint);

        int plankFill = darkThemeActive
            ? lerpColor(Color.rgb(34, 30, 39), edgeTintColor, 0.06f)
            : lerpColor(Color.rgb(238, 234, 242), edgeTintColor, 0.05f);
        previewFillPaint.setColor(withAlpha(plankFill,
            Math.round((darkThemeActive ? 226f : 238f) * alpha)));
        canvas.drawRoundRect(tmpRect, plankRadius, plankRadius, previewFillPaint);

        int accent = FocusOutlineRenderer.resolveAccent(this);
        float density = getResources().getDisplayMetrics().density;
        float breathScale = AzFloatingStripPolicy.breathScale(breathPhase);
        float breathAlpha = AzFloatingStripPolicy.breathAlpha(breathPhase);
        for (int slot = 0; slot < floatingStripIcons.size() && slot < strip.slotCount; slot++) {
            Drawable icon = floatingStripIcons.get(slot);
            if (icon == null) {
                continue;
            }
            boolean focused = slot == floatingStripFocusedSlot;
            float cx = strip.slotCenterX(slot) + offsetX;
            float cy = strip.centerY() + offsetY;
            float drawnSize = focused ? iconSize * 1.06f : iconSize;
            if (focused) {
                previewRect.set(cx - (iconSize * 0.5f), cy - (iconSize * 0.5f),
                    cx + (iconSize * 0.5f), cy + (iconSize * 0.5f));
                FocusOutlineRenderer.Visual visual = slot < floatingStripVisuals.size()
                    ? floatingStripVisuals.get(slot) : null;
                if (visual != null) {
                    // Same expansion OutlineDrawable.draw uses: the rasterised visual is the
                    // slot's own icon size, so this scale is ~1 and the padding is the halo width.
                    float scale = iconSize / (float) visual.sourceWidth;
                    float pad = visual.outerPadding * scale;
                    floatingStripOutlineRect.set(previewRect.left - pad, previewRect.top - pad,
                        previewRect.right + pad, previewRect.bottom + pad);
                    FocusOutlineRenderer.draw(canvas, visual, floatingStripOutlineRect, accent,
                        alpha * breathAlpha, breathScale, floatingStripOutlinePaints);
                } else {
                    FocusOutlineRenderer.drawRoundRectFallback(canvas, previewRect,
                        iconSize * 0.28f, accent, alpha * breathAlpha, breathScale, density);
                }
            }
            icon.setAlpha(Math.round(255f * alpha * (focused ? 1f : 0.9f)));
            icon.setBounds(
                Math.round(cx - (drawnSize * 0.5f)),
                Math.round(cy - (drawnSize * 0.5f)),
                Math.round(cx + (drawnSize * 0.5f)),
                Math.round(cy + (drawnSize * 0.5f))
            );
            icon.draw(canvas);
            icon.setAlpha(255);
        }
        canvas.restoreToCount(save);
    }

    private void animateFloatingStripTo(float target) {
        float bounded = clamp01(target);
        if (floatingStripAnimator != null) {
            floatingStripAnimator.cancel();
            floatingStripAnimator = null;
        }
        float start = floatingStripProgress;
        if (Math.abs(start - bounded) < 0.01f) {
            floatingStripProgress = bounded;
            refreshVisibility();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(start, bounded);
        floatingStripAnimator = animator;
        animator.setDuration(bounded > start ? 128L : 96L);
        animator.setInterpolator(new DecelerateInterpolator(1.55f));
        animator.addUpdateListener(animation -> {
            floatingStripProgress = (float) animation.getAnimatedValue();
            refreshVisibility();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (floatingStripAnimator != animation) return;
                floatingStripAnimator = null;
                floatingStripProgress = bounded;
                if (bounded <= 0.01f) {
                    floatingStripIcons.clear();
                    floatingStripVisuals.clear();
                    floatingStrip = null;
                }
                refreshVisibility();
                invalidate();
            }
        });
        animator.start();
    }

    /**
     * Starts or stops the focused icon's breath. It runs only while a finger is down on something
     * focused, which is the whole reason it is allowed to repaint per frame; nothing here can be
     * left ticking at rest.
     */
    private void syncBreathing() {
        boolean stripFocus = floatingStrip != null && floatingStripFocusedSlot >= 0;
        boolean rowFocus = focusedIconRingEnabled
            && interactionMode == InteractionMode.ICON_TRACK_LOCKED
            && hasFocus && !focusRawRect.isEmpty();
        boolean wanted = dragActive && (stripFocus || rowFocus)
            && FocusOutlineRenderer.animationsEnabled(getContext());
        if (wanted == (breathAnimator != null)) {
            return;
        }
        if (!wanted) {
            stopBreathing();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        breathAnimator = animator;
        animator.setDuration(AzFloatingStripPolicy.BREATH_PERIOD_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            breathPhase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopBreathing() {
        if (breathAnimator != null) {
            breathAnimator.cancel();
            breathAnimator = null;
        }
        breathPhase = 0f;
        invalidate();
    }

    /**
     * Which side of the band the focused app's name reads on: the far side from the bar, and within
     * that the orientation's own answer. The policy owns it, so the view only has to say which edge
     * the bar is on and which way up the screen is.
     */
    @NonNull
    private AzFloatingStripPolicy.LabelSide labelSide() {
        return AzFloatingStripPolicy.labelSideFor(barFrame.edge(),
            getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    private void drawFocusedAppPreviewIcon(Canvas canvas) {
        if (focusedAppPreviewIcon == null || appsRowRawBounds.isEmpty()) {
            return;
        }
        float progress = clamp01(focusedAppPreviewProgress);
        // The band the name belongs to is a row of icons on every edge, so the bubble sits over it
        // (or under it off a top bar) and centres on the icon it is naming. It used to stack up the
        // screen off a column, which drew it over the very matches it was labelling.
        float rowTop = appsRowRawBounds.top - locationOnScreen[1];
        float rowBottom = appsRowRawBounds.bottom - locationOnScreen[1];
        float rowLeft = appsRowRawBounds.left - locationOnScreen[0];
        float rowRight = appsRowRawBounds.right - locationOnScreen[0];
        float focusCx = hasPreviewPosition
            ? previewDisplayRawX - locationOnScreen[0]
            : (hasFocus && !focusRawRect.isEmpty()
                ? focusRawRect.centerX() - locationOnScreen[0]
                : targetRawX - locationOnScreen[0]);
        focusCx = clamp(focusCx, rowLeft + dp(8f), rowRight - dp(8f));

        float sourceIconSize = hasFocus && !focusRawRect.isEmpty()
            ? Math.max(focusRawRect.width(), focusRawRect.height())
            : dp(44f);
        boolean drawLabel = focusedAppPreviewLabelEnabled
            && focusedAppPreviewLabel != null
            && !focusedAppPreviewLabel.isEmpty();
        float bubbleScale = drawLabel ? 1.02f : 1.18f;
        float iconScale = drawLabel ? 0.72f : 0.82f;
        float bubbleSize = clamp(sourceIconSize * bubbleScale, dp(40f), dp(58f));
        float iconSize = clamp(sourceIconSize * iconScale, dp(28f), bubbleSize - dp(12f));
        float left = clamp(focusCx - (bubbleSize * 0.5f), dp(8f), Math.max(dp(8f), getWidth() - bubbleSize - dp(8f)));
        float verticalGap = clamp(sourceIconSize * 0.22f, dp(8f), dp(14f));
        // The label's own band has to be measured before the bubble is placed: below the icon it
        // sits between the bubble and the row, so the bubble rises by exactly that much.
        AzFloatingStripPolicy.LabelSide labelSide = labelSide();
        StaticLayout labelLayout = drawLabel
            ? buildFocusedAppPreviewLabelLayout(focusedAppPreviewLabel, sourceIconSize) : null;
        float labelPillHeight = labelLayout == null
            ? 0f : labelLayout.getHeight() + (PREVIEW_LABEL_VERTICAL_PADDING_DP * getResources().getDisplayMetrics().density * 2f);
        float labelReserveAbove = labelLayout != null ? labelPillHeight + dp(5f) : 0f;
        float labelReserve = labelSide == AzFloatingStripPolicy.LabelSide.BELOW
            ? labelReserveAbove : 0f;
        // The name reads on the far side of the band from the bar, so it never lands in the gap
        // between the letters and the icons they matched: above the band for a bottom bar, below it
        // for a top one, and beside the focused icon down a column.
        float aboveTop = rowTop - bubbleSize - verticalGap - labelReserve;
        float belowTop = rowBottom + verticalGap
            + (labelSide == AzFloatingStripPolicy.LabelSide.ABOVE ? labelReserveAbove : 0f);
        boolean nameBelowBand = previewTrackOutward != (barFrame.edge() == PlaceLayout.Edge.TOP);
        float top = nameBelowBand ? belowTop : aboveTop;
        // Never off the top, and — because a band level with a thumb near either end of a column
        // can be close to both — never off the bottom either; the label under it has to stay on
        // screen too.
        if (!nameBelowBand && top < dp(8f)) {
            top = belowTop;
        }
        if (top < dp(8f)) {
            top = dp(8f);
        }
        float labelBelow = labelSide == AzFloatingStripPolicy.LabelSide.BELOW
            ? labelReserveAbove : 0f;
        top = Math.min(top, Math.max(dp(8f), getHeight() - bubbleSize - labelBelow - dp(8f)));
        top += focusedAppPreviewLaunchDismissing
            ? lerp(dp(-8f), 0f, progress)
            : lerp(dp(6f), 0f, progress);
        float settleScale = 1f + (0.035f * focusedAppPreviewSettleProgress);
        float scale = focusedAppPreviewLaunchDismissing
            ? lerp(0.92f, 1f, progress)
            : lerp(0.88f, 1f, progress) * settleScale;
        float alpha = lerp(0f, 1f, progress);
        float cx = left + (bubbleSize * 0.5f);
        float cy = top + (bubbleSize * 0.5f);
        float iconLeft = cx - (iconSize * 0.5f);
        float iconTop = cy - (iconSize * 0.5f);
        float radius = bubbleSize * 0.5f;
        previewRect.set(left, top, left + bubbleSize, top + bubbleSize);

        int save = canvas.save();
        canvas.scale(scale, scale, cx, cy);
        int shadowAlpha = Math.round((darkThemeActive ? 64f : 28f) * alpha);
        previewFillPaint.setColor(withAlpha(Color.BLACK, shadowAlpha));
        tmpRect.set(previewRect);
        tmpRect.offset(0f, dp(2f));
        canvas.drawRoundRect(tmpRect, radius, radius, previewFillPaint);

        int baseFill = darkThemeActive
            ? lerpColor(Color.rgb(34, 30, 39), edgeTintColor, 0.06f)
            : lerpColor(Color.rgb(238, 234, 242), edgeTintColor, 0.05f);
        previewFillPaint.setColor(withAlpha(baseFill, Math.round((darkThemeActive ? 222f : 236f) * alpha)));
        canvas.drawRoundRect(previewRect, radius, radius, previewFillPaint);

        if (labelLayout != null) {
            drawFocusedAppPreviewLabel(canvas, labelLayout, cx, top, bubbleSize, labelPillHeight,
                labelSide, alpha);
        }

        focusedAppPreviewIcon.setAlpha(Math.round(255f * alpha));
        focusedAppPreviewIcon.setBounds(
            Math.round(iconLeft),
            Math.round(iconTop),
            Math.round(iconLeft + iconSize),
            Math.round(iconTop + iconSize)
        );
        focusedAppPreviewIcon.draw(canvas);
        focusedAppPreviewIcon.setAlpha(255);
        canvas.restoreToCount(save);
    }

    /** The label's inner padding above and below its text, in dp; the pill height rides on it. */
    private static final float PREVIEW_LABEL_VERTICAL_PADDING_DP = 4f;

    /**
     * Measures and caches the focused app's label. Split out from the drawing because the pill's
     * height decides where the bubble goes when the label reads below it.
     */
    @Nullable
    private StaticLayout buildFocusedAppPreviewLabelLayout(@Nullable String label,
                                                           float sourceIconSize) {
        if (label == null || label.isEmpty()) {
            return null;
        }
        String displayLabel = addPreviewLabelBreakOpportunities(label);
        previewLabelPaint.setTextSize(clamp(sourceIconSize * 0.22f, dp(9.5f), dp(11.5f)));
        int maxInnerWidth = Math.round(clamp(getWidth() * 0.30f, dp(78f), dp(124f)));
        int minInnerWidth = Math.round(dp(38f));
        float measuredTextWidth = previewLabelPaint.measureText(displayLabel);
        int textWidth = Math.max(minInnerWidth, Math.min(maxInnerWidth, Math.round(measuredTextWidth + dp(1f))));
        StaticLayout layout = focusedAppPreviewLabelLayout;
        if (layout == null
            || !TextUtils.equals(focusedAppPreviewLabelLayoutText, displayLabel)
            || focusedAppPreviewLabelLayoutWidth != textWidth
            || focusedAppPreviewLabelLayoutTextSize != previewLabelPaint.getTextSize()) {
            layout = StaticLayout.Builder.obtain(
                    displayLabel, 0, displayLabel.length(), previewLabelPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
            focusedAppPreviewLabelLayout = layout;
            focusedAppPreviewLabelLayoutText = displayLabel;
            focusedAppPreviewLabelLayoutWidth = textWidth;
            focusedAppPreviewLabelLayoutTextSize = previewLabelPaint.getTextSize();
        }
        return layout;
    }

    private void drawFocusedAppPreviewLabel(
        @NonNull Canvas canvas,
        @NonNull StaticLayout layout,
        float centerX,
        float bubbleTop,
        float bubbleSize,
        float pillHeight,
        @NonNull AzFloatingStripPolicy.LabelSide labelSide,
        float alpha
    ) {
        previewLabelPaint.setColor(darkThemeActive
            ? withAlpha(Color.rgb(230, 224, 233), Math.round(245f * alpha))
            : withAlpha(Color.rgb(29, 27, 32), Math.round(235f * alpha)));

        float horizontalPadding = dp(7f);
        int maxInnerWidth = Math.round(clamp(getWidth() * 0.30f, dp(78f), dp(124f)));
        float pillWidth = Math.min(maxInnerWidth + (horizontalPadding * 2f), Math.max(dp(52f), layout.getWidth() + (horizontalPadding * 2f)));
        float pillLeft = clamp(centerX - (pillWidth * 0.5f), dp(8f), Math.max(dp(8f), getWidth() - pillWidth - dp(8f)));
        float pillTop = labelSide == AzFloatingStripPolicy.LabelSide.BELOW
            ? bubbleTop + bubbleSize + dp(5f)
            : bubbleTop - pillHeight - dp(5f);
        // The name stays on screen at either end of the band, the way the bubble itself does.
        pillTop = Math.min(Math.max(pillTop, dp(8f)),
            Math.max(dp(8f), getHeight() - pillHeight - dp(8f)));

        tmpRect.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight);
        previewFillPaint.setColor(withAlpha(Color.BLACK, Math.round((darkThemeActive ? 58f : 18f) * alpha)));
        tmpShadowRect.set(tmpRect);
        tmpShadowRect.offset(0f, dp(1.5f));
        canvas.drawRoundRect(tmpShadowRect, pillHeight * 0.5f, pillHeight * 0.5f, previewFillPaint);

        int pillColor = darkThemeActive
            ? lerpColor(Color.rgb(34, 30, 39), edgeTintColor, 0.04f)
            : lerpColor(Color.rgb(238, 234, 242), edgeTintColor, 0.035f);
        previewFillPaint.setColor(withAlpha(pillColor, Math.round((darkThemeActive ? 188f : 204f) * alpha)));
        canvas.drawRoundRect(tmpRect, pillHeight * 0.5f, pillHeight * 0.5f, previewFillPaint);

        int save = canvas.save();
        float textLeft = tmpRect.left + ((tmpRect.width() - layout.getWidth()) * 0.5f);
        float textTop = tmpRect.top + ((pillHeight - layout.getHeight()) * 0.5f);
        canvas.translate(textLeft, textTop);
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }

    @NonNull
    private static String addPreviewLabelBreakOpportunities(@NonNull String label) {
        StringBuilder out = new StringBuilder(label.length() + 4);
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            out.append(c);
            if ((c == ':' || c == '/' || c == '-' || c == '_' || c == '.') && i < label.length() - 1) {
                out.append('\u200B');
            }
        }
        return out.toString();
    }

    private void drawEdgeDwellBloom(Canvas canvas) {
        float progress = clamp01(edgeDwellProgress);
        float cx = edgeDwellRawX - locationOnScreen[0];
        float cy = edgeDwellRawY - locationOnScreen[1];
        if (!appsRowRawBounds.isEmpty()) {
            float top = appsRowRawBounds.top - locationOnScreen[1];
            float bottom = appsRowRawBounds.bottom - locationOnScreen[1];
            cy = clamp(cy, top + dp(8f), bottom - dp(8f));
        }
        float radius = lerp(dp(18f), dp(38f), progress);
        int mutedTint = darkThemeActive
            ? lerpColor(Color.rgb(34, 30, 39), edgeTintColor, 0.20f)
            : lerpColor(Color.rgb(238, 234, 242), edgeTintColor, 0.16f);
        int outerAlpha = Math.round(lerp(24f, 62f, progress));
        int innerAlpha = Math.round(lerp(58f, 132f, progress));
        int coreColor = withAlpha(mutedTint, innerAlpha);
        int outerColor = withAlpha(mutedTint, outerAlpha);
        int endColor = withAlpha(edgeTintColor, 0);
        if (edgeDwellShader == null
            || edgeDwellShaderX != cx
            || edgeDwellShaderY != cy
            || edgeDwellShaderRadius != radius
            || edgeDwellShaderCoreColor != coreColor
            || edgeDwellShaderOuterColor != outerColor
            || edgeDwellShaderEndColor != endColor) {
            edgeDwellGradientColors[0] = coreColor;
            edgeDwellGradientColors[1] = outerColor;
            edgeDwellGradientColors[2] = endColor;
            edgeDwellShader = new RadialGradient(cx, cy, radius, edgeDwellGradientColors,
                edgeDwellGradientStops, Shader.TileMode.CLAMP);
            edgeDwellShaderX = cx;
            edgeDwellShaderY = cy;
            edgeDwellShaderRadius = radius;
            edgeDwellShaderCoreColor = coreColor;
            edgeDwellShaderOuterColor = outerColor;
            edgeDwellShaderEndColor = endColor;
        }
        edgeDwellPaint.setShader(edgeDwellShader);
        canvas.drawCircle(cx, cy, radius, edgeDwellPaint);
        edgeDwellPaint.setShader(null);

        float ringRadius = radius * lerp(0.42f, 0.78f, progress);
        glassStrokePaint.setStrokeWidth(dp(1.15f));
        glassStrokePaint.setColor(withAlpha(
            darkThemeActive ? Color.rgb(226, 222, 232) : Color.rgb(70, 64, 78),
            Math.round(lerp(24f, 72f, progress))
        ));
        canvas.drawCircle(cx, cy, ringRadius, glassStrokePaint);
    }

    private void animateFocusedAppPreviewTo(float target, boolean launchDismiss) {
        float boundedTarget = clamp01(target);
        if (focusedAppPreviewAnimator != null) {
            focusedAppPreviewAnimator.cancel();
            focusedAppPreviewAnimator = null;
        }
        float start = focusedAppPreviewProgress;
        if (Math.abs(start - boundedTarget) < 0.01f) {
            focusedAppPreviewProgress = boundedTarget;
            if (boundedTarget <= 0.01f) {
                focusedAppPreviewIcon = null;
                focusedAppPreviewLaunchDismissing = false;
                focusedAppPreviewSettleProgress = 0f;
            }
            refreshVisibility();
            invalidate();
            return;
        }
        focusedAppPreviewAnimator = ValueAnimator.ofFloat(start, boundedTarget);
        focusedAppPreviewAnimator.setDuration(launchDismiss ? 112L : (boundedTarget > start ? 96L : 84L));
        focusedAppPreviewAnimator.setInterpolator(new DecelerateInterpolator(launchDismiss ? 1.75f : 1.55f));
        focusedAppPreviewAnimator.addUpdateListener(animation -> {
            focusedAppPreviewProgress = (Float) animation.getAnimatedValue();
            refreshVisibility();
            invalidate();
        });
        focusedAppPreviewAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (focusedAppPreviewAnimator != animation) {
                    return;
                }
                focusedAppPreviewAnimator = null;
                focusedAppPreviewProgress = boundedTarget;
                if (boundedTarget <= 0.01f) {
                    focusedAppPreviewIcon = null;
                    focusedAppPreviewLaunchDismissing = false;
                    focusedAppPreviewSettleProgress = 0f;
                }
                refreshVisibility();
                invalidate();
            }
        });
        focusedAppPreviewAnimator.start();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float lerp(float start, float end, float t) {
        return start + ((end - start) * t);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int lerpColor(int start, int end, float t) {
        float clamped = clamp01(t);
        int a = Math.round(lerp(Color.alpha(start), Color.alpha(end), clamped));
        int r = Math.round(lerp(Color.red(start), Color.red(end), clamped));
        int g = Math.round(lerp(Color.green(start), Color.green(end), clamped));
        int b = Math.round(lerp(Color.blue(start), Color.blue(end), clamped));
        return Color.argb(a, r, g, b);
    }

    private static int enforceGlassVisibility(int color, float minValue) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0.42f, hsv[1]);
        hsv[2] = Math.max(minValue, hsv[2]);
        return Color.HSVToColor((color >>> 24) == 0 ? 0xE8 : (color >>> 24), hsv);
    }

    private static int boostColor(int color, float satMul, float valMul) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = clamp(hsv[1] * satMul, 0f, 1f);
        hsv[2] = clamp(hsv[2] * valMul, 0f, 1f);
        return Color.HSVToColor((color >>> 24) == 0 ? 0xFF : (color >>> 24), hsv);
    }

}
