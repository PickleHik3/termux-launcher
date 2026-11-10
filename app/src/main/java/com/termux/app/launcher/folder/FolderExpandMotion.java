package com.termux.app.launcher.folder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.material.ModalSheetMotion;

/**
 * Plays {@link FolderExpandGeometry} on a folder panel: one animator driving the panel's scale
 * and alpha, view properties only (scale about a point inside the panel, and alpha), so nothing is laid out while it runs. Opens on the
 * emphasized decelerate and closes on the emphasized accelerate (the sheets' curves), and a close
 * takes only as long as the open it undoes had got, so an open caught half way closes from there.
 *
 * <p>Set the geometry once the panel is on screen ({@link #setGeometry}), then {@link #open};
 * {@link #close} runs its callback exactly once when the panel is back in its icon.</p>
 */
public final class FolderExpandMotion {
    @NonNull private final View target;
    @NonNull private final FolderExpandGeometry.Frame frame = new FolderExpandGeometry.Frame();
    @NonNull private final PathInterpolator decelerate = new PathInterpolator(
        ModalSheetMotion.DECELERATE_X1, ModalSheetMotion.DECELERATE_Y1,
        ModalSheetMotion.DECELERATE_X2, ModalSheetMotion.DECELERATE_Y2);
    @NonNull private final PathInterpolator accelerate = new PathInterpolator(
        ModalSheetMotion.ACCELERATE_X1, ModalSheetMotion.ACCELERATE_Y1,
        ModalSheetMotion.ACCELERATE_X2, ModalSheetMotion.ACCELERATE_Y2);
    @NonNull private FolderExpandGeometry geometry = FolderExpandGeometry.fallback(0, 0);
    @Nullable private ValueAnimator animator;
    @Nullable private Runnable onEnd;
    private float progress;

    public FolderExpandMotion(@NonNull View target) {
        this.target = target;
    }

    /** The icon's and the panel's rest rect, both in screen pixels. */
    public void setGeometry(int iconLeft, int iconTop, int iconRight, int iconBottom,
                            int panelLeft, int panelTop, int panelRight, int panelBottom) {
        geometry = FolderExpandGeometry.of(iconLeft, iconTop, iconRight, iconBottom,
            panelLeft, panelTop, panelRight, panelBottom);
        applyPivot();
    }

    /** Uses the no-icon look: a small scale and fade about the panel's centre. */
    public void setFallbackGeometry() {
        geometry = FolderExpandGeometry.fallback(target.getWidth(), target.getHeight());
        applyPivot();
    }

    private void applyPivot() {
        target.setPivotX(geometry.pivotX());
        target.setPivotY(geometry.pivotY());
    }

    /** Shows the panel at the icon (invisible) and grows it to its rest position. */
    public void open() {
        cancel();
        run(progress, 1f, decelerate, FolderExpandGeometry.DURATION_MS, null);
    }

    /** Shrinks the panel back into its icon from where it is, then runs {@code end} once. */
    public void close(@Nullable Runnable end) {
        cancel();
        long duration = Math.round(FolderExpandGeometry.DURATION_MS * progress);
        run(progress, 0f, accelerate, duration, end);
    }

    /** Stops where it is; no callback runs. */
    public void cancel() {
        ValueAnimator current = animator;
        animator = null;
        onEnd = null;
        if (current != null) {
            current.removeAllListeners();
            current.removeAllUpdateListeners();
            current.cancel();
        }
    }

    public boolean isRunning() {
        ValueAnimator current = animator;
        return current != null && current.isRunning();
    }

    private void run(float from, float to, PathInterpolator curve, long duration,
                     @Nullable Runnable end) {
        onEnd = end;
        apply(from);
        ValueAnimator next = ValueAnimator.ofFloat(from, to);
        next.setInterpolator(curve);
        next.setDuration(duration);
        next.addUpdateListener(a -> apply((Float) a.getAnimatedValue()));
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (animator != next) return;
                animator = null;
                apply(to);
                Runnable done = onEnd;
                onEnd = null;
                if (done != null) done.run();
            }
        });
        animator = next;
        next.start();
    }

    private void apply(float t) {
        progress = Math.max(0f, Math.min(1f, t));
        geometry.frameAt(progress, frame);
        target.setScaleX(frame.scale);
        target.setScaleY(frame.scale);
        target.setAlpha(frame.alpha);
    }
}
