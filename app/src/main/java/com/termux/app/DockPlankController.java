package com.termux.app;

import android.view.Choreographer;
import android.view.View;
import android.view.ViewParent;

/**
 * Spring-physics "glass plank" reactive treatment for the launcher dock.
 *
 * <p>The whole dock stack behaves like a tactile glass slab: touching it tilts the plank in 3D
 * toward the finger, slides it a little that way, dips it slightly on press, and springs it back on
 * release. A moving specular highlight tracks the touch point and the accent rim glow swells on
 * contact. All motion is driven by critically-damped springs integrated on a {@link Choreographer}
 * frame loop that sleeps when the springs settle, so there is no idle cost. When the system animator
 * duration scale is 0 (reduce-motion), the springs snap to their targets instead of animating.</p>
 *
 * <p>Everything on the dock — glass, icons, rows — is one plane: the slab owns the only transform
 * and its contents inherit it, so nothing on it can ease on a timeline of its own.</p>
 *
 * <p>This mirrors the {@code dock-ui.jsx} prototype's plank physics (press dip and glow/specular
 * coupling) recreated natively, with the tilt tightened to {@code maxTiltDeg} and the springs
 * moved onto true critical damping.</p>
 */
public final class DockPlankController implements Choreographer.FrameCallback {

    private static final float DEFAULT_MAX_TILT_DEG = 3f;
    /** Pivot height for the floating capsule: slightly below centre, so it reads as pushed, not spun. */
    private static final float PIVOT_BELOW_CENTRE = 0.6f;
    /** The small slide that keeps the rotation from feeling mathematically isolated. */
    private static final float DEFAULT_SHIFT_DP = 3f;
    /** Vertical share of that slide. The hinged bar gets none: its bottom edge stays pinned. */
    private static final float SHIFT_Y_FACTOR = 0.5f;
    /** Slack over the slide that the edge-to-edge slab overscans by, covering the tilt's own inset. */
    private static final float OVERSCAN_SLACK_DP = 2f;
    /** The capsule's press dip. */
    private static final float DEFAULT_PRESS_DIP = 0.013f;

    // Per-instance tuning: the dock keeps the defaults; the terminal's full-screen pane uses far
    // gentler values, since 3° on a surface that tall reads as the whole screen keeling over.
    private final float mMaxTiltDeg;
    private final float mShiftDp;
    private final float mPressDip;

    private final View mPlank;       // the transformed slab (whole dock stack)
    private final View mSpecular;    // moving specular highlight
    private final View mGlow;        // accent rim glow
    private final float mDensity;
    private View mIconLayer;         // the dock's icon row
    private boolean mIconLayerInherits;  // true when the row is inside the slab and needs no transform

    private boolean mEnabled = true;
    private boolean mReducedMotion = false;
    private boolean mPressed = false;
    private boolean mFrameScheduled = false;
    private boolean mMotionEnabled = true;
    // Hinge mode (edge-to-edge "normal" dock): pivot at the screen-bottom edge so the bar tips back
    // from the bottom toward the finger, instead of the capsule's free-floating centre tilt+dip.
    private boolean mHingeMode = false;
    private long mLastFrameTimeNanos;

    // Spring channels: tilt about X/Y, press dip, rim glow, and the specular's position. Every
    // damping constant is 2*sqrt(stiffness) — the integrator is a = k*(target - x) - c*v at unit
    // mass, so that is the critical value: one clean settle, no overshoot on any channel.
    private final Spring mRx = new Spring(0f, 170f, 26.08f);
    private final Spring mRy = new Spring(0f, 170f, 26.08f);
    private final Spring mPress = new Spring(0f, 320f, 35.78f);
    private final Spring mGlowLevel = new Spring(0f, 130f, 22.80f);
    private final Spring mLightX = new Spring(0.5f, 210f, 28.98f);
    private final Spring mLightY = new Spring(0.5f, 210f, 28.98f);

    public DockPlankController(View plank, View specular, View glow) {
        this(plank, specular, glow, DEFAULT_MAX_TILT_DEG, DEFAULT_SHIFT_DP, DEFAULT_PRESS_DIP);
    }

    public DockPlankController(View plank, View specular, View glow,
                        float maxTiltDeg, float shiftDp, float pressDip) {
        mMaxTiltDeg = maxTiltDeg;
        mShiftDp = shiftDp;
        mPressDip = pressDip;
        mPlank = plank;
        mSpecular = specular;
        mGlow = glow;
        View metricsSource = plank != null ? plank : (specular != null ? specular : glow);
        mDensity = metricsSource == null
            ? 1f : metricsSource.getResources().getDisplayMetrics().density;
        if (mPlank != null) {
            // Keep the perspective gentle so the small tilt reads as depth, not distortion.
            mPlank.setCameraDistance(mDensity * 2600f);
        }
    }

    /**
     * The dock's icon row. Wherever the row sits inside the transformed slab it is left completely
     * alone — it inherits the slab's transform, which is the only way its motion can be exactly the
     * glass's motion. The one state where it is not a descendant (the in-app keyboard tilts the
     * glass surface alone, since the slab there also holds the keyboard) it is driven with the very
     * same spring values, never an easing of its own. Passing a different view (or null)
     * neutralizes the previous one.
     */
    public void setIconLayer(View iconLayer) {
        if (mIconLayer == iconLayer) {
            return;
        }
        if (mIconLayer != null) {
            resetIconLayer(mIconLayer);
        }
        mIconLayer = iconLayer;
        mIconLayerInherits = isInsidePlank(iconLayer);
        if (mIconLayer != null) {
            if (mIconLayerInherits) {
                resetIconLayer(mIconLayer);
            } else {
                mIconLayer.setCameraDistance(mDensity * 2600f);
                applyToViews();
            }
        }
    }

    private boolean isInsidePlank(View view) {
        if (mPlank == null || view == null) {
            return false;
        }
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == mPlank) {
                return true;
            }
        }
        return false;
    }

    private static void resetIconLayer(View layer) {
        layer.setRotationX(0f);
        layer.setRotationY(0f);
        layer.setTranslationX(0f);
        layer.setTranslationY(0f);
        layer.setScaleX(1f);
        layer.setScaleY(1f);
    }

    public void setReducedMotion(boolean reduced) {
        mReducedMotion = reduced;
    }

    /** Enable/disable the slab transform (tilt). Both styles use motion; the mode differs. */
    public void setMotionEnabled(boolean enabled) {
        mMotionEnabled = enabled;
        if (!enabled) {
            mRx.target = 0f;
            mRy.target = 0f;
        }
        kick();
    }

    /** Capsule = false (free-floating centre tilt + press dip); normal = true (bottom-hinged tilt). */
    public void setHingeMode(boolean hinge) {
        mHingeMode = hinge;
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled) {
            if (enabled) {
                showRestingGlow();
                applyToViews();
            }
            return;
        }
        mEnabled = enabled;
        if (!enabled) {
            reset();
        } else {
            showRestingGlow();
            applyToViews();
        }
    }

    /** Begin a touch on the plank. {@code nx}/{@code ny} are normalized [0,1] within the plank. */
    public void onPointerDown(float nx, float ny) {
        if (!mEnabled) {
            return;
        }
        mPressed = true;
        aim(nx, ny);
        mPress.target = 1f;
        mGlowLevel.target = 1f;
        if (mGlow != null) {
            mGlow.setVisibility(View.VISIBLE);
        }
        if (mSpecular != null) {
            mSpecular.setVisibility(View.VISIBLE);
        }
        kick();
    }

    public void onPointerMove(float nx, float ny) {
        if (!mEnabled || !mPressed) {
            return;
        }
        aim(nx, ny);
        kick();
    }

    public void onPointerUp() {
        if (!mPressed) {
            return;
        }
        mPressed = false;
        mPress.target = 0f;
        mGlowLevel.target = 0f;
        mRx.target = 0f;
        mRy.target = 0f;
        kick();
    }

    /** Snap everything back to neutral and stop the frame loop. */
    public void reset() {
        mPressed = false;
        mRx.reset(0f);
        mRy.reset(0f);
        mPress.reset(0f);
        mGlowLevel.reset(0f);
        mLightX.reset(0.5f);
        mLightY.reset(0.5f);
        applyToViews();
        if (mGlow != null) {
            mGlow.setVisibility(View.GONE);
        }
        if (mSpecular != null) {
            mSpecular.setVisibility(View.GONE);
        }
    }

    private void showRestingGlow() {
        if (mGlow instanceof DockEdgeGlowView) {
            mGlow.setVisibility(View.VISIBLE);
        }
    }

    private void aim(float nx, float ny) {
        nx = clamp01(nx);
        ny = clamp01(ny);
        // The specular always tracks the finger (both axes); the slab only tilts when motion is on.
        mLightX.target = nx;
        mLightY.target = ny;
        if (mMotionEnabled) {
            mRy.target = (nx - 0.5f) * 2f * mMaxTiltDeg;
            mRx.target = -(ny - 0.5f) * 2f * mMaxTiltDeg;
        } else {
            mRy.target = 0f;
            mRx.target = 0f;
        }
    }

    private void kick() {
        if (!mFrameScheduled) {
            mFrameScheduled = true;
            mLastFrameTimeNanos = 0L;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mFrameScheduled = false;
        // First frame has no prior timestamp; use the minimum stable timestep.
        float dt = mLastFrameTimeNanos == 0L
            ? Spring.MIN_DT
            : (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000_000f;
        mLastFrameTimeNanos = frameTimeNanos;
        dt = Spring.clampDelta(dt);
        boolean moving = false;
        moving |= mRx.tick(mReducedMotion, dt);
        moving |= mRy.tick(mReducedMotion, dt);
        moving |= mPress.tick(mReducedMotion, dt);
        moving |= mGlowLevel.tick(mReducedMotion, dt);
        moving |= mLightX.tick(mReducedMotion, dt);
        moving |= mLightY.tick(mReducedMotion, dt);
        applyToViews();
        if (moving) {
            kick();
        } else if (!mPressed && mGlowLevel.value < 0.002f) {
            // Fully settled and released: drop transient layers. The custom edge glow still draws a
            // quiet resting refraction rim, so keep only that view alive.
            if (mGlow != null && !(mGlow instanceof DockEdgeGlowView)) {
                mGlow.setVisibility(View.GONE);
            }
            if (mSpecular != null) {
                mSpecular.setVisibility(View.GONE);
            }
        }
    }

    private void applyToViews() {
        if (mPlank != null && mPlank.getWidth() > 0 && mPlank.getHeight() > 0) {
            if (mMotionEnabled) {
                applySlabTransform(mPlank);
            } else if (mPlank.getRotationX() != 0f || mPlank.getRotationY() != 0f
                || mPlank.getScaleX() != 1f) {
                mPlank.setRotationX(0f);
                mPlank.setRotationY(0f);
                mPlank.setScaleX(1f);
                mPlank.setScaleY(1f);
                mPlank.setTranslationX(0f);
                mPlank.setTranslationY(0f);
            }
        }
        applyToIconLayer();
        if (mGlow instanceof DockEdgeGlowView) {
            // Drive the reactive rim: overall strength from the glow spring, and the live tilt so the
            // hot lobe sweeps around the perimeter as the plank tips (physical glass-edge light).
            float glowTiltX = mMotionEnabled
                ? mRx.value
                : -(mLightY.value - 0.5f) * 2f * mMaxTiltDeg;
            float glowTiltY = mMotionEnabled
                ? mRy.value
                : (mLightX.value - 0.5f) * 2f * mMaxTiltDeg;
            ((DockEdgeGlowView) mGlow).setGlowState(clamp01(mGlowLevel.value), glowTiltX, glowTiltY);
        } else if (mGlow != null) {
            mGlow.setAlpha(clamp01(mGlowLevel.value));
        }
        if (mSpecular != null) {
            // Track the touch point in both axes within the specular's own parent (the glass host),
            // so the highlight sits under the finger rather than pinned to the top edge.
            View host = mSpecular.getParent() instanceof View ? (View) mSpecular.getParent() : mPlank;
            float hw = host != null ? host.getWidth() : 0f;
            float hh = host != null ? host.getHeight() : 0f;
            mSpecular.setTranslationX((mLightX.value - 0.5f) * hw);
            mSpecular.setTranslationY((mLightY.value - 0.5f) * hh);
            mSpecular.setAlpha(clamp01(0.07f + mGlowLevel.value * 0.22f + mPress.value * 0.12f));
        }
    }

    /**
     * The one transform on the dock, applied to the slab so everything on it moves as one plane.
     *
     * <p>It rotates about the touch point horizontally and about a line just below centre
     * vertically (the screen-bottom edge for the edge-to-edge bar, whose bottom must stay pinned),
     * so the slab reads as pushed rather than spun. The rotation carries a small slide the same way
     * a real plank shifts as it tips. The edge-to-edge bar is full-bleed, so it overscans
     * horizontally by exactly the slide plus the tilt's own perspective inset — otherwise the slide
     * would open a strip of background at the screen edge. The overscan is scaled by contact, so a
     * resting dock is at exactly its laid-out size.</p>
     */
    private void applySlabTransform(View slab) {
        float width = slab.getWidth();
        float height = slab.getHeight();
        slab.setPivotX(width * clamp01(mLightX.value));
        slab.setPivotY(mHingeMode ? height : height * PIVOT_BELOW_CENTRE);
        slab.setRotationX(mRx.value);
        slab.setRotationY(mRy.value);
        float tiltFraction = mRy.value / mMaxTiltDeg;
        float shiftPx = mDensity * mShiftDp;
        slab.setTranslationX(tiltFraction * shiftPx);
        // The hinged bar slides sideways only: any vertical travel would lift it off the screen edge.
        slab.setTranslationY(mHingeMode
            ? 0f : (-mRx.value / mMaxTiltDeg) * shiftPx * SHIFT_Y_FACTOR);
        if (mHingeMode) {
            float overscan = (Math.abs(tiltFraction) * shiftPx + mDensity * OVERSCAN_SLACK_DP)
                * clamp01(mGlowLevel.value);
            slab.setScaleX(width > 0f ? (width + 2f * overscan) / width : 1f);
            slab.setScaleY(1f);
        } else {
            // The floating capsule has margins to slide into, so it needs no overscan — just the dip.
            float scale = 1f - mPress.value * mPressDip;
            slab.setScaleX(scale);
            slab.setScaleY(scale);
        }
    }

    /**
     * Only for the one state where the icon row is not inside the transformed slab: it gets the
     * slab's transform verbatim, off the same springs, so it still cannot ease independently.
     */
    private void applyToIconLayer() {
        View icons = mIconLayer;
        if (icons == null || mIconLayerInherits || !mMotionEnabled
            || icons.getWidth() <= 0 || icons.getHeight() <= 0) {
            return;
        }
        applySlabTransform(icons);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
