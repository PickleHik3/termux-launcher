package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.view.View;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The Settings root's content starts under the title bar: the process-limit row, hidden while
 * there is no notice, must not hold the card's height open above the search field.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w411dp-h914dp-420dpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsRootTopTest {

    @Test
    public void searchSitsUnderTheTitleBarInPortrait() {
        assertSearchUnderToolbar(1080, 2400);
    }

    @Test
    @Config(qualifiers = "w914dp-h411dp-420dpi")
    public void searchSitsUnderTheTitleBarInLandscape() {
        assertSearchUnderToolbar(2400, 1080);
    }

    private static void assertSearchUnderToolbar(int width, int height) {
        Application app = RuntimeEnvironment.getApplication();
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class,
            new Intent(app, SettingsActivity.class)).create().start().resume().visible();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);

        View toolbar = activity.findViewById(R.id.toolbar);
        View search = (View) activity.findViewById(R.id.settings_search_input).getParent().getParent().getParent();
        androidx.recyclerview.widget.RecyclerView list =
            ((SettingsActivity.RootPreferencesFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.settings)).getListView();
        View notice = null;
        for (int i = 0; i < list.getChildCount(); i++) {
            if (list.getChildAt(i) instanceof com.termux.app.processlimit.ProcessLimitNoticeView) {
                notice = list.getChildAt(i);
            }
        }
        assertNotNull(toolbar);
        // The list may skip the hidden row's view, or lay it out; either way it holds no height.
        if (notice != null) assertEquals("a hidden notice has no height", 0, notice.getHeight());

        int[] toolbarAt = new int[2];
        int[] searchAt = new int[2];
        toolbar.getLocationInWindow(toolbarAt);
        search.getLocationInWindow(searchAt);
        int gap = searchAt[1] - (toolbarAt[1] + toolbar.getHeight());
        int bound = Math.round(16 * app.getResources().getDisplayMetrics().density);
        assertTrue("search starts " + gap + "px under the title bar", gap >= 0 && gap <= bound);
    }
}
