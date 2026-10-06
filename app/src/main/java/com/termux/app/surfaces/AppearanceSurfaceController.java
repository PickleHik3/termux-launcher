package com.termux.app.surfaces;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
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
import com.termux.app.terminal.Motion;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The one Appearance surface (appearance-round-2026-10-04.md, "One Appearance surface in the
 * activity window"): Overview, Look, Layout and Icons as pages of one host view in
 * {@code android.R.id.content}, above the launcher's own container, registered once with the
 * overlay registry. It replaces the wallpaper picker's dialog window and the two editors' separate
 * entries, whose hops each built a window or a sheet from scratch while animating.
 *
 * <p><b>Pages.</b> The Overview (the wallpaper page) is built once per session and hidden, not
 * destroyed, while another page shows; its thumbnails live until the surface closes. Look and Layout are the editor ({@link Editor}), presented over
 * the launcher by the surface: the editor's frame and sheet are not in the host view, they are the
 * launcher's container scaled and a sheet over it, as they always were; Icons is the editor's third
 * mode (the real dock in the frame, the pack controls in the sheet). Back goes to the previous
 * page, then closes: an editor to Overview (after its unsaved-changes question), Overview to the launcher. A
 * surface opened straight into an editor (a corner tab's Look or Layout, a deep link) has no
 * Overview behind it: Done and Back close it.</p>
 *
 * <p><b>Shell.</b> Every page wears the same bar ({@link AppearanceEditorPage}): back arrow, the
 * Wallpaper | Look | Layout | Icon pack pill, Undo while the editor is dirty,
 * and Done, over the page's content. The Overview is the Wallpaper segment, Look and Layout the
 * other three. The editor's content is the scaled launcher itself, so their bar is over a
 * transparent region, with a colorSurface scrim behind the container in the content view; the sheet
 * below the frame keeps only its controls. The bar never moves between pages: the Overview's and
 * the editor's stand exactly over each other through a hop.
 * Back goes one page toward Wallpaper and then closes; Done closes the surface (saving first in an
 * editor), after the editor's unsaved-changes question when leaving it.</p>
 *
 * <p><b>One session.</b> The editor's session (the held wall, its wallpaper decoded when the
 * surface opens in passthrough mode only, the launcher left as it was) begins when the
 * surface opens and ends when it closes; Look, Layout, Icons and the Overview move inside it.</p>
 *
 * <p><b>The hop.</b> Overview to editor: the strip and the shortcut row slide down
 * and fade while the editor's sheet slides up; the launcher's container, primed at the Home card's
 * rect, settles into the editor's frame while the Overview's background and cards fade, so the card
 * becomes the frame. Editor to Overview plays the same in reverse, and the swap happens from the
 * end of the frame's hide, never one frame into it. Everything the hop needs is built before it
 * starts; the launcher is put right (repainted, the raised keyboard taken down) only after the
 * Overview covers it again. With animations off every step lands at once.</p>
 *
 * <p><b>Where it was left.</b> Every way out (Done, Back out of the surface, Home, a close nobody
 * announced) writes down the page and what it comes back to ({@link AppearanceReturnState}) with
 * the time. An Overview open within Settings' 30-minute window opens on the remembered card and
 * queues the remembered editor page behind it, reached by the ordinary hop, with the editor told
 * where to stand ({@link Editor#restoreNext}); after the window it opens as before. A door that
 * names a page opens that page and ignores the memory.</p>
 */
public final class AppearanceSurfaceController {
    private static final String LOG_TAG = "AppearanceSurface";

    /** The surface's pages. */
    public enum PageId { OVERVIEW, LOOK, LAYOUT, ICONS }

    /**
     * A page the surface can show. Look, Layout and Icon pack are the editor's modes and are not
     * pages in this sense.
     */
    public interface Page {
        /** The page's view tree, added to the host once and kept. */
        @NonNull View root();

        @NonNull CharSequence title();

        /** The page is on screen (or about to be): start what animates. */
        void onShown();

        /** The page has left the screen but lives on: stop what animates. */
        void onHidden();

        /** The surface closed: let go of everything. */
        void release();

        /**
         * The part of the page that travels when it slides to or from another page: the content
         * under the shared bar, never the bar, which stays where it is. The root by default.
         */
        @NonNull
        default View slidingPart() {
            return root();
        }

        /**
         * {@code changed} runs when the page's content changes size by itself (a list that lands
         * after the page is built), so whoever holds it can measure it again. Nothing by default.
         */
        default void setOnContentChanged(@Nullable Runnable changed) {}

        /** How far the content is scrolled sideways, px, for the surface to remember. 0 by default. */
        default int scrollPosition() {
            return 0;
        }

        /**
         * Scrolls the content back to {@code px} once it is laid out and holds that much (less
         * where it holds less). Nothing by default.
         */
        default void restoreScrollPosition(int px) {}
    }

    /** The Overview: a {@link Page} that also tells the surface what its hop moves. */
    public interface OverviewPage extends Page {
        /**
         * The card standing in the middle, whose rect the launcher's frame grows out of and settles
         * back into; null while it is not laid out.
         */
        @Nullable View sharedCard();

        /** The rows that slide down and fade as the editor takes over. */
        @NonNull List<View> leavingViews();

        /** The views that only fade: the pager's cards and labels (the bar is the surface's and stays). */
        @NonNull List<View> fadingViews();

        /** The page's own background, 1 at rest and 0 while the launcher's frame shows through. */
        void setBackgroundAlpha(float alpha);

        /**
         * The page's bar hidden (false) while the editor's bar stands exactly over it, and shown
         * again once the editor has left. Nothing by default.
         */
        default void setBarVisible(boolean visible) {}

        /**
         * The wallpaper card standing in the middle, as its slot's name, for the surface to
         * remember; null where it cannot say.
         */
        @Nullable
        default String centredSlot() {
            return null;
        }

        /** Whether the page holds a choice nothing has applied yet: what Done would set. */
        default boolean hasPendingChanges() {
            return false;
        }

        /**
         * Applies every pending choice off the main thread and runs exactly one of the callbacks on
         * the main thread: {@code done} when all of it is set, {@code failed} when something is not
         * (the page has told the person). Nothing pending runs {@code done} at once.
         */
        default void commit(@NonNull Runnable done, @NonNull Runnable failed) {
            done.run();
        }
    }

    /** What a page asks of the surface. */
    public interface Navigator {
        void openLook();

        void openLayout();

        void openIcons();

        /** Closes the surface at once, applying nothing. */
        void close();

        /**
         * Done: every page's pending changes are applied, then the surface closes. Nothing pending
         * is a close.
         */
        default void done() {
            close();
        }

        /** One page back. Spent as a Back press is. */
        void back();
    }

    /** What the editor tells the page bar over its frame: the mode (the title) and the dirty state (Undo). */
    public interface PageListener {
        void onModeChanged(@NonNull EditorMode mode);

        void onDirtyChanged(boolean dirty);

        /**
         * The frame has no wallpaper to show: passthrough mode, and the system's picture could
         * not be read. The scrim then stays clear so the window's own wallpaper shows through.
         */
        void onFrameWallpaperMissing();
    }

    /**
     * The editor's session and presentation, which {@link SurfaceEditorController} implements. The
     * surface never touches the editor's views; it tells it when to begin, show, leave and end.
     */
    public interface Editor {
        /** Opens the session: the held wall, and the editor's wallpaper read now. */
        void beginSession();

        /** Runs {@code ready} once the editor's wallpaper is painted (or has none), promptly. */
        void awaitWallpaper(@NonNull Runnable ready);

        /**
         * Shows the editor. With {@code fromScale} above zero the frame starts at that scale and
         * vertical offset (the Home card) and settles into place; {@code onSettled} runs when it
         * has.
         */
        void present(@NonNull EditorMode mode, @Nullable PaneWallPage place, @Nullable String section,
                     float fromScale, float fromTranslationY, @Nullable Runnable onSettled);

        boolean isPresented();

        /** The mode the editor shows (Look while it is not presented). */
        @NonNull EditorMode mode();

        /** The page bar's pill: switches the editor's mode in place (no question, no reset). */
        void setMode(@NonNull EditorMode mode);

        /**
         * Leaving the editor page: asks the unsaved-changes question when there is something to
         * lose, and runs {@code proceed} when the answer lets it go; never when it keeps editing.
         */
        void requestLeave(@NonNull Runnable proceed);

        /**
         * Leaving with something besides the editor unsaved (the Overview's pending wallpaper):
         * always asks, once, whether or not the editor itself is dirty. Keep editing runs nothing;
         * Discard puts the editor back to how it was at open and runs {@code onDiscard}; Save keeps
         * the look and runs {@code onSave}.
         */
        default void confirmLeave(@NonNull Runnable onSave, @NonNull Runnable onDiscard) {
            requestLeave(onDiscard);
        }

        /**
         * Takes the editor off screen, keeping the session. The frame goes to the given scale and
         * offset (the Home card) when {@code toScale} is above zero; {@code onHidden} runs when it
         * has arrived.
         */
        void dismiss(float toScale, float toTranslationY, @Nullable Runnable onHidden);

        /** Puts the launcher right after a dismiss that left it at the card's pose. */
        void restoreLauncher();

        /** Ends the session. */
        void endSession();

        /** Done in the editor: the surface decides where it goes. */
        void setOnDone(@Nullable Runnable onDone);

        /** The page bar's listener (title by mode, Undo while dirty); told the current state at once. */
        void setPageListener(@Nullable PageListener listener);

        /** The bar's Undo: the look and the arrangement back to the state at open. */
        void undo();

        /** The bar's Done: saves, then the done runnable set with {@link #setOnDone} runs. */
        void done();

        /** The activity stopped with the surface up. */
        void onStopWhileOpen();

        /**
         * Fills in where the editor stands on its page, for the surface to remember: Look's
         * selection and whether it is at the Custom stop, Layout's place, the Icons row's scroll.
         * Nothing while it is not presented.
         */
        default void describe(@NonNull AppearanceReturnState.Builder out) {}

        /**
         * The next presentation of {@code state}'s page opens where it says (the selection and
         * the Custom stop, the place, the row's scroll) instead of at its defaults; a
         * presentation of another page forgets it. Null forgets it now.
         */
        default void restoreNext(@Nullable AppearanceReturnState state) {}
    }

    /** What the surface needs from the activity. */
    public interface Host {
        @NonNull Context context();

        /** {@code android.R.id.content}, which the host view is added to; null before it exists. */
        @Nullable ViewGroup content();

        /**
         * Builds the Overview, reading what it needs off the main thread, and hands it to
         * {@code ready} on the main thread.
         */
        void createOverview(@NonNull Navigator navigator, @NonNull Consumer<OverviewPage> ready);

        /**
         * {@link #createOverview(Navigator, Consumer)} with the card the surface remembers in the
         * middle ({@code centredSlot}, a slot's name), or the page's own choice for null.
         */
        default void createOverview(@NonNull Navigator navigator, @Nullable String centredSlot,
                                    @NonNull Consumer<OverviewPage> ready) {
            createOverview(navigator, ready);
        }

        /** Where the person last left the surface ({@link AppearanceReturnState#serialize}), or null. */
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

        /** The person closed the surface themselves (Back or the Overview's back), after {@link #onClosed}. */
        default void onClosedByUser() {}
    }

    /** Overview to editor; the editor's own frame settles in {@link AppearanceEditorFrame#ENTER_MS}. */
    static final long HOP_ENTER_MS = AppearanceEditorFrame.ENTER_MS + 40L;
    /** Editor to Overview. */
    static final long HOP_EXIT_MS = AppearanceEditorFrame.EXIT_MS + 40L;
    /** The editor's bar easing in on a direct open. */
    static final long PAGE_MS = 260L;
    /** The host fading in on open and out on close. */
    static final long FADE_MS = 180L;
    /** How far the rows travel down as they fade. */
    private static final float AWAY_DP = 72f;
    /** The pager's cards fade out this much faster than the page's background. */
    private static final float CARD_FADE = 2.2f;
    private static final float ROW_FADE = 1.5f;

    @NonNull private final Host mHost;
    @NonNull private final Editor mEditor;
    @NonNull private final Navigator mNavigator = new Navigator() {
        @Override public void openLook() {
            go(PageId.LOOK);
        }

        @Override public void openLayout() {
            go(PageId.LAYOUT);
        }

        @Override public void openIcons() {
            go(PageId.ICONS);
        }

        @Override public void close() {
            closeAnimated();
        }

        @Override public void done() {
            onDonePressed();
        }

        @Override public void back() {
            onBack();
        }
    };

    private boolean mOpen;
    /** The close under way is the person's own, so the host may take them back where they came from. */
    private boolean mClosingByUser;
    /** Opened straight into an editor: nothing behind it, so Done and Back close. */
    private boolean mDirect;
    /** Done on Look or Layout: the editor goes back to the Overview, which then closes the surface. */
    private boolean mCloseAfterHop;
    private boolean mTransitioning;
    /** Done or a Save is applying the Overview's pending changes: Done, Back and the pill wait. */
    private boolean mCommitting;
    /** Counts commits, so a backstop of an older one does nothing. */
    private int mCommitSeq;
    /** How long Done or Save may hold the surface before Done and Back work again. */
    private static final long COMMIT_BACKSTOP_MS = 20_000L;
    /** Which page is showing: the Overview, or the editor (Look, Layout or Icons by the editor's mode). */
    @Nullable private PageId mShown;
    @Nullable private FrameLayout mView;
    @Nullable private OverviewPage mOverview;
    /** Look and Layout's page bar over the scaled frame; built on the first editor open. */
    @Nullable private AppearanceEditorPage mEditorPage;
    /** colorSurface behind the scaled launcher, at index 0 of the content view, while open. */
    @Nullable private View mScrim;
    /**
     * The next Overview open covers the launcher at once (an opaque colorSurface host at full
     * alpha) and fades the page in over that cover, instead of fading the whole host in over the
     * home screen. For doors that arrive from another activity (Settings' Appearance row): the
     * launcher's own frame would otherwise show for the time the Overview takes to build.
     */
    private boolean mCoverNextOpen;

    public void coverNextOpen() {
        mCoverNextOpen = true;
    }
    /** Bumped on every open and close, so a late callback of an older surface does nothing. */
    private int mToken;
    /**
     * Where the person is has been written down for the leave under way (Done on an editor page
     * hops to the Overview before it closes, and the close must not overwrite the editor page).
     */
    private boolean mLeaveRemembered;
    @Nullable private PageId mQueued;
    private final List<Animator> mRunning = new ArrayList<>();

    public AppearanceSurfaceController(@NonNull Host host, @NonNull Editor editor) {
        mHost = host;
        mEditor = editor;
    }

    // ------------------------------------------------------------------------------------ state

    public boolean isOpen() {
        return mOpen;
    }

    /** The page on screen now (Look, Layout or Icons by the editor's mode); null while closed. */
    @Nullable
    public PageId shownPage() {
        if (!mOpen || mShown == null)
            return null;
        if (isEditorPage(mShown))
            return pageOf(mEditor.mode());
        return mShown;
    }

    private static boolean isEditorPage(@Nullable PageId page) {
        return page == PageId.LOOK || page == PageId.LAYOUT || page == PageId.ICONS;
    }

    @NonNull
    private static EditorMode modeOf(@NonNull PageId page) {
        return page == PageId.LAYOUT ? EditorMode.LAYOUT
            : page == PageId.ICONS ? EditorMode.ICONS : EditorMode.LOOK;
    }

    @NonNull
    private static PageId pageOf(@NonNull EditorMode mode) {
        return mode == EditorMode.LAYOUT ? PageId.LAYOUT
            : mode == EditorMode.ICONS ? PageId.ICONS : PageId.LOOK;
    }

    /** Whether a hop is playing: Back is spent on it and navigation waits. */
    public boolean isTransitioning() {
        return mTransitioning;
    }

    @Nullable
    @VisibleForTesting
    OverviewPage overview() {
        return mOverview;
    }

    // -------------------------------------------------------------------------------------- open

    /** {@link #open(PageId, String, PaneWallPage)} with no section and no place. */
    public void open(@NonNull PageId page) {
        open(page, null, null);
    }

    /**
     * Opens the surface on {@code page}: the Overview, or straight into Look, Layout or Icons (a corner
     * tab's door, a settings deep link naming {@code section}, a Layout door naming {@code place}).
     * Open already, it goes to that page; an editor already up moves to that mode and place.
     */
    public void open(@NonNull PageId page, @Nullable String section, @Nullable PaneWallPage place) {
        boolean editorPage = isEditorPage(page);
        if (mOpen) {
            if (editorPage && mEditor.isPresented() && !mTransitioning)
                mEditor.present(modeOf(page), place, section, 0f, 0f, null);
            else
                go(page);
            return;
        }
        ViewGroup content = mHost.content();
        if (content == null)
            return;
        mOpen = true;
        mToken++;
        mLeaveRemembered = false;
        final int token = mToken;
        mEditor.setOnDone(this::onEditorDone);
        mEditor.beginSession();
        FrameLayout view = hostView(content);
        showScrim(content);
        if (editorPage) {
            // Straight into an editor: nothing behind it. The backdrop is not covered (it is the
            // frame's picture, scaled), the host takes no touches, the bar eases
            // in and the scrim fades up behind the frame as it scales.
            mDirect = true;
            mShown = page;
            setTakesTouches(false);
            AppearanceEditorPage bar = ensureEditorPage();
            bar.root().setVisibility(View.VISIBLE);
            animateEditorPageIn();
            fadeScrim(1f);
            mEditor.present(modeOf(page), place, section, 0f, 0f, null);
            return;
        }
        mDirect = false;
        mShown = PageId.OVERVIEW;
        // Within the window since the person last left, the surface comes back where they were:
        // the Overview opens on the card it had centred, and an editor page they were on is
        // queued behind it, reached by the normal hop once the Overview stands (never a direct
        // open, which would hide the Wallpaper segment and make Done and Back close).
        final AppearanceReturnState back =
            AppearanceReturnState.freshFrom(mHost.readReturnState(), mHost.now());
        mEditor.restoreNext(null);
        if (back != null && isEditorPage(back.page)) {
            mQueued = back.page;
            mEditor.restoreNext(back);
        }
        // Every Overview open covers at once (2026-10-05, pong): the page fades in over the cover.
        final boolean cover = true;
        mCoverNextOpen = false;
        if (cover) {
            view.setBackgroundColor(MaterialColors.getColor(mHost.context(),
                com.google.android.material.R.attr.colorSurface, Color.BLACK));
            view.setAlpha(1f);
            if (mScrim != null) mScrim.setAlpha(1f);
        } else {
            view.setAlpha(0f);
        }
        mHost.createOverview(mNavigator, back == null ? null : back.wallpaperSlot, overview -> {
            if (!mOpen || token != mToken || mView != view) {
                overview.release();
                return;
            }
            mOverview = overview;
            addPage(overview.root());
            overview.onShown();
            Runnable settled = () -> {
                // The page carries its own colorSurface; the host goes clear again so the
                // editor pages' frame can show through it later.
                view.setBackground(null);
                if (mQueued != null) {
                    PageId queued = mQueued;
                    mQueued = null;
                    go(queued);
                }
            };
            if (cover)
                fadePageIn(overview.root(), settled);
            else
                fadeHost(1f, settled);
        });
    }

    @NonNull
    private FrameLayout hostView(@NonNull ViewGroup content) {
        FrameLayout view = mView;
        if (view != null)
            return view;
        view = new FrameLayout(mHost.context());
        view.setId(R.id.appearance_surface_host);
        // A page takes every touch it does not use: nothing under it (the launcher, the editor's
        // overlay) must hear a tap meant for the Overview.
        view.setClickable(true);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        final FrameLayout host = view;
        ViewCompat.setOnApplyWindowInsetsListener(host, (v, insets) -> {
            padPages(host, insets);
            return insets;
        });
        // The launcher's window consumes insets before they reach a child of the content view, so
        // dispatch alone leaves the pages under the status and navigation bars (seen at 411 dp):
        // read the window's own insets each time the host is laid out as well.
        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            WindowInsetsCompat root = ViewCompat.getRootWindowInsets(host);
            if (root != null) padPages(host, root);
        });
        mView = view;
        return view;
    }

    /** Whether the host swallows the touches its pages do not use; off while an editor page shows. */
    private void setTakesTouches(boolean takes) {
        FrameLayout view = mView;
        if (view == null)
            return;
        view.setClickable(takes);
        view.setFocusable(takes);
    }

    // ------------------------------------------------------------------- scrim and editor page

    /**
     * An opaque colorSurface view under the launcher's container while the surface is open: what
     * shows around the scaled frame, and what hides the system wallpaper in passthrough mode. It
     * replaces swapping the window's background, a relayout of a window that shows the wallpaper.
     * Starts clear and fades up with the host (or by itself for a direct editor open).
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
        final View scrim = mScrim;
        if (scrim == null)
            return;
        if (ReducedMotion.isEnabled(mHost.context())) {
            scrim.setAlpha(alpha);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(scrim.getAlpha(), alpha);
        animator.setDuration(FADE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> scrim.setAlpha((Float) a.getAnimatedValue()));
        track(animator, () -> scrim.setAlpha(alpha));
        animator.start();
    }

    /**
     * The scrim leaves with the surface. When the editor's frame is still scaled the session's end
     * brings it back to full size first, so the scrim stays up for that long: the frame never
     * grows over a bare window.
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
        scrim.postDelayed(() -> parent.removeView(scrim),
            AppearanceEditorFrame.EXIT_MS + 80L);
    }

    @NonNull
    private AppearanceEditorPage ensureEditorPage() {
        AppearanceEditorPage page = mEditorPage;
        if (page != null)
            return page;
        final AppearanceEditorPage built = new AppearanceEditorPage(mHost.context(),
            new AppearanceEditorPage.Callbacks() {
                @Override public void onBack() {
                    AppearanceSurfaceController.this.onBack();
                }

                @Override public void onUndo() {
                    if (!mTransitioning && !mCommitting)
                        mEditor.undo();
                }

                @Override public void onDone() {
                    onDonePressed();
                }

                @Override public void onSegment(@NonNull AppearanceEditorPage.Segment segment) {
                    if (mTransitioning || mCommitting)
                        return;
                    if (segment == AppearanceEditorPage.Segment.WALLPAPER) {
                        // Leaving for the Overview asks the unsaved-changes question; the pill is
                        // on Wallpaper only once the answer lets it go.
                        if (!mDirect)
                            go(PageId.OVERVIEW);
                    } else {
                        mLeaveRemembered = false;
                        mEditor.setMode(segment == AppearanceEditorPage.Segment.LAYOUT ? EditorMode.LAYOUT
                            : segment == AppearanceEditorPage.Segment.ICON_PACK ? EditorMode.ICONS
                            : EditorMode.LOOK);
                    }
                }
            });
        built.setWallpaperAvailable(!mDirect);
        mEditorPage = built;
        built.root().setVisibility(View.INVISIBLE);
        addPage(built.root());
        mEditor.setPageListener(new PageListener() {
            @Override public void onModeChanged(@NonNull EditorMode mode) {
                built.setMode(mode);
            }

            @Override public void onDirtyChanged(boolean dirty) {
                built.setDirty(dirty);
            }

            @Override public void onFrameWallpaperMissing() {
                fadeScrim(0f);
            }
        });
        return built;
    }

    /**
     * The editor page's bar at {@code in} (0 hidden, 1 at rest) on linear time for the fade, and
     * {@code travel} for the quarter-width slide: the slide and fade of a page arriving.
     */
    private void applyEditorPage(float in, float travel) {
        AppearanceEditorPage page = mEditorPage;
        FrameLayout host = mView;
        if (page == null)
            return;
        float shift = (host == null ? 0 : host.getWidth()) * 0.25f;
        View root = page.root();
        root.setAlpha(clamp01((in - 0.2f) * 1.25f));
        root.setTranslationX(shift * (1f - travel));
    }

    /** The bar of a direct editor open eases in; instant with animations off. */
    private void animateEditorPageIn() {
        if (ReducedMotion.isEnabled(mHost.context())) {
            applyEditorPage(1f, 1f);
            return;
        }
        final Interpolator settle = Motion.settle();
        applyEditorPage(0f, 0f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(PAGE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            applyEditorPage(f, settle.getInterpolation(f));
        });
        track(animator, () -> applyEditorPage(1f, 1f));
        animator.start();
    }

    private void addPage(@NonNull View root) {
        FrameLayout view = mView;
        if (view == null)
            return;
        if (root.getParent() != view)
            view.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        ViewCompat.requestApplyInsets(view);
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
        if (insets != null) padPages(view, insets);
    }

    /**
     * The system bars over the content, as pages' padding (the pages' backgrounds run under the
     * bars): the content view may already stand clear of a bar, so only the part over it counts.
     */
    private void padPages(@NonNull FrameLayout host, @NonNull WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
            | WindowInsetsCompat.Type.displayCutout());
        int[] at = new int[2];
        host.getLocationInWindow(at);
        View decor = host.getRootView();
        int below = decor == null ? 0 : Math.max(0, decor.getHeight() - (at[1] + host.getHeight()));
        int top = Math.max(0, bars.top - at[1]);
        int bottom = Math.max(0, bars.bottom - below);
        for (int i = 0; i < host.getChildCount(); i++)
            host.getChildAt(i).setPadding(bars.left, top, bars.right, bottom);
    }

    // ------------------------------------------------------------------------------- navigation

    /** Goes to {@code target}; while a hop plays or the Overview is still loading it waits its turn. */
    private void go(@NonNull PageId target) {
        if (!mOpen || mCommitting)
            return;
        if (mTransitioning || (mOverview == null && !mDirect && mShown == PageId.OVERVIEW)) {
            mQueued = target;
            return;
        }
        PageId from = shownPage();
        if (from == null || from == target)
            return;
        // A leave the person called off (Keep editing) is behind them once they move on.
        mLeaveRemembered = false;
        boolean toEditor = isEditorPage(target);
        boolean fromEditor = isEditorPage(from);
        if (fromEditor && toEditor) {
            // Look, Layout and Icons are one presentation: the mode changes, the frame stays.
            mEditor.present(modeOf(target), null, null, 0f, 0f, null);
            return;
        }
        if (from == PageId.OVERVIEW && toEditor) {
            hopIntoEditor(modeOf(target));
        } else if (fromEditor && target == PageId.OVERVIEW && !mDirect) {
            mEditor.requestLeave(this::hopToOverview);
        }
    }

    /** Back, from the overlay registry. True when it was spent here. */
    public boolean onBack() {
        if (!mOpen)
            return false;
        if (mTransitioning || mCommitting)
            return true;
        PageId page = shownPage();
        if (page == null || page == PageId.OVERVIEW) {
            rememberWhere();
            leaveOverview(this::closeAnimated);
        } else {
            if (mDirect)
                rememberWhere();
            mEditor.requestLeave(mDirect ? this::closeAnimated : this::hopToOverview);
        }
        return true;
    }

    /** A HOME press: the editor is left through its own unsaved-changes rule, then the surface closes. */
    public void requestExit() {
        if (!mOpen)
            return;
        if (mCommitting)
            return;
        rememberWhere();
        PageId page = shownPage();
        if (isEditorPage(page)) {
            if (hasPendingWallpaper())
                mEditor.confirmLeave(() -> commitThen(this::closeNow), this::closeNow);
            else
                mEditor.requestLeave(this::closeNow);
        } else {
            leaveOverview(this::closeNow);
        }
    }

    private boolean hasPendingWallpaper() {
        OverviewPage overview = mOverview;
        return overview != null && overview.hasPendingChanges();
    }

    /**
     * Leaves the Overview for good ({@code leave}): at once when nothing is
     * pending, else after the one question (Keep editing / Discard / Save), where Discard drops
     * the pending choices (the page's release does) and Save applies them first.
     */
    private void leaveOverview(@NonNull Runnable leave) {
        if (!hasPendingWallpaper()) {
            leave.run();
            return;
        }
        mEditor.confirmLeave(() -> commitThen(leave), leave);
    }

    /**
     * Applies the Overview's pending changes, then runs {@code then}. The surface waits while it
     * runs; a failure leaves everything as it was, the page having told the person.
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
        // A wallpaper set that never answers must not hold the surface: Done and Back work again.
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

    /**
     * Done, from any page: the editor (Look, Layout or Icons) saves its look first; then the Overview's pending changes
     * are applied with the Overview showing (its progress is where the wait is seen), and the
     * surface closes. A failure stays on the Overview.
     */
    private void onDonePressed() {
        if (!mOpen || mTransitioning || mCommitting)
            return;
        PageId page = shownPage();
        if (isEditorPage(page)) {
            mEditor.done();
        } else {
            rememberWhere();
            finishFromOverview();
        }
    }

    /** On the Overview: applies what is pending, then closes. */
    private void finishFromOverview() {
        commitThen(this::closeAnimated);
    }

    /** The activity stopped: the surface survives it, the status pane's borrowed shape goes back. */
    public void onStop() {
        if (mOpen)
            mEditor.onStopWhileOpen();
    }

    /** Done in the editor: the look is saved already; the surface closes, over the Overview for an opened-through one. */
    private void onEditorDone() {
        if (!mOpen || mTransitioning)
            return;
        // The editor page is where they were, though the close runs from the Overview.
        rememberWhere();
        if (mDirect) {
            closeAnimated();
        } else {
            mCloseAfterHop = true;
            hopToOverview();
        }
    }

    /**
     * Writes down where the person is (the page, the Overview's centred card, and what the editor
     * says of its own page) with the time, so the next Overview open within the window comes
     * back here. Every way out calls it before anything is taken down.
     */
    private void rememberWhere() {
        PageId page = shownPage();
        if (!mOpen || page == null)
            return;
        AppearanceReturnState.Builder where = new AppearanceReturnState.Builder(page);
        OverviewPage overview = mOverview;
        if (overview != null)
            where.wallpaperSlot(overview.centredSlot());
        if (isEditorPage(page) && mEditor.isPresented())
            mEditor.describe(where);
        mHost.writeReturnState(where.build(mHost.now()).serialize());
        mLeaveRemembered = true;
    }

    // ---------------------------------------------------------------------------------- the hops

    /** Overview to Look, Layout or Icons. */
    private void hopIntoEditor(@NonNull EditorMode mode) {
        final OverviewPage overview = mOverview;
        if (overview == null)
            return;
        mTransitioning = true;
        final int token = mToken;
        mEditor.awaitWallpaper(() -> {
            if (!mOpen || token != mToken)
                return;
            float[] pose = cardPose(overview);
            // The bar does not move between pages: the editor's bar stands over the Overview's at
            // once, with only the pill's selection and the end actions different.
            ensureEditorPage().setMode(mode);
            applyEditorPage(1f, 1f);
            final int[] pending = {2};
            Runnable done = () -> {
                if (--pending[0] > 0 || !mOpen || token != mToken)
                    return;
                overview.root().setVisibility(View.INVISIBLE);
                overview.onHidden();
                mShown = pageOf(mode);
                mTransitioning = false;
                // The editor's own overlay lies under this host: with no page of ours showing the
                // host must not take touches, or a tap on a launcher element never reaches it.
                setTakesTouches(false);
            };
            mEditor.present(mode, null, null, pose == null ? 0f : pose[0],
                pose == null ? 0f : pose[1], done);
            if (!mEditor.isPresented()) {
                // The editor could not open (no preferences, no container): stay on the Overview.
                mTransitioning = false;
                return;
            }
            AppearanceEditorPage bar = mEditorPage;
            if (bar != null)
                bar.root().setVisibility(View.VISIBLE);
            overview.setBarVisible(false);
            animateOverview(overview, true, HOP_ENTER_MS, done);
        });
    }

    /** Look, Layout or Icons to Overview: the editor goes down to the card while the Overview comes back. */
    private void hopToOverview() {
        final OverviewPage overview = mOverview;
        if (overview == null || !mOpen) {
            closeAnimated();
            return;
        }
        mTransitioning = true;
        final int token = mToken;
        float[] pose = cardPose(overview);
        final int[] pending = {2};
        // The pill goes to Wallpaper as the editor leaves; the editor's bar stays until the
        // Overview's own takes its place at the end.
        if (mEditorPage != null) {
            mEditorPage.showWallpaper();
            applyEditorPage(1f, 1f);
        }
        overview.setBarVisible(false);
        final boolean closeAfter = mCloseAfterHop;
        mCloseAfterHop = false;
        Runnable done = () -> {
            if (--pending[0] > 0 || !mOpen || token != mToken)
                return;
            mShown = PageId.OVERVIEW;
            mTransitioning = false;
            AppearanceEditorPage bar = mEditorPage;
            if (bar != null)
                bar.root().setVisibility(View.INVISIBLE);
            overview.setBarVisible(true);
            // The Overview covers the launcher again; only now is it put right, on the next frame
            // so it never lands on the last frame of the hop. A Done on Look or Layout closes the
            // surface from here, over the Overview, as a Done on the Overview does.
            View view = mView;
            Runnable restore = () -> {
                mEditor.restoreLauncher();
                if (closeAfter && mOpen && token == mToken)
                    finishFromOverview();
            };
            if (view != null)
                view.postOnAnimation(restore);
            else
                restore.run();
        };
        setTakesTouches(true);
        overview.root().setVisibility(View.VISIBLE);
        overview.onShown();
        applyAway(overview, 1f, 1f);
        if (mEditorPage != null)
            mEditorPage.root().setVisibility(View.VISIBLE);
        mEditor.dismiss(pose == null ? -1f : pose[0], pose == null ? 0f : pose[1], done);
        animateOverview(overview, false, HOP_EXIT_MS, done);
    }

    /**
     * Where the editor's frame stands when it is the Home card: the container's scale and offset
     * (about its top-centre pivot) that put it at the card's rect. Null when the card or the
     * container is not laid out, and the frame then grows from, and returns to, full size.
     */
    @Nullable
    private float[] cardPose(@NonNull OverviewPage overview) {
        ViewGroup content = mHost.content();
        View card = overview.sharedCard();
        View root = content == null ? null : content.findViewById(R.id.terminal_root_container);
        if (card == null || root == null || card.getWidth() <= 0 || root.getWidth() <= 0
            || !(root.getParent() instanceof View))
            return null;
        int[] cardAt = new int[2];
        int[] contentAt = new int[2];
        card.getLocationInWindow(cardAt);
        content.getLocationInWindow(contentAt);
        int[] parentOffset = new int[2];
        if (!AppearanceEditorFrame.offsetIn((View) root.getParent(), content, parentOffset))
            return null;
        float containerTop = parentOffset[1] + root.getTop();
        float cardTop = cardAt[1] - contentAt[1];
        return new float[] {card.getWidth() / (float) root.getWidth(), cardTop - containerTop};
    }

    /**
     * The Overview's half of a hop, on one clock: the rows travel down on the app's settle curve
     * while fading on linear time (easing shapes distance, never fades), the cards and the top bar
     * fade, and the background gives way to the launcher's frame. {@code away} leaves, else it
     * returns. Instant with animations off.
     */
    private void animateOverview(@NonNull OverviewPage overview, boolean away, long ms,
                                 @NonNull Runnable end) {
        if (ms <= 0L || ReducedMotion.isEnabled(mHost.context())) {
            applyAway(overview, away ? 1f : 0f, away ? 1f : 0f);
            end.run();
            return;
        }
        final Interpolator settle = Motion.settle();
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ms);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            float travel = settle.getInterpolation(f);
            applyAway(overview, away ? f : 1f - f, away ? travel : 1f - travel);
        });
        track(animator, () -> {
            applyAway(overview, away ? 1f : 0f, away ? 1f : 0f);
            end.run();
        });
        animator.start();
    }

    /**
     * The Overview at {@code linear} (0 at rest, 1 gone) for the fades, {@code travel} for the
     * distance the rows have gone down.
     */
    private void applyAway(@NonNull OverviewPage overview, float linear, float travel) {
        float distance = AWAY_DP * mHost.context().getResources().getDisplayMetrics().density;
        for (View row : overview.leavingViews()) {
            row.setTranslationY(distance * travel);
            row.setAlpha(1f - clamp01(linear * ROW_FADE));
        }
        for (View view : overview.fadingViews())
            view.setAlpha(1f - clamp01(linear * CARD_FADE));
        overview.setBackgroundAlpha(1f - clamp01(linear));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ------------------------------------------------------------------------------------ close

    /** The Overview's own close, or an editor's Done for a direct open: fades out, then ends. */
    private void closeAnimated() {
        if (!mOpen || mTransitioning)
            return;
        mClosingByUser = true;
        FrameLayout view = mView;
        if (view == null || mShown != PageId.OVERVIEW || ReducedMotion.isEnabled(mHost.context())) {
            closeNow();
            return;
        }
        mTransitioning = true;
        // The session ends (the launcher is dressed again, its wallpaper and opaque window put
        // back) while the Overview still covers it fully; the fade then shows a finished launcher.
        mEditor.endSession();
        fadeHost(0f, this::closeNow);
    }

    /** Closes at once: the pages let go, the backdrop shows again, the session ends. */
    public void closeNow() {
        if (!mOpen)
            return;
        // A close nobody announced (the photo picker's hand-off, a failed load) is a leave too.
        if (!mLeaveRemembered)
            rememberWhere();
        mEditor.restoreNext(null);
        mOpen = false;
        mToken++;
        mTransitioning = false;
        mQueued = null;
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
        mEditor.setPageListener(null);
        mEditorPage = null;
        removeScrim(mEditor.isPresented());
        mShown = null;
        mDirect = false;
        mCloseAfterHop = false;
        mCommitting = false;
        mEditor.setOnDone(null);
        mEditor.endSession();
        boolean byUser = mClosingByUser;
        mClosingByUser = false;
        mHost.onClosed();
        if (byUser)
            mHost.onClosedByUser();
    }

    /** Fades one page in over an already opaque host (a covered open). */
    private void fadePageIn(@NonNull View page, @NonNull Runnable end) {
        if (ReducedMotion.isEnabled(mHost.context())) {
            page.setAlpha(1f);
            end.run();
            return;
        }
        page.setAlpha(0f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(FADE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> page.setAlpha((Float) a.getAnimatedValue()));
        track(animator, () -> {
            page.setAlpha(1f);
            end.run();
        });
        animator.start();
    }

    private void fadeHost(float alpha, @NonNull Runnable end) {
        FrameLayout view = mView;
        if (view == null) {
            end.run();
            return;
        }
        final View scrim = mScrim;
        if (ReducedMotion.isEnabled(mHost.context())) {
            view.setAlpha(alpha);
            if (scrim != null) scrim.setAlpha(alpha);
            end.run();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(view.getAlpha(), alpha);
        animator.setDuration(FADE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            view.setAlpha(f);
            if (scrim != null) scrim.setAlpha(f);
        });
        track(animator, () -> {
            view.setAlpha(alpha);
            if (scrim != null) scrim.setAlpha(alpha);
            end.run();
        });
        animator.start();
    }

    /** Runs {@code end} when {@code animator} ends; nothing when it is cancelled (a close). */
    private void track(@NonNull ValueAnimator animator, @NonNull Runnable end) {
        mRunning.add(animator);
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(Animator a) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                mRunning.remove(a);
                if (!mCancelled)
                    end.run();
            }
        });
    }
}
