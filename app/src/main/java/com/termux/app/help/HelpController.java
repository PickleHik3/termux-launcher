package com.termux.app.help;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.termux.app.wall.PaneWallPage;

/**
 * Help's whole lifecycle, so the launcher only ever calls a controller: the corner overview, the
 * explorer that runs over the live launcher, and the hand-off to and from {@link HelpActivity},
 * where the reading happens now.
 *
 * <p>Nothing here switches places, writes a preference or touches a session: an explicit action
 * button is the only thing that does anything, and everything it starts either runs on the
 * launcher (explore, show a control, practise a lesson) or opens the reading screen.
 */
public final class HelpController {

    /** The launcher's side of help: what happens when help goes away, and reopening the screen. */
    public interface Host {
        /** Help came up or went away: the run's probe, and the focus the terminal wants back. */
        void onHelpVisibilityChanged(boolean showing);
        /**
         * Reading happens on {@link HelpActivity}, not over the launcher: open it. A topic id
         * lands on that page; null lands the reader back where they left off.
         */
        void openHelpScreen(PaneWallPage place, @Nullable String topicId);
    }

    /** Where "Try it" sends the reader; help is already down by the time this is called. */
    public interface PracticeListener {
        void onPracticeRequested(String lessonId);
    }

    /**
     * The explorer's way back into the reading side — the B–C seam of
     * {@code project-docs/plans/help-guide.md}. Phase C's {@code HelpOverlayView} declares the
     * same five calls.
     */
    public interface ExploreListener {
        /** A card tapped in the overview, or "Read topic" on the seated card. */
        void onReadTopic(String topicId);
        /** The overview's Guide button: the reading screen, at its home page. */
        void onOpenGuide();
        /** Back to help, from the toolbar or from Back with nothing selected. */
        void onBackToHelp();
        void onCloseHelp();
        /** The selected control vanished after a relayout. */
        void onTargetGone(String topicId);
        /** Placement found no seat for the card: the topic page has to explain it instead. */
        void onCardDoesNotFit(String topicId);
    }

    /** The explorer, as the reading side needs it. Phase C's {@code HelpOverlayView} answers it. */
    public interface Explorer {
        void setExploreListener(ExploreListener listener);
        /** The curated overview help opens on: a few cards at once over the live launcher. */
        void overview(PaneWallPage place);
        /** Enter exploring for a place; {@code selectTopicId} pre-selects, or is null. */
        void explore(PaneWallPage place, @Nullable String selectTopicId);
        /** Explore with the topic selected and its gesture playing. */
        void demonstrate(PaneWallPage place, String topicId);
        boolean isShowing();
        void dismiss();
        /** True when the explorer consumed Back; false hands it back to the controller. */
        boolean onBackPressed();
    }

    private final Context context;
    private final ViewGroup viewHost;
    private final HelpTargets.ViewFinder finder;
    private final Host host;
    private final HelpNavigation navigation = new HelpNavigation();
    @Nullable private final Explorer injectedExplorer;

    private Explorer explorer;
    private PracticeListener practiceListener;
    private boolean practiceAvailable = true;
    /** Whether the overlay is up as the overview rather than as "Explore this screen". */
    private boolean overviewShowing;
    /**
     * Whether the overlay is running on behalf of the help screen. It was the reader's page that
     * sent them here, so closing the overlay takes them back to that page.
     */
    private boolean fromHelpScreen;
    /**
     * True once the screen has been asked for and nothing has opened help again since. The
     * explorer can report itself closed more than one way, and the reader asked once.
     */
    private boolean handedBack;

    public HelpController(Context context, ViewGroup viewHost, HelpTargets.ViewFinder finder,
                          Host host) {
        this(context, viewHost, finder, host, null);
    }

    @VisibleForTesting
    public HelpController(Context context, ViewGroup viewHost, HelpTargets.ViewFinder finder,
                          Host host, @Nullable Explorer explorer) {
        this.context = context;
        this.viewHost = viewHost;
        this.finder = finder;
        this.host = host;
        this.injectedExplorer = explorer;
    }

    public void setPracticeListener(@Nullable PracticeListener listener) {
        this.practiceListener = listener;
    }

    /**
     * Whether "Try it" is offered at all. A first-run run already partway through a lesson cannot
     * take one, and a dead button is kinder than a lost run.
     */
    public void setPracticeAvailable(boolean available) {
        this.practiceAvailable = available;
    }

    // ---- opening and closing -----------------------------------------------------------------

    /**
     * The corner tab, Settings and the palette all land here: a few curated cards over the
     * reader's own screen, with the whole guide one Guide button behind it.
     */
    public void show(@Nullable PaneWallPage place) {
        ensureViews();
        closeExplorer();
        fromHelpScreen = false;
        handedBack = false;
        navigation.open(place == null ? PaneWallPage.TERMINAL : place);
        overviewShowing = true;
        explorer.overview(navigation.place());
        host.onHelpVisibilityChanged(true);
    }

    public boolean isShowing() {
        return explorer != null && explorer.isShowing();
    }

    /** @return true when help was up and is now down, so a Back registry can stop looking. */
    public boolean dismiss() {
        if (!isShowing()) return false;
        overviewShowing = false;
        fromHelpScreen = false;
        explorer.dismiss();
        host.onHelpVisibilityChanged(false);
        HelpLog.d("help dismissed");
        return true;
    }

    /** Back while the explorer is up: the explorer first, then the overview or the screen. */
    public boolean onBackPressed() {
        if (!isShowing()) return false;
        if (explorer.onBackPressed()) return true;
        // The overview is where help opens, so there is nothing behind it but the launcher.
        if (overviewShowing) dismiss();
        else backToHelp();
        return true;
    }

    // ---- practice ----------------------------------------------------------------------------

    /** The end of a practice run: the reader goes back to the screen that offered it. */
    public void onPracticeEnded() {
        if (!navigation.isPracticing()) return;
        HelpNavigation.Frame frame = navigation.practiceEnd();
        if (frame == null) return;
        handBack(frame.screen == HelpNavigation.Screen.TOPIC ? frame.id : null);
    }

    @VisibleForTesting
    boolean isPracticing() {
        return navigation.isPracticing();
    }

    // ---- what the help screen hands back -----------------------------------------------------

    /**
     * "Explore this screen", "Show on screen" or "Show the gesture", asked for on the help screen:
     * the overlay runs over the live launcher as it always has, and closing it returns the reader
     * to the page they asked from.
     *
     * @param topicId the control to select, or null to explore the whole screen.
     * @param gesture whether the topic's gesture plays as well.
     */
    public void exploreFromHelpScreen(@Nullable PaneWallPage place, @Nullable String topicId,
                                      boolean gesture) {
        ensureViews();
        closeExplorer();
        navigation.open(place == null ? PaneWallPage.TERMINAL : place);
        fromHelpScreen = true;
        handedBack = false;
        enterExplore(topicId, gesture);
        host.onHelpVisibilityChanged(true);
    }

    /**
     * "Try it", asked for on the help screen: the lesson runs on the launcher, and the frame the
     * reader was on is held so {@link #onPracticeEnded()} can take them back to it.
     */
    public void practiseFromHelpScreen(@Nullable PaneWallPage place, @Nullable String topicId,
                                       String lessonId) {
        if (lessonId == null) return;
        ensureViews();
        closeExplorer();
        navigation.open(place == null ? PaneWallPage.TERMINAL : place);
        if (topicId != null) navigation.topic(resolve(topicId));
        navigation.practiceStart();
        fromHelpScreen = true;
        handedBack = false;
        if (practiceListener != null) practiceListener.onPracticeRequested(lessonId);
    }

    // ---- the explorer ------------------------------------------------------------------------

    private final ExploreListener exploreListener = new ExploreListener() {
        @Override public void onReadTopic(String topicId) { readInstead(topicId); }

        @Override public void onOpenGuide() { openGuide(); }

        @Override public void onBackToHelp() { backToHelp(); }

        @Override public void onCloseHelp() {
            // The reader came here from a page of the guide: × on the overlay is done with the
            // launcher, not done with help, so it takes them back to that page.
            if (fromHelpScreen) handBack(null);
            else dismiss();
        }

        @Override public void onTargetGone(String topicId) { readInstead(topicId); }

        @Override public void onCardDoesNotFit(String topicId) { readInstead(topicId); }
    };

    private void enterExplore(@Nullable String topicId, boolean gesture) {
        ensureViews();
        String id = topicId == null ? null : resolve(topicId);
        navigation.explore(id);
        overviewShowing = false;
        if (gesture && id != null) explorer.demonstrate(navigation.place(), id);
        else explorer.explore(navigation.place(), id);
    }

    /** The Guide button on the overview: the whole guide, on its own screen. */
    private void openGuide() {
        handBack(null);
    }

    /**
     * Help's overlays go down and the reading screen comes up. A topic id lands the reader on that
     * page; null lands them wherever they were reading when they asked for the launcher.
     */
    private void handBack(@Nullable String topicId) {
        if (handedBack) return;
        handedBack = true;
        PaneWallPage place = navigation.place();
        fromHelpScreen = false;
        // Dismissed before the stack is tidied: dismiss() is the one path that tells the host
        // help has gone away, and it only speaks while something is still up.
        dismiss();
        while (navigation.screen() == HelpNavigation.Screen.EXPLORE && navigation.depth() > 1)
            navigation.back();
        host.openHelpScreen(place, topicId);
    }

    /** The explorer could not seat a card, or the reader asked to read: the screen, at that topic. */
    private void readInstead(@Nullable String topicId) {
        handBack(topicId == null ? null : resolve(topicId));
    }

    /** "Back to help" off the explorer: the screen, wherever the reader left it. */
    private void backToHelp() {
        handBack(null);
    }

    /** A fresh invocation over an explorer that is still up: one overlay at a time. */
    private void closeExplorer() {
        if (explorer != null && explorer.isShowing()) explorer.dismiss();
    }

    // ---- plumbing ----------------------------------------------------------------------------

    private void ensureViews() {
        if (explorer != null) return;
        explorer = injectedExplorer != null ? injectedExplorer
            : overlayExplorer(context, viewHost, finder);
        explorer.setExploreListener(exploreListener);
    }

    /** A topic id or the target id the explorer names a control by; one lookup answers both. */
    private String resolve(String id) {
        HelpTopics.Entry entry = HelpTopics.entry(navigation.place(), id);
        return entry == null ? id : entry.id;
    }

    @VisibleForTesting
    HelpNavigation navigation() {
        return navigation;
    }

    // ---- the explorer -----------------------------------------------------------------------

    /** The explore overlay, created on first use and added over the same host as everything else. */
    private static Explorer overlayExplorer(Context context, ViewGroup host,
                                            HelpTargets.ViewFinder finder) {
        return new Explorer() {
            private HelpOverlayView view;

            private HelpOverlayView view() {
                if (view != null) return view;
                view = new HelpOverlayView(context, finder);
                host.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return view;
            }

            @Override public void setExploreListener(ExploreListener listener) {
                view().setExploreListener(listener == null ? null : new HelpOverlayView.ExploreListener() {
                    @Override public void onReadTopic(String topicId) { listener.onReadTopic(topicId); }
                    @Override public void onOpenGuide() { listener.onOpenGuide(); }
                    @Override public void onBackToHelp() { listener.onBackToHelp(); }
                    @Override public void onCloseHelp() { listener.onCloseHelp(); }
                    @Override public void onTargetGone(String topicId) { listener.onTargetGone(topicId); }
                    @Override public void onCardDoesNotFit(String topicId) { listener.onCardDoesNotFit(topicId); }
                });
            }

            @Override public void overview(PaneWallPage place) {
                view().overview(place);
            }

            @Override public void explore(PaneWallPage place, @Nullable String selectTopicId) {
                view().explore(place, selectTopicId);
            }

            @Override public void demonstrate(PaneWallPage place, String topicId) {
                view().demonstrate(place, topicId);
            }

            @Override public boolean isShowing() {
                return view != null && view.isShowing();
            }

            @Override public void dismiss() {
                if (view != null) view.dismiss();
            }

            @Override public boolean onBackPressed() {
                return view != null && view.onBackPressed();
            }
        };
    }
}
