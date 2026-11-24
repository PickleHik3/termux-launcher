package com.termux.app.dock;

import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeEdgeRule;
import com.termux.app.chrome.ChromeSpec;
import com.termux.app.chrome.GlassFilters;
import com.termux.app.chrome.SurfaceDirtyLedger;
import com.termux.app.place.RestCards;
import com.termux.app.place.SeamCrossing;

/**
 * The foot under the gesture pill as a card of its own (ADR 0032 2026-11-24, #307): while the
 * cards stand apart under Docked and both sides' hosts wear the foot at one rect, the band is one
 * shared card on {@code dock_foot_host}, laid out once and left still by a crossing, and Home's
 * host and the Terminal's host each stop at its top ({@link RestCards.StackView#FOOT}).
 *
 * <p>It joins the host above it with nothing between: the same Live picture of the wallpaper
 * (its own glass entry, sampled at its own place on screen, so the picture runs on), the slab's
 * plate (the dock's tint) and every edge a seam, so the program draws no rim along the join or the
 * screen's edges. One foot under two hosts can match only one host's frost at a time: at a rest it
 * wears the frost of the host that stands (the same filter object), and across a crossing it
 * blends from the leaving host's to the arriving host's on the crossing's own share, as every
 * shared glass surface blends look and ink on one curve (ADR 0030). The steps of that blend are
 * built once as the crossing begins; a frame only picks one.
 *
 * <p>Live glass only while it stands; released, with its picture dropped, at a rest where it is
 * gone or the stack stands down on a frame. Never laid out or dressed per frame.
 */
public final class FootDress {

    /** What only the activity owns: the card source's needs, the stand-down and the card. */
    public interface Source extends KeysCardDress.Source {
        /** The stack stands down on a pane's frame: the frame is the glass, the foot wears none. */
        boolean standsDownOnFrame();
        /** The shared foot at this rest; null while none stands on its own view. */
        @Nullable RestCards.Card card();
    }

    @NonNull private final DockController.Host mHost;
    @NonNull private final Source mSource;
    @NonNull private final GlassBackdropDress mBackdrop;

    /** The blend's ends as the crossing began (20 entries each) and its steps, built then. */
    @NonNull private final float[] mFrom = new float[20];
    @NonNull private final float[] mTo = new float[20];
    @NonNull private final float[] mStep = new float[20];
    @NonNull private final ColorFilter[] mSteps = new ColorFilter[SeamCrossing.END_STEPS + 1];
    @Nullable private ColorFilter mFromUp;
    @Nullable private ColorFilter mToUp;
    private boolean mBlending;

    public FootDress(@NonNull DockController dock, @NonNull Source source) {
        mHost = dock.host();
        mSource = source;
        mBackdrop = new GlassBackdropDress(dock, source, new GlassBackdropDress.Owner() {
            @Override public float glassRadiusPx(int heightPx) {
                return 0f;
            }
            @Override public int seams() {
                // The join with the host above and the screen's edges: no rim anywhere.
                return ChromeEdgeRule.ALL;
            }
            @Override public void dressShape(@NonNull View surface) {
                if (surface.getBackground() != null) surface.setBackground(null);
            }
            @Override public void syncShelf(@Nullable ChromeSpec state) {
            }
        }, SurfaceDirtyLedger.Backdrop.FOOT, R.id.dock_foot_blur_backdrop, false);
    }

    /** Whether the foot's glass is up (dressed and not released). */
    public boolean shown() {
        return mBackdrop.shown();
    }

    /**
     * The frost of the host that stands now, which the foot wears at a rest: the Terminal's own
     * host while it is the slab, else Home's glass host. Null while that host wears none.
     */
    @Nullable
    public ColorFilter standingHostFrost() {
        int id = mHost.stackRenderer().terminalHostStands() ? R.id.terminal_dock_blur_backdrop
            : R.id.accessory_blur_backdrop;
        View backdrop = mHost.findViewById(id);
        return backdrop instanceof ImageView ? ((ImageView) backdrop).getColorFilter() : null;
    }

    /** One rest pass: dresses the foot while its card stands, releases it otherwise. */
    public void update(@NonNull ChromeSpec state) {
        if (mSource.crossing()) return;
        mBlending = false;
        View foot = mHost.findViewById(R.id.dock_foot_host);
        boolean standing = foot != null && foot.getVisibility() == View.VISIBLE
            && state.toolbarShown && mSource.card() != null && !mSource.standsDownOnFrame();
        if (!standing) {
            release();
            return;
        }
        ImageView backdrop = mHost.findViewById(R.id.dock_foot_blur_backdrop);
        if (backdrop == null) return;
        mBackdrop.watch(foot);
        mBackdrop.setShown(true);
        View plate = mHost.findViewById(R.id.dock_foot_background);
        if (plate != null) {
            plate.setBackground(mSource.plate(state.barAlpha));
            plate.setAlpha(1f);
            if (plate.getVisibility() != View.VISIBLE) plate.setVisibility(View.VISIBLE);
        }
        mBackdrop.updateBackdrop(state, foot, backdrop, true);
    }

    /**
     * A crossing between Home and the Terminal begins with the foot up: its frost blends from the
     * host it stands under now to the arriving side's, whose frost both hosts already wear (each
     * is dressed at its own rest, the arriving one as the crossing begins). The steps are built
     * here, once; nothing blends between two Home pages or without both ends.
     */
    public void onCrossingBegin(boolean fromHome, boolean toHome) {
        mBlending = false;
        if (!mBackdrop.shown() || fromHome == toHome) return;
        View home = mHost.findViewById(R.id.accessory_blur_backdrop);
        View terminal = mHost.findViewById(R.id.terminal_dock_blur_backdrop);
        if (!(home instanceof ImageView) || !(terminal instanceof ImageView)) return;
        ColorFilter homeFrost = ((ImageView) home).getColorFilter();
        ColorFilter terminalFrost = ((ImageView) terminal).getColorFilter();
        mFromUp = fromHome ? homeFrost : terminalFrost;
        mToUp = fromHome ? terminalFrost : homeFrost;
        ColorMatrix from = GlassFilters.matrixOf(mFromUp);
        ColorMatrix to = GlassFilters.matrixOf(mToUp);
        if (from == null || to == null) return;
        System.arraycopy(from.getArray(), 0, mFrom, 0, mFrom.length);
        System.arraycopy(to.getArray(), 0, mTo, 0, mTo.length);
        int last = mSteps.length - 1;
        mSteps[0] = mFromUp;
        mSteps[last] = mToUp;
        for (int step = 1; step < last; step++) {
            float share = step / (float) last;
            for (int i = 0; i < mStep.length; i++) mStep[i] = mFrom[i] + (mTo[i] - mFrom[i]) * share;
            mSteps[step] = new ColorMatrixColorFilter(mStep);
        }
        mBlending = true;
    }

    /** The crossing's share (0 at the leaving rest, 1 at the arriving): the step's frost goes up. */
    public void blend(float share) {
        if (!mBlending) return;
        int last = mSteps.length - 1;
        int step = Math.max(0, Math.min(last, Math.round(share * last)));
        ColorFilter filter = mSteps[step];
        ImageView backdrop = mHost.findViewById(R.id.dock_foot_blur_backdrop);
        if (backdrop != null && filter != null && backdrop.getColorFilter() != filter)
            backdrop.setColorFilter(filter);
    }

    /** Lets the foot's glass go: its picture and glass entry, its effect, its plate. */
    public void release() {
        mBlending = false;
        java.util.Arrays.fill(mSteps, null);
        mFromUp = mToUp = null;
        if (!mBackdrop.shown() && !mBackdrop.watching()) return;
        mBackdrop.setShown(false);
        ImageView backdrop = mHost.findViewById(R.id.dock_foot_blur_backdrop);
        if (backdrop != null) mBackdrop.clearBackdrop(backdrop);
        mBackdrop.hide(R.id.dock_foot_background);
    }
}
