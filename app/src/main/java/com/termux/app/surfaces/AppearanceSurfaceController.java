package com.termux.app.surfaces;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Appearance editor's host in the activity window (appearance-and-settings-rework §2): one
 * session from open to close, the editor ({@link Editor}, the launcher scaled into a frame with
 * its cards and its bar) presented over the launcher, and the wallpaper picker raised over the
 * editor when the Appearance card's Wallpaper row asks for it.
 *
 * <p><b>One page.</b> There are no tabs and no mode: every door opens the editor, on the object
 * it names ({@link #open(PageId, EditorCard, PaneWallPage)}), or with no card up. The picker is
 * not a page beside the editor but a sheet over it: the editor's session, its frame and its Undo
 * stay where they are underneath, and Back puts the picker down. Done on the picker is the
 * editor's Done.</p>
 *
 * <p><b>Commit on Done.</b> Everything previews live and Done keeps it (the picker's pending
 * wallpaper with it); Back puts a card down first and, from rest, asks before throwing a change
 * away (AGENTS.md, "Things already decided").</p>
 *
 * <p><b>Where it was left.</b> Every way out writes down the page and the card that was up
 * ({@link AppearanceReturnState}); a door that names nothing reopens that card within Settings'
 * window. A configuration relaunch hands the session to the next activity ({@link #carry},
 * {@link #reopen}).</p>
 */
public final class AppearanceSurfaceController {
    private static final String LOG_TAG = "AppearanceSurface";

    /** The editor, or the wallpaper picker over it. */
    public enum PageId { EDITOR, WALLPAPER }

    /** A page of content the surface or the editor hosts: the picker, the icon packs. */
    public interface Page {
        /** The page's view tree, added once and kept. */
        @NonNull View root();

        @NonNull CharSequence title();

        /** The page is on screen (or about to be): start what animates. */
        void onShown();

        /** The page has left the screen but lives on: stop what animates. */
        void onHidden();

        /** The surface closed: let go of everything. */
        void release();

        /** {@code changed} runs when the page's content changes size by itself. */
        default void setOnContentChanged(@Nullable Runnable changed) {}

        /** How far the content is scrolled sideways, px. 0 by default. */
        default int scrollPosition() {
            return 0;
        }

        /** Scrolls the content back to {@code px} once it is laid out. Nothing by default. */
        default void restoreScrollPosition(int px) {}
    }

    /** The wallpaper picker: a {@link Page} with choices that Done applies. */
    public interface OverviewPage extends Page {
        /** The wallpaper card standing in the middle, as its slot's name; null where unknown. */
        @Nullable
        default String centredSlotName() {
            return null;
        }

        /** Runs {@code ready} once the page can draw what it opens on. At once by default. */
        default void whenReady(@NonNull Runnable ready) {
            ready.run();
        }

        /** Whether the page holds a choice nothing has applied yet. */
        default boolean hasPendingChanges() {
            return false;
        }

        /**
         * Applies every pending choice and runs exactly one of the callbacks on the main thread:
         * {@code done} when all of it is set, {@code failed} when something is not.
         */
        default void commit(@NonNull Runnable done, @NonNull Runnable failed) {
            done.run();
        }
    }

    /** What the picker asks of the surface. */
    public interface Navigator {
        /** Closes the surface at once, applying nothing. */
        void close();

        /** Done: the session kept, the pending wallpaper applied, the surface closed. */
        void done();

        /** Back: the picker goes down, over the editor. */
        void back();
    }

    /** What the editor tells the surface. */
    public interface PageListener {
        /**
         * The frame has no wallpaper to show: passthrough mode, and the system's picture could
         * not be read. The scrim then stays clear so the window's own wallpaper shows through.
         */
        void onFrameWallpaperMissing();

        /** The Appearance card's Wallpaper row asked for the picker. */
        void onOpenWallpaper();
    }

    /**
     * The editor's session and presentation, which {@link SurfaceEditorController} implements.
     * The surface never touches the editor's views.
     */
    public interface Editor {
        /** Opens the session: the held wall, and the editor's wallpaper read now. */
        void beginSession();

        /** Runs {@code ready} once the editor's wallpaper is painted (or has none), promptly. */
        void awaitWallpaper(@NonNull Runnable ready);

        /**
         * Runs {@code ready} once the launcher's container is laid out, {@code failed} when it is
         * not within a bounded time.
         */
        default void awaitFrame(@NonNull Runnable ready, @NonNull Runnable failed) {
            ready.run();
        }

        /**
         * Shows the editor with {@code card} up (none for null), on {@code place}'s half (the
         * place on screen for null); {@code onSettled} runs once the frame has settled. Already
         * presented, it raises that card (or puts the card down for null).
         */
        void present(@Nullable EditorCard card, @Nullable PaneWallPage place,
                     @Nullable Runnable onSettled);

        boolean isPresented();

        /** Back spent inside the editor: a card put down, a lift cancelled. False otherwise. */
        boolean consumeBack();

        /**
         * Leaving: asks the unsaved-changes question when there is something to lose, and runs
         * {@code proceed} when the answer lets it go.
         */
        void requestLeave(@NonNull Runnable proceed);

        /**
         * Leaving with the picker's choice pending as well: always asks. Discard puts the editor
         * back and runs {@code onDiscard}; Save keeps everything and runs {@code onSave}.
         */
        void confirmLeave(@NonNull Runnable onSave, @NonNull Runnable onDiscard);

        /** Ends the session. */
        void endSession();

        /** Done in the editor: the surface decides what follows. */
        void setOnDone(@Nullable Runnable onDone);

        void setPageListener(@Nullable PageListener listener);

        /** Undo: everything back to the state at open. */
        void undo();

        /** Done: saves, then the done runnable set with {@link #setOnDone} runs. */
        void done();

        /** The activity stopped with the surface up. */
        void onStopWhileOpen();

        /** The card that is up, for the surface to remember; null for none. */
        @Nullable
        default EditorCard shownCard() {
            return null;
        }

        /** The presentation's session as it stands, for a relaunch; null while there is none. */
        @Nullable
        default EditorSession carrySession() {
            return null;
        }

        /** The next presentation continues {@code session}. Null forgets it. */
        default void adoptSessionNext(@Nullable EditorSession session) {}
    }

    /** An editor's session, opaque to the surface; only the editor that made it reads it. */
    public interface EditorSession {}

    /** The surface as it stood when the activity went down for a configuration relaunch. */
    public static final class Relaunch {
        @NonNull public final AppearanceReturnState where;
        @Nullable public final EditorSession editor;

        public Relaunch(@NonNull AppearanceReturnState where, @Nullable EditorSession editor) {
            this.where = where;
            this.editor = editor;
        }
    }

    /** What the surface needs from the activity. */
    public interface Host {
        @NonNull Context context();

        /** {@code android.R.id.content}; null before it exists. */
        @Nullable ViewGroup content();

        /**
         * Builds the wallpaper picker with the card {@code centredSlot} names in the middle (the
         * page's own choice for null), reading what it needs off the main thread, and hands it to
         * {@code ready} on the main thread.
         */
        void createOverview(@NonNull Navigator navigator, @Nullable String centredSlot,
                            @NonNull Consumer<OverviewPage> ready);

        /** Where the person last left the surface, or null. */
        @Nullable
        default String readReturnState() {
            return null;
        }

        /** Keeps where the person left the surface, across the launcher being killed. */
        default void writeReturnState(@NonNull String state) {}

        /** Wall-clock time, which the remembered place's window is measured in. */
        default long now() {
            return System.currentTimeMillis();
        }

        /** The surface closed, by any path. */
        void onClosed();

        /** The person closed the surface themselves, after {@link #onClosed}. */
        default void onClosedByUser() {}
    }

    /** The picker fading in and out over the editor. */
    static final long FADE_MS = 180L;
    /** How long a wallpaper apply may hold the surface before Done and Back work again. */
    private static final long COMMIT_BACKSTOP_MS = 20_000L;

    @NonNull private final Host mHost;
    @NonNull private final Editor mEditor;
    @NonNull private final Navigator mNavigator = new Navigator() {
        @Override public void close() {
            closeNow();
        }

        @Override public void done() {
            onPickerDone();
        }

        @Override public void back() {
            onBack();
        }
    };

    private boolean mOpen;
    private boolean mClosingByUser;
    /** The picker's apply is running: Done, Back and the picker wait. */
    private boolean mCommitting;
    private int mCommitSeq;
    /** Bumped on every open and close, so a late callback of an older surface does nothing. */
    private int mToken;
    private boolean mLeaveRemembered;
    @Nullable private FrameLayout mView;
    @Nullable private OverviewPage mOverview;
    /** The picker is being built: a second Change waits for it. */
    private boolean mOverviewLoading;
    private boolean mWallpaperShown;
    @Nullable private View mScrim;
    private final List<Animator> mRunning = new ArrayList<>();

    public AppearanceSurfaceController(@NonNull Host host, @NonNull Editor editor) {
        mHost = host;
        mEditor = editor;
    }

    // ------------------------------------------------------------------------------------ state

    public boolean isOpen() {
        return mOpen;
    }

    /** The page on screen now; null while closed. */
    @Nullable
    public PageId shownPage() {
        if (!mOpen)
            return null;
        return mWallpaperShown ? PageId.WALLPAPER : PageId.EDITOR;
    }

    @Nullable
    @VisibleForTesting
    OverviewPage overview() {
        return mOverview;
    }

    // -------------------------------------------------------------------------------------- open

    /** {@link #open(PageId, EditorCard, PaneWallPage)} with no card and no place. */
    public void open(@NonNull PageId page) {
        open(page, null, null);
    }

    /**
     * Opens the editor with {@code card} up (a door's object), on {@code place}'s half; for
     * {@link PageId#WALLPAPER} the picker rises over it once it stands. A door that names no card
     * reopens the one the person left up within Settings' window. Open already, it raises that
     * card (or the picker).
     */
    public void open(@NonNull PageId page, @Nullable EditorCard card,
                     @Nullable PaneWallPage place) {
        if (mOpen) {
            if (page == PageId.WALLPAPER) {
                showWallpaper();
            } else if (mEditor.isPresented()) {
                if (mWallpaperShown) hideWallpaper();
                if (card != null || place != null) mEditor.present(card, place, null);
            }
            return;
        }
        ViewGroup content = mHost.content();
        if (content == null)
            return;
        EditorCard opening = card;
        if (opening == null && page == PageId.EDITOR && place == null) {
            AppearanceReturnState back = AppearanceReturnState.freshFrom(
                mHost.readReturnState(), mHost.now());
            if (back != null)
                opening = back.editorCard();
        }
        beginOpen(content);
        presentEditor(opening, place, page == PageId.WALLPAPER, 1);
    }

    /** Marks the surface open, begins the session, and adds the host view and the scrim. */
    private void beginOpen(@NonNull ViewGroup content) {
        mOpen = true;
        mToken++;
        mLeaveRemembered = false;
        mEditor.setOnDone(this::onEditorDone);
        mEditor.setPageListener(new PageListener() {
            @Override public void onFrameWallpaperMissing() {
                fadeScrim(0f);
            }

            @Override public void onOpenWallpaper() {
                showWallpaper();
            }
        });
        mEditor.beginSession();
        hostView(content);
        showScrim(content);
        fadeScrim(1f);
    }

    /**
     * How many times a relaunch's reopen waits for the launcher's container before giving up. A
     * theme change rebuilds the whole window, and its first layout can take seconds on a slow
     * phone; giving up early would drop a session whose changes are already written live.
     */
    static final int REOPEN_ATTEMPTS = 6;

    /**
     * Presents the editor once its wallpaper and the launcher's container are ready, trying up to
     * {@code attempts} times before the surface closes.
     */
    private void presentEditor(@Nullable EditorCard card, @Nullable PaneWallPage place,
                               boolean thenWallpaper, int attempts) {
        final int token = mToken;
        final Runnable gaveUp = () -> {
            if (!mOpen || token != mToken)
                return;
            if (attempts > 1) {
                presentEditor(card, place, thenWallpaper, attempts - 1);
                return;
            }
            Logger.logWarn(LOG_TAG, "The editor could not open; closing the surface");
            mLeaveRemembered = true;
            closeNow();
        };
        mEditor.awaitWallpaper(() -> {
            if (!mOpen || token != mToken)
                return;
            mEditor.awaitFrame(() -> {
                if (!mOpen || token != mToken)
                    return;
                mEditor.present(card, place, null);
                if (!mEditor.isPresented()) {
                    gaveUp.run();
                    return;
                }
                if (thenWallpaper)
                    showWallpaper();
            }, gaveUp);
        });
    }

    /** Where the surface stands, for the activity a relaunch builds next; null while closed. */
    @Nullable
    public Relaunch carry() {
        if (!mOpen)
            return null;
        AppearanceReturnState.Builder out = new AppearanceReturnState.Builder(
            mWallpaperShown ? PageId.WALLPAPER : PageId.EDITOR);
        out.card(mEditor.shownCard());
        OverviewPage overview = mOverview;
        if (overview != null)
            out.wallpaperSlot(overview.centredSlotName());
        return new Relaunch(out.build(mHost.now()), mEditor.carrySession());
    }

    /**
     * Opens the surface the way {@code carried} says it stood when the activity before this one
     * was relaunched: its card up, its session continued. False when it could not open.
     */
    public boolean reopen(@NonNull Relaunch carried) {
        if (mOpen)
            return false;
        ViewGroup content = mHost.content();
        if (content == null)
            return false;
        beginOpen(content);
        mEditor.adoptSessionNext(carried.editor);
        presentEditor(carried.where.editorCard(), null,
            carried.where.page == PageId.WALLPAPER, REOPEN_ATTEMPTS);
        return true;
    }

    // --------------------------------------------------------------------------- the picker

    /** Raises the wallpaper picker over the editor, building it the first time. */
    private void showWallpaper() {
        if (!mOpen || mCommitting)
            return;
        OverviewPage overview = mOverview;
        if (overview == null) {
            if (mOverviewLoading)
                return;
            mOverviewLoading = true;
            final int token = mToken;
            mHost.createOverview(mNavigator, null, built -> {
                mOverviewLoading = false;
                if (!mOpen || token != mToken || mView == null) {
                    built.release();
                    return;
                }
                mOverview = built;
                built.root().setVisibility(View.INVISIBLE);
                addPage(built.root());
                showWallpaper();
            });
            return;
        }
        if (mWallpaperShown)
            return;
        mWallpaperShown = true;
        setTakesTouches(true);
        final View page = overview.root();
        overview.onShown();
        final int token = mToken;
        overview.whenReady(() -> {
            if (!mOpen || token != mToken || !mWallpaperShown)
                return;
            page.setVisibility(View.VISIBLE);
            fade(page, 0f, 1f, null);
        });
    }

    /** Puts the picker down; its pending choice stays for Done. */
    private void hideWallpaper() {
        OverviewPage overview = mOverview;
        if (!mWallpaperShown || overview == null)
            return;
        mWallpaperShown = false;
        setTakesTouches(false);
        final View page = overview.root();
        fade(page, page.getAlpha(), 0f, () -> {
            if (!mWallpaperShown) {
                page.setVisibility(View.INVISIBLE);
                overview.onHidden();
            }
        });
    }

    private boolean hasPendingWallpaper() {
        OverviewPage overview = mOverview;
        return overview != null && overview.hasPendingChanges();
    }

    // ------------------------------------------------------------------------------- navigation

    /** Back, from the overlay registry. True when it was spent here. */
    public boolean onBack() {
        if (!mOpen)
            return false;
        if (mCommitting)
            return true;
        if (mWallpaperShown) {
            hideWallpaper();
            return true;
        }
        if (mEditor.consumeBack())
            return true;
        rememberWhere();
        leave(() -> {
            mClosingByUser = true;
            closeNow();
        });
        return true;
    }

    /** A HOME press: the editor is left through its own unsaved-changes rule, then closes. */
    public void requestExit() {
        if (!mOpen || mCommitting)
            return;
        rememberWhere();
        leave(this::closeNow);
    }

    /**
     * Leaves for good ({@code then}): through the editor's unsaved question, or the one question
     * for the editor and the pending wallpaper together.
     */
    private void leave(@NonNull Runnable then) {
        if (hasPendingWallpaper())
            mEditor.confirmLeave(() -> commitThen(then), then);
        else
            mEditor.requestLeave(then);
    }

    /**
     * Applies the picker's pending choice, then runs {@code then}. The surface waits while it
     * runs; a failure leaves everything as it was, the picker having told the person.
     */
    private void commitThen(@NonNull Runnable then) {
        final OverviewPage overview = mOverview;
        if (!mOpen || overview == null || !overview.hasPendingChanges()) {
            then.run();
            return;
        }
        mCommitting = true;
        final int token = mToken;
        final int commit = ++mCommitSeq;
        FrameLayout view = mView;
        if (view != null) {
            view.postDelayed(() -> {
                if (mCommitting && commit == mCommitSeq && mOpen && token == mToken) {
                    Logger.logWarn(LOG_TAG, "Applying the pending wallpaper did not answer; the surface is usable again");
                    mCommitting = false;
                }
            }, COMMIT_BACKSTOP_MS);
        }
        overview.commit(() -> {
            if (!mOpen || token != mToken)
                return;
            mCommitting = false;
            then.run();
        }, () -> {
            if (!mOpen || token != mToken)
                return;
            mCommitting = false;
        });
    }

    /** The picker's Done: the editor's Done, which keeps the session and closes. */
    private void onPickerDone() {
        if (!mOpen || mCommitting)
            return;
        mEditor.done();
    }

    /** Done in the editor: the look is saved; the picker's choice is applied, then the close. */
    private void onEditorDone() {
        if (!mOpen)
            return;
        rememberWhere();
        commitThen(() -> {
            mClosingByUser = true;
            closeNow();
        });
    }

    /** The activity stopped: the surface survives it. */
    public void onStop() {
        if (mOpen)
            mEditor.onStopWhileOpen();
    }

    /** Writes down where the person is, so a door that names nothing comes back here. */
    private void rememberWhere() {
        if (!mOpen)
            return;
        AppearanceReturnState.Builder where = new AppearanceReturnState.Builder(
            mWallpaperShown ? PageId.WALLPAPER : PageId.EDITOR);
        where.card(mEditor.shownCard());
        OverviewPage overview = mOverview;
        if (overview != null)
            where.wallpaperSlot(overview.centredSlotName());
        mHost.writeReturnState(where.build(mHost.now()).serialize());
        mLeaveRemembered = true;
    }

    // ------------------------------------------------------------------------------- the views

    @NonNull
    private FrameLayout hostView(@NonNull ViewGroup content) {
        FrameLayout view = mView;
        if (view != null)
            return view;
        view = new FrameLayout(mHost.context());
        view.setId(R.id.appearance_surface_host);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        final FrameLayout host = view;
        ViewCompat.setOnApplyWindowInsetsListener(host, (v, insets) -> {
            padPages(host, insets);
            return insets;
        });
        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            WindowInsetsCompat root = ViewCompat.getRootWindowInsets(host);
            if (root != null) padPages(host, root);
        });
        mView = view;
        setTakesTouches(false);
        return view;
    }

    /** Whether the host swallows touches: only while the picker stands over the editor. */
    private void setTakesTouches(boolean takes) {
        FrameLayout view = mView;
        if (view == null)
            return;
        view.setClickable(takes);
        view.setFocusable(takes);
        if (takes)
            view.bringToFront();
    }

    private void addPage(@NonNull View root) {
        FrameLayout view = mView;
        if (view == null)
            return;
        if (root.getParent() != view) {
            if (root.getParent() instanceof ViewGroup)
                ((ViewGroup) root.getParent()).removeView(root);
            view.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        }
        ViewCompat.requestApplyInsets(view);
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
        if (insets != null) padPages(view, insets);
    }

    /** The system bars over the content, as the pages' padding. */
    private void padPages(@NonNull FrameLayout host, @NonNull WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
            | WindowInsetsCompat.Type.displayCutout());
        int[] at = new int[2];
        host.getLocationInWindow(at);
        View decor = host.getRootView();
        int below = decor == null ? 0 : Math.max(0, decor.getHeight() - (at[1] + host.getHeight()));
        int top = Math.max(0, bars.top - at[1]);
        int bottom = Math.max(0, bars.bottom - below);
        for (int i = 0; i < host.getChildCount(); i++) {
            View child = host.getChildAt(i);
            if (child.getPaddingLeft() != bars.left || child.getPaddingTop() != top
                || child.getPaddingRight() != bars.right || child.getPaddingBottom() != bottom)
                child.setPadding(bars.left, top, bars.right, bottom);
        }
    }

    /**
     * An opaque colorSurface view under the launcher's container while the surface is open: what
     * shows round the scaled frame, and what hides the system wallpaper in passthrough mode.
     */
    private void showScrim(@NonNull ViewGroup content) {
        View scrim = mScrim;
        if (scrim != null && scrim.getParent() == content)
            return;
        scrim = new View(mHost.context());
        scrim.setBackgroundColor(MaterialColors.getColor(mHost.context(),
            com.google.android.material.R.attr.colorSurface, Color.BLACK));
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scrim.setClickable(false);
        scrim.setFocusable(false);
        scrim.setAlpha(0f);
        content.addView(scrim, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mScrim = scrim;
    }

    @VisibleForTesting
    @Nullable
    View scrim() {
        return mScrim;
    }

    private void fadeScrim(float alpha) {
        View scrim = mScrim;
        if (scrim != null)
            fade(scrim, scrim.getAlpha(), alpha, null);
    }

    /**
     * The scrim leaves with the surface; while the editor's frame grows back to full size it
     * stays, so the frame never grows over a bare window.
     */
    private void removeScrim(boolean editorWasUp) {
        final View scrim = mScrim;
        mScrim = null;
        if (scrim == null || !(scrim.getParent() instanceof ViewGroup))
            return;
        final ViewGroup parent = (ViewGroup) scrim.getParent();
        if (!editorWasUp || ReducedMotion.isEnabled(mHost.context())) {
            parent.removeView(scrim);
            return;
        }
        scrim.postDelayed(() -> parent.removeView(scrim), AppearanceEditorFrame.EXIT_MS + 80L);
    }

    // ------------------------------------------------------------------------------------ close

    /** Closes at once: the picker lets go, the session ends and the frame grows back. */
    public void closeNow() {
        if (!mOpen)
            return;
        if (!mLeaveRemembered)
            rememberWhere();
        mEditor.adoptSessionNext(null);
        mOpen = false;
        mToken++;
        mWallpaperShown = false;
        mOverviewLoading = false;
        for (Animator a : new ArrayList<>(mRunning))
            a.cancel();
        mRunning.clear();
        OverviewPage overview = mOverview;
        mOverview = null;
        if (overview != null) {
            overview.onHidden();
            overview.release();
        }
        FrameLayout view = mView;
        mView = null;
        if (view != null && view.getParent() instanceof ViewGroup)
            ((ViewGroup) view.getParent()).removeView(view);
        boolean editorWasUp = mEditor.isPresented();
        mEditor.setPageListener(null);
        removeScrim(editorWasUp);
        mCommitting = false;
        mEditor.setOnDone(null);
        mEditor.endSession();
        boolean byUser = mClosingByUser;
        mClosingByUser = false;
        mHost.onClosed();
        if (byUser)
            mHost.onClosedByUser();
    }

    /** Fades {@code view} from {@code from} to {@code to}; {@code end} runs unless cancelled. */
    private void fade(@NonNull View view, float from, float to, @Nullable Runnable end) {
        if (ReducedMotion.isEnabled(mHost.context())) {
            view.setAlpha(to);
            if (end != null) end.run();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(FADE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> view.setAlpha((Float) a.getAnimatedValue()));
        mRunning.add(animator);
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(Animator a) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                mRunning.remove(a);
                if (!mCancelled) {
                    view.setAlpha(to);
                    if (end != null) end.run();
                }
            }
        });
        animator.start();
    }
}
