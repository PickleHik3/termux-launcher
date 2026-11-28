package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.WindowSpec;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The window menu's card (#425): its rows' words, a list opened in its place, and Back. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WindowMenuViewTest {

    @Test public void aListOpensInTheCardsPlaceAndBackReturns() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> chosen = new ArrayList<>();
        WindowMenuView card = new WindowMenuView(activity, new ReadingCardView.Style() {
            @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
            @Override public float cornerRadiusPx() { return 0f; }
            @Override public int ink() { return 0xFFFFFFFF; }
        }, new WindowMenuView.Listener() {
            @Override public void onRow(@NonNull WindowMenu.Row row) { chosen.add(row.toString()); }
            @Override public void onDismiss() { chosen.add("dismiss"); }
        });
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long shell = stack.open(WindowSpec.shell(null));
        stack.open(WindowSpec.linuxApp("org.example", "App"));
        stack.renameWorkspace(1, "Work");

        // No sheets layer here: the card fills its rows and stays off screen.
        card.show(new View(activity), new Rect(), false, WindowMenu.subjectOf(stack, shell, false), "Terminal");

        // Updated for round 2: the prototype's rows, the width choices and Float and pin among them.
        assertEquals(Arrays.asList("Float", "Full screen", "Move to Workspace", "Tab with", "Stack with", "1/2 Full",
            "Edit look", "Float and pin", "Close"), strings(card.rowLabels()));
        assertEquals("TERMINAL", card.title().toString());
        for (int i = 0; i < 9; i++) {
            if (i == 5) continue;
            assertTrue("row " + i + " has its icon", card.rowIcon(i) != null);
        }
        card.clickWidth(0);
        assertEquals(Arrays.asList("SET_WIDTH"), chosen);
        chosen.clear();
        card.clickRow(2);
        assertEquals(Arrays.asList("‹  Back", "New Workspace", "Home"), strings(card.rowLabels()));
        card.clickRow(0);
        card.clickRow(3);
        assertEquals(Arrays.asList("‹  Back", "App"), strings(card.rowLabels()));
        card.clickRow(1);
        assertEquals(Arrays.asList("TAB 2"), chosen);
    }

    @Test public void theCardStandsAboveTheWholeBarNeverOverIt() {
        // Round 2: the menu ran over the bar's marks.
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
        activity.setContentView(root);
        // The card's host keeps Android's navigation clear in its padding, as the window's root does.
        ((View) root.getParent()).setPadding(0, 0, 0, 48);
        View bar = new View(activity);
        android.widget.FrameLayout.LayoutParams barParams = new android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 200, android.view.Gravity.BOTTOM);
        root.addView(bar, barParams);
        WindowMenuView card = new WindowMenuView(activity, new ReadingCardView.Style() {
            @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
            @Override public float cornerRadiusPx() { return 0f; }
            @Override public int ink() { return 0xFFFFFFFF; }
        }, new WindowMenuView.Listener() {
            @Override public void onRow(@NonNull WindowMenu.Row row) { }
            @Override public void onDismiss() { }
        });
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long shell = stack.open(WindowSpec.shell(null));
        layout(root);

        // The held mark's cell in the bar's top row.
        assertTrue(card.show(bar, new Rect(40, 20, 120, 90), false, WindowMenu.subjectOf(stack, shell, false),
            "Terminal"));
        layout(root);

        int[] barAt = new int[2];
        int[] cardAt = new int[2];
        bar.getLocationInWindow(barAt);
        View face = card.getChildAt(0);
        face.getLocationInWindow(cardAt);
        assertTrue("the card ends above the bar: " + (cardAt[1] + face.getHeight()) + " > " + barAt[1],
            cardAt[1] + face.getHeight() <= barAt[1]);
    }

    private static void layout(@NonNull View root) {
        View content = root.getRootView();
        content.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, 480, 1600);
    }

    private static List<String> strings(List<CharSequence> labels) {
        List<String> out = new ArrayList<>();
        for (CharSequence label : labels) out.add(label.toString());
        return out;
    }
}
