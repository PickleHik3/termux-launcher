package com.termux.app.help;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Every help topic has a drawn diagram, and every drawable the table names inflates. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class HelpDiagramsTest {

    @Test public void everyTopicIdResolvesToADrawable() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            int resource = HelpDiagrams.forTopic(entry.id);
            assertNotEquals("no diagram for topic " + entry.id, 0, resource);
            String name = activity.getResources().getResourceEntryName(resource);
            assertTrue(entry.id + " -> " + name,
                name.equals("help_diagram_" + entry.id) || name.equals("help_anim_" + entry.id));
        }
    }

    @Test public void everyDrawableInflatesUnderTheAppTheme() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            Drawable drawable = activity.getDrawable(HelpDiagrams.forTopic(entry.id));
            assertNotNull(entry.id, drawable);
            assertTrue(entry.id, drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0);
        }
    }

    @Test public void aTopicWithoutADiagramIsReportedAsNone() {
        assertEquals(0, HelpDiagrams.forTopic("no_such_topic"));
        assertEquals(0, HelpDiagrams.forTopic(null));
    }

    @Test public void theRemovedTopicsHaveNoDiagram() {
        for (String gone : new String[] {"hold_terminal", "start", "setup", "base_values",
            "distro_apps"}) {
            assertEquals(gone, 0, HelpDiagrams.forTopic(gone));
        }
    }
}
