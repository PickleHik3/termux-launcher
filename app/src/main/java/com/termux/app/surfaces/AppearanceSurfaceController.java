package com.termux.app.surfaces;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
 * it names ({@link #open(EditorCard, PaneWallPage)}), or with no card up. The picker is not a
 * door of its own: the Appearance card's Wallpaper › Change raises it as a sheet over the editor: the editor's session, its frame and its Undo
 * stay where they are underneath, and Back puts the picker down. Done on the picker applies its
 * choice and goes back to the card it came from: the picker stays up, busy, until the new
 * wallpaper's colours have landed ({@link WallpaperApplyHold}), so the editor is revealed once,
 * already wearing them.</p>
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

        /**
         * Back, before the picker goes down: true when the page spent it going up a level (a
         * Live scene's page back to the scenes). False by default.
         */
        default boolean consumeBack() {
            return false;
        }

        /**
         * Held: the page stays busy, showing its progress and taking no choice, after its apply
         * while the surface waits for the new colours. Nothing by default.
         */
        default void holdBusy(boolean held) {}
    }

    /** What the picker asks of the surface. */
    public interface Navigator {
        /** Closes the surface at once, applying nothing. */
        void close();

        /**
         * Done: the pending wallpaper applied and the picker put down over the card it was
         * raised from, the editor's session continued. Opened onto the picker by a door of its
         * own ({@link #resumeWallpaper}), it is the editor's Done: the session kept, the surface
         * closed.
         */
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

        /**
         * {@link #present} with nothing moving: the frame at its pose and the card at its detent
         * from the first frame, {@code onSettled} run once the frame's first pass has placed them.
         * A relaunch reopening its carried session. Animated like {@link #present} by default.
         */
        default void presentInPlace(@Nullable EditorCard card, @Nullable PaneWallPage place,
                                    @Nullable Runnable onSettled) {
            present(card, place, onSettled);
        }

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

        /**
         * Keeps the card sheet and the bar unseen ({@code veiled}) while the picker rises over
         * them with the editor's entry, so its cards never show under the rising sheet; shown
         * again once the sheet covers them, or as the picker goes down. Set before
         * {@link #present}; the editor's exit clears it. Nothing by default.
         */
        default void setChromeVeiled(boolean veiled) {}

        /**
         * Starts reading the editor's wallpaper ahead of {@link #beginSession}, which takes the
         * read over (a Wallpaper door's tap, while its menu slides away). Nothing by default.
         */
        default void prepareWallpaper() {}

        /** Lets go of a {@link #prepareWallpaper} read no session took. Nothing by default. */
        default void dropPreparedWallpaper() {}
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

        /** The monotonic clock {@link WallpaperApplyHold} measures in. */
        default long uptime() {
            return SystemClock.uptimeMillis();
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
    /** The picker can draw what it opens on ({@link OverviewPage#whenReady} has answered). */
    private boolean mOverviewReady;
    /** A door's presentation waiting for the picker to be ready; null while none waits. */
    @Nullable private Runnable mOverviewWaiter;
    /** {@link #showWallpaper} was asked while the picker was being built: it rises once built. */
    private boolean mShowWhenBuilt;
    /** A relaunch's reopen: the editor and the picker stand where they were, nothing moving. */
    private boolean mInPlace;
    /** Told once the reopened surface is on screen as carried, or will not be; null when done. */
    @Nullable private Runnable mOnReopened;
    private boolean mWallpaperShown;
    @Nullable private View mScrim;
    private final List<Animator> mRunning = new ArrayList<>();
    /** The surface was opened onto the picker ({@link #resumeWallpaper}): its Done closes. */
    private boolean mPickerIsDoor;
    /**
     * A Wallpaper door's picker is being built ahead of the door, the surface still closed
     * ({@link #prepareWallpaper}): the host view holds it unseen until {@link #resumeWallpaper}
     * takes it over or {@link #releasePrepared} lets it go.
     */
    private boolean mPrepared;
    /**
     * The picker's Done is applying, or holding for the colours: its destination is the card
     * under it, which a relaunch now carries ({@link #carry}).
     */
    private boolean mReturningToCard;
    /** The picker's apply has not answered yet. */
    private boolean mApplying;
    /** The apply answered; the picker stays up, busy, until {@link WallpaperApplyHold} says. */
    private boolean mHolding;
    private long mApplyFinishedAt;
    /** The latest system colours callback since the apply began; {@link WallpaperApplyHold#NONE}. */
    private long mColoursAt = WallpaperApplyHold.NONE;
    private int mHoldSeq;
    /** A page whose activity went down mid-apply: it lets go once the apply answers. */
    @Nullable private OverviewPage mOrphan;
    private final Handler mMain = new Handler(Looper.getMainLooper());

    public AppearanceSurfaceController(@NonNull Host host, @NonNull Editor editor) {
        mHost = host;
        mEditor = editor;
    }

    // ------------------------------------------------------------------------------------ state

    public boolean isOpen() {
        return mOpen;
    }

    /** Whether the wallpaper picker stands over the editor now. */
    public boolean isWallpaperShown() {
        return mOpen && mWallpaperShown;
    }

    @Nullable
    @VisibleForTesting
    OverviewPage overview() {
        return mOverview;
    }

    // -------------------------------------------------------------------------------------- open

    /** {@link #open(EditorCard, PaneWallPage)} with no card and no place. */
    public void open() {
        open(null, null);
    }

    /**
     * Opens the editor with {@code card} up (a door's object), on {@code place}'s half. A door
     * that names no card reopens the one the person left up within Settings' window. Open
     * already, it puts the picker down and raises that card.
     */
    public void open(@Nullable EditorCard card, @Nullable PaneWallPage place) {
        if (mOpen) {
            if (mEditor.isPresented()) {
                if (mWallpaperShown) hideWallpaper();
                if (card != null || place != null) mEditor.present(card, place, null);
            }
            return;
        }
        ViewGroup content = mHost.content();
        if (content == null)
            return;
        EditorCard opening = card;
        if (opening == null && place == null) {
            AppearanceReturnState back = AppearanceReturnState.freshFrom(
                mHost.readReturnState(), mHost.now());
            if (back != null)
                opening = back.editorCard();
        }
        beginOpen(content);
        presentEditor(opening, place, false, 1);
    }

    /**
     * Opens the picker over the Global card: for every door named Wallpaper
     * ({@link EditorDoor#opensWallpaperPicker}), the setup wizard, and the picker's own Photo… on
     * its way back from the photo pick when the session it interrupted could not be carried.
     */
    public void resumeWallpaper() {
        if (mOpen) {
            showWallpaper();
            return;
        }
        ViewGroup content = mHost.content();
        if (content == null) {
            releasePrepared();
            return;
        }
        // A picker prepared while the door's menu slid away is taken over as it stands: ready,
        // the entry starts at once.
        beginOpen(content, true);
        mPickerIsDoor = true;
        // Built while the editor waits for its wallpaper and its frame, so the sheet rises with
        // the entry rather than being inflated in the middle of it.
        buildOverview();
        presentEditor(EditorCard.APPEARANCE, null, true, 1);
    }

    /**
     * A Wallpaper door was tapped and its menu is sliding away: the picker (and the editor's
     * wallpaper) start loading now, unseen, so the door's {@link #resumeWallpaper} finds them
     * ready. What no door takes, {@link #releasePrepared} lets go. Open already, nothing.
     */
    public void prepareWallpaper() {
        if (mOpen || mPrepared)
            return;
        ViewGroup content = mHost.content();
        if (content == null)
            return;
        mPrepared = true;
        mToken++;
        mOverviewReady = false;
        mOverviewWaiter = null;
        mShowWhenBuilt = false;
        hostView(content);
        mEditor.prepareWallpaper();
        buildOverview();
    }

    /** Lets go of a {@link #prepareWallpaper} no door took (its menu closed another way). */
    public void releasePrepared() {
        if (!mPrepared)
            return;
        mPrepared = false;
        mToken++;
        mOverviewLoading = false;
        mOverviewReady = false;
        mOverviewWaiter = null;
        mShowWhenBuilt = false;
        mEditor.dropPreparedWallpaper();
        OverviewPage overview = mOverview;
        mOverview = null;
        if (overview != null)
            overview.release();
        FrameLayout view = mView;
        mView = null;
        if (view != null && view.getParent() instanceof ViewGroup)
            ((ViewGroup) view.getParent()).removeView(view);
    }

    @VisibleForTesting
    boolean isPrepared() {
        return mPrepared;
    }

    /** {@link #beginOpen(ViewGroup, boolean)}, letting go of anything prepared. */
    private void beginOpen(@NonNull ViewGroup content) {
        beginOpen(content, false);
    }

    /**
     * Marks the surface open, begins the session, and adds the host view and the scrim;
     * {@code takePrepared} keeps what {@link #prepareWallpaper} built, else it is let go.
     */
    private void beginOpen(@NonNull ViewGroup content, boolean takePrepared) {
        boolean taken = takePrepared && mPrepared;
        if (!taken)
            releasePrepared();
        mPrepared = false;
        mOpen = true;
        if (!taken) {
            // A prepared picker's build and readiness answer under the token they began with.
            mToken++;
            mOverviewReady = false;
            mOverviewWaiter = null;
            mShowWhenBuilt = false;
        }
        mLeaveRemembered = false;
        mPickerIsDoor = false;
        mInPlace = false;
        resetHold();
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
                if (!thenWallpaper) {
                    presentNow(card, place, false, gaveUp);
                    return;
                }
                awaitOverview(() -> {
                    if (mOpen && token == mToken)
                        presentNow(card, place, true, gaveUp);
                });
            }, gaveUp);
        });
    }

    /**
     * Presents the editor now, the picker rising over it for {@code thenWallpaper}: with the
     * picker ready, the card sheet and the bar stay unseen under it until it covers them
     * ({@link WallpaperDoorMotion#veilsChrome}).
     */
    private void presentNow(@Nullable EditorCard card, @Nullable PaneWallPage place,
                            boolean thenWallpaper, @NonNull Runnable gaveUp) {
        boolean inPlace = mInPlace;
        boolean veil = !inPlace && WallpaperDoorMotion.veilsChrome(thenWallpaper, mOverviewReady,
            ReducedMotion.isEnabled(mHost.context()));
        if (veil)
            mEditor.setChromeVeiled(true);
        if (inPlace)
            // Posted: the picker over it is placed below, in this same message.
            mEditor.presentInPlace(card, place, () -> mMain.post(this::reopened));
        else
            mEditor.present(card, place, null);
        if (!mEditor.isPresented()) {
            if (veil)
                mEditor.setChromeVeiled(false);
            gaveUp.run();
            return;
        }
        if (thenWallpaper)
            showWallpaper();
        // Only the carried state stands in place; the picker raised later rises as usual.
        mInPlace = false;
    }

    /** The reopened surface is on screen as carried (or never will be): the activity is told. */
    private void reopened() {
        Runnable told = mOnReopened;
        mOnReopened = null;
        if (told != null)
            told.run();
    }

    /**
     * Runs {@code ready} once the picker being built can draw, or after
     * {@link WallpaperDoorMotion#PICKER_WAIT_MS} when it cannot yet; at once when it is ready or
     * nothing is building it.
     */
    private void awaitOverview(@NonNull Runnable ready) {
        if (mOverviewReady || (mOverview == null && !mOverviewLoading)) {
            ready.run();
            return;
        }
        mOverviewWaiter = ready;
        mMain.postDelayed(() -> {
            if (mOverviewWaiter != ready)
                return;
            mOverviewWaiter = null;
            ready.run();
        }, WallpaperDoorMotion.PICKER_WAIT_MS);
    }

    /** Where the surface stands, for the activity a relaunch builds next; null while closed. */
    @Nullable
    public Relaunch carry() {
        if (!mOpen)
            return null;
        return new Relaunch(where(), mEditor.carrySession());
    }

    /**
     * Where the person is: the card, and the picker over it unless its Done is already on the
     * way back to the card (a relaunch mid-apply then opens on the card, not the picker).
     */
    @NonNull
    private AppearanceReturnState where() {
        AppearanceReturnState.Builder out = new AppearanceReturnState.Builder(
            mWallpaperShown && !mReturningToCard);
        out.card(mEditor.shownCard());
        OverviewPage overview = mOverview;
        if (overview != null)
            out.wallpaperSlot(overview.centredSlotName());
        return out.build(mHost.now());
    }

    /**
     * Opens the surface the way {@code carried} says it stood when the activity before this one
     * was relaunched: its card up, its session continued. False when it could not open.
     */
    public boolean reopen(@NonNull Relaunch carried) {
        return reopen(carried, null);
    }

    /**
     * {@link #reopen(Relaunch)}, standing in place: the editor at its pose with its card at its
     * detent and the picker up, nothing growing or rising. {@code onShown} runs once that is on
     * screen, or once it will not be (the surface closed or never opened); not when this
     * returns false.
     */
    public boolean reopen(@NonNull Relaunch carried, @Nullable Runnable onShown) {
        if (mOpen)
            return false;
        ViewGroup content = mHost.content();
        if (content == null)
            return false;
        beginOpen(content);
        mInPlace = true;
        mOnReopened = onShown;
        mEditor.adoptSessionNext(carried.editor);
        if (carried.where.wallpaper)
            buildOverview();
        presentEditor(carried.where.editorCard(), null,
            carried.where.wallpaper, REOPEN_ATTEMPTS);
        return true;
    }

    // --------------------------------------------------------------------------- the picker

    /** Raises the wallpaper picker over the editor, building it the first time. */
    private void showWallpaper() {
        if (!mOpen || mCommitting)
            return;
        OverviewPage overview = mOverview;
        if (overview == null) {
            mShowWhenBuilt = true;
            buildOverview();
            return;
        }
        if (mWallpaperShown)
            return;
        mWallpaperShown = true;
        setTakesTouches(true);
        final View page = overview.root();
        overview.onShown();
        final int token = mToken;
        final boolean inPlace = mInPlace;
        overview.whenReady(() -> {
            if (!mOpen || token != mToken || !mWallpaperShown)
                return;
            mOverviewReady = true;
            page.setVisibility(View.VISIBLE);
            page.setAlpha(1f);
            if (inPlace) {
                // A relaunch's reopen: up already, as it stood when the activity went down.
                page.setTranslationY(0f);
                return;
            }
            // Up, it covers the card sheet and the bar a door's entry kept unseen.
            rise(page, true, () -> mEditor.setChromeVeiled(false));
        });
    }

    /**
     * Builds the picker once, off screen and unseen, and adds it under the host; a door's
     * presentation waiting for it ({@link #awaitOverview}) goes once it can draw, and a
     * {@link #showWallpaper} asked meanwhile raises it.
     */
    private void buildOverview() {
        if (mOverview != null || mOverviewLoading)
            return;
        mOverviewLoading = true;
        final int token = mToken;
        mHost.createOverview(mNavigator, null, built -> {
            if (!live(token) || mView == null) {
                built.release();
                return;
            }
            mOverviewLoading = false;
            mOverview = built;
            built.root().setVisibility(View.INVISIBLE);
            addPage(built.root());
            built.whenReady(() -> {
                if (!live(token) || mOverview != built)
                    return;
                mOverviewReady = true;
                Runnable waiter = mOverviewWaiter;
                mOverviewWaiter = null;
                // Posted: the editor opens outside the picker's layout callback.
                if (waiter != null)
                    mMain.post(waiter);
            });
            if (mShowWhenBuilt) {
                mShowWhenBuilt = false;
                showWallpaper();
            }
        });
    }

    /** Whether a callback begun under {@code token} still belongs: the surface open, or prepared. */
    private boolean live(int token) {
        return (mOpen || mPrepared) && token == mToken;
    }

    /** Puts the picker down; its pending choice stays for Done. */
    private void hideWallpaper() {
        OverviewPage overview = mOverview;
        if (!mWallpaperShown || overview == null)
            return;
        mWallpaperShown = false;
        setTakesTouches(false);
        // Put down mid-entry, it uncovers the card sheet as it goes.
        mEditor.setChromeVeiled(false);
        final View page = overview.root();
        rise(page, false, () -> {
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
            OverviewPage overview = mOverview;
            if (overview != null && overview.consumeBack())
                return true;
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
        commitThen(then, () -> {});
    }

    /** {@link #commitThen(Runnable)}, with {@code failed} run when the apply did not succeed. */
    private void commitThen(@NonNull Runnable then, @NonNull Runnable failed) {
        final OverviewPage overview = mOverview;
        if (!mOpen || overview == null || !overview.hasPendingChanges()) {
            then.run();
            return;
        }
        mCommitting = true;
        mApplying = true;
        mColoursAt = WallpaperApplyHold.NONE;
        final int token = mToken;
        final int commit = ++mCommitSeq;
        FrameLayout view = mView;
        if (view != null) {
            view.postDelayed(() -> {
                if (mCommitting && commit == mCommitSeq && mOpen && token == mToken) {
                    Logger.logWarn(LOG_TAG, "Applying the pending wallpaper did not answer; the surface is usable again");
                    mCommitting = false;
                    mApplying = false;
                    mReturningToCard = false;
                }
            }, COMMIT_BACKSTOP_MS);
        }
        overview.commit(() -> {
            if (letGoAfterApply(overview) || !mOpen || token != mToken)
                return;
            mApplying = false;
            mCommitting = false;
            then.run();
        }, () -> {
            if (letGoAfterApply(overview) || !mOpen || token != mToken)
                return;
            mApplying = false;
            mCommitting = false;
            failed.run();
        });
    }

    /** The apply of a page whose activity went down has answered: that page lets go now. */
    private boolean letGoAfterApply(@NonNull OverviewPage overview) {
        if (mOrphan != overview)
            return false;
        mOrphan = null;
        overview.release();
        return true;
    }

    /**
     * The picker's Done: its choice applied, then back to the card it was raised from, the
     * editor's session continued. Opened onto the picker by a door of its own, the editor's Done.
     */
    private void onPickerDone() {
        if (!mOpen || mCommitting)
            return;
        if (mPickerIsDoor) {
            mEditor.done();
            return;
        }
        if (!hasPendingWallpaper()) {
            hideWallpaper();
            return;
        }
        mReturningToCard = true;
        commitThen(this::holdForColours, () -> mReturningToCard = false);
    }

    // ------------------------------------------------------------------------- the colours hold

    /**
     * The picker's apply finished: it stays up, busy, until the new colours have landed, so the
     * editor is revealed once. A relaunch ends this by itself, the next activity opening on the
     * card; {@link WallpaperApplyHold} decides when none is coming.
     */
    private void holdForColours() {
        OverviewPage overview = mOverview;
        if (!mOpen || overview == null || !mWallpaperShown) {
            mReturningToCard = false;
            return;
        }
        mCommitting = true;
        mHolding = true;
        mApplyFinishedAt = mHost.uptime();
        overview.holdBusy(true);
        scheduleHoldCheck();
    }

    /** Ends the hold when its time is up, else looks again when it would be. */
    private void scheduleHoldCheck() {
        final int seq = ++mHoldSeq;
        long wait = WallpaperApplyHold.remainingMs(mApplyFinishedAt, mColoursAt, mHost.uptime());
        if (wait <= 0) {
            endHold();
            return;
        }
        mMain.postDelayed(() -> {
            if (mOpen && mHolding && seq == mHoldSeq)
                scheduleHoldCheck();
        }, wait);
    }

    /** The colours have landed, or none are coming: the picker goes down over the card. */
    private void endHold() {
        OverviewPage overview = mOverview;
        resetHold();
        mCommitting = false;
        if (overview != null)
            overview.holdBusy(false);
        hideWallpaper();
    }

    private void resetHold() {
        mHoldSeq++;
        mHolding = false;
        mApplying = false;
        mReturningToCard = false;
        mColoursAt = WallpaperApplyHold.NONE;
    }

    /**
     * The system wallpaper's colours changed: a relaunch for the new theme may follow. While the
     * picker's Done holds, it waits for that relaunch.
     */
    public void onWallpaperColoursChanged() {
        if (!mOpen)
            return;
        mColoursAt = mHost.uptime();
        if (mHolding)
            scheduleHoldCheck();
    }

    /**
     * The activity went down (a relaunch has already carried the surface on, {@link #carry}):
     * the surface lets go without a trace, writing nothing, closing nothing and ending no session,
     * since the next activity continues it. A wallpaper apply still running finishes first (its
     * Lock slot may not be set yet), and only then does its page let go.
     */
    public void onHostDestroyed() {
        mOnReopened = null;
        releasePrepared();
        if (!mOpen)
            return;
        mOpen = false;
        mToken++;
        boolean applying = mApplying;
        resetHold();
        mCommitting = false;
        mWallpaperShown = false;
        mOverviewLoading = false;
        for (Animator a : new ArrayList<>(mRunning))
            a.cancel();
        mRunning.clear();
        mEditor.setOnDone(null);
        mEditor.setPageListener(null);
        mView = null;
        mScrim = null;
        OverviewPage overview = mOverview;
        mOverview = null;
        if (overview == null)
            return;
        if (applying)
            mOrphan = overview;
        else
            overview.release();
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
        mHost.writeReturnState(where().serialize());
        mLeaveRemembered = true;
    }

    // ------------------------------------------------------------------------------- the views

    @NonNull
    private FrameLayout hostView(@NonNull ViewGroup content) {
        FrameLayout view = mView;
        if (view != null)
            return view;
        view = new SurfaceHostView(mHost.context());
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
        // Draw order goes by Z before child order: the card sheet and the editor bar have
        // elevation, so bringToFront alone leaves them over the picker and its Choose photo.
        view.setTranslationZ(takes ? siblingsTopZ(view) + 1f : 0f);
    }

    /** The highest Z among the host's siblings (the editor's sheet and bar). */
    private static float siblingsTopZ(@NonNull View view) {
        float top = 0f;
        if (view.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view.getParent();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child != view) top = Math.max(top, child.getZ());
            }
        }
        return top;
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
        final boolean applying = mApplying;
        boolean pickerShown = mWallpaperShown;
        resetHold();
        mWallpaperShown = false;
        mOverviewLoading = false;
        mOverviewReady = false;
        mOverviewWaiter = null;
        mShowWhenBuilt = false;
        for (Animator a : new ArrayList<>(mRunning))
            a.cancel();
        mRunning.clear();
        final OverviewPage overview = mOverview;
        mOverview = null;
        final FrameLayout view = mView;
        mView = null;
        // The picker over the editor rides down with the editor's exit, the frame growing and
        // the card sheet falling under it on the same motion; it is taken off once off screen.
        boolean ride = overview != null && view != null
            && WallpaperDoorMotion.ridesPickerDown(pickerShown,
                overview.root().getParent() == view
                    && overview.root().getVisibility() == View.VISIBLE,
                ReducedMotion.isEnabled(mHost.context()));
        // An apply still running finishes its other slot first.
        if (overview != null && applying)
            mOrphan = overview;
        Runnable letGo = () -> {
            if (overview != null) {
                overview.onHidden();
                if (!applying)
                    overview.release();
            }
            if (view != null && view.getParent() instanceof ViewGroup)
                ((ViewGroup) view.getParent()).removeView(view);
        };
        if (ride) {
            ((SurfaceHostView) view).mLeaving = true;
            rideDown(overview.root(), letGo);
        } else {
            letGo.run();
        }
        boolean editorWasUp = mEditor.isPresented();
        mEditor.setPageListener(null);
        removeScrim(editorWasUp);
        mCommitting = false;
        mEditor.setOnDone(null);
        mEditor.endSession();
        mInPlace = false;
        reopened();
        boolean byUser = mClosingByUser;
        mClosingByUser = false;
        mHost.onClosed();
        if (byUser)
            mHost.onClosedByUser();
    }

    /**
     * The picker riding down off screen as the surface closes, on WALLPAPER_PAGE like its
     * {@link #rise}; {@code end} runs once it is down, or at once if the ride is cut short. Not
     * one of {@link #mRunning}: the next surface's close must not strand this one's views.
     */
    private void rideDown(@NonNull View page, @NonNull Runnable end) {
        float below = page.getHeight() > 0 ? page.getHeight()
            : page.getResources().getDisplayMetrics().heightPixels;
        ValueAnimator animator = ValueAnimator.ofFloat(page.getTranslationY(), below);
        animator.setDuration(EditorMotion.Use.WALLPAPER_PAGE.durationMs(false));
        animator.setInterpolator(EditorMotion.Use.WALLPAPER_PAGE.curve(false));
        animator.addUpdateListener(a -> page.setTranslationY((Float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                end.run();
            }
        });
        animator.start();
    }

    /**
     * The surface's host: while a closing surface rides the picker down it takes every touch,
     * so nothing on the leaving page answers one.
     */
    private static final class SurfaceHostView extends FrameLayout {
        boolean mLeaving;

        SurfaceHostView(@NonNull Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
            return mLeaving || super.onInterceptTouchEvent(event);
        }

        @Override
        @SuppressWarnings("ClickableViewAccessibility")
        public boolean onTouchEvent(android.view.MotionEvent event) {
            return mLeaving || super.onTouchEvent(event);
        }
    }

    /** Fades {@code view} from {@code from} to {@code to}; {@code end} runs unless cancelled. */
    /**
     * Rule d: the Wallpaper page rises from the screen's bottom over the card sheet, or falls
     * back to it, on WALLPAPER_PAGE (spatial-default); under reduced motion it jumps.
     */
    private void rise(@NonNull View view, boolean up, @Nullable Runnable end) {
        float below = view.getHeight() > 0 ? view.getHeight()
            : view.getResources().getDisplayMetrics().heightPixels;
        float from = up ? below : view.getTranslationY();
        float to = up ? 0f : below;
        if (ReducedMotion.isEnabled(mHost.context())) {
            view.setTranslationY(0f);
            if (!up) view.setAlpha(0f);
            if (end != null) end.run();
            return;
        }
        view.setTranslationY(from);
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(EditorMotion.Use.WALLPAPER_PAGE.durationMs(false));
        animator.setInterpolator(EditorMotion.Use.WALLPAPER_PAGE.curve(false));
        animator.addUpdateListener(a -> view.setTranslationY((Float) a.getAnimatedValue()));
        mRunning.add(animator);
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(Animator a) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                mRunning.remove(a);
                if (!mCancelled) {
                    // Put down, it waits in place, unseen, for the next rise.
                    if (!up) {
                        view.setAlpha(0f);
                        view.setTranslationY(0f);
                    }
                    if (end != null) end.run();
                }
            }
        });
        animator.start();
    }

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
