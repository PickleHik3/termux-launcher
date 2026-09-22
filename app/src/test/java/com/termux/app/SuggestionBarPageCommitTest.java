package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.launcher.paging.PageTickStripView;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.app.wall.PaneWallPage;
import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A qualified page swipe is a decision, and the slide is only how it is shown. Interrupting the
 * slide — a new touch on the row, a reset from the host — must therefore land on the page the
 * swipe asked for, not back on the page it came from. Interrupting without idling first is what
 * makes these deterministic: the settle is still on its first frame, exactly where a second
 * finger or a host reset lands in practice.
 *
 * <p>And a decision the row keeps: the page survives every other surface that borrows the row's
 * slots — the A–Z matches, a line typed at the prompt — because those surfaces were what put the
 * icons back on the first page with no gesture having asked for it. The later cases here build the
 * developer's own arrangement on the real {@code activity_termux.xml} and drive the real letters
 * through the real window, so the whole tree decides who owns a touch.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class SuggestionBarPageCommitTest {

    private static final int ROW_WIDTH = 720;
    private static final int ROW_HEIGHT = 160;
    private static final float TRAVEL = 300f;
    /** The letter Robolectric's own label for every test activity files under. */
    private static final char SCRUB_LETTER = 'T';
    /** A second letter, holding apps that are on no pinned page, so a filtered row is unmistakable. */
    private static final char OTHER_LETTER = 'Z';
    private static final int OTHER_LETTER_APPS = 3;

    private Context context;
    private SuggestionBarView row;
    private TermuxActivity screen;
    private FrameLayout window;
    private ActivityController<Activity> activity;

    @Before
    public void setUp() {
        // Robolectric's default scale of zero would finish every settle inside the frame that
        // starts it, and an animation that cannot be interrupted cannot show this defect.
        setDurationScale(1f);
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        context.getResources().getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;
        row = new SuggestionBarView(context, null);
        row.addView(new View(context), new ViewGroup.LayoutParams(ROW_WIDTH / 2, ROW_HEIGHT));
        // In a window, not free-floating: a detached view queues everything it posts until it is
        // attached, so every deferred render and every timeout the row relies on would sit unrun,
        // and the gates under test would look stuck for a reason the phone never has.
        activity = Robolectric.buildActivity(Activity.class).setup();
        FrameLayout host = new FrameLayout(activity.get());
        activity.get().setContentView(host);
        host.addView(row, new ViewGroup.LayoutParams(ROW_WIDTH, ROW_HEIGHT));
        row.measure(
            View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ROW_HEIGHT, View.MeasureSpec.EXACTLY));
        row.layout(0, 0, ROW_WIDTH, ROW_HEIGHT);
        assertTrue("the row must be in a window", row.isAttachedToWindow());
        assertEquals(ROW_HEIGHT, row.getHeight());

        List<PinnedItem> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(new PinnedAppItem(new AppRef("com.example.app" + i, "Main")));
        }
        ReflectionHelpers.setField(row, "pinnedItems", new ArrayList<>(items));
        ReflectionHelpers.setField(row, "maxButtonCount", 3);
        ReflectionHelpers.setField(row, "pinnedItemsPerPage", 3);
    }

    @After
    public void tearDown() {
        if (activity != null) activity.pause().stop().destroy();
        setDurationScale(0f);
    }

    @Test
    public void anUninterruptedSwipeLandsOnTheNextPage() {
        swipeLeft();
        idle();

        assertEquals(1, page());
    }

    @Test
    public void aTouchThatInterruptsTheSettleStillLandsOnTheSwipedPage() {
        swipeLeft();
        // Mid-settle, without idling: the slide is running and the page is not committed yet.
        // The settle has its own field, apart from the drag-back rebound that commits nothing.
        assertNotNull(ReflectionHelpers.getField(row, "swipePreviewSettleAnimator"));
        assertNull(ReflectionHelpers.getField(row, "swipePreviewReboundAnimator"));
        assertEquals(0, page());

        dispatch(MotionEvent.ACTION_DOWN, 100f, 80f);

        assertEquals(1, page());
    }

    @Test
    public void aHostResetThatInterruptsTheSettleStillLandsOnTheSwipedPage() {
        swipeLeft();
        assertEquals(0, page());

        row.resetTransientVisualState();

        assertEquals(1, page());
    }

    /**
     * The dock hands the row the band it was given, and that band carries the page ticks' own
     * strip as well as the icons — so the hint can ask for a height this row is never laid out at.
     * The gate that waits for it is anti-flicker for the first frame, never a mute switch: a page
     * swipe under an impossible hint must still land, render and draw.
     */
    @Test
    public void aSwipeUnderAnImpossibleHeightHintStillRendersAndDraws() {
        row.setDockRowHeightHintPx(ROW_HEIGHT + 40);
        row.reload();
        List<View> beforeSwipe = children();

        swipeLeft();
        idle();

        assertEquals(1, page());
        assertNotEquals("the committed page must actually be rendered", beforeSwipe, children());
        assertTrue("the row must hold something", row.getChildCount() > 0);
        assertTrue("the hint must stop deciding once it has had its say",
            ReflectionHelpers.callInstanceMethod(row, "hasStableRenderBounds"));
        assertFalse("draw suppression must not outlive the swipe", suppressed());
        assertFalse("the drag is over", dragging());
        assertEquals(0f, row.getTranslationX(), 0.01f);
        assertEquals(0f, offset(), 0.01f);
    }

    /**
     * The A-Z scrub remembers the page it put up so a held letter does not re-render every frame.
     * A page it was turned away from is not a page it put up — remembering one silenced every
     * later scrub of the same letter, because the row "already had" matches it had never drawn.
     */
    @Test
    public void aScrubThatCouldNotRenderDoesNotSilenceTheNextOne() throws Exception {
        row.setAppDataProvider(loadedCatalogue());
        row.setDockRowHeightHintPx(ROW_HEIGHT + 40);

        row.previewAzLetter(SCRUB_LETTER, 0, false);
        List<View> turnedAway = children();

        idle();
        row.previewAzLetter(SCRUB_LETTER, 0, false);

        assertNotEquals("the repeat scrub must render", turnedAway, children());
    }

    /**
     * A settle cut short is still a settle finished: the row back on a whole page, untranslated,
     * with no neighbouring page left staged behind it and the ticks told where it landed. Cancelled
     * straight on the animator, which is what a host that tears the slide down does — it used to
     * commit the page and walk away from everything the last frame would have tidied, and get away
     * with it only because the commit's own re-render tidied up after it. Under a height hint the
     * row can never meet there is no re-render to lean on.
     */
    @Test
    public void aSettleCutShortLeavesNoHalfSlideBehind() {
        PageTickStripView ticks = new PageTickStripView(context);
        row.setPageIndicator(ticks);
        row.setDockRowHeightHintPx(ROW_HEIGHT + 40);
        row.reload();

        swipeLeft();
        ValueAnimator settle = ReflectionHelpers.getField(row, "swipePreviewSettleAnimator");
        assertNotNull(settle);
        assertNotEquals("the slide is mid-flight", 0f, offset(), 0.01f);

        settle.cancel();

        assertEquals(1, page());
        assertFalse("the drag is over", dragging());
        assertEquals(0f, offset(), 0.01f);
        assertEquals(0f, row.getTranslationX(), 0.01f);
        assertEquals(1f, ticks.getPagePosition(), 0.01f);
        assertTrue("the row has pages to count", ticks.getPageCount() > 1);
    }

    // ----------------------------------------- the row as one band of the developer's dock stack

    /** The phone in the report: 1080x2412, portrait, everything on the bottom edge. */
    private static final int PHONE_WIDTH = 1080;
    private static final int PHONE_HEIGHT = 2412;

    /**
     * The arrangement the ghost swipe was reported on: extra keys, apps row and letters all on the
     * bottom edge in that order reading down, with the status bar innermost on its own sheet above
     * them. Every band is the one the real {@code activity_termux.xml} and {@code DockLayoutPolicy}
     * give it, so the row is handed the height and the hint the arrangement actually produces.
     */
    private SuggestionBarView rowInTheDockStack() {
        return rowInTheDockStack(
            bottomStack(Element.STATUS, Element.EXTRA_KEYS, Element.APPS, Element.AZ));
    }

    private SuggestionBarView rowInTheDockStack(PlaceLayout arrangement) {
        screen = Robolectric.buildActivity(TermuxActivity.class).get();
        screen.setContentView(R.layout.activity_termux);
        TermuxAppSharedPreferences preferences =
            TermuxAppSharedPreferences.build(screen, false);
        assertNotNull(preferences);
        ReflectionHelpers.setField(screen, "mPreferences", preferences);

        SuggestionBarView bar = new SuggestionBarView(screen, null);
        ViewGroup plank = screen.findViewById(R.id.apps_bar_plank_layer);
        plank.addView(bar, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ReflectionHelpers.setField(screen, "mSuggestionBarView", bar);
        // Pinned to what the catalogue actually holds, so every slot resolves to a real icon: a
        // row of unresolvable refs falls back to blank fillers, which have no press target to lift.
        bar.setAppDataProvider(loadedCatalogue(9));
        bar.reloadAllApps();
        List<LauncherAppEntry> catalogue = ReflectionHelpers.getField(bar, "allApps");
        assertTrue("the catalogue reached the row", catalogue != null && catalogue.size() >= 9);
        List<PinnedItem> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) items.add(new PinnedAppItem(catalogue.get(i).appRef));
        // Persisted as well as set: the screen's own wiring reloads the row's pinned items from the
        // config repository, so a row pinned only in memory empties the moment it is wired up.
        LauncherConfigRepository.getInstance(screen).savePinnedItems(items);
        ReflectionHelpers.setField(bar, "pinnedItems", items);
        bar.setMaxButtonCount(3);

        PlaceLayout layout = arrangement;
        // Stored as well as applied: everything the scrub reads — which band the matches fill and
        // which way the finger travels to reach it — comes from the store through
        // currentPlaceLayout(), not from the arrangement handed to applyEdgeStacks().
        PlaceLayoutStore store = ReflectionHelpers.callInstanceMethod(screen, "placeLayoutStore");
        assertNotNull(store);
        for (PaneWallPage place : PaneWallPage.values())
            for (PlaceOrientation orientation : PlaceOrientation.values())
                for (Element element : Element.values())
                    store.setSlot(place, orientation, element, layout.slot(element));
        screen.applyEdgeStacks(layout);
        screen.syncPinnedAppsHost(layout);
        screen.applyDockLayout(screen.dockLayoutFor(layout));
        showBand(screen, R.id.apps_bar_az_row);
        showBand(screen, R.id.terminal_toolbar_view_pager);
        showBand(screen, R.id.terminal_window_bar_host);
        showBand(screen, R.id.apps_bar_viewpager);
        showBand(screen, R.id.apps_bar_indicator_band);
        screen.findViewById(R.id.accessory_stack_container).setVisibility(View.VISIBLE);

        // In a window: a detached view queues everything it posts, so every deferred render and
        // every gate timeout the row leans on would sit unrun. The screen is built rather than
        // resumed — TermuxActivity's own onCreate wants a service — so its decor is stood inside
        // an activity that is, which attaches the whole tree without moving a single band.
        activity = Robolectric.buildActivity(Activity.class).setup();
        window = new FrameLayout(activity.get());
        activity.get().setContentView(window);
        window.addView(screen.getWindow().getDecorView(),
            new FrameLayout.LayoutParams(PHONE_WIDTH, PHONE_HEIGHT));
        layoutWindow();
        assertTrue("the row must be in a window", bar.isAttachedToWindow());
        assertTrue("the row must have a band to draw in: " + bar.getHeight(),
            bar.getWidth() > 0 && bar.getHeight() > 0);
        bar.reload();
        idle();
        return bar;
    }

    /** A measure and layout pass over the whole screen, as a frame on the phone runs one. */
    private void layoutWindow() {
        window.measure(
            View.MeasureSpec.makeMeasureSpec(PHONE_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(PHONE_HEIGHT, View.MeasureSpec.EXACTLY));
        window.layout(0, 0, PHONE_WIDTH, PHONE_HEIGHT);
    }

    /** Everything on the bottom edge in the order given, innermost (nearest the canvas) first. */
    private static PlaceLayout bottomStack(Element... innermostFirst) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (int i = 0; i < innermostFirst.length; i++)
            slots.put(innermostFirst[i],
                new Slot(false, PlaceLayout.Edge.BOTTOM, innermostFirst.length - i));
        for (Element element : Element.values())
            if (!slots.containsKey(element))
                slots.put(element, Slot.on(PlaceLayout.Edge.BOTTOM, element));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    /** A band the arrangement stands, which the render passes this test does not run would show. */
    private static void showBand(TermuxActivity screen, int viewId) {
        View band = screen.findViewById(viewId);
        assertNotNull(band);
        band.setVisibility(View.VISIBLE);
    }

    /**
     * The ghost swipe from the report: with the row one band of a three-row dock under a status
     * bar, a page swipe played its whole slide and landed back on page 1.
     */
    @Test
    public void aSwipeOnTheRowInADockStackLandsOnTheNextPage() {
        SuggestionBarView bar = rowInTheDockStack();
        assertTrue("the row has pages to swipe between: " + pagesOf(bar), pagesOf(bar) > 1);

        swipeLeftOn(bar);
        idle();

        assertEquals(1, pageOf(bar));
        assertFalse("draw suppression must not outlive the swipe",
            (Boolean) ReflectionHelpers.getField(bar, "suppressDrawUntilStableLayout"));
    }

    /**
     * The "ghost touch" icon: a finger starts a page swipe <em>on</em> an icon, so that icon has
     * already played its press-down lift. The row then claims the stream and consumes the release
     * itself, so the icon never sees an UP and never bounces back — it is left standing 4dp above
     * the row it belongs to and scaled up, looking permanently pressed. The re-render the commit
     * happens to run is the only thing that has ever hidden it, and the row's own render gates mean
     * a commit cannot promise one.
     */
    @Test
    public void aPageSwipeTakesBackTheLiftOfTheIconItStartedOn() {
        SuggestionBarView bar = rowInTheDockStack();
        View pressed = pressTargetUnder(bar, downXOn(bar));
        assertNotNull("the swipe has to start on an icon to show this", pressed);

        swipeLeftOn(bar);
        idle();

        assertEquals(1, pageOf(bar));
        assertEquals("the icon the swipe started on is left lifted",
            0f, pressed.getTranslationY(), 0.01f);
        assertEquals("and left scaled up", 1f, pressed.getScaleY(), 0.01f);
        for (int i = 0; i < bar.getChildCount(); i++) {
            assertEquals("slot " + i + " is drawn off its own row",
                0f, bar.getChildAt(i).getTranslationY(), 0.01f);
        }
    }

    /**
     * The same defect at the moment it happens. A claim is where the row takes the stream away from
     * its children, so a claim is where they have to stand down — the drawer's drag has always sent
     * one synthetic cancel at exactly that point for exactly this reason. A page swipe sent none,
     * so the icon under the finger kept the lift it took on the way down and only ever lost it to
     * whatever re-render happened to follow; with the render deferred — which is the state the dock
     * spends its first frames in — nothing ever took it back.
     */
    @Test
    public void theIconUnderTheFingerStandsDownTheMomentTheRowClaimsTheSwipe() {
        SuggestionBarView bar = rowInTheDockStack();
        View pressed = pressTargetUnder(bar, downXOn(bar));
        assertNotNull("the swipe has to start on an icon to show this", pressed);

        float y = bar.getHeight() * 0.5f;
        float from = downXOn(bar);
        dispatchOn(bar, MotionEvent.ACTION_DOWN, from, y);
        dispatchOn(bar, MotionEvent.ACTION_MOVE, from - (bar.getWidth() * 0.3f), y);
        idle();

        assertEquals("the row owns the swipe, so the icon it started on is not left pressed",
            0f, pressed.getTranslationY(), 0.01f);
        assertEquals("nor left scaled up", 1f, pressed.getScaleY(), 0.01f);
    }

    /**
     * The ghost swipe as the developer sees it: the drag shows the next page, and when the settle
     * finishes the row is showing the page the finger came from. The page index is not the proof —
     * it commits — the icons on screen are.
     */
    @Test
    public void theRowShowsTheSwipedPageOnceTheSettleIsOver() {
        SuggestionBarView bar = rowInTheDockStack();
        List<String> cameFrom = renderedLabels(bar);
        assertFalse("the row must be showing icons to begin with", cameFrom.isEmpty());

        swipeLeftOn(bar);
        idle();

        assertEquals(1, pageOf(bar));
        assertNotEquals("the row is still showing the page the finger came from",
            cameFrom, renderedLabels(bar));
    }

    /** What the row is actually showing: every slot's own label, in slot order. */
    private static List<String> renderedLabels(ViewGroup bar) {
        List<String> labels = new ArrayList<>();
        collectLabels(bar, labels);
        return labels;
    }

    private static void collectLabels(View view, List<String> into) {
        CharSequence description = view.getContentDescription();
        if (description != null) into.add(description.toString());
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collectLabels(group.getChildAt(i), into);
    }

    // --------------------------------------------------------------- the A-Z scrub in that stack

    /**
     * The stack on the phone today, reading down the screen: the page ticks, the pinned apps row,
     * the A-Z letters, the extra keys — with the status bar moved back to the top edge.
     */
    private static PlaceLayout developersStack() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        Element[] innermostFirst = {Element.APPS, Element.AZ, Element.EXTRA_KEYS};
        for (int i = 0; i < innermostFirst.length; i++)
            slots.put(innermostFirst[i],
                new Slot(false, PlaceLayout.Edge.BOTTOM, innermostFirst.length - i));
        slots.put(Element.STATUS, Slot.on(PlaceLayout.Edge.TOP, Element.STATUS));
        for (Element element : Element.values())
            if (!slots.containsKey(element))
                slots.put(element, Slot.on(PlaceLayout.Edge.BOTTOM, element));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    /**
     * The letters filter the row: a finger down on a letter fills the pinned apps row with that
     * letter's apps, which is the whole point of the index.
     */
    @Test
    public void aFingerOnTheLettersFiltersTheRow() {
        SuggestionBarView bar = rowInTheDockStack(developersStack());
        AzScrubRowView letters = wireTheScrub();
        List<String> pinned = renderedLabels(bar);

        // Through the window, not straight at the letters: who gets a touch that lands on the
        // letters is exactly the question, so the whole tree above them has to answer it. The far
        // end of the track is the last letter, whose apps are on no pinned page — so a row that
        // filtered and a row that did not cannot be told apart by accident.
        scrubDownOn(letters, 0.98f);
        idle();

        assertEquals("the finger is on the last letter", Character.valueOf(OTHER_LETTER),
            ReflectionHelpers.getField(bar, "activeAzLetter"));
        assertNotEquals("the row is still showing the pinned apps, not the letter's matches",
            pinned, renderedLabels(bar));
        for (String label : renderedLabels(bar))
            assertTrue("the row must hold the letter's own apps, not " + label,
                label.startsWith(String.valueOf(OTHER_LETTER)));
    }

    /** The index is not a one-shot: a second finger on the same letter filters the row again. */
    @Test
    public void aSecondScrubOfTheSameLetterFiltersTheRowAgain() {
        SuggestionBarView bar = rowInTheDockStack(developersStack());
        AzScrubRowView letters = wireTheScrub();
        List<String> pinned = renderedLabels(bar);

        scrubDownOn(letters, 0.98f);
        idle();
        scrubUpOn(letters, 0.98f);
        // The matches stay up after the finger leaves, and the row's own timeout puts them away.
        idleFor(8);
        assertEquals("the row goes back to the pinned apps once the preview times out",
            pinned, renderedLabels(bar));

        scrubDownOn(letters, 0.98f);
        idle();

        assertNotEquals("the second scrub of the letter did nothing",
            pinned, renderedLabels(bar));
    }

    /**
     * A scrub is a look at another surface, not a page turn: the pinned row the finger goes back to
     * is the page it left. The A-Z preview borrows the row's slots, so it also borrowed its page
     * counter — and put it back at zero.
     */
    @Test
    public void aScrubDoesNotSendTheRowBackToItsFirstPage() {
        SuggestionBarView bar = rowInTheDockStack(developersStack());
        AzScrubRowView letters = wireTheScrub();
        swipeLeftOn(bar);
        idle();
        assertEquals("the row has to be off its first page to show this", 1, pageOf(bar));
        List<String> secondPage = renderedLabels(bar);

        scrubDownOn(letters, 0.98f);
        idle();
        bar.clearAzPreview();
        idle();

        assertEquals("the scrub sent the row back to its first page", 1, pageOf(bar));
        assertEquals("and drew it there", secondPage, renderedLabels(bar));
    }

    /**
     * The terminal is the other surface that borrows the row: a typed line turns it into a list of
     * suggestions, and clearing the line gives it back. The page the icons were on is not the
     * terminal's to reset — and a keystroke lands on the dock far more often than a scrub does,
     * which is what makes a row that will not stay on its page look like a swipe that bounces.
     */
    @Test
    public void aLineTypedInTheTerminalDoesNotTakeTheRowOffItsPage() {
        SuggestionBarView bar = rowInTheDockStack(developersStack());
        wireTheScrub();
        swipeLeftOn(bar);
        idle();
        assertEquals("the row has to be off its first page to show this", 1, pageOf(bar));
        List<String> secondPage = renderedLabels(bar);

        bar.reloadWithInput("Tango", null);
        awaitADifferentRow(bar, secondPage);
        bar.reloadWithInput("", null);
        idle();

        assertEquals("a line typed at the prompt sent the row back to its first page",
            1, pageOf(bar));
        assertEquals("and drew it there", secondPage, renderedLabels(bar));
    }

    /**
     * Waits for the row to draw something other than what it was drawing. The search that fills it
     * runs on the row's own worker, so idling the main looper alone can walk straight past it and
     * leave the test asserting about a surface that was never rendered.
     */
    private void awaitADifferentRow(SuggestionBarView bar, List<String> before) {
        for (int attempt = 0; attempt < 200 && renderedLabels(bar).equals(before); attempt++) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            idle();
        }
        assertNotEquals("the typed line never reached the row", before, renderedLabels(bar));
    }

    /** A finger on the letters, through the window the way a thumb arrives. */
    private void scrubDownOn(AzScrubRowView letters, float alongFraction) {
        scrubOn(letters, MotionEvent.ACTION_DOWN, alongFraction);
    }

    private void scrubUpOn(AzScrubRowView letters, float alongFraction) {
        scrubOn(letters, MotionEvent.ACTION_UP, alongFraction);
    }

    private void scrubOn(AzScrubRowView letters, int action, float alongFraction) {
        int[] at = new int[2];
        letters.getLocationOnScreen(at);
        dispatchOn(screen.getWindow().getDecorView(), action,
            at[0] + (letters.getWidth() * alongFraction), at[1] + (letters.getHeight() * 0.5f));
    }

    /** The letters view, wired to the screen's own scrub callback the way onCreate wires it. */
    private AzScrubRowView wireTheScrub() {
        ReflectionHelpers.callInstanceMethod(screen, "setSuggestionBarView");
        // The screen's own pass over the index: which edge it stands on, which view carries the
        // scrub, and which face of the bar the matches are on.
        ReflectionHelpers.callInstanceMethod(screen, "syncAzBarHosts");
        ReflectionHelpers.callInstanceMethod(screen, "syncAzScrubLettersAndTint");
        // The wiring hands the row the icons-per-page preference; the fixture wants three slots and
        // three pages, which is what makes a page change visible at all.
        screen.mSuggestionBarView.setMaxButtonCount(3);
        screen.mSuggestionBarView.reload();
        layoutWindow();
        idle();
        AzScrubRowView letters = screen.findViewById(R.id.apps_bar_az_row);
        assertNotNull(letters);
        assertTrue("the letters must be laid out to be scrubbed: "
                + letters.getWidth() + "x" + letters.getHeight(),
            letters.getWidth() > 0 && letters.getHeight() > 0);
        return letters;
    }

    private void dispatchOn(View view, int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    /** The icon's own press target at that point along the row, which is what carries the lift. */
    private static View pressTargetUnder(SuggestionBarView bar, float x) {
        for (int i = 0; i < bar.getChildCount(); i++) {
            View slot = bar.getChildAt(i);
            if (x < slot.getLeft() || x >= slot.getRight()) continue;
            if (!(slot instanceof ViewGroup)) return slot;
            View press = ((ViewGroup) slot).getChildAt(0);
            float inSlot = x - slot.getLeft();
            return press != null && inSlot >= press.getLeft() && inSlot < press.getRight()
                ? press : null;
        }
        return null;
    }

    private static int pageOf(SuggestionBarView bar) {
        return ReflectionHelpers.getField(bar, "pinnedPageIndex");
    }

    private static int pagesOf(SuggestionBarView bar) {
        return bar.getPinnedVisiblePageCount();
    }

    /** The report's gesture: a swipe along the row, well past the commit distance it asks for. */
    private void swipeLeftOn(SuggestionBarView bar) {
        float y = bar.getHeight() * 0.5f;
        float travel = bar.getWidth() * 0.6f;
        float from = downXOn(bar);
        dispatchOn(bar, MotionEvent.ACTION_DOWN, from, y);
        dispatchOn(bar, MotionEvent.ACTION_MOVE, from - (travel * 0.5f), y);
        dispatchOn(bar, MotionEvent.ACTION_MOVE, from - travel, y);
        dispatchOn(bar, MotionEvent.ACTION_UP, from - travel, y);
    }

    /**
     * Where the swipe puts its finger down: the middle of a slot, where a thumb starts one, so the
     * icon standing there takes the press.
     */
    private static float downXOn(SuggestionBarView bar) {
        return bar.getWidth() * 5f / 6f;
    }

    private void dispatchOn(SuggestionBarView bar, int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        bar.dispatchTouchEvent(event);
        event.recycle();
    }

    /**
     * A catalogue with something under {@link #SCRUB_LETTER}. Robolectric labels every resolved
     * activity from the test application's own info, so the letter is the one that label starts
     * with rather than one this can choose.
     */
    private LauncherAppDataProvider loadedCatalogue() {
        return loadedCatalogue(4);
    }

    private LauncherAppDataProvider loadedCatalogue(int apps) {
        ShadowPackageManager packageManager = Shadows.shadowOf(context.getPackageManager());
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (int i = 0; i < apps; i++) {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.activityInfo = new ActivityInfo();
            resolveInfo.activityInfo.packageName = "com.example.app" + i;
            // Distinct activity names as well as packages: the shadow package manager files every
            // resolve info under the test application's own package, so apps that differ only by
            // package name all resolve to one AppRef — and a row of nine of them is one app nine
            // times over, which no page swipe could ever be seen to change.
            resolveInfo.activityInfo.name = "Main" + i;
            resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();
            // Distinct labels, all under SCRUB_LETTER: the row's slots carry the entry label as
            // their content description, which is the only way a test can read back *which* page
            // of icons the row is actually showing.
            resolveInfo.activityInfo.nonLocalizedLabel = SCRUB_LETTER + "ango " + i;
            packageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);
        }
        for (int i = 0; i < OTHER_LETTER_APPS; i++) {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.activityInfo = new ActivityInfo();
            resolveInfo.activityInfo.packageName = "com.example.other" + i;
            resolveInfo.activityInfo.name = "Other" + i;
            resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();
            resolveInfo.activityInfo.nonLocalizedLabel = OTHER_LETTER + "ulu " + i;
            packageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);
        }
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        provider.invalidate();
        provider.getAllAppsBlocking();
        assertTrue("the catalogue must be loaded", provider.hasLoadedApps());
        assertFalse("the scrub letter must have matches",
            provider.getAppsForLetter(SCRUB_LETTER).isEmpty());
        return provider;
    }

    private List<View> children() {
        List<View> views = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) views.add(row.getChildAt(i));
        return views;
    }

    private boolean suppressed() {
        return ReflectionHelpers.getField(row, "suppressDrawUntilStableLayout");
    }

    private boolean dragging() {
        return ReflectionHelpers.getField(row, "swipePageDragging");
    }

    private float offset() {
        return ReflectionHelpers.getField(row, "swipeVisualOffsetPx");
    }

    private void swipeLeft() {
        dispatch(MotionEvent.ACTION_DOWN, 400f, 80f);
        dispatch(MotionEvent.ACTION_MOVE, 400f - TRAVEL, 80f);
        dispatch(MotionEvent.ACTION_UP, 400f - TRAVEL, 80f);
    }

    private int page() {
        return ReflectionHelpers.getField(row, "pinnedPageIndex");
    }

    private void dispatch(int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        row.dispatchTouchEvent(event);
        event.recycle();
    }

    private void idle() {
        idleFor(2);
    }

    private void idleFor(int seconds) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(seconds, TimeUnit.SECONDS);
    }

    private static void setDurationScale(float scale) {
        ReflectionHelpers.setStaticField(ValueAnimator.class, "sDurationScale", scale);
    }
}
