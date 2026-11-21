package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

/**
 * Typing into the root's search field keeps the field: it stays the list's first row, above the
 * results, with the focus, so the next letter lands in it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsRootSearchFieldTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1991;

    private static void idleAndLayOut(SettingsActivity activity, RecyclerView list) {
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
        list.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, WIDTH, HEIGHT);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void theFieldStaysAboveTheResultsAndKeepsTakingLetters() {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class))
            .create().start().resume().visible().get();
        SettingsActivity.RootPreferencesFragment root = (SettingsActivity.RootPreferencesFragment)
            activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        RecyclerView list = root.getListView();
        idleAndLayOut(activity, list);

        // The first bind parks focus on the row itself, so the keyboard stays shut until a tap.
        EditText field = list.findViewById(R.id.settings_search_input);
        assertNotNull(field);
        assertTrue("focus is parked in the list", list.hasFocus());

        field.requestFocus();
        field.append("a");
        idleAndLayOut(activity, list);
        assertFieldFirstAboveResults(list, "a");

        EditText again = list.findViewById(R.id.settings_search_input);
        again.append("b");
        idleAndLayOut(activity, list);
        assertFieldFirstAboveResults(list, "ab");
    }

    private static void assertFieldFirstAboveResults(RecyclerView list, String query) {
        View fieldRow = null;
        View headerRow = null;
        String header = RuntimeEnvironment.getApplication().getString(R.string.settings_search_results_header);
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.findViewById(R.id.settings_search_input) != null) fieldRow = child;
            TextView title = child.findViewById(android.R.id.title);
            if (title != null && header.contentEquals(title.getText())) headerRow = child;
        }
        assertNotNull("the search field is a laid-out row", fieldRow);
        assertNotNull("the results header is a laid-out row", headerRow);
        // The notice above the field has no height while there is no card.
        assertEquals("the field is the list's first row", 0, fieldRow.getTop());
        assertTrue("the results follow the field", headerRow.getTop() >= fieldRow.getBottom());
        EditText field = fieldRow.findViewById(R.id.settings_search_input);
        assertEquals(query, field.getText().toString());
        assertTrue("the field keeps the focus", field.hasFocus());
    }
}
