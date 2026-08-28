package com.termux.app.launcher.popup;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The anchored-menu module owns every rule the launcher's popups obey. Those rules are only visible
 * as arithmetic and state, so they are pinned here rather than through the twelve call sites.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, qualifiers = "w411dp-h891dp-xxhdpi", application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AnchoredMenuTest {

    private ActivityController<Activity> controller;
    private Activity activity;
    private FrameLayout host;
    private AnchoredMenuTheme theme;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        host = new FrameLayout(activity);
        activity.setContentView(host, new ViewGroup.LayoutParams(720, 160));
        theme = new FixedTheme();
        idle();
    }

    @After
    public void tearDown() {
        if (controller != null) controller.close();
    }

    // ---------------------------------------------------------------- off-screen clamping

    @Test
    public void aMenuWiderThanTheRoomRightOfItsAnchor_isClampedToTheFrame() {
        Rect frame = new Rect(0, 0, 1000, 2000);
        // A left-third anchor left-aligns, which would run the panel off the right edge.
        Rect anchor = new Rect(40, 1500, 140, 1600);
        int[] xy = new int[2];

        AnchoredMenuGeometry.anchoredPosition(anchor, 990, 300, 1000, frame, 8, xy);

        assertEquals("clamped to the right edge, not left-aligned off it", 10, xy[0]);
    }

    @Test
    public void aRightEdgeAnchor_rightAlignsAndNeverGoesNegative() {
        Rect frame = new Rect(0, 0, 1000, 2000);
        Rect anchor = new Rect(900, 1500, 1000, 1600);
        int[] xy = new int[2];

        AnchoredMenuGeometry.anchoredPosition(anchor, 400, 300, 1000, frame, 8, xy);
        assertEquals(600, xy[0]);

        // Wider than the whole screen's worth of right-alignment room: clamp, don't underflow.
        AnchoredMenuGeometry.anchoredPosition(anchor, 1200, 300, 1000, frame, 8, xy);
        assertEquals(0, xy[0]);
    }

    @Test
    public void aMidDockAnchor_centresTheMenuOverIt() {
        Rect frame = new Rect(0, 0, 1000, 2000);
        Rect anchor = new Rect(450, 1500, 550, 1600);
        int[] xy = new int[2];

        AnchoredMenuGeometry.anchoredPosition(anchor, 400, 300, 1000, frame, 8, xy);

        assertEquals(500 - 200, xy[0]);
    }

    @Test
    public void aTallerThanTheScreenMenu_isClampedToTheTopOfTheFrame() {
        Rect frame = new Rect(0, 100, 1000, 900);
        Rect anchor = new Rect(450, 700, 550, 800);
        int[] xy = new int[2];

        // Taller than the frame: the flip fires and then the clamp pins it to the top.
        AnchoredMenuGeometry.anchoredPosition(anchor, 400, 1200, 1000, frame, 8, xy);

        assertEquals(frame.top, xy[1]);
    }

    @Test
    public void anAnchorWithNoRoomAbove_flipsUnderItselfRatherThanCoveringIt() {
        Rect frame = new Rect(0, 0, 1000, 2000);
        Rect anchor = new Rect(450, 0, 550, 150);
        int[] xy = new int[2];

        AnchoredMenuGeometry.anchoredPosition(anchor, 400, 300, 1000, frame, 8, xy);

        assertEquals(150 + 8, xy[1]);
    }

    // ---------------------------------------------------------------- side alignment

    @Test
    public void aSideMenu_sitsRightOfTheMainPanelAndCentresOnItsRow() {
        int[] xy = new int[2];

        AnchoredMenuGeometry.sideAlignedPosition(100, 300, 800, 200, 400, 1000, 2000, 8, xy);

        assertEquals(100 + 300 + 8, xy[0]);
        assertEquals(800 - 200, xy[1]);
    }

    @Test
    public void aSideMenuWithNoRoomOnTheRight_flipsToTheLeftOfTheMainPanel() {
        int[] xy = new int[2];

        AnchoredMenuGeometry.sideAlignedPosition(600, 300, 800, 300, 400, 1000, 2000, 8, xy);

        assertEquals(600 - 300 - 8, xy[0]);
    }

    @Test
    public void aSideMenuWithRoomOnNeitherSide_staysOnTheRightAndIsClamped() {
        int[] xy = new int[2];

        // Right would overflow and left would be negative, so the right preference stands.
        AnchoredMenuGeometry.sideAlignedPosition(50, 300, 800, 900, 400, 1000, 2000, 8, xy);

        assertEquals(100, xy[0]);
    }

    @Test
    public void aSideMenuCentredOnATopRow_isClampedIntoTheScreen() {
        int[] xy = new int[2];

        AnchoredMenuGeometry.sideAlignedPosition(100, 300, 10, 200, 400, 1000, 2000, 8, xy);

        assertEquals(0, xy[1]);
    }

    // ---------------------------------------------------------------- width policy

    @Test
    public void theWidthCaps_areScreenRelativeAndOrdered() {
        float density = 1f;
        int screenW = 1000;

        // 90% of the screen, the 320dp cap and (screen - 24dp) all apply; the cap wins here.
        assertEquals(320, AnchoredMenuGeometry.maxWidth(screenW, density));
        // A narrow screen falls back to the 90% rule.
        assertEquals(270, AnchoredMenuGeometry.maxWidth(300, density));
        // A tight-wrapping menu has no floor; a normal one is padded out to 188dp.
        assertEquals(0, AnchoredMenuGeometry.minWidth(screenW, true, density));
        assertEquals(188, AnchoredMenuGeometry.minWidth(screenW, false, density));
        // The floor can never exceed the ceiling.
        assertEquals(AnchoredMenuGeometry.maxWidth(200, density),
            AnchoredMenuGeometry.minWidth(200, false, density));
    }

    @Test
    public void theHeightCeiling_isTheTighterOfTheInsetAndTheFraction() {
        assertEquals((int) (1000 * 0.45f), AnchoredMenuGeometry.maxHeight(1000, 1f));
        assertEquals(100 - 80, AnchoredMenuGeometry.maxHeight(100, 1f));
    }

    @Test
    public void aRequestedWidthIsClampedIntoThePolicy() {
        MenuRowFactory factory = new MenuRowFactory(activity, theme);
        LinearLayout shell = factory.newShell();
        factory.addActionRow(shell, "Only row", 0x336699, () -> { });
        AnchoredMenu menu = new AnchoredMenu(host, theme);
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        float density = activity.getResources().getDisplayMetrics().density;

        PopupWindow popup = menu.buildDetached(
            MenuSpec.of(shell, 0x336699).tightWrap(false).width(100000).build());

        assertEquals(AnchoredMenuGeometry.maxWidth(screenW, density), popup.getWidth());
        assertTrue(popup.getHeight() <= AnchoredMenuGeometry.maxHeight(
            activity.getResources().getDisplayMetrics().heightPixels, density));
    }

    // ---------------------------------------------------------------- header vs rows

    @Test
    public void rowsAllTakeTheWidthOfTheWidest() {
        MenuRowFactory factory = new MenuRowFactory(activity, theme);
        LinearLayout shell = factory.newShell();
        List<MenuRow> rows = Arrays.asList(
            new MenuRow(factory.addActionRow(shell, "Pin", 0x336699, () -> { }), () -> { }),
            new MenuRow(factory.addActionRow(shell, "A considerably longer row", 0x336699, () -> { }),
                () -> { }));

        int width = MenuRowWidths.normalize(rows);

        assertTrue("expected a measured width, got " + width, width > 0);
        for (MenuRow row : rows) {
            assertEquals(width, row.rowView.getLayoutParams().width);
        }
    }

    @Test
    public void aShortHeaderTakesTheRowWidthAndStaysOnOneLine() {
        MenuRowFactory factory = new MenuRowFactory(activity, theme);
        TextView header = header("Files");

        int width = MenuRowWidths.constrainHeader(header, 600);

        assertEquals(600, width);
        assertEquals(1, header.getMaxLines());
        assertEquals(600, header.getLayoutParams().width);
    }

    @Test
    public void aLongHeaderWidensThePanelOnlyUpToTheMediumNameBudgetThenWraps() {
        TextView header = header("An extremely long application name that will never fit on one line");

        int width = MenuRowWidths.constrainHeader(header, 10);

        int budget = (int) Math.ceil(header.getPaint().measureText("MMMMMMMMMMMM"))
            + header.getPaddingLeft() + header.getPaddingRight();
        assertEquals("widened to the budget, no further", budget, width);
        assertEquals("and wrapped, because the name still does not fit", 2, header.getMaxLines());
    }

    @Test
    public void theNegotiatedWidthIsPushedBackOntoTheRows() {
        MenuRowFactory factory = new MenuRowFactory(activity, theme);
        LinearLayout shell = factory.newShell();
        List<MenuRow> rows = new ArrayList<>();
        rows.add(new MenuRow(factory.addActionRow(shell, "Pin", 0x336699, () -> { }), () -> { }));
        TextView header = header("An extremely long application name");

        int rowWidth = MenuRowWidths.normalize(rows);
        int contentWidth = MenuRowWidths.constrainHeader(header, rowWidth);
        MenuRowWidths.constrainRows(rows, contentWidth);

        int budget = (int) Math.ceil(header.getPaint().measureText("MMMMMMMMMMMM"))
            + header.getPaddingLeft() + header.getPaddingRight();
        assertEquals("the panel is the wider of the rows and the header's budget",
            Math.max(rowWidth, budget), contentWidth);
        assertEquals("and the rows follow it", contentWidth,
            rows.get(0).rowView.getLayoutParams().width);
    }

    @Test
    public void aNonPositiveTargetLeavesRowsAndHeaderAlone() {
        MenuRowFactory factory = new MenuRowFactory(activity, theme);
        LinearLayout shell = factory.newShell();
        List<MenuRow> rows = new ArrayList<>();
        rows.add(new MenuRow(factory.addActionRow(shell, "Pin", 0x336699, () -> { }), () -> { }));
        int before = rows.get(0).rowView.getLayoutParams().width;

        MenuRowWidths.constrainRows(rows, 0);
        assertEquals(before, rows.get(0).rowView.getLayoutParams().width);
        assertEquals(0, MenuRowWidths.constrainHeader(header("Files"), 0));
        assertEquals(0, MenuRowWidths.normalize(new ArrayList<>()));
    }

    // ---------------------------------------------------------------- highlight tracking

    @Test
    public void theHighlightFollowsTheRowUnderTheFinger() {
        Fixture f = new Fixture();

        assertTrue(f.tracker.updateForRaw(20f, 10f, false, false));
        assertSame(f.rows.get(0), f.tracker.highlighted());

        assertTrue(f.tracker.updateForRaw(20f, 130f, false, false));
        assertSame(f.rows.get(1), f.tracker.highlighted());
    }

    @Test
    public void aFingerOutsideTheRowsHighlightsNothingUntilTheSelectionIsArmed() {
        Fixture f = new Fixture();

        // Not armed: strictly-inside only, so a point below the panel resolves to nothing.
        assertFalse(f.tracker.updateForRaw(20f, 400f, false, false));
        assertNull(f.tracker.highlighted());

        // Armed: the same point projects onto the nearest row by vertical distance.
        assertTrue(f.tracker.updateForRaw(20f, 260f, false, true));
        assertSame(f.rows.get(2), f.tracker.highlighted());
    }

    @Test
    public void aFingerWellBelowTheLowestRowDropsTheHighlightEvenWhenArmed() {
        Fixture f = new Fixture();
        f.tracker.updateForRaw(20f, 10f, false, false);
        assertNotNull(f.tracker.highlighted());

        // Past the lowest row plus the projection slack, nothing is highlighted any more.
        assertFalse(f.tracker.updateForRaw(20f, 5000f, false, true));
        assertNull(f.tracker.highlighted());
    }

    @Test
    public void aRowThatOpensTheSubmenu_asksTheHostExactlyOnce() {
        Fixture f = new Fixture();
        int[] opened = new int[1];
        f.tracker.setSubmenuOpener(() -> opened[0]++);

        // openSubmenuOnFocus off: hovering the submenu row must not open anything.
        f.tracker.updateForRaw(20f, 250f, false, false);
        assertEquals(0, opened[0]);

        f.tracker.updateForRaw(20f, 250f, true, false);
        assertEquals(1, opened[0]);
    }

    @Test
    public void leavingTheSubmenuRowAsksTheHostToCloseTheSubmenu() {
        Fixture f = new Fixture();
        int[] dismissed = new int[1];
        f.tracker.setSubmenuDismisser(() -> dismissed[0]++);
        f.showSideMenu();

        // Over the row that owns the submenu: it stays.
        f.tracker.updateForRaw(20f, 250f, false, false);
        assertEquals(0, dismissed[0]);

        // Over an unrelated row: the submenu is closed.
        f.tracker.updateForRaw(20f, 10f, false, false);
        assertEquals(1, dismissed[0]);
    }

    // ---------------------------------------------------------------- commit

    @Test
    public void aReleaseOverARowCommitsThatRowsAction() {
        Fixture f = new Fixture();
        f.tracker.updateForRaw(20f, 130f, false, false);

        f.tracker.commitHighlighted();

        assertEquals("row1", f.committed.toString());
    }

    @Test
    public void aReleaseWithNothingHighlightedKeepsTheMenuOpenAndCommitsNothing() {
        Fixture f = new Fixture();

        f.tracker.commitHighlighted();

        assertEquals("", f.committed.toString());
        assertTrue("the menu is still up", f.main.isShowing());
    }

    @Test
    public void clearingTheHighlightDropsIt() {
        Fixture f = new Fixture();
        f.tracker.updateForRaw(20f, 10f, false, false);

        f.tracker.clear();

        assertNull(f.tracker.highlighted());
        f.tracker.commitHighlighted();
        assertEquals("", f.committed.toString());
    }

    // ---------------------------------------------------------------- dismissal

    @Test
    public void anAnimatedDismissEndsWithTheWindowGoneAndTheRowsReleased() {
        MenuRowFactory factory = new MenuRowFactory(activity, theme);
        LinearLayout shell = factory.newShell();
        List<MenuRow> rows = new ArrayList<>();
        rows.add(new MenuRow(factory.addActionRow(shell, "Pin", 0x336699, () -> { }), () -> { }));
        AnchoredMenu menu = new AnchoredMenu(host, theme);
        boolean[] dismissed = new boolean[1];
        menu.show(MenuSpec.of(shell, 0x336699).rows(rows).onDismiss(() -> dismissed[0] = true).build(),
            null);
        idle();
        assertTrue(menu.isShowing());
        assertEquals(1, menu.rows().size());

        menu.dismiss();
        // The rows are released synchronously: the finger must not be able to hit a row of a menu
        // that is on its way out.
        assertTrue(menu.rows().isEmpty());
        idle();

        assertFalse(menu.isShowing());
        assertNull(menu.window());
        assertTrue("the consumer's dismiss hook ran", dismissed[0]);
    }

    @Test
    public void dismissingAMenuThatWasNeverShownIsANoOp() {
        AnchoredMenu menu = new AnchoredMenu(host, theme);

        menu.dismiss();

        assertFalse(menu.isShowing());
        assertNull(menu.window());
    }

    // ---------------------------------------------------------------- helpers

    private TextView header(String text) {
        TextView header = new TextView(activity);
        header.setText(text);
        header.setTextSize(12f);
        header.setPadding(8, 6, 8, 7);
        return header;
    }

    private void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    /**
     * A main menu of three rows at known screen coordinates — the last of which owns a submenu —
     * plus a side menu that can be raised. Rows are placed by hand so hit testing is arithmetic.
     */
    private final class Fixture {
        final StringBuilder committed = new StringBuilder();
        final AnchoredMenu main = new AnchoredMenu(host, theme);
        final AnchoredMenu side = new AnchoredMenu(host, theme);
        final MenuRowFactory factory = new MenuRowFactory(activity, theme);
        final MenuHighlightTracker tracker =
            new MenuHighlightTracker(host, factory, main, side);
        final List<MenuRow> rows = new ArrayList<>();

        Fixture() {
            LinearLayout shell = factory.newShell();
            rows.add(new MenuRow(placed(shell, "row0", 0), () -> committed.append("row0")));
            rows.add(new MenuRow(placed(shell, "row1", 120), () -> committed.append("row1")));
            rows.add(new MenuRow(placed(shell, "row2", 240), () -> committed.append("row2"), true));
            main.show(MenuSpec.of(shell, 0x336699).rows(rows).build(), null);
            idle();
        }

        void showSideMenu() {
            LinearLayout shell = factory.newShell();
            List<MenuRow> sideRows = new ArrayList<>();
            sideRows.add(new MenuRow(placed(shell, "side0", 240), () -> committed.append("side0")));
            side.show(MenuSpec.of(shell, 0x336699).rows(sideRows).build(), null);
            idle();
        }

        private TextView placed(LinearLayout shell, String label, int screenTop) {
            FixedRow row = new FixedRow(activity, screenTop);
            row.setText(label);
            shell.addView(row, new LinearLayout.LayoutParams(200, 100));
            row.layout(0, screenTop, 200, screenTop + 100);
            return row;
        }
    }

    /** A row whose screen position is declared rather than laid out. */
    private static final class FixedRow extends TextView {
        private final int screenTop;

        FixedRow(Context context, int screenTop) {
            super(context);
            this.screenTop = screenTop;
        }

        @Override
        public void getLocationOnScreen(int[] outLocation) {
            outLocation[0] = 0;
            outLocation[1] = screenTop;
        }
    }

    private static final class FixedTheme implements AnchoredMenuTheme {
        @Override public int textColor() { return 0xFFEEEEEE; }
        @Override public int selectedTextColor() { return 0xFF112233; }
        @Override public int opacityPercent() { return 80; }
        @Override public boolean blurEnabled() { return false; }
        @Override public int blurRadiusDp() { return 0; }
    }
}
