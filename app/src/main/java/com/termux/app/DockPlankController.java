package com.termux.app;

import android.view.Choreographer;
import android.view.View;

/**
 * Spring-physics "glass plank" reactive treatment for the launcher dock.
 *
 * <p>The whole dock stack behaves like a tactile glass slab: touching it tilts the plank in 3D
 * toward the finger, dips it slightly on press, and springs it back on release. A moving specular
 * highlight tracks the touch point and the accent rim glow swells on contact. All motion is driven
 * by critically-damped springs integrated on a {@link Choreographer} frame loop that sleeps when the
 * springs settle, so there is no idle cost. When the system animator duration scale is 0
 * (reduce-motion), the springs snap to their targets instead of animating.</p>
 *
 * <p>This mirrors the {@code dock-ui.jsx} prototype's plank physics (MAX_TILT 5°, the same spring
 * stiffness/damping constants, press dip and glow/specular coupling), recreated natively.</p>
 */
final class DockPlankController implements Choreographer.FrameCallback {

    private static final float MAX_TILT_DEG = 4f;
    private static final float MIN_DT = 1f / 120f;
    private static final float MAX_DT = 1f / 30f;
    private static final float SETTLE_EPSILON = 4e-4f;

    private final View mPlank;       // the transformed slab (whole dock stack)
    private final View mSpecular;    // moving specular highlight
    private final View mGlow;        // accent rim glow

    private boolean mEnabled = true;
    private boolean mReducedMotion = false;
    private boolean mPressed = false;
    private boolean mFrameScheduled = false;
    private boolean mMotionEnabled = true;
    // Hinge mode (edge-to-edge "normal" dock): pivot at the screen-bottom edge so the bar tips back
    // from the bottom toward the finger, instead of the capsule's free-floating centre tilt+dip.
    private boolean mHingeMode = false;
    private long mLastFrameTimeNanos;

    // Spring channels: tilt about X/Y, press dip, rim glow, and the specular's horizontal position.
    private final Spring mRx = new Spring(0f, 170f, 17f);
    private final Spring mRy = new Spring(0f, 170f, 17f);
    private final Spring mPress = new Spring(0f, 320f, 22f);
    private final Spring mGlowLevel = new Spring(0f, 130f, 24f);
    private final Spring mLightX = new Spring(0.5f, 210f, 23f);
    private final Spring mLightY = new Spring(0.5f, 210f, 23f);

    DockPlankController(View plank, View specular, View glow) {
        mPlank = plank;
        mSpecular = specular;
        mGlow = glow;
        if (mPlank != null) {
            // Keep the perspective gentle so the small tilt reads as depth, not distortion.
            mPlank.setCameraDistance(mPlank.getResources().getDisplayMetrics().density * 2600f);
        }
    }

    void setReducedMotion(boolean reduced) {
        mReducedMotion = reduced;
    }

    /** Enable/disable the slab transform (tilt). Both styles use motion; the mode differs. */
    void setMotionEnabled(boolean enabled) {
        mMotionEnabled = enabled;
        if (!enabled) {
            mRx.target = 0f;
            mRy.target = 0f;
        }
        kick();
    }

    /** Capsule = false (free-floating centre tilt + press dip); normal = true (bottom-hinged tilt). */
    void setHingeMode(boolean hinge) {
        mHingeMode = hinge;
    }

    void setEnabled(boolean enabled) {
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
    void onPointerDown(float nx, float ny) {
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

    void onPointerMove(float nx, float ny) {
        if (!mEnabled || !mPressed) {
            return;
        }
        aim(nx, ny);
        kick();
    }

    void onPointerUp() {
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
    void reset() {
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
            mRy.target = (nx - 0.5f) * 2f * MAX_TILT_DEG;
            mRx.target = -(ny - 0.5f) * 2f * MAX_TILT_DEG;
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
            ? MIN_DT
            : (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000_000f;
        mLastFrameTimeNanos = frameTimeNanos;
        dt = Math.max(MIN_DT, Math.min(MAX_DT, dt));
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
                mPlank.setPivotX(mPlank.getWidth() * 0.5f);
                // Hinge at the bottom edge for the edge-to-edge dock; centre for the floating capsule.
                mPlank.setPivotY(mHingeMode ? mPlank.getHeight() : mPlank.getHeight() * 0.5f);
                mPlank.setRotationX(mRx.value);
                mPlank.setRotationY(mRy.value);
                // The hinged bar tips only (its bottom edge stays pinned); the capsule also dips.
                float scale = mHingeMode ? 1f : (1f - mPress.value * 0.013f);
                mPlank.setScaleX(scale);
                mPlank.setScaleY(scale);
            } else if (mPlank.getRotationX() != 0f || mPlank.getRotationY() != 0f
                || mPlank.getScaleX() != 1f) {
                mPlank.setRotationX(0f);
                mPlank.setRotationY(0f);
                mPlank.setScaleX(1f);
                mPlank.setScaleY(1f);
            }
        }
        if (mGlow instanceof DockEdgeGlowView) {
            // Drive the reactive rim: overall strength from the glow spring, and the live tilt so the
            // hot lobe sweeps around the perimeter as the plank tips (physical glass-edge light).
            float glowTiltX = mMotionEnabled
                ? mRx.value
                : -(mLightY.value - 0.5f) * 2f * MAX_TILT_DEG;
            float glowTiltY = mMotionEnabled
                ? mRy.value
                : (mLightX.value - 0.5f) * 2f * MAX_TILT_DEG;
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

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** A single critically-damped spring channel integrated with a fixed timestep. */
    private static final class Spring {
        float value;
        float target;
        float vel;
        final float stiffness;
        final float damping;

        Spring(float init, float stiffness, float damping) {
            this.value = init;
            this.target = init;
            this.stiffness = stiffness;
            this.damping = damping;
        }

        void reset(float v) {
            value = v;
            target = v;
            vel = 0f;
        }

        /** @return true if the spring is still in motion and needs another frame. */
        boolean tick(boolean reduced, float dt) {
            if (reduced) {
                value = target;
                vel = 0f;
                return false;
            }
            float accel = stiffness * (target - value) - damping * vel;
            vel += accel * dt;
            value += vel * dt;
            return Math.abs(target - value) > SETTLE_EPSILON || Math.abs(vel) > SETTLE_EPSILON;
        }
    }
}
