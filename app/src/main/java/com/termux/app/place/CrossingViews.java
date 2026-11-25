package com.termux.app.place;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.page.PageKind;

/**
 * What a crossing moves ({@link Crossing}'s adapter), and the last values it wrote, so a frame that
 * repeats one touches no view: the accessory stack's translation, its rows' sink into the
 * keyboard, the status content's travel alpha, the status bar's ride, the travel cover the pane
 * controller holds for the window in front, and the keyboard brought up for a slide. Only
 * views are written here; every decision is the crossing's.
 *
 * <p>The activity gives it what only the activity has through its {@link Host}: the window's
 * views, the travel's shares it keeps for its own layout passes, the pane and wall controllers,
 * and the keyboard.
 */
public final class CrossingViews implements Crossing.Stack, Crossing.Bar, Crossing.Cover,
        Crossing.Keyboard {

    /** What only the activity can give the adapter. */
    public interface Host {
        /** The window's view of {@code id}, found once per tree; null while it has none. */
        @Nullable View hotView(int id);
        /**
         * The accessory stack drawn {@code dockPx} lower, of which {@code keyboardPx} is the
         * keyboard's: the travel's shares kept, and the stack's translation applied.
         */
        void translateStack(float dockPx, float keyboardPx);
        /** The status bar drawn {@code px} from its rest, with the strip behind the system bar. */
        void rideStatusX(float px);
        /** The travel cover the pane controller holds for the window in front. */
        void coverWindowInFront(int px);
        /** The in-app keyboard brought up below the screen for {@code target}'s half, quietly. */
        void preRollKeyboard(@NonNull PageKind target);
        /** The stack's one writer of transforms: the sink, the cut and the slide go through it. */
        @NonNull com.termux.app.StackRenderer stackRenderer();
    }

    @NonNull private final Host mHost;
    /** The sink the stack's rows are handed the renderer, reused by every frame. */
    private final StackScene.Motion mSinkMotion = new StackScene.Motion();
    /** The alpha the travel last wrote, 1 when it has put everything back. */
    private float mStatusAlpha = 1f;

    /** @param context the window the adapter moves views of */
    public CrossingViews(@NonNull Context context, @NonNull Host host) {
        mHost = host;
    }

    @Override
    public void translate(float dockPx, float keyboardPx) {
        mHost.translateStack(dockPx, keyboardPx);
    }

    @Override
    public void rideX(float px) {
        mHost.rideStatusX(px);
    }

    @Override
    public void cover(int px) {
        mHost.coverWindowInFront(px);
    }

    @Override
    public void preRoll(@NonNull PageKind target) {
        mHost.preRollKeyboard(target);
    }

    /**
     * The dock's rows drawn {@code sinkPx} lower than they are laid out, each layer cut at its
     * own laid-out bottom, which is the keyboard's top: the rows go into the keyboard's edge
     * rather than across it, while the keyboard stays where its own travel puts it. Over the
     * seam only, with a keyboard in the stack; 0 puts every layer back.
     */
    @Override
    public void sink(float sinkPx) {
        mSinkMotion.sink(sinkPx);
        applyLayerSink(mSinkMotion);
    }

    /**
     * The dock's layers drawn lower than they are laid out, each cut at its own laid-out bottom
     * less a hidden part ({@link StackScene.Motion#layerSink}): handed to the stack's renderer,
     * the only writer of those transforms, which writes what changed.
     */
    private void applyLayerSink(@NonNull StackScene.Motion motion) {
        mHost.stackRenderer().setSink(motion.rowsSinkPx, motion.rowsCutPx, motion.sheetSinkPx,
            motion.sheetCutPx);
    }

    /**
     * Fades the status bar's content toward a page that wears no bar where the bar cannot ride.
     * Alpha only, on views whose alpha nothing else writes; 1 puts them back. The bars off the
     * dock are cards (RestCards, #307): the edge stacks stand still through a crossing, so a bar
     * both ends stand alike stays put and nothing fades it.
     */
    @Override
    public void alpha(float statusAlpha) {
        // Most frames of most slides move between two places that look alike here; those cost
        // nothing past this comparison.
        if (statusAlpha == mStatusAlpha) return;
        mStatusAlpha = statusAlpha;
        setTravelAlpha(R.id.terminal_status_row, statusAlpha);
        setTravelAlpha(R.id.terminal_status_lens, statusAlpha);
    }

    private void setTravelAlpha(int viewId, float alpha) {
        View view = mHost.hotView(viewId);
        if (view == null) return;
        float clamped = Math.max(0f, Math.min(1f, alpha));
        if (view.getAlpha() != clamped) view.setAlpha(clamped);
    }

    @Override
    public void sides(boolean fromHome, boolean toHome, float progress) {
        mHost.stackRenderer().setEnds(fromHome, toHome, progress);
    }

    @Override
    public void sides(boolean fromHome, boolean toHome, float progress, int leavesDirection) {
        mHost.stackRenderer().setEnds(fromHome, toHome, progress, leavesDirection);
    }

    @Override
    public void endSides() {
        mHost.stackRenderer().clearEnds();
    }
}
