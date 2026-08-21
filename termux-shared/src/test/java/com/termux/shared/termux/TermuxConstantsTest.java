package com.termux.shared.termux;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TermuxConstantsTest {

    @Test
    public void stylingActivityClassesAreNotDerivedFromThePackageName() {
        // Rebranding an edition changes TERMUX_PACKAGE_NAME (and with it the styling plugin's
        // applicationId), but the plugin's Java classes keep their com.termux.styling names.
        // Deriving these from the package made every non-com.termux edition resolve a class that
        // does not exist and silently drop the Style row (issue #13).
        assertEquals("com.termux.styling.TermuxStyleActivity",
            TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        assertEquals("com.termux.styling.activities.TermuxStylingMainActivity",
            TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_MAIN_ACTIVITY_NAME);
    }
}
