package com.termux.app.launcher.popup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Where the App menu goes for an icon near each screen edge, where its arrow points and what it
 * scales from, and the card's group shapes and shades.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, qualifiers = "w411dp-h891dp-mdpi", application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppMenuGeometryTest {

    // px at density 1, the card's own numbers
    private static final int CARD_W = 216;
    private static final int CONTENT_H = 400;
    private static final int ARROW_W = 12;
    private static final int ARROW_H = 10;
    private static final int SHADOW = 4;
    private static final int GAP = 4;
    private static final int MARGIN = 8;
    private static final int INSET = 26 + ARROW_W / 2;
    private static final Rect FRAME = new Rect(0, 0, 1080, 2000);

    private ActivityController<Activity> controller;

    @After
    public void tearDown() {
        if (controller != null) controller.close();
    }

    private static AppMenuGeometry.Placement place(Rect anchor, Rect frame, int contentHeight) {
        AppMenuGeometry.Placement out = new AppMenuGeometry.Placement();
        AppMenuGeometry.place(anchor, frame, CARD_W, contentHeight, ARROW_W, ARROW_H, SHADOW, GAP,
            MARGIN, INSET, out);
        return out;
    }

    private static int centerX(Rect anchor) {
        return anchor.left + anchor.width() / 2;
    }

    // ---------------------------------------------------------------- placement

    @Test
    public void aDockIcon_opensAboveWithTheArrowOnTheBottomEdgePointingAtIt() {
        Rect icon = new Rect(500, 1800, 580, 1880);
        AppMenuGeometry.Placement p = place(icon, FRAME, CONTENT_H);

        assertFalse(p.arrowOnTop);
        assertFalse(p.scrolls);
        assertEquals(CARD_W + 2 * SHADOW, p.windowWidth);
        assertEquals(CONTENT_H + ARROW_H + 2 * SHADOW, p.windowHeight);
        assertEquals("centred on the icon", 540 - CARD_W / 2 - SHADOW, p.x);
        assertEquals("the arrow's tip sits the gap above the icon",
            icon.top - GAP, p.y + p.windowHeight - SHADOW);
        assertEquals(centerX(icon), p.x + p.arrowCenterX);
    }

    @Test
    public void theCardScalesFromTheArrowsTip() {
        Rect icon = new Rect(500, 1800, 580, 1880);
        AppMenuGeometry.Placement p = place(icon, FRAME, CONTENT_H);
        assertEquals(p.arrowCenterX, p.pivotX, 0f);
        assertEquals(SHADOW + CONTENT_H + ARROW_H, p.pivotY, 0f);
        assertEquals(icon.top - GAP, p.y + p.pivotY, 0f);

        Rect topIcon = new Rect(500, 40, 580, 120);
        AppMenuGeometry.Placement below = place(topIcon, FRAME, CONTENT_H);
        assertEquals(SHADOW, below.pivotY, 0f);
        assertEquals(topIcon.bottom + GAP, below.y + below.pivotY, 0f);
    }

    @Test
    public void anIconAtTheTop_flipsTheCardBelowWithTheArrowOnTop() {
        Rect icon = new Rect(500, 40, 580, 120);
        AppMenuGeometry.Placement p = place(icon, FRAME, CONTENT_H);

        assertTrue(p.arrowOnTop);
        assertEquals("the arrow's tip sits the gap below the icon", icon.bottom + GAP, p.y + SHADOW);
        assertEquals(centerX(icon), p.x + p.arrowCenterX);
    }

    @Test
    public void anIconAtTheLeftEdge_holdsTheCardAtTheMarginAndTheArrowOnTheIcon() {
        Rect icon = new Rect(0, 1800, 80, 1880);
        AppMenuGeometry.Placement p = place(icon, FRAME, CONTENT_H);

        assertEquals(MARGIN, p.x + SHADOW);
        assertEquals(centerX(icon), p.x + p.arrowCenterX);
    }

    @Test
    public void anIconAtTheRightEdge_holdsTheCardAtTheMarginAndTheArrowOnTheIcon() {
        Rect icon = new Rect(1000, 1800, 1080, 1880);
        AppMenuGeometry.Placement p = place(icon, FRAME, CONTENT_H);

        assertEquals(FRAME.right - MARGIN, p.x + SHADOW + CARD_W);
        assertEquals(centerX(icon), p.x + p.arrowCenterX);
    }

    @Test
    public void anIconInTheCorner_keepsTheArrowOffTheRoundedCorner() {
        Rect icon = new Rect(0, 1800, 20, 1880);
        AppMenuGeometry.Placement p = place(icon, FRAME, CONTENT_H);

        assertEquals(SHADOW + INSET, p.arrowCenterX);
    }

    @Test
    public void aCardThatFitsNeitherSide_takesTheRoomierOneAndScrolls() {
        Rect frame = new Rect(0, 0, 1080, 1000);
        Rect icon = new Rect(500, 300, 580, 380);
        AppMenuGeometry.Placement p = place(icon, frame, 800);

        assertTrue(p.arrowOnTop);
        assertTrue(p.scrolls);
        assertEquals(frame.bottom - (icon.bottom + GAP + ARROW_H), p.cardHeight);
        assertEquals(frame.bottom, p.y + SHADOW + ARROW_H + p.cardHeight);
    }

    // ---------------------------------------------------------------- shapes and shades

    @Test
    public void groupRows_haveOuterEdgesSmallJoinsAndALoneRowIsAPill() {
        float[] r = new float[2];
        AppMenuGeometry.rowRadii(0, 1, 20f, 4f, 100f, r);
        assertArrayEquals(new float[]{100f, 100f}, r, 0f);
        AppMenuGeometry.rowRadii(0, 3, 20f, 4f, 100f, r);
        assertArrayEquals(new float[]{20f, 4f}, r, 0f);
        AppMenuGeometry.rowRadii(1, 3, 20f, 4f, 100f, r);
        assertArrayEquals(new float[]{4f, 4f}, r, 0f);
        AppMenuGeometry.rowRadii(2, 3, 20f, 4f, 100f, r);
        assertArrayEquals(new float[]{4f, 20f}, r, 0f);
    }

    @Test
    public void theShadesRunAStepLighterTopToBottomInEveryTheme() {
        // Dark: the highest container is the lightest.
        assertArrayEquals(new int[]{0xFF2A2E33, 0xFF32373D, 0xFF3A4047},
            AppMenuShades.orderDarkToLight(new int[]{0xFF3A4047, 0xFF2A2E33, 0xFF32373D}));
        // Light: the lowest is.
        assertArrayEquals(new int[]{0xFFE6E0E9, 0xFFECE6F0, 0xFFF3EDF7},
            AppMenuShades.orderDarkToLight(new int[]{0xFFF3EDF7, 0xFFECE6F0, 0xFFE6E0E9}));
        assertEquals(0, AppMenuGeometry.shadeIndex(0));
        assertEquals(2, AppMenuGeometry.shadeIndex(2));
        assertEquals(2, AppMenuGeometry.shadeIndex(5));
    }

    @Test
    public void theCard_isRowsOf216By52InTheirGroupsShadeWith4dpBetweenGroups() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        MenuRowFactory factory = new MenuRowFactory(activity, new FixedTheme());
        Runnable none = () -> { };
        List<List<AppMenuCard.Item>> groups = Arrays.asList(
            Collections.singletonList(new AppMenuCard.Item("2 notifications", 0, none)),
            Arrays.asList(new AppMenuCard.Item("Compose", 0, none),
                new AppMenuCard.Item("New window", 0, none)),
            Arrays.asList(new AppMenuCard.Item("Widgets", 0, none),
                new AppMenuCard.Item("App info", 0, none),
                new AppMenuCard.Item("A label far too long to fit one App menu row at all", 0, none)));

        AppMenuCard card = AppMenuCard.build(activity, factory, groups);

        assertEquals(6, card.rows.size());
        assertEquals(3, card.groupShades.length);
        assertEquals(6 * 52 + 2 * 4, card.measureContentHeight());
        LinearLayout column = card.view.groups();
        for (int i = 0; i < column.getChildCount(); i++) {
            assertEquals(216, column.getChildAt(i).getMeasuredWidth());
            assertEquals(52, column.getChildAt(i).getMeasuredHeight());
        }
        int[] groupOf = {0, 1, 1, 2, 2, 2};
        for (int i = 0; i < card.rows.size(); i++) {
            AppMenuRowDrawable bg = (AppMenuRowDrawable) card.rows.get(i).rowView.getBackground();
            assertEquals(card.groupShades[groupOf[i]], bg.shade());
        }
        assertEquals(card.groupShades[2], card.arrowShade(false));
        assertEquals(card.groupShades[0], card.arrowShade(true));
        TextView longRow = card.rows.get(5).rowView;
        assertEquals("a long label stays on one line", 1, longRow.getLineCount());
    }

    @Test
    public void highlightingAnAppMenuRow_keepsItsSurfaceAndLaysTheStateLayer() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        MenuRowFactory factory = new MenuRowFactory(activity, new FixedTheme());
        AppMenuCard card = AppMenuCard.build(activity, factory, Collections.singletonList(
            Collections.singletonList(new AppMenuCard.Item("Pin", 0, () -> { }))));
        TextView row = card.rows.get(0).rowView;
        AppMenuRowDrawable surface = (AppMenuRowDrawable) row.getBackground();

        factory.styleRow(row, true, 0);
        assertSame(surface, row.getBackground());
        assertTrue(surface.isHighlighted());
        factory.styleRow(row, false, 0);
        assertFalse(surface.isHighlighted());
    }

    private static final class FixedTheme implements AnchoredMenuTheme {
        @Override public int textColor() { return 0xFFFFFFFF; }
        @Override public int selectedTextColor() { return 0xFFFFFFFF; }
        @Override public int opacityPercent() { return 100; }
        @Override public boolean blurEnabled() { return false; }
        @Override public int blurRadiusDp() { return 0; }
    }
}
