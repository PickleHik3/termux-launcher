package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.WindowSpec;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageSwitchTargets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Overview under the Terl switch (#430) through Overview's own doors: a row per place, a tap goes
 * to a window or a place, a drag moves a window or makes tabs, and Back closes it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w400dp-h800dp-mdpi")
public class StackOverviewTest {

    private FrameLayout mPages, mChrome, mSheets;
    private View mDesktop, mBar;
    private static final int BAR_PX = 140;
    private OverviewController mController;
    private TilingStack mStack;
    private final List<String> mAsked = new ArrayList<>();
    private final java.util.Map<Long, String> mTitles = new java.util.HashMap<>();
    private final List<SavedCard> mSaved = new ArrayList<>();
    private int mSavedReads;
    private long mFirst;
    private long mSecond;
    private long mThird;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        root.setId(R.id.window_layers_root);
        mPages = layer(activity, root, R.id.activity_termux_root_relative_layout);
        mChrome = layer(activity, root, R.id.window_layer_chrome);
        mSheets = layer(activity, root, R.id.window_layer_sheets);
        activity.setContentView(root);

        // Home, Workspace 1 with two terminals (the second in front), Workspace 2 with one, waiting 3.
        mStack = TilingStack.firstStart(1, "/home");
        mFirst = mStack.workspace(1).windows().get(0).id();
        mSecond = mStack.openIn(Place.workspace(1), WindowSpec.shell("/home"), false);
        mThird = mStack.openIn(Place.workspace(2), WindowSpec.shell("/home"), false);
        mStack.focusWindow(mSecond);

        mController = new OverviewController(activity, new V1Host());
        StackOverview overview = new StackOverview(activity, new StackOverview.Host() {
            @NonNull @Override public TilingStack stack() { return mStack; }
            @Nullable @Override public String titleOf(long windowId) { return mTitles.get(windowId); }
            @Override public void openEditMode() { mAsked.add("edit"); }
            @Override public int cutoutClearPx(int cutoutPx) { return cutoutPx; }
            @Override public boolean moveWindow(long windowId, @NonNull Place place) {
                mAsked.add("move " + windowId + " " + place);
                return mStack.moveWindow(windowId, place);
            }
            @Override public boolean joinTab(long windowId, long targetId) {
                mAsked.add("tab " + windowId + " " + targetId);
                return mStack.joinTab(windowId, targetId);
            }
            @Override public boolean focusWindow(long windowId) {
                mAsked.add("focus " + windowId);
                return true;
            }
            @Override public boolean goTo(@NonNull Place place) {
                mAsked.add("go " + place);
                return true;
            }
            @NonNull @Override public OverviewLook look() { return look_(); }
            @Nullable @Override public Drawable appIcon(@NonNull String appId) { return null; }
            @Override public void savedWorkspaces(@NonNull Consumer<List<SavedCard>> onRead) {
                mSavedReads++;
                onRead.accept(new ArrayList<>(mSaved));
            }
            @Override public void openSaved(@NonNull String name) { mAsked.add("saved " + name); }
            @Override public void renameSaved(@NonNull String name, @NonNull Runnable onChanged) {
                mAsked.add("rename " + name);
                onChanged.run();
            }
            @Override public void deleteSaved(@NonNull String name, @NonNull Runnable onChanged) {
                mAsked.add("delete " + name);
                mSaved.clear();
                onChanged.run();
            }
            @Override public void setOpenAtStart(@NonNull String name, boolean on, @NonNull Runnable onChanged) {
                mAsked.add("open at start " + name + " " + on);
                onChanged.run();
            }
        });
        mController.setStack(overview);
        // The control bar and its handle stand in the pages layer, at the bottom, beside the desktop.
        mDesktop = new View(activity);
        mPages.addView(mDesktop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mBar = new View(activity);
        mPages.addView(mBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, BAR_PX,
            android.view.Gravity.BOTTOM));
        View handle = new View(activity);
        handle.setVisibility(View.GONE);
        mPages.addView(handle, new FrameLayout.LayoutParams(10, 10));
        overview.keepBar(mBar, handle, () -> BAR_PX);
    }

    @Test public void openingShowsTheStacksRowsOnTheSheetsLayerOverHiddenLayers() {
        mController.open();
        idle();

        assertTrue(mController.isOpen());
        assertEquals(1, mSheets.getChildCount());
        assertTrue(mSheets.getChildAt(0) instanceof StackOverviewView);
        // Updated for round 2: the pages are hidden around the control bar, which stays shown.
        assertEquals(0f, mDesktop.getAlpha(), 0f);
        assertEquals(1f, mBar.getAlpha(), 0f);
        assertEquals(1f, mPages.getAlpha(), 0f);
        assertEquals(0f, mChrome.getAlpha(), 0f);
        assertNotNull(text("Home"));
        assertNotNull(text("Workspace 1"));
        assertNotNull(text("Workspace 2"));
        assertNotNull(text("New Workspace"));
        assertNotNull(text("Drag a window here"));
        assertNotNull(text("Saved"));
        assertNotNull("no saved Workspaces yet (#431)", text("No saved Workspaces"));
    }

    @Test public void theSheetEndsAboveTheBarItLeavesShown() {
        // Round 2: Overview covered the bar; the prototype keeps the bar below the sheet.
        mController.open();
        idle();
        View screen = mSheets.getChildAt(0);
        View sheet = ((ViewGroup) screen).getChildAt(0);
        int[] at = new int[2];
        sheet.getLocationInWindow(at);
        int[] bar = new int[2];
        mBar.getLocationInWindow(bar);
        assertTrue("the sheet ends above the bar: " + (at[1] + sheet.getHeight()) + " > " + bar[1],
            at[1] + sheet.getHeight() <= bar[1]);
    }

    @Test public void theRowsStandAtTheBottomInThumbReach() {
        mController.open();
        idle();
        View screen = mSheets.getChildAt(0);
        TextView saved = text("No saved Workspaces");
        int[] at = new int[2];
        saved.getLocationInWindow(at);
        int[] top = new int[2];
        screen.getLocationInWindow(top);
        assertTrue("the last row ends near the bottom edge",
            top[1] + screen.getHeight() - (at[1] + saved.getHeight()) < screen.getHeight() / 4);
        int[] home = new int[2];
        text("Home").getLocationInWindow(home);
        assertTrue("the free room is above the rows", home[1] - top[1] > screen.getHeight() / 10);
    }

    @Test public void backTheToggleAndCloseEachLeaveWithTheLayersBack() {
        mController.open();
        assertTrue(mController.onBack());
        assertAtRest();

        mController.toggle();
        assertTrue(mController.isOpen());
        mController.toggle();
        assertAtRest();

        mController.open();
        mController.close();
        assertAtRest();
        assertFalse("Back with it closed is not spent", mController.onBack());
    }

    // ---- the look (terl-integration-1) ----------------------------------------------------------

    @Test public void theEditPillStandsAtTheSheetsTopAboveTheRows() {
        mController.open();
        idle();
        int[] edit = new int[2];
        text("Edit").getLocationInWindow(edit);
        int[] home = new int[2];
        text("Home").getLocationInWindow(home);
        assertTrue("Edit is above Home's row", edit[1] < home[1]);
        assertNotNull("the sheet is titled", text("Overview"));
    }

    @Test public void theRowsStandOnAGlassSheetLowOnTheScreen() {
        mController.open();
        idle();
        ViewGroup screen = (ViewGroup) mSheets.getChildAt(0);
        View sheet = screen.getChildAt(0);
        assertSame("the sheet wears the sheets' glass", SHEET_GLASS, sheet.getBackground());
        assertTrue("the sheet clips to its rounded outline", sheet.getClipToOutline());
        assertTrue("free room above it", sheet.getTop() > 0);
        assertEquals("it stands on the bottom", screen.getHeight() - sheet.getBottom(),
            ((ViewGroup.MarginLayoutParams) sheet.getLayoutParams()).bottomMargin);
    }

    @Test public void aTerminalWithNoTitleIsCalledByItsProgramThenItsFolder() {
        assertEquals("fish", StackCardName.of("fish", "vim", "/x", "/h"));
        assertEquals("vim", StackCardName.of("  ", "vim", "/x", "/h"));
        assertEquals("src", StackCardName.of(null, null, "/h/src/", "/h"));
        assertEquals("~", StackCardName.of(null, null, "/h", "/h"));
        assertNull(StackCardName.of(null, null, null, "/h"));
    }

    @Test public void aDropOverTheHeaderMovesNothingIntoARowScrolledUnderIt() {
        // Enough Workspaces that the rows scroll, then scrolled so rows stand behind the header.
        for (int n = 3; n <= 9; n++) mStack.openIn(Place.workspace(n), WindowSpec.shell("/home"), false);
        mController.open();
        idle();
        List<View> scrollers = new ArrayList<>();
        collect(mSheets, v -> v instanceof android.widget.ScrollView, scrollers);
        android.widget.ScrollView rows = (android.widget.ScrollView) scrollers.get(0);
        rows.scrollTo(0, 10_000);
        idle();
        assertTrue("the rows overflow their room", rows.getScrollY() > 0);

        drag(card(mThird), text("Overview"), text("Overview"));

        assertTrue("nothing moved: " + mAsked, mAsked.isEmpty());
    }

    @Test public void theEditButtonClosesOverviewAndOpensEditMode() {
        mController.open();
        idle();
        TextView edit = text("Edit");
        assertNotNull(edit);
        edit.performClick();
        assertAtRest();
        assertEquals(Collections.singletonList("edit"), mAsked);
    }

    @Test public void theHomeExitFadesTheStacksScreen() {
        mController.open();
        List<View> views = new ArrayList<>();
        mController.exitViews(views);
        assertEquals(1, views.size());
        assertTrue(views.get(0) instanceof StackOverviewView);
    }

    @Test public void theWallsPullBringsNothingInUnderTheSwitch() {
        assertFalse(mController.wallEdge().available());
    }

    @Test public void aTapOnASavedWorkspaceOpensIt() {
        mSaved.add(SavedCard.of("dev", savedOneShell()));
        mController.open();
        idle();
        mAsked.clear();

        savedCard("dev").performClick();

        assertEquals(Collections.singletonList("saved dev"), mAsked);
        assertFalse(mController.isOpen());
    }

    @Test public void aHeldSavedWorkspaceOffersRenameDeleteAndOpenAtStart() {
        mSaved.add(SavedCard.of("dev", savedOneShell().withOpenAtStart(true)));
        mController.open();
        idle();
        mAsked.clear();

        android.widget.PopupMenu menu = holdSaved("dev");
        assertEquals(3, menu.getMenu().size());
        assertEquals("Rename", menu.getMenu().getItem(0).getTitle().toString());
        assertEquals("Delete", menu.getMenu().getItem(1).getTitle().toString());
        android.view.MenuItem openAtStart = menu.getMenu().getItem(2);
        assertEquals("Open at start", openAtStart.getTitle().toString());
        assertTrue(openAtStart.isChecked());

        click(menu, 2);
        click(holdSaved("dev"), 0);
        click(holdSaved("dev"), 1);

        assertEquals(java.util.Arrays.asList("open at start dev false", "rename dev", "delete dev"), mAsked);
        assertEquals("the row is read on opening and after each change", 4, mSavedReads);
        assertTrue("Overview stays open", mController.isOpen());
        assertNotNull("the row reads again", text("No saved Workspaces"));
    }

    @Test public void aTapOnACardGoesToItsWindow() {
        mController.open();
        idle();

        card(mThird).performClick();

        assertEquals(Collections.singletonList("focus " + mThird), mAsked);
        assertAtRest();
    }

    @Test public void aTapOnARowsTitleGoesToThePlace() {
        mController.open();
        idle();

        text("Workspace 2").performClick();
        assertEquals(Collections.singletonList("go Workspace 2"), mAsked);
        assertAtRest();
    }

    @Test public void draggingACardOntoAnotherRowMovesItsWindowThere() {
        mController.open();
        idle();

        drag(card(mThird), (View) text("Workspace 1").getParent(), text("Workspace 1"));

        assertEquals(Collections.singletonList("move " + mThird + " Workspace 1"), mAsked);
        assertEquals(Place.workspace(1), mStack.placeOf(mThird));
        assertTrue("Overview stays open, read again", mController.isOpen());
        assertNull("the emptied Workspace went", textOrNull("Workspace 3"));
    }

    @Test public void draggingACardOntoHomesRowMakesItAFloatOfTheHomePageInFront() {
        mController.open();
        idle();

        drag(card(mThird), (View) text("Home").getParent(), text("Home"));

        assertEquals(Collections.singletonList("move " + mThird + " Home page 1"), mAsked);
        assertEquals(Place.home(0), mStack.placeOf(mThird));
        assertTrue(mStack.window(mThird).isFloat());
    }

    @Test public void draggingACardOntoAnotherCardMakesThemTabs() {
        mController.open();
        idle();

        View target = card(mFirst);
        drag(card(mThird), target, target);

        assertEquals(Collections.singletonList("tab " + mThird + " " + mFirst), mAsked);
        assertEquals(Place.workspace(1), mStack.placeOf(mThird));
    }

    @Test public void lettingGoInItsOwnRowChangesNothing() {
        mController.open();
        idle();

        View own = text("Workspace 2");
        drag(card(mThird), own, own);

        assertTrue(mAsked.isEmpty());
    }

    @Test public void talkBackMovesACardToAnotherPlaceByAnAction() {
        mController.open();
        idle();
        View card = card(mThird);
        AccessibilityNodeInfo info = card.createAccessibilityNodeInfo();
        int action = -1;
        for (AccessibilityNodeInfo.AccessibilityAction a : info.getActionList()) {
            if ("Move to New Workspace".contentEquals(String.valueOf(a.getLabel()))) action = a.getId();
        }
        assertTrue("an action per other place that takes windows", action > 0);

        assertTrue(card.performAccessibilityAction(action, null));

        assertEquals(Collections.singletonList("move " + mThird + " Workspace 3"), mAsked);
    }

    @Test public void talkBacksLongPressStartsNoDrag() {
        mController.open();
        idle();
        View card = card(mThird);
        ViewGroup screen = (ViewGroup) mSheets.getChildAt(0);
        int children = screen.getChildCount();

        assertFalse(card.isLongClickable());
        card.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null);
        idle();

        assertEquals("no copy of the card is left lifted", children, screen.getChildCount());
        assertEquals(1f, card.getAlpha(), 0f);
    }

    @Test public void aDropFindsItsRowAgainWhenAWorkspaceAboveEmptiesWhileOpen() {
        long fourth = mStack.openIn(Place.workspace(3), WindowSpec.shell("/home"), false);
        mController.open();
        idle();
        View fourthCard = cardLabelled("Terminal in Workspace 3", 0);
        View shownTwo = text("Workspace 2");

        // Workspace 1's windows exit while Overview is up: 2 and 3 are 1 and 2 now.
        mStack.close(mFirst);
        mStack.close(mSecond);
        drag(fourthCard, shownTwo, shownTwo);

        assertEquals("the window joins the Workspace that was shown as 2",
            mStack.placeOf(mThird), mStack.placeOf(fourth));
        assertEquals(Place.workspace(1), mStack.placeOf(fourth));
    }

    @Test public void aTerminalsCardIsCalledByItsShellsTitle() {
        mTitles.put(mThird, "htop");
        mController.open();
        idle();

        assertNotNull(text("htop"));
    }

    @Test public void theHomePageDoorsKeepTheWallsEditor() {
        mController.openWall();
        idle();

        assertTrue(mController.isOpen());
        assertTrue("v1's Overview with its page controls", mSheets.getChildAt(0) instanceof OverviewView);
        mController.toggle();
        assertAtRest();
    }

    @Test public void aTapOnACardSpendsASettingsReturn() {
        int[] returned = {0};
        mController.setBackReturn(() -> returned[0]++);
        mController.open();
        idle();
        card(mThird).performClick();

        mController.open();
        assertTrue(mController.onBack());

        assertEquals("Back from the bar's Overview stays here", 0, returned[0]);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private void assertAtRest() {
        assertFalse(mController.isOpen());
        assertEquals(0, mSheets.getChildCount());
        assertEquals(1f, mPages.getAlpha(), 0f);
        assertEquals(1f, mDesktop.getAlpha(), 0f);
        assertEquals(1f, mChrome.getAlpha(), 0f);
    }

    /** A still finger held on {@code card}, then a slide to the middle of {@code finger}, then letting go there. */
    private void drag(@NonNull View card, @NonNull View over, @NonNull View finger) {
        int[] from = new int[2];
        card.getLocationOnScreen(from);
        int[] to = new int[2];
        finger.getLocationOnScreen(to);
        float x = to[0] + Math.max(1, finger.getWidth()) / 2f;
        float y = to[1] + Math.max(1, finger.getHeight()) / 2f;
        long t = SystemClock.uptimeMillis();
        card.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN,
            from[0] + 10f, from[1] + 10f, 0));
        org.robolectric.shadows.ShadowLooper.idleMainLooper(
            android.view.ViewConfiguration.getLongPressTimeout() + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("the hold lifted a copy of the card", 2, ((ViewGroup) mSheets.getChildAt(0)).getChildCount());
        card.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_MOVE,
            from[0] + 10f, from[1] + 10f, 0));
        card.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_MOVE, x, y, 0));
        card.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, x, y, 0));
        idle();
    }

    /** The {@code index}th card spoken as {@code label}. */
    @NonNull
    private View cardLabelled(@NonNull String label, int index) {
        List<View> found = new ArrayList<>();
        collect(mSheets, v -> label.contentEquals(String.valueOf(v.getContentDescription())), found);
        assertTrue(label, found.size() > index);
        return found.get(index);
    }

    /** The card of window {@code id}: its spoken name names its row. */
    @NonNull
    private View card(long id) {
        String label = id == mThird ? "Terminal in Workspace 2" : "Terminal in Workspace 1";
        List<View> found = new ArrayList<>();
        collect(mSheets, v -> label.contentEquals(String.valueOf(v.getContentDescription())), found);
        int index = id == mSecond ? 1 : 0;
        assertTrue("card of " + id, found.size() > index);
        return found.get(index);
    }

    @NonNull
    private static com.termux.app.tiling.SavedWorkspace savedOneShell() {
        TilingStack stack = TilingStack.empty(0);
        stack.openIn(Place.workspace(1), WindowSpec.shell("/home"), false);
        return com.termux.app.tiling.SavedWorkspace.of(stack, 1, Collections.emptyMap());
    }

    /** The Saved row's card for {@code name}: the clickable item its title stands in. */
    @NonNull
    private View savedCard(@NonNull String name) {
        View view = text(name);
        while (view != null && !view.isClickable()) view = (View) view.getParent();
        assertNotNull(view);
        return view;
    }

    @NonNull
    private android.widget.PopupMenu holdSaved(@NonNull String name) {
        assertTrue(savedCard(name).performLongClick());
        android.widget.PopupMenu menu = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(menu);
        return menu;
    }

    private static void click(@NonNull android.widget.PopupMenu menu, int index) {
        android.view.MenuItem item = menu.getMenu().getItem(index);
        assertTrue(menu.getMenu().performIdentifierAction(item.getItemId(), 0));
        idle();
    }

    private TextView text(@NonNull String text) {
        TextView view = textOrNull(text);
        assertNotNull(text, view);
        return view;
    }

    @Nullable
    private TextView textOrNull(@NonNull String text) {
        List<View> found = new ArrayList<>();
        collect(mSheets, v -> v instanceof TextView && text.contentEquals(((TextView) v).getText()), found);
        return found.isEmpty() ? null : (TextView) found.get(0);
    }

    private static void collect(@NonNull View view, @NonNull java.util.function.Predicate<View> match,
                                @NonNull List<View> out) {
        if (match.test(view)) out.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), match, out);
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static FrameLayout layer(Activity activity, FrameLayout root, int id) {
        FrameLayout layer = new FrameLayout(activity);
        layer.setId(id);
        root.addView(layer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        return layer;
    }

    @NonNull
    private static OverviewLook look_() {
        return new OverviewLook(new ColorDrawable(0x99000000), 0xFF000000, 0xFF444444, 0xFF0000FF,
            (radiusPx, anchor) -> SHEET_GLASS);
    }

    /** The glass the look hands out: the sheet wears it once it has a size. */
    private static final ColorDrawable SHEET_GLASS = new ColorDrawable(0x55FFFFFF);

    /** v1's side, which the stack's Overview never asks. */
    private static final class V1Host implements OverviewController.Host {
        @Override @NonNull public PageBarModel wall() {
            PageBarModel.Input in = new PageBarModel.Input();
            in.pages = java.util.Arrays.asList(new PageBarModel.Page(1, com.termux.app.page.PageKind.HOME, null));
            in.windowNames = Collections.emptyList();
            in.currentWindow = -1;
            in.currentPageId = 1;
            return PageBarModel.build(in);
        }
        @Override @NonNull public PageSwitchTargets.Navigator navigator() {
            return new PageSwitchTargets.Navigator() {
                @Override public void goToPage(int pageId) { }
                @Override public void showTerminalWindow(int index) { }
                @Override public void goToTerminal() { }
                @Override public void openOverview() { }
            };
        }
        @Override public CardFacts facts(@NonNull OverviewModel.Card card, @NonNull TextBudget budget) {
            return null;
        }
        @Override public void assistantFacts(@NonNull Consumer<CardFacts> onFacts) { }
        @Override public int homePageId() { return -1; }
        @Override public int homePageCount() { return 1; }
        @Override public int contentsOf(@NonNull OverviewModel.Card card) { return 0; }
        @Override public void setHome(@NonNull OverviewModel.Card card) { }
        @Override public void rename(@NonNull OverviewModel.Card card, @NonNull Runnable renamed) { }
        @Override public void remove(@NonNull OverviewModel.Card card) { }
        @Override public void moveWallPage(int pageId, int refPageId, boolean after) { }
        @Override public void moveWindow(int from, int to) { }
        @Override @Nullable public String commandOf(@NonNull OverviewModel.Card card) { return null; }
        @Override @NonNull public OverviewLook look() { return look_(); }
    }
}
