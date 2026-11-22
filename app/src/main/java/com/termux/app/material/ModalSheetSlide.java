package com.termux.app.material;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

/**
 * Slides a modal sheet's card in and out in {@link ModalSheetMotion#OPEN_CLOSE_MS}, which the
 * stock {@link BottomSheetBehavior} settle cannot be told to do. One per sheet: it drives the
 * card's translationY between {@code travel} (the card off the bottom of its parent) and 0, and
 * the sheet's {@link Frame} sets what follows it (the dim, Home's cover of the nav band). The
 * behavior stays expanded and keeps the drag; only an open from a state other than expanded needs
 * {@link #armExpanded}. The animator follows the system animator scale, so with animations off the
 * slide jumps to its end and {@code onEnd} still runs.
 */
public final class ModalSheetSlide {
    /** Told after every step of the slide; reads the card's translation and {@link #scrimAlpha}. */
    public interface Frame { void onFrame(); }

    @NonNull private final View card;
    @NonNull private final Frame frame;
    @NonNull private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    @NonNull private final PathInterpolator in = new PathInterpolator(
        ModalSheetMotion.DECELERATE_X1, ModalSheetMotion.DECELERATE_Y1,
        ModalSheetMotion.DECELERATE_X2, ModalSheetMotion.DECELERATE_Y2);
    @NonNull private final PathInterpolator out = new PathInterpolator(
        ModalSheetMotion.ACCELERATE_X1, ModalSheetMotion.ACCELERATE_Y1,
        ModalSheetMotion.ACCELERATE_X2, ModalSheetMotion.ACCELERATE_Y2);
    private float travel;
    private float fromY;
    private float toY;
    /** Runs when the running slide reaches its end; null once it was cancelled or taken over. */
    @Nullable private Runnable pendingEnd;

    public ModalSheetSlide(@NonNull View card, @NonNull Frame frame) {
        this.card = card;
        this.frame = frame;
        animator.addUpdateListener(a -> {
            card.setTranslationY(fromY + (toY - fromY) * a.getAnimatedFraction());
            this.frame.onFrame();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                Runnable done = pendingEnd;
                pendingEnd = null;
                if (done != null) done.run();
            }
        });
    }

    /** How far the card must go to be off the bottom of its parent, from its laid-out top. */
    public float travel() {
        View parent = card.getParent() instanceof View ? (View) card.getParent() : null;
        return parent == null ? 0f : Math.max(0f, parent.getHeight() - card.getTop());
    }

    /** The dim's alpha for the card where it now stands, by the travel of the last slide. */
    public float scrimAlpha() {
        return ModalSheetMotion.scrimAlpha(card.getTranslationY(), travel);
    }

    public boolean isRunning() { return animator.isStarted(); }

    /**
     * Slides the card up to rest. From off the bottom when none is running, from where it stands
     * when it takes over a close; {@code onEnd} runs once it is up.
     */
    public void slideIn(@Nullable Runnable onEnd) {
        boolean takingOver = animator.isStarted();
        stop();
        travel = travel();
        float from = takingOver ? Math.max(0f, Math.min(travel, card.getTranslationY())) : travel;
        card.setTranslationY(from);
        start(from, 0f, ModalSheetMotion.remainingDuration(travel <= 0f ? 1f : from / travel, true),
            in, onEnd);
    }

    /** Slides the card away from where it stands; {@code onEnd} runs once it is off the bottom. */
    public void slideOut(@Nullable Runnable onEnd) {
        stop();
        travel = travel();
        float from = Math.max(0f, Math.min(travel, card.getTranslationY()));
        start(from, travel, ModalSheetMotion.remainingDuration(travel <= 0f ? 0f : from / travel, false),
            out, onEnd);
    }

    /** Stops where it is, without running {@code onEnd}. */
    public void cancel() {
        stop();
    }

    /** Jumps to the end of the running slide, and runs its {@code onEnd}. */
    public void end() {
        if (animator.isStarted()) animator.end();
    }

    /** Stops the slide and puts the card at rest. */
    public void reset() {
        stop();
        card.setTranslationY(0f);
    }

    private void stop() {
        pendingEnd = null;
        if (animator.isStarted()) animator.cancel();
    }

    private void start(float from, float to, long durationMs, @NonNull PathInterpolator curve,
                       @Nullable Runnable onEnd) {
        fromY = from;
        toY = to;
        pendingEnd = onEnd;
        animator.setInterpolator(curve);
        animator.setDuration(durationMs);
        animator.start();
    }

    /**
     * The behavior to open with: {@code current} when it is expanded, else a fresh one with the
     * same settings and {@code callback}, already told to be expanded. A fresh behavior places the
     * card at the expanded offset on its first layout with no settle, which is what lets the slide
     * be the only motion; a stock one dragged down to hidden would settle up on its own clock.
     * Call before the sheet is made visible.
     */
    @NonNull
    public static <V extends View> BottomSheetBehavior<V> armExpanded(
        @NonNull V card, @NonNull BottomSheetBehavior<V> current,
        @NonNull BottomSheetBehavior.BottomSheetCallback callback) {
        if (current.getState() == BottomSheetBehavior.STATE_EXPANDED) return current;
        BottomSheetBehavior<V> fresh = new BottomSheetBehavior<>();
        fresh.setHideable(current.isHideable());
        fresh.setSkipCollapsed(current.getSkipCollapsed());
        fresh.setFitToContents(current.isFitToContents());
        fresh.setDraggable(current.isDraggable());
        if (!current.isFitToContents()) fresh.setExpandedOffset(current.getExpandedOffset());
        fresh.addBottomSheetCallback(callback);
        current.removeBottomSheetCallback(callback);
        fresh.setState(BottomSheetBehavior.STATE_EXPANDED);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) card.getLayoutParams();
        // The behavior offsets the card from where the parent lays it out, so a bottom gravity
        // would count the offset twice; the card stands at the top of its parent's layout.
        if (params.gravity != android.view.Gravity.NO_GRAVITY) {
            params.gravity = (params.gravity & android.view.Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
                | android.view.Gravity.TOP;
        }
        params.setBehavior(fresh);
        card.requestLayout();
        return fresh;
    }
}
