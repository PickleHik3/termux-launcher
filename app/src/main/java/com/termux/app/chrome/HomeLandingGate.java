package com.termux.app.chrome;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.scene.SceneLayerView;
import com.termux.app.window.FirstLayout;
import com.termux.app.window.WindowLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Holds a home press's landing behind one frame with no pages ({@link HomeExitPolicy#holdsLanding}).
 * The pages, the chrome and every ground view but the wallpaper go to a transition alpha of 0; the
 * window draws that frame (the wallpaper alone); then the page settles, and the hidden views come
 * back in the pre-draw of the first frame after the settle with no layout pending, so Home's first
 * frame is drawn whole.
 *
 * <p>A gate on drawing can leave a blank home screen, so each wait has a way out: the settle runs
 * {@link #HIDDEN_FRAME_BACKSTOP_MS} after the hide if the hidden frame never draws; the views come
 * back after {@link #REVEAL_FRAMES} frames past the settle whatever is pending, or
 * {@link #REVEAL_BACKSTOP_MS} after it with no frame at all; a settle that throws shows them on the
 * way out; {@link #finishNow} settles and shows at once (a stop, a pause, a rotation, a second
 * press); and a missing or detached window never hides anything.
 *
 * <p>Transition alpha, not visibility or alpha: a visibility change reaches every SurfaceView and
 * accessibility below, and alpha belongs to Overview's layers ({@code LayersUnderOverview}) and the
 * ground's own owners, so this never overwrites what they set. Nothing here is per frame at rest.
 */
public final class HomeLandingGate {

    /** Longest the settle waits for the frame with no pages to draw ({@code FirstDrawGate}'s wait). */
    public static final long HIDDEN_FRAME_BACKSTOP_MS = 100L;
    /** Longest the views stay hidden once the page has settled: the keyboard reveal's backstop. */
    public static final long REVEAL_BACKSTOP_MS = 160L;
    /** Frames past the settle the views wait for one with no layout pending before they show. */
    public static final int REVEAL_FRAMES = 3;

    /** How the gate ended ({@link #lastEnd}). */
    public static final String BY_FRAME = "frame";
    public static final String BY_FRAMES = "frames";
    public static final String BY_BACKSTOP = "backstop";
    public static final String BY_FINISH = "finish";
    public static final String BY_EXCEPTION = "exception";
    public static final String BY_DETACHED = "detached";

    private static final int IDLE = 0;
    /** The views are hidden and the hidden frame has not drawn yet. */
    private static final int HIDDEN = 1;
    /** The settle is running. */
    private static final int SETTLING = 2;
    /** The page has settled; the views wait for its first frame. */
    private static final int SETTLED = 3;

    private int mPhase = IDLE;
    @Nullable private View mRoot;
    @Nullable private Runnable mSettle;
    /** The views hidden, and the transition alpha each had: given back even if one left its parent. */
    private final ArrayList<View> mHidden = new ArrayList<>();
    private float[] mHiddenAlpha = new float[8];
    @Nullable private FirstLayout.Handle mDrawWait;
    @Nullable private FirstLayout.Handle mRevealWait;
    private int mFrames;
    @Nullable private String mSettledBy;
    @Nullable private String mLastEnd;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mSettleAfterHiddenFrame = () -> settle(BY_FRAME);
    private final Runnable mHiddenBackstop = () -> settle(BY_BACKSTOP);
    private final Runnable mRevealBackstop = () -> reveal(BY_BACKSTOP);
    /** Inside the hidden frame's draw: the settle goes on the loop's next turn, after the frame. */
    private final Runnable mOnHiddenDrawn = this::onHiddenFrameDrawn;
    private final BooleanSupplier mRevealFrame = this::onRevealFrame;

    public boolean isRunning() {
        return mPhase != IDLE;
    }

    /** How the last gate ended, or null while none has. */
    @Nullable
    public String lastEnd() {
        return mLastEnd;
    }

    /** What let the last settle run: {@link #BY_FRAME}, {@link #BY_BACKSTOP} or {@link #BY_FINISH}. */
    @Nullable
    public String settledBy() {
        return mSettledBy;
    }

    /**
     * Hides the pages and chrome of {@code root}'s window and runs {@code settle} once that frame
     * has drawn. False, with nothing hidden and nothing run, when there is no window to hold: the
     * caller settles at once.
     */
    public boolean begin(@Nullable ViewGroup root, @NonNull Runnable settle) {
        if (mPhase != IDLE) finishNow();
        if (root == null || !root.isAttachedToWindow() || !root.getViewTreeObserver().isAlive()) {
            return false;
        }
        collectLayers(root, mHidden);
        if (mHidden.isEmpty()) return false;
        int count = mHidden.size();
        if (mHiddenAlpha.length < count) mHiddenAlpha = new float[count];
        for (int i = 0; i < count; i++) {
            View view = mHidden.get(i);
            mHiddenAlpha[i] = view.getTransitionAlpha();
            view.setTransitionAlpha(0f);
        }
        mRoot = root;
        mSettle = settle;
        mSettledBy = null;
        mLastEnd = null;
        mPhase = HIDDEN;
        mDrawWait = FirstLayout.afterNextDraw(root, mOnHiddenDrawn);
        mHandler.postDelayed(mHiddenBackstop, HIDDEN_FRAME_BACKSTOP_MS);
        return true;
    }

    /** Settles now if the page has not yet, and shows the views. */
    public void finishNow() {
        if (mPhase == HIDDEN) settle(BY_FINISH);
        reveal(BY_FINISH);
    }

    /** The hidden frame drew (inside its draw): the settle runs after it. */
    void onHiddenFrameDrawn() {
        if (mPhase != HIDDEN) return;
        mHandler.removeCallbacks(mSettleAfterHiddenFrame);
        mHandler.post(mSettleAfterHiddenFrame);
    }

    /** A pre-draw past the settle: true once the views are shown. */
    boolean onRevealFrame() {
        if (mPhase != SETTLED) return true;
        mFrames++;
        View root = mRoot;
        if (root != null && !root.isLayoutRequested()) {
            reveal(BY_FRAME);
            return true;
        }
        if (mFrames >= REVEAL_FRAMES) {
            reveal(BY_FRAMES);
            return true;
        }
        return false;
    }

    private void settle(@NonNull String by) {
        if (mPhase != HIDDEN) return;
        cancelHiddenWait();
        mPhase = SETTLING;
        mSettledBy = by;
        Runnable settle = mSettle;
        mSettle = null;
        boolean done = false;
        try {
            if (settle != null) settle.run();
            done = true;
        } finally {
            if (!done) reveal(BY_EXCEPTION);
        }
        // Finished from inside the settle (a stop, an overlay opening): already shown.
        if (mPhase != SETTLING) return;
        mPhase = SETTLED;
        mFrames = 0;
        View root = mRoot;
        if (root == null || !root.isAttachedToWindow() || !root.getViewTreeObserver().isAlive()) {
            reveal(BY_DETACHED);
            return;
        }
        mRevealWait = FirstLayout.beforeEachDrawUntil(root, mRevealFrame);
        mHandler.postDelayed(mRevealBackstop, REVEAL_BACKSTOP_MS);
    }

    private void reveal(@NonNull String by) {
        if (mPhase == IDLE) return;
        mPhase = IDLE;
        mLastEnd = by;
        cancelHiddenWait();
        mHandler.removeCallbacks(mRevealBackstop);
        FirstLayout.Handle revealWait = mRevealWait;
        mRevealWait = null;
        if (revealWait != null) revealWait.cancel();
        for (int i = 0, n = mHidden.size(); i < n; i++) {
            mHidden.get(i).setTransitionAlpha(mHiddenAlpha[i]);
        }
        mHidden.clear();
        mRoot = null;
        mSettle = null;
    }

    private void cancelHiddenWait() {
        mHandler.removeCallbacks(mHiddenBackstop);
        mHandler.removeCallbacks(mSettleAfterHiddenFrame);
        FirstLayout.Handle drawWait = mDrawWait;
        mDrawWait = null;
        if (drawWait != null) drawWait.cancel();
    }

    /**
     * The views a landing hides in {@code root}'s window: the pages layer, the chrome layer and
     * every view of the ground but the wallpaper (the launcher's own picture and a scene layer).
     * The sheets, editor and notices are left: a press that lands on Home has nothing open there.
     */
    static void collectLayers(@NonNull View root, @NonNull List<View> out) {
        out.clear();
        View ground = root.findViewById(WindowLayer.GROUND.hostId());
        if (ground instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) ground;
            for (int i = 0, n = group.getChildCount(); i < n; i++) {
                View child = group.getChildAt(i);
                if (child == null || isWallpaper(child)) continue;
                out.add(child);
            }
        }
        View pages = root.findViewById(WindowLayer.PAGES.hostId());
        if (pages != null) out.add(pages);
        View chrome = root.findViewById(WindowLayer.CHROME.hostId());
        if (chrome != null) out.add(chrome);
    }

    private static boolean isWallpaper(@NonNull View view) {
        return view instanceof WallpaperBackdropView || view instanceof SceneLayerView;
    }
}
