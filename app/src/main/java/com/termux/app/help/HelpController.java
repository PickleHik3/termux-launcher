package com.termux.app.help;

import android.content.Context;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.interact.ShareUtils;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Help's whole lifecycle, so the launcher only ever calls a controller: which page is showing,
 * what Back means, who owns the keyboard, when the explorer takes over, and where practice comes
 * back to.
 *
 * <p>The reading panel and the explorer are two views over one navigation stack. The panel is
 * hidden while the explorer is up and comes back with its scroll where it was. Nothing here
 * switches places, writes a preference or touches a session: help reads, and only an explicit
 * action button does anything.
 */
public final class HelpController {

    /** The launcher's side of help: the input hand-off, and what happens when help goes away. */
    public interface Host {
        /** Give this field the configured keyboard, through the launcher's own input path. */
        void beginHelpTextInput(EditText field);
        /** Put the keyboard back the way help found it. */
        void endHelpTextInput();
        /** Help came up or went away: the run's probe, and the focus the terminal wants back. */
        void onHelpVisibilityChanged(boolean showing);
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
        /** The overview's Guide button: the reading sheet, at its home page. */
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

    private HelpPanelView panel;
    private HelpTargets targets;
    private Explorer explorer;
    private PracticeListener practiceListener;
    private boolean practiceAvailable = true;
    /** The target ids measured on the last pass; what "On this screen" and a hidden topic read. */
    private Set<String> measured = Collections.emptySet();
    private boolean watchingLayout;
    /** Whether the overlay is up as the overview rather than as "Explore this screen". */
    private boolean overviewShowing;
    /** A remeasurement mid-render would ask for another one; one pass at a time. */
    private boolean rendering;

    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener =
        new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                // Layout changes, not frames: a moved control is remeasured, and the page is
                // redrawn only when the set of controls on screen actually changed.
                if (panel == null || !panel.isShowing() || rendering) return;
                if (remeasure()) render();
            }
        };

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
     * Every entry point: the corner tab, Settings, the palette. Help opens on the overview — a few
     * cards on the reader's own screen — and the reading sheet is one button behind it.
     */
    public void show(@Nullable PaneWallPage place) {
        ensureViews();
        closeExplorer();
        navigation.open(place == null ? PaneWallPage.TERMINAL : place);
        remeasure();
        endTextEntry();
        panel.hide();
        render();
        overviewShowing = true;
        explorer.overview(navigation.place());
        host.onHelpVisibilityChanged(true);
    }

    /** A Learn-more link: the topic itself, and Back leaves help rather than stacking a home page. */
    public void showTopic(@Nullable PaneWallPage place, String topicId) {
        ensureViews();
        closeExplorer();
        navigation.openTopic(place == null ? PaneWallPage.TERMINAL : place, topicId);
        remeasure();
        panel.show();
        render();
        watchLayout();
        host.onHelpVisibilityChanged(true);
    }

    public boolean isShowing() {
        if (panel == null) return false;
        return panel.isShowing() || (explorer != null && explorer.isShowing());
    }

    /** @return true when help was up and is now down, so a Back registry can stop looking. */
    public boolean dismiss() {
        if (!isShowing()) return false;
        endTextEntry();
        overviewShowing = false;
        if (explorer != null) explorer.dismiss();
        panel.hide();
        unwatchLayout();
        host.onHelpVisibilityChanged(false);
        HelpLog.d("help dismissed");
        return true;
    }

    /**
     * Back, in the one order help has: an open definition, then text entry, then a step of
     * navigation, then help itself. While the explorer is up it gets the press first.
     */
    public boolean onBackPressed() {
        if (!isShowing()) return false;
        if (explorer != null && explorer.isShowing()) {
            if (explorer.onBackPressed()) return true;
            // The overview is where help opens, so there is nothing behind it but the launcher.
            if (overviewShowing) dismiss();
            else backToHelp();
            return true;
        }
        if (navigation.frame().openTermId != null) {
            navigation.closeTerm();
            render();
            return true;
        }
        if (navigation.textEntryActive()) {
            endTextEntry();
            render();
            return true;
        }
        if (navigation.back()) {
            render();
            return true;
        }
        dismiss();
        return true;
    }

    // ---- practice ----------------------------------------------------------------------------

    /**
     * The end of a practice run: the reader goes back to the topic that offered it.
     *
     * <p>Nothing in the launcher calls this yet — the tour has no end-of-practice signal the
     * activity can hear without reaching into its package (see D8 in the spec). The frame is kept
     * and restored here so one call is all that is needed once a signal exists.
     */
    public void onPracticeEnded() {
        if (!navigation.isPracticing()) return;
        HelpNavigation.Frame frame = navigation.practiceEnd();
        if (frame == null) return;
        ensureViews();
        remeasure();
        panel.show();
        render();
        watchLayout();
        host.onHelpVisibilityChanged(true);
    }

    @VisibleForTesting
    boolean isPracticing() {
        return navigation.isPracticing();
    }

    // ---- what the panel asks for -------------------------------------------------------------

    private final HelpPanelView.Listener panelListener = new HelpPanelView.Listener() {
        @Override public void onBack() { onBackPressed(); }

        @Override public void onClose() { dismiss(); }

        @Override public void onHome() {
            // Leaving the page the field was on: the keyboard goes back the way help found it,
            // or the flag outlives the field and swallows the next Back.
            endTextEntry();
            navigation.home();
            render();
        }

        @Override public void onSearch() {
            navigation.search();
            render();
        }

        @Override public void onQueryChanged(String text) {
            navigation.setQuery(text);
            // Only the results move; the field keeps its caret and its keyboard.
            panel.renderList(navigation);
        }

        @Override public void onTextEntry(EditText field) {
            if (navigation.textEntryActive()) return;
            navigation.setTextEntryActive(true);
            host.beginHelpTextInput(field);
        }

        @Override public void onTopic(String topicId) {
            endTextEntry();
            navigation.topic(resolve(topicId));
            render();
        }

        @Override public void onGlossary() {
            endTextEntry();
            navigation.glossary();
            render();
        }

        @Override public void onTerm(String termId) {
            if (termId != null && termId.equals(navigation.frame().openTermId))
                navigation.closeTerm();
            else navigation.openTerm(termId);
            render();
        }

        @Override public void onExplore() { enterExplore(null, false); }

        @Override public void onShowOnScreen(String topicId) { enterExplore(topicId, false); }

        @Override public void onShowGesture(String topicId) { enterExplore(topicId, true); }

        @Override public void onTryIt(@Nullable String topicId, String lessonId) {
            if (lessonId == null) return;
            if (topicId != null) navigation.topic(resolve(topicId));
            // Where the reader was, so End practice has somewhere to bring them back to.
            navigation.practiceStart();
            dismiss();
            if (practiceListener != null) practiceListener.onPracticeRequested(lessonId);
        }

        @Override public void onLink(String url) {
            ShareUtils.openUrl(context, url);
        }

        @Override public void onScroll(int y) {
            navigation.setScroll(y);
        }
    };

    // ---- the explorer ------------------------------------------------------------------------

    private final ExploreListener exploreListener = new ExploreListener() {
        @Override public void onReadTopic(String topicId) { readInstead(topicId); }

        @Override public void onOpenGuide() { openGuide(); }

        @Override public void onBackToHelp() { backToHelp(); }

        @Override public void onCloseHelp() { dismiss(); }

        @Override public void onTargetGone(String topicId) { readInstead(topicId); }

        @Override public void onCardDoesNotFit(String topicId) { readInstead(topicId); }
    };

    private void enterExplore(@Nullable String topicId, boolean gesture) {
        ensureViews();
        endTextEntry();
        String id = topicId == null ? null : resolve(topicId);
        navigation.explore(id);
        panel.hide();
        overviewShowing = false;
        if (gesture && id != null) explorer.demonstrate(navigation.place(), id);
        else explorer.explore(navigation.place(), id);
    }

    /** The Guide button on the overview: the reading sheet, at this place's home page. */
    private void openGuide() {
        leaveExplore();
        navigation.open(navigation.place());
        backToPanel();
    }

    /** The explorer could not seat a card, or the reader asked to read: the topic page instead. */
    private void readInstead(@Nullable String topicId) {
        leaveExplore();
        String id = topicId == null ? null : resolve(topicId);
        if (id != null) navigation.topic(id);
        backToPanel();
    }

    private void backToHelp() {
        leaveExplore();
        navigation.home();
        backToPanel();
    }

    /** A fresh invocation over an explorer that is still up: one overlay at a time. */
    private void closeExplorer() {
        if (explorer != null && explorer.isShowing()) explorer.dismiss();
    }

    private void leaveExplore() {
        overviewShowing = false;
        if (explorer != null) explorer.dismiss();
        while (navigation.screen() == HelpNavigation.Screen.EXPLORE && navigation.depth() > 1)
            navigation.back();
    }

    private void backToPanel() {
        remeasure();
        panel.show();
        render();
        watchLayout();
    }

    // ---- plumbing ----------------------------------------------------------------------------

    private void ensureViews() {
        if (panel != null) return;
        panel = new HelpPanelView(context);
        panel.setListener(panelListener);
        viewHost.addView(panel, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        targets = new HelpTargets(finder, panel);
        explorer = injectedExplorer != null ? injectedExplorer
            : overlayExplorer(context, viewHost, finder);
        explorer.setExploreListener(exploreListener);
    }

    private void render() {
        if (panel == null) return;
        rendering = true;
        try {
            panel.render(navigation, measured, practiceAvailable);
        } finally {
            rendering = false;
        }
    }

    /** @return true when the controls on screen are not the ones the last page was drawn for. */
    private boolean remeasure() {
        if (targets == null) return false;
        Set<String> ids = new LinkedHashSet<>();
        for (HelpTargets.Target target : targets.measure(navigation.place()).targets)
            ids.add(target.id);
        if (ids.equals(measured)) return false;
        measured = ids;
        return true;
    }

    private void endTextEntry() {
        if (!navigation.textEntryActive()) return;
        navigation.setTextEntryActive(false);
        host.endHelpTextInput();
    }

    /** A topic id or the target id the explorer names a control by; one lookup answers both. */
    private String resolve(String id) {
        HelpTopics.Entry entry = HelpTopics.entry(navigation.place(), id);
        return entry == null ? id : entry.id;
    }

    private void watchLayout() {
        if (watchingLayout || panel == null) return;
        ViewTreeObserver observer = panel.getViewTreeObserver();
        if (!observer.isAlive()) return;
        observer.addOnGlobalLayoutListener(layoutListener);
        watchingLayout = true;
    }

    private void unwatchLayout() {
        if (!watchingLayout || panel == null) return;
        ViewTreeObserver observer = panel.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnGlobalLayoutListener(layoutListener);
        watchingLayout = false;
    }

    /** The target ids the last pass measured. */
    @VisibleForTesting
    Set<String> measuredTargetIds() {
        return measured;
    }

    @VisibleForTesting
    HelpNavigation navigation() {
        return navigation;
    }

    @VisibleForTesting
    HelpPanelView panel() {
        return panel;
    }

    // ---- the explorer -----------------------------------------------------------------------

    /** The explore overlay, created on first use and added over the same host as the panel. */
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
