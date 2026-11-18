package com.termux.app.statusbar;

import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.CrossfadeDrawable;
import com.termux.app.place.SeamFade;
import com.termux.app.window.FrameGuards;
import com.termux.app.window.FramePipeline;
import com.termux.app.window.FrameStep;

/**
 * The only writer of the top stack's transforms (ADR 0030 section 6), during a crossing and at
 * rest: the bar host's ride, the strip behind the system bar and the window chips' slide, all
 * their {@code translationX}, and the blend of the two glass views' {@link CrossfadeDrawable}s
 * with the strip glass's presence. Each owner hands its share in (the crossing's two ends as
 * {@link TopStackScene.End}s, the wall's progress, the ride), which only records it and asks the
 * frame pipeline for its {@link FrameStep#TOP_STACK} step; the step ({@link #apply}) asks
 * {@link TopStackScene} for the frame and writes only what differs from what the views hold. The
 * crossfades themselves are built and settled by the activity (TopBandFade); this writes their
 * share. Without a pipeline (a test, before the window) a share is written at once.
 *
 * <p>The ink is written too: {@link TopStackScene.Frame#inks} goes to the {@link Inks} sink
 * whenever it differs from what the sink was last given, so a crossing writes the bar's content
 * its blended colours and a rest writes nothing. A crossing measures nothing (ADR 0029 section
 * 9): both ends' inks are resolved once, at begin, by the activity. Nothing allocates per frame.
 */
public final class TopStackRenderer {

    /** What the renderer reads off the activity. */
    public interface Views {
        /** The window's view of {@code id}; null while it has none. */
        @Nullable View view(int id);
    }

    /** Where the frame's blended inks go: the bar's content, owned by {@link StatusBarWidgets}. */
    public interface Inks {
        /** Writes {@code inks} (a {@link StatusInkSlots} layout) to the content; reads it only for the call. */
        void apply(@NonNull int[] inks);
    }

    private static final String[] OWNED = {"terminal_window_bar_host.translationX",
        "terminal_status_bar_background.translationX", "terminal_status_place_content.translationX"};

    @NonNull private final Views mViews;
    @Nullable private FramePipeline mFrames;
    @Nullable private FrameGuards.Writer mGuard;
    /** The next apply is a crossing's landing frame, which should write nothing new. */
    private boolean mLandingPending;

    private final TopStackScene.End mFrom = new TopStackScene.End();
    private final TopStackScene.End mTo = new TopStackScene.End();
    private final TopStackScene.End mRest = new TopStackScene.End();
    private final TopStackScene.Frame mFrame = new TopStackScene.Frame();
    private boolean mCrossing;
    private boolean mStripHereShown;
    private float mProgress;
    /** The ride's x, NaN when the bar does not ride a page; and whether it wears the other end. */
    private float mRideX = Float.NaN;
    private boolean mRideWearsTo;
    private int mWidth;
    private float mGlassShare;
    @Nullable private Inks mInks;
    /** What the sink holds now; {@link #mInksKnown} once it holds anything this writer knows. */
    private final int[] mAppliedInks = new int[TopStackScene.INK_COUNT];
    private boolean mInksKnown;
    /** The number of view writes made so far: a test's and a trace's count. */
    int mWriteCount;

    public TopStackRenderer(@NonNull Views views) {
        mViews = views;
    }

    /**
     * Joins {@code frames}: its {@link FrameStep#TOP_STACK} step writes from here on, and the
     * properties this writer owns register with its guards. Once per pipeline.
     */
    public void attach(@NonNull FramePipeline frames) {
        if (mFrames == frames) return;
        mFrames = frames;
        frames.bind(FrameStep.TOP_STACK, this::apply);
        FrameGuards.Writer guard = frames.guards().writer("top");
        int[] ids = {R.id.terminal_window_bar_host, R.id.terminal_status_bar_background,
            R.id.terminal_status_place_content};
        for (int i = 0; i < ids.length; i++) {
            final int id = ids[i];
            guard.own(OWNED[i], () -> {
                View view = mViews.view(id);
                return view == null ? 0f : view.getTranslationX();
            });
        }
        mGuard = guard;
    }

    /** The bar's content, which the frame's inks are written to. */
    public void setInks(@Nullable Inks inks) {
        mInks = inks;
    }

    /**
     * The rest's inks, measured and already written to the content by their owner (the bar's own
     * sync at rest): the renderer takes them as what the content wears, and as the rest's, so a
     * landing or a later frame writes only a difference.
     */
    public void restInks(@NonNull int[] inks) {
        System.arraycopy(inks, 0, mRest.inks, 0, TopStackScene.INK_COUNT);
        mRest.inksResolved = true;
        System.arraycopy(inks, 0, mAppliedInks, 0, TopStackScene.INK_COUNT);
        mInksKnown = true;
    }

    /** A crossing is under way: the renderer, not the bar's sync, owns the content's inks. */
    public boolean crossing() {
        return mCrossing;
    }

    /** A share the renderer does not own moved (a crossfade was built): the step writes it on this frame. */
    public void requestApply() {
        FramePipeline frames = mFrames;
        if (frames != null) frames.requestFrame(FrameStep.TOP_STACK);
        else apply();
    }

    // ---- The shares handed in ---------------------------------------------------------------

    /**
     * A crossing begins between {@code from}, the committed end, and {@code to}; the bar does not
     * ride and the progress is 0 until they are set. Another crossing begun while one runs
     * replaces it. {@code stripHereShown}: the leaving end shows the strip's glass.
     */
    public void begin(@NonNull TopStackScene.End from, @NonNull TopStackScene.End to,
                      boolean stripHereShown) {
        mFrom.set(from);
        mTo.set(to);
        mStripHereShown = stripHereShown;
        mCrossing = true;
        mLandingPending = false;
        mProgress = 0f;
        mRideX = Float.NaN;
        mRideWearsTo = false;
        requestApply();
    }

    /** The wall's progress from the committed end toward the other, 0 to 1. */
    public void setProgress(float p) {
        if (mProgress == p) return;
        mProgress = p;
        requestApply();
    }

    /** The ride: {@code rideXPx} the bar's x while it rides a page, NaN while it does not. */
    public void setRide(float rideXPx, boolean rideWearsTo) {
        boolean same = Float.isNaN(rideXPx) ? Float.isNaN(mRideX) : rideXPx == mRideX;
        if (same && mRideWearsTo == rideWearsTo) return;
        mRideX = rideXPx;
        mRideWearsTo = rideWearsTo;
        requestApply();
    }

    /** The wall's width, which the chips slide a whole of. */
    public void setWidth(int widthPx) {
        if (mWidth == widthPx) return;
        mWidth = widthPx;
        requestApply();
    }

    /**
     * The crossing landed: it ends on whichever end the last frame stood nearer, so the next apply
     * is that end's rest and writes nothing new; the activity's {@link #rest} then names the page
     * it settled on.
     */
    public void onLanding() {
        if (mCrossing) mRest.set(mProgress >= 0.5f ? mTo : mFrom);
        mCrossing = false;
        mRideX = Float.NaN;
        mLandingPending = true;
        requestApply();
    }

    /** The crossing is dropped without a landing: the bar stands the end it began on. */
    public void cancel() {
        if (mCrossing) mRest.set(mFrom);
        mCrossing = false;
        mRideX = Float.NaN;
        requestApply();
    }

    /**
     * The front page's rest: what a frame with no crossing stands. Called wherever the rest may
     * have moved (the wall moved or was laid out, the chips or the place bar were synced); it
     * does not end a crossing.
     */
    public void rest(@NonNull TopStackScene.End front) {
        // The front's inks come with it only when it was measured; else the rest keeps its own.
        if (front.inksResolved) mRest.set(front);
        else mRest.setShape(front);
        requestApply();
    }

    /** The glass share the last frame wrote: where a crossfade built now begins. */
    public float glassShare() {
        return mGlassShare;
    }

    // ---- The frame ---------------------------------------------------------------------------

    /** Asks the scene for this frame and writes what changed. */
    public void apply() {
        FrameGuards.Writer guard = mGuard;
        boolean landing = mLandingPending;
        mLandingPending = false;
        if (guard != null) guard.check();
        boolean riding = mCrossing && !Float.isNaN(mRideX) && !TopStackScene.shared(mFrom, mTo);
        if (mCrossing) {
            TopStackScene.at(mFrom, mTo, mProgress, mWidth, mRideX, mRideWearsTo, mFrame);
        } else {
            TopStackScene.rest(mRest, mWidth, mFrame);
        }
        writeTx(mViews.view(R.id.terminal_window_bar_host), mFrame.slabTx);
        writeTx(mViews.view(R.id.terminal_status_bar_background), mFrame.stripTx);
        writeTx(mViews.view(R.id.terminal_status_place_content), mFrame.chipsTx);
        writeInks(riding);
        if (mCrossing) {
            mGlassShare = riding ? (mRideWearsTo ? 1f : 0f) : mFrame.lookShare;
            writeGlass();
        }
        if (guard != null) {
            FrameGuards guards = mFrames.guards();
            if (landing) guards.setLanding(true);
            for (int slot = 0; slot < OWNED.length; slot++) guard.wrote(slot);
            if (landing) guards.setLanding(false);
        }
    }

    /**
     * Hands the frame's inks to the sink when they differ from what it holds. A frame whose ends
     * were never measured has none to give; a riding bar wears one end's.
     */
    private void writeInks(boolean riding) {
        Inks sink = mInks;
        if (sink == null) return;
        boolean known = !mCrossing ? mRest.inksResolved
            : riding ? (mRideWearsTo ? mTo.inksResolved : mFrom.inksResolved)
            : mFrom.inksResolved && mTo.inksResolved;
        if (!known) return;
        if (mInksKnown && java.util.Arrays.equals(mFrame.inks, mAppliedInks)) return;
        System.arraycopy(mFrame.inks, 0, mAppliedInks, 0, TopStackScene.INK_COUNT);
        mInksKnown = true;
        sink.apply(mAppliedInks);
        mWriteCount++;
    }

    private void writeTx(@Nullable View view, float x) {
        if (view == null || view.getTranslationX() == x) return;
        view.setTranslationX(x);
        mWriteCount++;
    }

    /**
     * The two glass views' blend at the share, built by the activity once per crossing. A strip
     * the leaving end did not show comes in by its alpha with the share and goes the same way on
     * a cancel; one it showed stays whole while its glass crosses.
     */
    private void writeGlass() {
        View strip = mViews.view(R.id.terminal_status_bar_glass_surface);
        if (strip != null && shareOf(strip)) {
            float alpha = SeamFade.presence(mStripHereShown, true, mGlassShare);
            if (strip.getAlpha() != alpha) {
                strip.setAlpha(alpha);
                mWriteCount++;
            }
        }
        shareOf(mViews.view(R.id.terminal_window_bar_background));
    }

    /** Writes the share into {@code view}'s blend; whether it wears one. */
    private boolean shareOf(@Nullable View view) {
        Drawable background = view == null ? null : view.getBackground();
        if (!(background instanceof CrossfadeDrawable)) return false;
        if (((CrossfadeDrawable) background).setShare(mGlassShare)) mWriteCount++;
        return true;
    }
}
