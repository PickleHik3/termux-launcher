package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

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

        assertEquals(Arrays.asList("Float", "Full screen", "Move to Workspace", "Tab with", "Stack with", "Edit look", "Close"),
            strings(card.rowLabels()));
        card.clickRow(2);
        assertEquals(Arrays.asList("‹  Back", "New Workspace", "Home"), strings(card.rowLabels()));
        card.clickRow(0);
        card.clickRow(3);
        assertEquals(Arrays.asList("‹  Back", "App"), strings(card.rowLabels()));
        card.clickRow(1);
        assertEquals(Arrays.asList("TAB 2"), chosen);
    }

    private static List<String> strings(List<CharSequence> labels) {
        List<String> out = new ArrayList<>();
        for (CharSequence label : labels) out.add(label.toString());
        return out;
    }
}
