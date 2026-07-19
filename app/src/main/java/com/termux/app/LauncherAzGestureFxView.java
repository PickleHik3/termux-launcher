package com.termux.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Glassmorphic renderer for AZ and icon-row drag interactions.
 */
public final class LauncherAzGestureFxView extends View {

    private interface FloatUpdate {
        void accept(float value);
    }

    public enum InteractionMode {
        LETTER_TRACK,
        ICON_TRACK_LOCKED
    }

    public enum RenderLayer {
        UNDERLAY,
        OVERLAY
    }

    private final Paint glassFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bridgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pageIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Soft underglow blur for the active page tick; lazily built once the density is known. */
    private BlurMaskFilter pageTickGlow;
    private final Paint previewFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final FocusOutlineRenderer.RenderPaints focusedIconOutlinePaints =
        new FocusOutlineRenderer.RenderPaints();
    private final TextPaint previewLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path liquidBridgePath = new Path();

    private final RectF tmpRect = new RectF();
    private final RectF focusDisplayRect = new RectF();
    private final RectF focusRawRect = new RectF();
    private final RectF previewRect = new RectF();
    private final RectF azRowRawBounds = new RectF();
    private final RectF appsRowRawBounds = new RectF();
    private final RectF indicatorBandRawBounds = new RectF();
    private final RectF extraKeysRawBounds = new RectF();
    private final int[] locationOnScreen = new int[2];

    private int glassTintColor = 0xFF86A7FF;
    private int vividLetterTintColor = 0xFF9AB8FF;
    private int edgeTintColor = 0xFF7CE2FF;
    private int overflowGlowTintColor = 0xFF98F0FF;

    private boolean dragActive;
    private float targetRawX;
    private float targetRawY;
    private float displayRawX;
    private float displayRawY;

    private boolean hasAnchor;
    private float anchorRawX;
    private float anchorRawY;

    private boolean hasFocus;
    private boolean interactionOverflowActive;
    private boolean interactionCanPageLeft;
    private boolean interactionCanPageRight;
    private int interactionCurrentPageIndex;
    private int interactionPageCount = 1;
    private int interactionDynamicPageIndex = -1;
    private float interactionPageIndicatorPosition;
    @Nullable private ValueAnimator interactionPageIndicatorAnimator;
    private boolean interactionShowsPageIndicators;
    private boolean interactionUseSubtlePageIndicators;
    private float subtlePageIndicatorAttention = 1f;
    private boolean subtlePageIndicatorFadeScheduled;
    @Nullable private ValueAnimator subtlePageIndicatorAttentionAnimator;
    private final Runnable fadeSubtlePageIndicatorRunnable = new Runnable() {
        @Override
        public void run() {
            subtlePageIndicatorFadeScheduled = false;
            animateSubtlePageIndicatorAttentionTo(PINNED_INDICATOR_IDLE_ATTENTION);
        }
    };
    private float edgeProximityLeft;
    private float edgeProximityRight;
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
    private boolean darkThemeActive = true;
    private boolean capsuleDockStyle;
    @NonNull private RenderLayer renderLayer = RenderLayer.OVERLAY;

    @NonNull private InteractionMode interactionMode = InteractionMode.LETTER_TRACK;
    private long lastFocusUpdateUptimeMs = 0L;
    private static final long FOCUS_HOLD_MS = 140L;
    private static final long PINNED_INDICATOR_IDLE_DELAY_MS = 5000L;
    private static final long PINNED_INDICATOR_FADE_DURATION_MS = 520L;
    private static final float PINNED_INDICATOR_IDLE_ATTENTION = 0.42f;

    private boolean launchBloomActive;
    private float launchBloomRawX;
    private float launchBloomRawY;
    private float launchBloomProgress;
    @Nullable private ValueAnimator launchBloomAnimator;

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

        glassFillPaint.setStyle(Paint.Style.FILL);
        glassInnerPaint.setStyle(Paint.Style.FILL);
        glassStrokePaint.setStyle(Paint.Style.STROKE);
        glassStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        glassStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        bridgePaint.setStyle(Paint.Style.FILL);
        edgePaint.setStyle(Paint.Style.FILL);
        edgeInnerPaint.setStyle(Paint.Style.FILL);
        pageIndicatorPaint.setStyle(Paint.Style.FILL);
        previewFillPaint.setStyle(Paint.Style.FILL);
        previewLabelPaint.setTextAlign(Paint.Align.LEFT);
        previewLabelPaint.setSubpixelText(true);
        previewLabelPaint.setTextSize(dp(11f));
    }

    public void setColors(int glassTintColor, int edgeTintColor) {
        this.glassTintColor = enforceGlassVisibility(glassTintColor, 0.78f);
        this.vividLetterTintColor = boostColor(this.glassTintColor, 1.20f, 1.08f);
        this.edgeTintColor = enforceGlassVisibility(edgeTintColor, 0.84f);
        this.overflowGlowTintColor = boostColor(this.edgeTintColor, 1.05f, 1.18f);
        invalidate();
    }

    public void updateDrag(
        boolean active,
        float rawX,
        float rawY,
        boolean anchorVisible,
        float anchorRawX,
        float anchorRawY,
        @Nullable RectF focusedBoundsRaw,
        @NonNull InteractionMode mode
    ) {
        dragActive = active;
        targetRawX = rawX;
        targetRawY = rawY;
        hasAnchor = anchorVisible;
        this.anchorRawX = anchorRawX;
        this.anchorRawY = anchorRawY;
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
        refreshVisibility();
        invalidate();
    }

    public void setRowBounds(@Nullable RectF azRowRaw, @Nullable RectF appsRowRaw, @Nullable RectF indicatorBandRaw, @Nullable RectF extraKeysRowRaw) {
        if (azRowRaw != null) {
            azRowRawBounds.set(azRowRaw);
        } else {
            azRowRawBounds.setEmpty();
        }
        if (appsRowRaw != null) {
            appsRowRawBounds.set(appsRowRaw);
        } else {
            appsRowRawBounds.setEmpty();
        }
        if (indicatorBandRaw != null) {
            indicatorBandRawBounds.set(indicatorBandRaw);
        } else {
            indicatorBandRawBounds.setEmpty();
        }
        if (extraKeysRowRaw != null) {
            extraKeysRawBounds.set(extraKeysRowRaw);
        } else {
            extraKeysRawBounds.setEmpty();
        }
    }

    public void setInteractionOverflowState(
        boolean active,
        boolean pageLeft,
        boolean pageRight,
        float currentPagePosition,
        int pageCount,
        boolean showPageIndicators,
        boolean useSubtlePageIndicators,
        int dynamicPageIndex
    ) {
        interactionDynamicPageIndex = dynamicPageIndex;
        int newPageCount = Math.max(1, pageCount);
        float newPagePosition = clamp(currentPagePosition, 0f, newPageCount - 1f);
        int newPageIndex = Math.min(Math.max(0, Math.round(newPagePosition)), newPageCount - 1);
        boolean directPosition = Math.abs(newPagePosition - newPageIndex) > 0.001f;
        boolean pagePositionChanged = Math.abs(newPagePosition - interactionPageIndicatorPosition) > 0.001f
            || interactionPageCount != newPageCount
            || interactionUseSubtlePageIndicators != useSubtlePageIndicators
            || interactionShowsPageIndicators != showPageIndicators;
        boolean shouldAnimatePage = active
            && interactionOverflowActive
            && interactionPageCount == newPageCount
            && interactionCurrentPageIndex != newPageIndex;
        interactionOverflowActive = active;
        interactionCanPageLeft = pageLeft;
        interactionCanPageRight = pageRight;
        interactionCurrentPageIndex = newPageIndex;
        interactionPageCount = newPageCount;
        interactionShowsPageIndicators = showPageIndicators;
        interactionUseSubtlePageIndicators = useSubtlePageIndicators;
        if (!active || !showPageIndicators || !useSubtlePageIndicators) {
            cancelSubtlePageIndicatorIdleFade();
            subtlePageIndicatorAttention = 1f;
        } else if (directPosition || pagePositionChanged || shouldAnimatePage) {
            showSubtlePageIndicatorAttention();
        } else {
            scheduleSubtlePageIndicatorIdleFade();
        }
        if (directPosition) {
            setInteractionPageIndicatorPosition(newPagePosition);
        } else {
            animateInteractionPageIndicatorTo(newPageIndex, shouldAnimatePage);
        }
        refreshVisibility();
        invalidate();
    }

    public void setEdgeProximity(float left, float right) {
        edgeProximityLeft = clamp01(left);
        edgeProximityRight = clamp01(right);
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

    public void setDarkThemeActive(boolean active) {
        if (darkThemeActive == active) {
            return;
        }
        darkThemeActive = active;
        invalidate();
    }

    public void setCapsuleDockStyle(boolean capsule) {
        if (capsuleDockStyle == capsule) {
            return;
        }
        capsuleDockStyle = capsule;
        invalidate();
    }

    public void clearDrag(boolean keepOverflowAffordance) {
        setFocusedIconOutline(null, null);
        dragActive = false;
        hasFocus = false;
        hasAnchor = false;
        displayRawX = 0f;
        displayRawY = 0f;
        edgeProximityLeft = 0f;
        edgeProximityRight = 0f;
        edgeDwellProgress = 0f;
        if (!focusedAppPreviewLaunchDismissing) {
            setFocusedAppPreviewIcon(null);
        }
        hasPreviewPosition = false;
        interactionMode = InteractionMode.LETTER_TRACK;
        if (!keepOverflowAffordance) {
            interactionOverflowActive = false;
            interactionCanPageLeft = false;
            interactionCanPageRight = false;
            interactionCurrentPageIndex = 0;
            interactionPageCount = 1;
            interactionDynamicPageIndex = -1;
            interactionPageIndicatorPosition = 0f;
            cancelPageIndicatorAnimations();
            cancelSubtlePageIndicatorIdleFade();
            subtlePageIndicatorAttention = 1f;
            interactionShowsPageIndicators = false;
            interactionUseSubtlePageIndicators = false;
        }
        launchBloomActive = false;
        launchBloomProgress = 0f;
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
        boolean shouldDrawInteractionOverflow = interactionOverflowActive
            && (interactionCanPageLeft || interactionCanPageRight || interactionPageCount > 1);
        boolean shouldDrawFocusRing = focusedIconRingEnabled
            && dragActive
            && interactionMode == InteractionMode.ICON_TRACK_LOCKED
            && hasFocus
            && !focusRawRect.isEmpty();
        setVisibility(shouldDrawInteractionOverflow || edgeDwellProgress > 0.01f
            || focusedAppPreviewProgress > 0.01f || shouldDrawFocusRing ? VISIBLE : GONE);
    }

    public void playLaunchBloom(float rawX, float rawY) {
        launchBloomRawX = rawX;
        launchBloomRawY = rawY;
        focusedAppPreviewLaunchDismissing = true;
        animateFocusedAppPreviewTo(0f, true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (launchBloomAnimator != null) {
            launchBloomAnimator.cancel();
            launchBloomAnimator = null;
        }
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
        cancelPageIndicatorAnimations();
        cancelSubtlePageIndicatorIdleFade();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        getLocationOnScreen(locationOnScreen);

        boolean shouldDrawInteractionOverflow = interactionOverflowActive
            && (interactionCanPageLeft || interactionCanPageRight || interactionPageCount > 1);
        boolean drawFocusRing = focusedIconRingEnabled
            && dragActive
            && interactionMode == InteractionMode.ICON_TRACK_LOCKED
            && hasFocus
            && !focusRawRect.isEmpty();
        if (!shouldDrawInteractionOverflow && edgeDwellProgress <= 0.01f
            && focusedAppPreviewProgress <= 0.01f && !drawFocusRing) {
            return;
        }
        if (edgeDwellProgress > 0.01f && renderLayer == RenderLayer.OVERLAY) {
            drawEdgeDwellBloom(canvas);
        }
        if (shouldDrawInteractionOverflow) {
            if (renderLayer == RenderLayer.UNDERLAY && !interactionUseSubtlePageIndicators) {
                drawEdgeGlowAmbient(canvas);
            }
            if (renderLayer == RenderLayer.OVERLAY) {
                drawInteractionPageIndicators(canvas);
            }
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
                focusedIconOutlineAlpha, focusedIconOutlineScale, focusedIconOutlinePaints);
        } else if (outgoingIconOutlineVisual == null && hasFocus && !focusRawRect.isEmpty()) {
            // No artwork mask available (folder previews, views not yet measured): keep the ring
            // present with the sibling rounded-rect treatment instead of showing nothing.
            focusDisplayRect.set(focusRawRect);
            focusDisplayRect.offset(-locationOnScreen[0], -locationOnScreen[1]);
            float density = getResources().getDisplayMetrics().density;
            FocusOutlineRenderer.drawRoundRectFallback(canvas, focusDisplayRect,
                Math.min(focusDisplayRect.width(), focusDisplayRect.height()) * 0.28f, accent,
                focusedIconOutlineAlpha > 0f ? focusedIconOutlineAlpha : 1f,
                focusedIconOutlineScale > 0f ? focusedIconOutlineScale : 1f, density);
        }
    }

    private void drawFocusedAppPreviewIcon(Canvas canvas) {
        if (focusedAppPreviewIcon == null || appsRowRawBounds.isEmpty()) {
            return;
        }
        float progress = clamp01(focusedAppPreviewProgress);
        float rowTop = appsRowRawBounds.top - locationOnScreen[1];
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
        float top = rowTop - bubbleSize - verticalGap;
        if (top < dp(8f)) {
            top = dp(8f);
        }
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

        if (drawLabel) {
            drawFocusedAppPreviewLabel(canvas, focusedAppPreviewLabel, cx, top, sourceIconSize, alpha);
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

    private void drawFocusedAppPreviewLabel(
        @NonNull Canvas canvas,
        @NonNull String label,
        float centerX,
        float bubbleTop,
        float sourceIconSize,
        float alpha
    ) {
        String displayLabel = addPreviewLabelBreakOpportunities(label);
        previewLabelPaint.setTextSize(clamp(sourceIconSize * 0.22f, dp(9.5f), dp(11.5f)));
        previewLabelPaint.setColor(darkThemeActive
            ? withAlpha(Color.rgb(230, 224, 233), Math.round(245f * alpha))
            : withAlpha(Color.rgb(29, 27, 32), Math.round(235f * alpha)));

        float horizontalPadding = dp(7f);
        float verticalPadding = dp(4f);
        int maxInnerWidth = Math.round(clamp(getWidth() * 0.30f, dp(78f), dp(124f)));
        int minInnerWidth = Math.round(dp(38f));
        float measuredTextWidth = previewLabelPaint.measureText(displayLabel);
        int textWidth = Math.max(minInnerWidth, Math.min(maxInnerWidth, Math.round(measuredTextWidth + dp(1f))));
        StaticLayout layout = StaticLayout.Builder.obtain(displayLabel, 0, displayLabel.length(), previewLabelPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build();

        float pillWidth = Math.min(maxInnerWidth + (horizontalPadding * 2f), Math.max(dp(52f), layout.getWidth() + (horizontalPadding * 2f)));
        float pillHeight = layout.getHeight() + (verticalPadding * 2f);
        float pillLeft = clamp(centerX - (pillWidth * 0.5f), dp(8f), Math.max(dp(8f), getWidth() - pillWidth - dp(8f)));
        float pillTop = bubbleTop - pillHeight - dp(5f);
        if (pillTop < dp(8f)) {
            pillTop = dp(8f);
        }

        tmpRect.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight);
        previewFillPaint.setColor(withAlpha(Color.BLACK, Math.round((darkThemeActive ? 58f : 18f) * alpha)));
        RectF shadow = new RectF(tmpRect);
        shadow.offset(0f, dp(1.5f));
        canvas.drawRoundRect(shadow, pillHeight * 0.5f, pillHeight * 0.5f, previewFillPaint);

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
        bloomPaint.setShader(new RadialGradient(
            cx,
            cy,
            radius,
            new int[] { coreColor, outerColor, withAlpha(edgeTintColor, 0) },
            new float[] { 0f, 0.48f, 1f },
            Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, radius, bloomPaint);
        bloomPaint.setShader(null);

        float ringRadius = radius * lerp(0.42f, 0.78f, progress);
        glassStrokePaint.setStrokeWidth(dp(1.15f));
        glassStrokePaint.setColor(withAlpha(
            darkThemeActive ? Color.rgb(226, 222, 232) : Color.rgb(70, 64, 78),
            Math.round(lerp(24f, 72f, progress))
        ));
        canvas.drawCircle(cx, cy, ringRadius, glassStrokePaint);
    }

    private void drawEdgeGlowAmbient(Canvas canvas) {
        if (appsRowRawBounds.isEmpty()) {
            return;
        }
        float top = appsRowRawBounds.top - locationOnScreen[1];
        float bottom = appsRowRawBounds.bottom - locationOnScreen[1];
        int saveCount = canvas.save();
        canvas.clipRect(0f, top, getWidth(), bottom);

        float glowHeight = Math.max(dp(34f), bottom - top);
        float edgeWidth = Math.max(dp(24f), getWidth() * 0.062f);
        float spread = Math.max(dp(20f), edgeWidth * 1.18f);
        float radius = Math.max(dp(18f), glowHeight * 0.30f);

        if (interactionCanPageLeft) {
            drawEdgeGlowAmbient(canvas, 0f, top, edgeWidth, bottom, radius, spread, false);
        }
        if (interactionCanPageRight) {
            drawEdgeGlowAmbient(canvas, getWidth() - edgeWidth, top, getWidth(), bottom, radius, spread, true);
        }
        canvas.restoreToCount(saveCount);
    }

    private void drawEdgeGlowAmbient(Canvas canvas, float left, float top, float right, float bottom, float radius, float spread, boolean rightEdge) {
        RectF glow = new RectF(left, top, right, bottom);
        if (rightEdge) {
            glow.left -= spread * 0.28f;
            glow.right += spread;
        } else {
            glow.left -= spread;
            glow.right += spread * 0.28f;
        }
        glow.top -= dp(10f);
        glow.bottom += dp(10f);
        edgePaint.setColor(withAlpha(overflowGlowTintColor, 68));
        canvas.drawRoundRect(glow, radius, radius, edgePaint);

        RectF innerGlow = new RectF(glow);
        innerGlow.inset(spread * 0.30f, dp(6f));
        edgePaint.setColor(withAlpha(overflowGlowTintColor, 44));
        canvas.drawRoundRect(innerGlow, Math.max(dp(16f), radius - dp(6f)), Math.max(dp(16f), radius - dp(6f)), edgePaint);
    }

    private void drawLetterGlassDroplet(Canvas canvas) {
        float cx = displayRawX - locationOnScreen[0];
        float cy = displayRawY - locationOnScreen[1];
        if (!azRowRawBounds.isEmpty()) {
            float top = azRowRawBounds.top - locationOnScreen[1];
            float bottom = azRowRawBounds.bottom - locationOnScreen[1];
            cy = clamp(cy, top + dp(6f), bottom - dp(6f));
        }

        float w = dp(24f);
        float h = dp(18f);
        float radius = dp(9f);
        if (hasFocus && !focusRawRect.isEmpty()) {
            RectF focusLocal = new RectF(
                focusRawRect.left - locationOnScreen[0],
                focusRawRect.top - locationOnScreen[1],
                focusRawRect.right - locationOnScreen[0],
                focusRawRect.bottom - locationOnScreen[1]
            );
            float expandX = Math.max(dp(0.8f), focusLocal.width() * 0.04f);
            float expandY = Math.max(dp(0.6f), focusLocal.height() * 0.03f);
            focusLocal.inset(-expandX, -expandY);
            if (!azRowRawBounds.isEmpty()) {
                float top = azRowRawBounds.top - locationOnScreen[1];
                float bottom = azRowRawBounds.bottom - locationOnScreen[1];
                float centerY = focusLocal.centerY();
                centerY = clamp(centerY, top + (focusLocal.height() * 0.5f), bottom - (focusLocal.height() * 0.5f));
                float halfH = focusLocal.height() * 0.5f;
                focusLocal.top = centerY - halfH;
                focusLocal.bottom = centerY + halfH;
            }
            drawLetterGlassPopupEvolution(canvas, focusLocal, Math.max(dp(8f), focusLocal.height() * 0.46f));
            return;
        }

        tmpRect.set(cx - (w * 0.5f), cy - (h * 0.5f), cx + (w * 0.5f), cy + (h * 0.5f));
        drawLetterGlassPopupEvolution(canvas, tmpRect, radius);
    }

    private void drawLetterGlassPopupEvolution(Canvas canvas, RectF pill, float radius) {
        glassFillPaint.setColor(withAlpha(vividLetterTintColor, 208));
        canvas.drawRoundRect(pill, radius, radius, glassFillPaint);

        RectF innerVeil = new RectF(pill);
        float veilPad = Math.max(dp(1.4f), Math.min(pill.width(), pill.height()) * 0.10f);
        innerVeil.inset(veilPad, veilPad * 0.95f);
        glassInnerPaint.setColor(withAlpha(Color.WHITE, 48));
        canvas.drawRoundRect(innerVeil, Math.max(dp(4f), radius - veilPad), Math.max(dp(4f), radius - veilPad), glassInnerPaint);

        RectF sheen = new RectF(innerVeil);
        sheen.bottom = sheen.top + Math.max(dp(4.6f), innerVeil.height() * 0.42f);
        glassInnerPaint.setColor(withAlpha(Color.WHITE, 84));
        canvas.drawRoundRect(sheen, Math.max(dp(3f), radius - dp(5f)), Math.max(dp(3f), radius - dp(5f)), glassInnerPaint);

        glassStrokePaint.setStrokeWidth(dp(1.25f));
        glassStrokePaint.setColor(withAlpha(Color.WHITE, 144));
        canvas.drawRoundRect(pill, radius, radius, glassStrokePaint);

        RectF innerRim = new RectF(pill);
        innerRim.inset(dp(0.9f), dp(0.9f));
        glassStrokePaint.setStrokeWidth(dp(0.9f));
        glassStrokePaint.setColor(withAlpha(glassTintColor, 122));
        canvas.drawRoundRect(innerRim, Math.max(dp(4f), radius - dp(1.2f)), Math.max(dp(4f), radius - dp(1.2f)), glassStrokePaint);
    }

    private void drawLockedIconTrackGlass(Canvas canvas) {
        if (hasFocus) {
            lastFocusUpdateUptimeMs = SystemClock.uptimeMillis();
            if (focusDisplayRect.isEmpty()) {
                focusDisplayRect.set(focusRawRect);
            } else {
                blendRect(focusDisplayRect, focusRawRect, 0.31f);
            }
        } else {
            if ((SystemClock.uptimeMillis() - lastFocusUpdateUptimeMs) > FOCUS_HOLD_MS) {
                focusDisplayRect.setEmpty();
            }
        }

        int save = -1;
        if (!appsRowRawBounds.isEmpty()) {
            float left = appsRowRawBounds.left - locationOnScreen[0];
            float top = appsRowRawBounds.top - locationOnScreen[1];
            float right = appsRowRawBounds.right - locationOnScreen[0];
            float bottom = appsRowRawBounds.bottom - locationOnScreen[1];
            save = canvas.save();
            canvas.clipRect(left, top, right, bottom);
        }

        if (hasAnchor && !focusDisplayRect.isEmpty()) {
            drawLiquidBridge(canvas, focusDisplayRect);
        }

        if (!focusDisplayRect.isEmpty()) {
            RectF local = new RectF(
                focusDisplayRect.left - locationOnScreen[0],
                focusDisplayRect.top - locationOnScreen[1],
                focusDisplayRect.right - locationOnScreen[0],
                focusDisplayRect.bottom - locationOnScreen[1]
            );
            float shellPad = Math.max(dp(6f), Math.min(local.width(), local.height()) * 0.17f);
            local.inset(-shellPad, -shellPad);
            drawGlassBody(canvas, local, dp(14f), 0.72f, 0.42f);
        } else {
            float cx = displayRawX - locationOnScreen[0];
            float cy = displayRawY - locationOnScreen[1];
            float halfW = dp(17f);
            float halfH = dp(17f);
            tmpRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
            drawGlassBody(canvas, tmpRect, dp(12f), 0.56f, 0.24f);
        }
        if (save >= 0) {
            canvas.restoreToCount(save);
        }
    }

    private void drawLiquidBridge(Canvas canvas, RectF focusRaw) {
        float startX = anchorRawX - locationOnScreen[0];
        float startY = anchorRawY - locationOnScreen[1];
        float endX = focusRaw.centerX() - locationOnScreen[0];
        float endY = Math.min(displayRawY, focusRaw.centerY()) - locationOnScreen[1];

        float neck = dp(7f);
        float shoulder = dp(12f);

        liquidBridgePath.reset();
        liquidBridgePath.moveTo(startX - neck, startY);
        liquidBridgePath.cubicTo(startX - shoulder, lerp(startY, endY, 0.42f), endX - shoulder, lerp(startY, endY, 0.58f), endX - neck, endY);
        liquidBridgePath.lineTo(endX + neck, endY);
        liquidBridgePath.cubicTo(endX + shoulder, lerp(startY, endY, 0.58f), startX + shoulder, lerp(startY, endY, 0.42f), startX + neck, startY);
        liquidBridgePath.close();

        bridgePaint.setColor(withAlpha(glassTintColor, 106));
        canvas.drawPath(liquidBridgePath, bridgePaint);
        bridgePaint.setColor(withAlpha(Color.WHITE, 52));
        canvas.drawPath(liquidBridgePath, bridgePaint);
    }

    private void drawInteractionPageIndicators(Canvas canvas) {
        if (!interactionOverflowActive || interactionPageCount <= 1 || appsRowRawBounds.isEmpty()) {
            return;
        }
        boolean subtle = interactionUseSubtlePageIndicators;
        float attention = subtle ? clamp01(subtlePageIndicatorAttention) : 1f;
        // Both dock styles use the same "minimal ticks" indicator centred on the top edge.
        drawPageTicksIndicator(canvas, interactionPageIndicatorPosition, interactionPageCount, attention, interactionDynamicPageIndex);
    }

    /**
     * "Minimal ticks" page indicator (per the design spec): a row of small horizontal tick marks
     * centred on the dock's top edge. The active tick brightens and widens with a soft underglow;
     * inactive ticks are dim and short. Everything keys off proximity to the fractional page
     * position, so the brightness/width morph is continuous across a swipe (adjacent ticks share the
     * transition at the midpoint). Pure function of (position, page count, accent); fades with the
     * indicator's attention. Applies to both dock styles.
     */
    // Toned-down warm amber for the dynamic "most-used" page tick: distinguishable from the
    // dock-blended ticks, but harmonized rather than a loud neon.
    private static final int DYNAMIC_PAGE_TICK_COLOR = 0xFFE0A338;

    private void drawPageTicksIndicator(Canvas canvas, float activePagePosition, int totalPages, float attention, int dynamicPageIndex) {
        if (totalPages <= 1 || getWidth() <= 0) {
            return;
        }
        float master = clamp01(attention);
        if (master <= 0.01f) {
            return;
        }

        float wInactive = dp(13f);
        float wActive = dp(24f);
        float h = dp(2.5f);
        float r = h * 0.5f;
        float gap = dp(4f);
        float cx = getWidth() * 0.5f;
        float cy = dp(3.5f); // flush against the dock's top rim
        float pos = clamp(activePagePosition, 0f, totalPages - 1f);

        // Per-tick widths (the active page's tick widens), then laid out left-to-right with a
        // CONSTANT gap so the spacing reads evenly no matter which page is active.
        float[] widths = new float[totalPages];
        float sumW = 0f;
        for (int p = 0; p < totalPages; p++) {
            float prox = Math.max(0f, 1f - Math.abs(p - pos));
            widths[p] = wInactive + ((wActive - wInactive) * prox);
            sumW += widths[p];
        }
        float maxW = getWidth() - dp(36f);
        float total = sumW + (totalPages - 1) * gap;
        if (total > maxW && totalPages > 1) {
            gap = Math.max(dp(4f), (maxW - sumW) / (totalPages - 1));
            total = sumW + (totalPages - 1) * gap;
        }
        float[] centers = new float[totalPages];
        float x = cx - (total * 0.5f);
        for (int p = 0; p < totalPages; p++) {
            centers[p] = x + (widths[p] * 0.5f);
            x += widths[p] + gap;
        }

        // Blend into the dock: a slightly-brighter shade of the dock's own surface tint, so the
        // ticks and glow harmonize with the capsule instead of contrasting against it.
        int accent = boostColor(glassTintColor, 1.0f, 1.18f);
        boolean dynamicActive = dynamicPageIndex >= 0 && Math.abs(pos - dynamicPageIndex) < 0.5f;
        int glowColor = dynamicActive ? DYNAMIC_PAGE_TICK_COLOR : accent;

        // Soft glow: a blurred capsule that hugs the active tick (same pill shape, slightly larger),
        // following the fractional position across a swipe — a gentle dock-toned highlight.
        int lo = (int) Math.floor(pos);
        int hi = Math.min(totalPages - 1, lo + 1);
        float frac = pos - lo;
        float glowCx = centers[lo] + (centers[hi] - centers[lo]) * frac;
        float glowW = (widths[lo] + (widths[hi] - widths[lo]) * frac) + dp(8f);
        float glowH = h + dp(7f);
        float glowR = glowH * 0.5f;
        if (pageTickGlow == null) {
            pageTickGlow = new BlurMaskFilter(dp(5f), BlurMaskFilter.Blur.NORMAL);
        }
        pageIndicatorPaint.setStyle(Paint.Style.FILL);
        pageIndicatorPaint.setColor(withAlpha(glowColor, Math.round(70f * master)));
        pageIndicatorPaint.setMaskFilter(pageTickGlow);
        tmpRect.set(glowCx - glowW * 0.5f, cy - glowH * 0.5f, glowCx + glowW * 0.5f, cy + glowH * 0.5f);
        canvas.drawRoundRect(tmpRect, glowR, glowR, pageIndicatorPaint);
        pageIndicatorPaint.setMaskFilter(null);

        // Ticks: alpha interpolates by proximity to the active position; inactive floor kept legible.
        for (int p = 0; p < totalPages; p++) {
            float prox = Math.max(0f, 1f - Math.abs(p - pos));
            float alpha = 0.40f + (0.60f * prox);
            int tickColor = accent;
            if (p == dynamicPageIndex) {
                tickColor = DYNAMIC_PAGE_TICK_COLOR;
                // Extra idle damp: the warm tint only reads when this page is active; sleeping it
                // must not steal attention.
                alpha *= (0.55f + (0.45f * prox));
            }
            float left = centers[p] - (widths[p] * 0.5f);
            pageIndicatorPaint.setColor(withAlpha(tickColor, Math.round(255f * alpha * master)));
            tmpRect.set(left, cy - r, left + widths[p], cy + r);
            canvas.drawRoundRect(tmpRect, r, r, pageIndicatorPaint);
        }
    }

    private void animateInteractionPageIndicatorTo(int pageIndex, boolean animate) {
        if (interactionPageIndicatorAnimator != null) {
            interactionPageIndicatorAnimator.cancel();
            interactionPageIndicatorAnimator = null;
        }
        if (!animate) {
            interactionPageIndicatorPosition = pageIndex;
            return;
        }
        interactionPageIndicatorAnimator = createPageIndicatorAnimator(
            interactionPageIndicatorPosition,
            pageIndex,
            value -> interactionPageIndicatorPosition = value
        );
        interactionPageIndicatorAnimator.start();
    }

    private void setInteractionPageIndicatorPosition(float pagePosition) {
        if (interactionPageIndicatorAnimator != null) {
            interactionPageIndicatorAnimator.cancel();
            interactionPageIndicatorAnimator = null;
        }
        interactionPageIndicatorPosition = pagePosition;
    }

    private void showSubtlePageIndicatorAttention() {
        cancelSubtlePageIndicatorIdleFade();
        subtlePageIndicatorAttention = 1f;
        scheduleSubtlePageIndicatorIdleFade();
    }

    private void scheduleSubtlePageIndicatorIdleFade() {
        if (!subtlePageIndicatorFadeScheduled
            && interactionUseSubtlePageIndicators
            && interactionShowsPageIndicators
            && interactionOverflowActive) {
            subtlePageIndicatorFadeScheduled = true;
            postDelayed(fadeSubtlePageIndicatorRunnable, PINNED_INDICATOR_IDLE_DELAY_MS);
        }
    }

    private void animateSubtlePageIndicatorAttentionTo(float target) {
        if (!interactionUseSubtlePageIndicators || !interactionShowsPageIndicators || !interactionOverflowActive) {
            subtlePageIndicatorAttention = 1f;
            return;
        }
        if (subtlePageIndicatorAttentionAnimator != null) {
            subtlePageIndicatorAttentionAnimator.cancel();
            subtlePageIndicatorAttentionAnimator = null;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(subtlePageIndicatorAttention, clamp01(target));
        subtlePageIndicatorAttentionAnimator = animator;
        animator.setDuration(PINNED_INDICATOR_FADE_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.35f));
        animator.addUpdateListener(animation -> {
            subtlePageIndicatorAttention = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void cancelSubtlePageIndicatorIdleFade() {
        removeCallbacks(fadeSubtlePageIndicatorRunnable);
        subtlePageIndicatorFadeScheduled = false;
        if (subtlePageIndicatorAttentionAnimator != null) {
            subtlePageIndicatorAttentionAnimator.cancel();
            subtlePageIndicatorAttentionAnimator = null;
        }
    }

    private ValueAnimator createPageIndicatorAnimator(float start, float end, @NonNull FloatUpdate update) {
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);
        animator.setDuration(210L);
        animator.setInterpolator(new DecelerateInterpolator(1.4f));
        animator.addUpdateListener(animation -> {
            update.accept((float) animation.getAnimatedValue());
            invalidate();
        });
        return animator;
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

    private void cancelPageIndicatorAnimations() {
        if (interactionPageIndicatorAnimator != null) {
            interactionPageIndicatorAnimator.cancel();
            interactionPageIndicatorAnimator = null;
        }
    }

    private void drawLaunchGlassBloom(Canvas canvas) {
        float cx = launchBloomRawX - locationOnScreen[0];
        float cy = launchBloomRawY - locationOnScreen[1];
        float maxR = (float) Math.hypot(getWidth(), getHeight());
        float r = lerp(dp(22f), maxR * 1.08f, launchBloomProgress);
        float alphaFactor = (1f - launchBloomProgress);

        bloomPaint.setColor(withAlpha(glassTintColor, (int) (198 * alphaFactor)));
        canvas.drawCircle(cx, cy, r, bloomPaint);

        bloomPaint.setColor(withAlpha(Color.WHITE, (int) (132 * alphaFactor)));
        canvas.drawCircle(cx, cy, r * 0.55f, bloomPaint);
    }

    private void drawGlassBody(Canvas canvas, RectF rect, float radius, float tintAlpha, float innerAlpha) {
        glassFillPaint.setColor(withAlpha(glassTintColor, (int) (255f * tintAlpha)));
        canvas.drawRoundRect(rect, radius, radius, glassFillPaint);

        float pad = Math.max(dp(1.8f), Math.min(rect.width(), rect.height()) * 0.12f);
        RectF inner = new RectF(rect);
        inner.inset(pad, pad * 0.9f);
        glassInnerPaint.setColor(withAlpha(Color.WHITE, (int) (255f * innerAlpha)));
        canvas.drawRoundRect(inner, Math.max(dp(4f), radius - pad), Math.max(dp(4f), radius - pad), glassInnerPaint);

        glassStrokePaint.setStrokeWidth(dp(1.6f));
        glassStrokePaint.setColor(withAlpha(Color.WHITE, 178));
        canvas.drawRoundRect(rect, radius, radius, glassStrokePaint);
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

    private static void blendRect(RectF out, RectF target, float t) {
        out.left = lerp(out.left, target.left, t);
        out.top = lerp(out.top, target.top, t);
        out.right = lerp(out.right, target.right, t);
        out.bottom = lerp(out.bottom, target.bottom, t);
    }
}
