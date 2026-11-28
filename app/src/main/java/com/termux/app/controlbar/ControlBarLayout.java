package com.termux.app.controlbar;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Lays the control bar out in the pages host for its form (#427): a row along the bottom at its
 * rest's answer ({@link ControlBarPlacement#settledBottomPx}), or a rail down one side
 * ({@code bar.rail_side}) clear of the camera and Android's buttons on that edge; the Workspaces end
 * above the row or beside the rail. Each layout parameter is set only when it changed, never from
 * inside a layout pass. On every frame the stack is drawn, {@link #ride} moves a row by the stack's
 * own translation ({@link ControlBarPlacement#rideTyPx}), so it rides on the keyboard with nothing
 * laid out.
 */
public final class ControlBarLayout {

    /** What the layout reads from the launcher; at most a handful of methods. */
    public interface Host {
        /** The band at the screen's bottom the pages keep clear: Android's navigation, unless a keyboard covers it. */
        int keptBottomPx();

        /**
         * How far Android's own bottom gesture reaches up the screen (its mandatory gesture inset),
         * which no app can claim: the full-screen handle stands above it. 0 with buttons.
         */
        default int gestureBottomPx() {
            return 0;
        }

        /** The launcher keyboard's keys' height while it stands laid out but down, else 0. */
        int keyboardDownLaidOutPx();

        /** Whether the launcher's keyboard is up. */
        boolean launcherKeyboardShown();

        /** Whether the phone's own keyboard is up. */
        boolean phoneKeyboardShown();

        /** Whether the launcher is used as a terminal (no Home). */
        boolean terminalMode();

        @NonNull ControlBarPlacement.RailSide railSide();

        /** The camera's keep-out on an edge whose cutout reaches {@code cutoutPx} in ({@code cutout.*}). */
        int cutoutClearPx(int cutoutPx);

        /** The bar's margin in dp ({@code bar.margin}): its air on the sides and below it. */
        int marginDp();

        /** Home's and the wall's pages end {@code leftPx} and {@code rightPx} in from the sides: beside a rail. */
        void pagesBesideBar(int leftPx, int rightPx);

        /** Runs {@code work} later on the main thread. */
        void post(@NonNull Runnable work);

        /** What the stack under the bar draws and who holds it ({@link Stack}); none by default. */
        @NonNull
        default Stack stack() {
            return NO_STACK;
        }

        /**
         * The bar was laid out again or took another form: what ends at its top (the drawer sheet)
         * reads it again.
         */
        default void barMoved() {}

        /**
         * The bar's drawn top moved ({@link #drawnTopAbovePx}): by a ride, or by a layout landing
         * after the frame that read it. What ends at its top (the drawer sheet) follows it on the
         * same frame (round 6: the sheet stood over the bar for a frame of the drawer's settle).
         */
        default void barRode() {}
    }

    /**
     * The accessory stack as the bar reads it on every frame it rides: what it draws, at rest and
     * as drawn, and whether another surface holds it. A part of {@link Host}.
     */
    public interface Stack {
        /**
         * How far the stack's laid-out top stands above the top of what it draws
         * ({@link ControlBarPlacement#emptyTopPx}): room the stack keeps for rows it does not show
         * (v1's lanes and cards, which stand down under the bar). The bar stands on what is drawn,
         * the keyboard or the rows over it, never on that room (pong, 11-28: a bar-tall gap over
         * the keyboard). 0 when the stack draws from its top. As drawn on this frame: it moves the
         * bar's ride only, never its layout ({@link #restingEmptyTopPx}).
         */
        default int emptyTopPx() {
            return 0;
        }

        /**
         * {@link #emptyTopPx} at the stack's rest with the keyboard up or down
         * ({@code keyboardUp}), translations left out:
         * what the bar and the pages are laid out against, so a reveal, whose rows and keyboard
         * move each frame, lays nothing out (review P2, 11-28).
         */
        default int restingEmptyTopPx(boolean keyboardUp) {
            return emptyTopPx();
        }

        /**
         * The launcher keyboard's keys' height, up or down: what its down rest sinks the stack by;
         * negative for the host's {@link Host#keyboardDownLaidOutPx}.
         */
        default int keyboardKeysPx() {
            return -1;
        }

        /**
         * Whether another surface holds the stack's transforms (the app drawer, an editor): its
         * writer draws nothing new until it lets go, so the bar stands at the stack's rest for the
         * keyboard now ({@link Host#keyboardDownLaidOutPx}), not on the last frame it drew (pong, 11-28:
         * the drawer scrolled the keyboard down and the bar stayed where the keyboard had been).
         */
        default boolean held() {
            return false;
        }

        /**
         * The stack's translation the bar rides on while it is held ({@link #held}), given its
         * rest's ({@code restPx}): the surface holding it may move the keyboard itself (the
         * drawer's scroll slides it away), and the bar rides on where that surface draws it.
         */
        default float heldTranslationPx(float restPx) {
            return restPx;
        }
    }

    /** A stack that draws from its top and is never held. */
    private static final Stack NO_STACK = new Stack() { };

    @NonNull private final ControlBar mBar;
    @NonNull private final View mHost;
    @NonNull private final View mStack;
    @Nullable private final View mWorkspaces;
    @NonNull private final Host mLauncher;
    @NonNull private final int[] mLocation = new int[2];
    @NonNull private final Runnable mSync = this::sync;
    /** Whether the launcher's keyboard was up when the form was last read. */
    private boolean mTypingSeen;
    private boolean mSyncPosted;
    /** A window is full screen (#426): the Workspaces reach over the bar's room. */
    private boolean mFullScreen;
    /** The stack's translation on the last frame it was drawn: what the bar rides on. */
    private float mStackTranslationPx;
    /** The bar's last ride, px from where it is laid out. */
    private float mRideTyPx;
    /** How far edit mode lifts the bar above its strip, px; 0 at rest. */
    private int mEditLiftPx;

    /**
     * @param host the pages host (a RelativeLayout) the bar, the stack and the Workspaces stand in
     * @param stack the accessory stack the bar stands on
     * @param workspaces the tiling desktop's Workspaces, laid out against the bar
     */
    public ControlBarLayout(@NonNull ControlBar bar, @NonNull View host, @NonNull View stack,
                            @Nullable View workspaces, @NonNull Host launcher) {
        mBar = bar;
        mHost = host;
        mStack = stack;
        mWorkspaces = workspaces;
        mLauncher = launcher;
        // The ride is measured from where the bar, the stack and the host are laid out: a layout
        // that moves any of them (the bar laid out for a new rest, after its ride already carried
        // it there) rides again from the new place, so the stack's translation is counted once.
        View.OnLayoutChangeListener relaid = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (t != ot || b != ob) rideAgain();
        };
        bar.view().addOnLayoutChangeListener(relaid);
        stack.addOnLayoutChangeListener(relaid);
        // Home's A–Z joining the bar (or leaving it) changes its thickness: laid out again.
        bar.setAzRowListener(this::post);
        host.addOnLayoutChangeListener(relaid);
        // A lifted bar's clip follows the Workspaces' size (a rotation while edit mode is open).
        if (workspaces != null) {
            workspaces.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (mEditLiftPx > 0 && (r - l != or - ol || b - t != ob - ot)) clipWorkspaces();
            });
        }
    }

    /**
     * The bar's first layout parameters, for adding it to the host; the Workspaces are laid out
     * for the same form at once ({@link #syncWorkspaces}, as {@link #sync} does), so a start in
     * landscape has no frame of Workspaces above a rail.
     */
    @NonNull
    public RelativeLayout.LayoutParams firstParams() {
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mBar.setForm(formNow());
        apply(params, mBar.form());
        mBar.setFlush(!mBar.form().isRail() && ControlBarPlacement.flush(mLauncher.marginDp()));
        syncWorkspaces(mBar.form());
        return params;
    }

    /** The bar's shape now: a rail in landscape, one row while typing or under the Terminal mode. */
    @NonNull
    ControlBarForm formNow() {
        boolean landscape = mHost.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE;
        mTypingSeen = mLauncher.launcherKeyboardShown();
        return ControlBarForm.of(landscape, mTypingSeen || mLauncher.phoneKeyboardShown(),
            mLauncher.terminalMode());
    }

    /**
     * A window is full screen, or is not (#426): the Workspaces stand over the room the bar had
     * (down to the stack), and the handle stands where the bar's bottom was.
     */
    public void setFullScreen(boolean fullScreen) {
        if (mFullScreen == fullScreen) return;
        mFullScreen = fullScreen;
        // At once, in the pass that asked: the handle and the Workspaces' room change together.
        syncWorkspaces(mBar.form());
        syncHandle();
    }

    /** The handle's layout parameters, for adding it to the host: at the bar's bottom, centred. */
    @NonNull
    public RelativeLayout.LayoutParams handleParams() {
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            px(FullScreenHandle.BAND_WIDTH_DP), px(FullScreenHandle.BAND_HEIGHT_DP));
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.bottomMargin = handleBottomPx();
        return params;
    }

    /** The handle's foot: the bar's bottom, kept above Android's bottom gesture. */
    private int handleBottomPx() {
        return FullScreenHandle.bottomClearOfGesturesPx(settledBottomPx(marginPx()), mLauncher.gestureBottomPx());
    }

    /** Moves the handle to the bar's bottom where it is not; only what changed. */
    private void syncHandle() {
        View handle = mBar.handleView();
        if (!(handle.getLayoutParams() instanceof RelativeLayout.LayoutParams)) return;
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) handle.getLayoutParams();
        int bottom = handleBottomPx();
        if (params.bottomMargin == bottom) return;
        params.bottomMargin = bottom;
        handle.setLayoutParams(params);
    }

    /** Asks for {@link #sync} after this frame, once. */
    public void post() {
        if (mSyncPosted) return;
        mSyncPosted = true;
        mLauncher.post(mSync);
    }

    /**
     * Edit mode lifts the bar {@code px} above its strip (round 3), 0 puts it back. The bar moves
     * by its translation and draws in front of the Workspaces (it is added after them); the
     * Workspaces stop drawing at the lifted bar's top ({@link ControlBarPlacement#liftedClipBottomPx}),
     * so no window shows through its glass. Nothing is laid out again: the windows keep their size.
     */
    public void setEditLift(int px) {
        int lift = Math.max(0, px);
        mBar.setEditLift(lift);
        if (lift == mEditLiftPx) return;
        mEditLiftPx = lift;
        clipWorkspaces();
    }

    /** The Workspaces' clip for the lift now; set only when it changes. */
    private void clipWorkspaces() {
        View workspaces = mWorkspaces;
        if (workspaces == null) return;
        int bottom = ControlBarPlacement.liftedClipBottomPx(workspaces.getHeight(), mEditLiftPx);
        android.graphics.Rect now = workspaces.getClipBounds();
        if (bottom < 0) {
            if (now != null) workspaces.setClipBounds(null);
            return;
        }
        if (now != null && now.left == 0 && now.top == 0 && now.right == workspaces.getWidth()
            && now.bottom == bottom) return;
        workspaces.setClipBounds(new android.graphics.Rect(0, 0, workspaces.getWidth(), bottom));
    }

    /** Lays the bar, the Workspaces and the pages out for the form now; only what changed. */
    public void sync() {
        mSyncPosted = false;
        if (!(mBar.view().getLayoutParams() instanceof RelativeLayout.LayoutParams)) return;
        ControlBarForm before = mBar.form();
        ControlBarForm form = formNow();
        mBar.setForm(form);
        View bar = mBar.view();
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) bar.getLayoutParams();
        boolean moved = apply(params, form);
        if (moved) bar.setLayoutParams(params);
        mBar.setFlush(!form.isRail() && ControlBarPlacement.flush(mLauncher.marginDp()));
        syncWorkspaces(form);
        syncHandle();
        int inset = 0;
        if (form.isRail()) inset = Math.max(params.leftMargin, params.rightMargin) + px(
            ControlBarPlacement.thicknessDp(form));
        boolean left = form.isRail() && mLauncher.railSide() == ControlBarPlacement.RailSide.LEFT;
        // Full screen is a Workspace's: Home's pages keep what the bar keeps.
        mLauncher.pagesBesideBar(left ? inset : 0, left ? 0 : inset);
        if (!form.ridesKeyboard()) {
            mRideTyPx = 0f;
            mBar.ride(0f);
        }
        // Held, the stack draws no frame to ride on: the bar stands at the rest it was laid out for.
        else if (mLauncher.stack().held()) rideAgain();
        if (moved || before != form) mLauncher.barMoved();
    }

    /**
     * The stack was drawn {@code translationPx} from where it is laid out: a row rides on it. A
     * change of the launcher's keyboard, or a rest the bar is not laid out for yet, lays the bar
     * out after this frame; the ride draws it right meanwhile.
     */
    public void ride(float translationPx) {
        mStackTranslationPx = translationPx;
        if (mLauncher.launcherKeyboardShown() != mTypingSeen) {
            mTypingSeen = mLauncher.launcherKeyboardShown();
            post();
        }
        View bar = mBar.view();
        if (!mBar.form().ridesKeyboard() || bar.getHeight() <= 0) {
            mRideTyPx = 0f;
            mBar.ride(0f);
            return;
        }
        int gap = marginPx();
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams
                && ((ViewGroup.MarginLayoutParams) params).bottomMargin != settledBottomPx(gap)) {
            post();
        }
        // Laid out at the rest; what the frame draws (its rows' and keyboard's tops) is a ride.
        int drawnStack = stackDrawnPx(false);
        float drawn = translationPx;
        if (mLauncher.stack().held()) {
            // Held by another surface, the stack stands where that surface draws the keyboard
            // (else where its writer will draw it at rest), never below the keyboard-down rest
            // the layout lands on when the keyboard goes down for real.
            float held = ControlBarPlacement.drawnStackPx(drawnStack,
                mLauncher.stack().heldTranslationPx(restTranslationPx()));
            float floor = ControlBarPlacement.drawnStackPx(stackRestPx(false),
                heldKeysPx());
            drawn = drawnStack - Math.max(held, floor);
        }
        mRideTyPx = ControlBarPlacement.rideTyPx(hostBottom() - bar.getBottom(),
            mLauncher.keptBottomPx(), drawnStack, drawn, gap);
        mBar.ride(mRideTyPx);
        int top = drawnTopAbovePx();
        if (top != mToldTopAbovePx) {
            mToldTopAbovePx = top;
            mLauncher.barRode();
        }
    }

    /** The drawn top last told ({@link Host#barRode}); MIN_VALUE before the first. */
    private int mToldTopAbovePx = Integer.MIN_VALUE;

    /**
     * The bar rides again on what the stack shows now: a surface holding the stack moved the
     * keyboard ({@link Stack#heldTranslationPx}). Translation only.
     */
    public void rideNow() {
        rideAgain();
    }

    /**
     * How far the bar's drawn top stands above the host's bottom on this frame: where it is laid
     * out now less its ride from there, both as they are, never a rest the layout has not reached
     * yet. What ends at the bar's top follows it through a motion. Round 6: read as the new rest
     * ({@link #topAbovePx}, a form and margin already set) less a ride measured from the old
     * layout, it put the drawer's bottom a keyboard's height into the bar while the keyboard went
     * down under the drawer. Before the bar is laid out, its rest's.
     */
    public int drawnTopAbovePx() {
        View bar = mBar.view();
        if (!mBar.form().ridesKeyboard() || bar.getHeight() <= 0) return topAbovePx();
        return Math.round(hostBottom() - bar.getTop() - mRideTyPx);
    }

    /** The bar, the stack or the host was laid out again: the bar rides the stack's last frame from there. */
    private void rideAgain() {
        if (mBar.view().getHeight() <= 0) return;
        ride(mStackTranslationPx);
    }

    /**
     * How far the bar's top stands above the host's bottom at the stack's rest: a row's settled
     * bottom and its thickness; beside a rail, its settled bottom (the stack's top and the gap).
     */
    public int topAbovePx() {
        int bottom = settledBottomPx(marginPx());
        ControlBarForm form = mBar.form();
        return form.isRail() ? bottom : bottom + thicknessPx(form);
    }

    /**
     * How far the bar's top stands above the screen's bottom on a rest whose stack reserves
     * {@code reservedStackPx} (Home's grid and every wall page end at least this far up): the
     * same drawn stack the bar stands on, the stack's room above what it draws taken off
     * ({@link Stack#emptyTopPx}), at the form's thickness. One owner of the number the bar and
     * the pages read (review P2: Home kept the room the bar no longer stood on).
     */
    public int pageTopInsetPx(int reservedStackPx) {
        ControlBarForm form = mBar.form();
        return ControlBarPlacement.topInsetPx(mLauncher.keptBottomPx(),
            drawnOf(reservedStackPx, true), marginPx(), thicknessPx(form));
    }

    /**
     * {@code stackPx} less the room the stack keeps above what it draws: at its rest
     * ({@code resting}, for layout) or as drawn on this frame (for the ride).
     */
    private int drawnOf(int stackPx, boolean resting) {
        return drawnOf(stackPx, resting ? mLauncher.stack().restingEmptyTopPx(
            mLauncher.launcherKeyboardShown()) : mLauncher.stack().emptyTopPx());
    }

    private static int drawnOf(int stackPx, int roomPx) {
        int stack = Math.max(0, stackPx);
        return stack - Math.min(stack, Math.max(0, roomPx));
    }

    /** The host's bottom inside its padding, where the bar's bottom margin counts from. */
    private int hostBottom() {
        return mHost.getHeight() - mHost.getPaddingBottom();
    }

    /**
     * How tall what the stack draws is laid out above the host's bottom: from the top of its shown
     * content, not of the room it keeps above it ({@link Stack#emptyTopPx}), at its rest or as
     * drawn now; 0 while it is away.
     */
    private int stackDrawnPx(boolean resting) {
        if (mStack.getVisibility() != View.VISIBLE || mStack.getHeight() <= 0) return 0;
        return drawnOf(hostBottom() - mStack.getTop(), resting);
    }

    /** {@link #stackDrawnPx} at the stack's rest with the keyboard up or down ({@code keyboardUp}). */
    private int stackRestPx(boolean keyboardUp) {
        if (mStack.getVisibility() != View.VISIBLE || mStack.getHeight() <= 0) return 0;
        return drawnOf(hostBottom() - mStack.getTop(), mLauncher.stack().restingEmptyTopPx(keyboardUp));
    }

    /**
     * The bar's bottom margin at the stack's rest: its translation there is the down keyboard's
     * share, the number the stack's writer draws at that rest.
     */
    private int settledBottomPx(int gapPx) {
        return ControlBarPlacement.settledBottomPx(mLauncher.keptBottomPx(), stackDrawnPx(true),
            restTranslationPx(), gapPx);
    }

    /** The keyboard's keys' height the down rest sinks the stack by ({@link Stack#keyboardKeysPx}). */
    private int heldKeysPx() {
        int keys = mLauncher.stack().keyboardKeysPx();
        return keys >= 0 ? keys : mLauncher.keyboardDownLaidOutPx();
    }

    /** The stack's translation at its rest now: the down keyboard's share, else 0. */
    private float restTranslationPx() {
        int down = mLauncher.launcherKeyboardShown() ? 0 : mLauncher.keyboardDownLaidOutPx();
        return com.termux.app.place.StackScene.keyboardTranslationPx(0f, down);
    }

    /** Writes the bar's layout for {@code form}; whether anything changed. */
    private boolean apply(@NonNull RelativeLayout.LayoutParams params, @NonNull ControlBarForm form) {
        int side = marginPx();
        int thickness = thicknessPx(form);
        int bottom = settledBottomPx(marginPx());
        boolean rail = form.isRail();
        boolean onLeft = rail && mLauncher.railSide() == ControlBarPlacement.RailSide.LEFT;
        int width = rail ? thickness : ViewGroup.LayoutParams.MATCH_PARENT;
        int height = rail ? ViewGroup.LayoutParams.MATCH_PARENT : thickness;
        int edge = rail ? railEdgeMarginPx(onLeft, side) : side;
        int left = rail && !onLeft ? 0 : edge;
        int right = rail && onLeft ? 0 : edge;
        int top = rail ? side : 0;
        int[] rules = params.getRules();
        boolean same = rules[RelativeLayout.ALIGN_PARENT_BOTTOM] != 0
            && (rules[RelativeLayout.ALIGN_PARENT_TOP] != 0) == rail
            && (rules[RelativeLayout.ALIGN_PARENT_LEFT] != 0) == onLeft
            && (rules[RelativeLayout.ALIGN_PARENT_RIGHT] != 0) == (rail && !onLeft)
            && rules[RelativeLayout.ABOVE] == 0
            && params.width == width && params.height == height && params.leftMargin == left
            && params.topMargin == top && params.rightMargin == right && params.bottomMargin == bottom;
        if (same) return false;
        params.width = width;
        params.height = height;
        params.removeRule(RelativeLayout.ABOVE);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        rule(params, RelativeLayout.ALIGN_PARENT_TOP, rail ? RelativeLayout.TRUE : 0);
        rule(params, RelativeLayout.ALIGN_PARENT_LEFT, onLeft ? RelativeLayout.TRUE : 0);
        rule(params, RelativeLayout.ALIGN_PARENT_RIGHT, rail && !onLeft ? RelativeLayout.TRUE : 0);
        params.leftMargin = left;
        params.topMargin = top;
        params.rightMargin = right;
        params.bottomMargin = bottom;
        return true;
    }

    /** The Workspaces end above a row, or beside a rail and above the stack's rest. */
    private void syncWorkspaces(@NonNull ControlBarForm form) {
        if (mWorkspaces == null || !(mWorkspaces.getLayoutParams() instanceof RelativeLayout.LayoutParams))
            return;
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mWorkspaces.getLayoutParams();
        boolean rail = form.isRail();
        boolean onLeft = rail && mLauncher.railSide() == ControlBarPlacement.RailSide.LEFT;
        int bar = mBar.view().getId();
        // Full screen: nothing of the bar's room is kept; the stack's own still is.
        boolean over = mFullScreen;
        boolean standOnStack = rail || over;
        int above = standOnStack ? 0 : bar;
        int leftOf = rail && !onLeft && !over ? bar : 0;
        int rightOf = onLeft && !over ? bar : 0;
        int bottom = standOnStack ? settledBottomPx(0) : 0;
        int[] rules = params.getRules();
        if (rules[RelativeLayout.ABOVE] == above && rules[RelativeLayout.LEFT_OF] == leftOf
            && rules[RelativeLayout.RIGHT_OF] == rightOf
            && (rules[RelativeLayout.ALIGN_PARENT_BOTTOM] != 0) == standOnStack
            && params.bottomMargin == bottom) return;
        rule(params, RelativeLayout.ABOVE, above);
        rule(params, RelativeLayout.LEFT_OF, leftOf);
        rule(params, RelativeLayout.RIGHT_OF, rightOf);
        rule(params, RelativeLayout.ALIGN_PARENT_BOTTOM, standOnStack ? RelativeLayout.TRUE : 0);
        params.bottomMargin = bottom;
        mWorkspaces.setLayoutParams(params);
    }

    /**
     * The rail's margin from its screen edge: its air, plus what of the camera's keep-out and
     * Android's navigation on that edge the host does not already stand clear of.
     */
    private int railEdgeMarginPx(boolean onLeft, int sidePx) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mHost);
        int cutout = 0;
        int navigation = 0;
        if (insets != null) {
            Insets cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            cutout = onLeft ? cut.left : cut.right;
            navigation = onLeft ? nav.left : nav.right;
        }
        mHost.getLocationInWindow(mLocation);
        int edgeGap = onLeft ? mLocation[0]
            : mHost.getRootView().getWidth() - (mLocation[0] + mHost.getWidth());
        return ControlBarPlacement.railEdgeMarginPx(sidePx, navigation,
            mLauncher.cutoutClearPx(cutout), edgeGap);
    }

    private static void rule(@NonNull RelativeLayout.LayoutParams params, int verb, int subject) {
        if (subject != 0) params.addRule(verb, subject);
        else params.removeRule(verb);
    }

    /**
     * How thick the bar stands in {@code form}: its rows, and on Home the A–Z letters' row when
     * Home's A–Z joins it at the bottom ({@link ControlBar#azRowAsked}); a rail's width.
     */
    private int thicknessPx(@NonNull ControlBarForm form) {
        return px(ControlBarPlacement.thicknessDp(form, mBar.azRowAsked()));
    }

    /** The bar's margin: its air on the sides and below it. */
    private int marginPx() {
        return px(ControlBarPlacement.clampMarginDp(mLauncher.marginDp()));
    }

    private int px(float dp) {
        return Math.round(dp * mHost.getResources().getDisplayMetrics().density);
    }
}
