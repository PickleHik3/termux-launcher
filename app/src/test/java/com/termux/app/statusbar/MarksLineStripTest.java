package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.os.Build;
import android.view.View;

import com.termux.R;
import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Where the status bar's marks line stands in the real layout: with the window chips it stands in
 * for, in the Terminal's strip, so the top stack's one write of that strip carries it with the
 * Terminal and rests it out of sight on Home. Home's strip holds no marks of its own.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class MarksLineStripTest {

    @Test public void theLineStandsInTheTerminalsStripAndHomesStripHoldsNone() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        View strip = activity.findViewById(R.id.terminal_status_place_content);
        View homeStrip = activity.findViewById(R.id.terminal_status_home_chips);
        assertNotNull(strip);
        assertNotNull(homeStrip);
        for (int id : new int[] {R.id.terminal_status_marks_row, R.id.terminal_status_marks_column}) {
            View line = activity.findViewById(id);
            assertNotNull(line);
            assertSame("the Terminal's strip is its parent", strip, line.getParent());
            assertEquals(View.GONE, line.getVisibility());
        }
        assertEquals("the Terminal's strip is the one the top stack slides with the Terminal",
            R.id.terminal_status_place_content, strip.getId());
        assertNotNull("the title host Home's pill hands its title to",
            activity.findViewById(R.id.terminal_status_home_title));
    }
}
