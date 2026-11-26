package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Build;

import com.termux.R;
import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * The old sessions doors (the session.panel, session.browser, workspace.picker and
 * workspace.save_prompt tools, the DRAWER key) all open Overview, and the same door again closes it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class OverviewDoorsRoutingTest {

    @Test
    public void theSessionsPanelDoorTogglesOverview() {
        TermuxActivity activity = laidOutActivity();

        ReflectionHelpers.callInstanceMethod(activity, "toggleSessionsPanel");
        assertEquals(Boolean.TRUE,
            ReflectionHelpers.callInstanceMethod(activity, "isSessionsPanelShowing"));

        ReflectionHelpers.callInstanceMethod(activity, "toggleSessionsPanel");
        assertEquals(Boolean.FALSE,
            ReflectionHelpers.callInstanceMethod(activity, "isSessionsPanelShowing"));
    }

    @Test
    public void theWorkspacePickerAndSavePromptDoorsOpenOverview() {
        for (String door : new String[] {"showWorkspacePicker", "promptSaveWorkspace"}) {
            TermuxActivity activity = laidOutActivity();

            ReflectionHelpers.callInstanceMethod(activity, door);

            assertEquals(door, Boolean.TRUE,
                ReflectionHelpers.callInstanceMethod(activity, "isSessionsPanelShowing"));
        }
    }

    private static TermuxActivity laidOutActivity() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        return activity;
    }
}
