package com.termux.app.help;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.R;

import org.junit.Test;

/** The help names the Looks the editor lists: Clear, Frosted, Tinted, Solid, Liquid and Custom (#320). */
public class HelpLookNamesTest {

    private final HelpTestText text = new HelpTestText();

    @Test
    public void helpNamesTheLooksByTheirCurrentNames() {
        for (int res : new int[] {R.string.help_topic_appearance_step1,
                                  R.string.help_term_look_definition}) {
            String copy = text.get(res);
            assertFalse(copy, copy.contains("Mist"));
            assertFalse(copy, copy.contains("Tint,") || copy.contains("Tint and"));
            assertTrue(copy, copy.contains("Frosted") && copy.contains("Tinted"));
        }
    }
}
