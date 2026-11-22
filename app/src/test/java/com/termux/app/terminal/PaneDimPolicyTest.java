package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PaneDimPolicyTest {

    @Test public void aLonePaneIsNeverDimmed() {
        assertTrue(PaneDimPolicy.dimmed("a", Collections.singletonList("a")).isEmpty());
        assertTrue(PaneDimPolicy.dimmed(null, Collections.singletonList("a")).isEmpty());
        assertTrue(PaneDimPolicy.dimmed(null, Collections.<String>emptyList()).isEmpty());
    }

    @Test public void everyPaneButTheFocusedOneIsDimmed() {
        Set<String> dimmed = PaneDimPolicy.dimmed("b", Arrays.asList("a", "b", "c"));
        assertEquals(new HashSet<>(Arrays.asList("a", "c")), dimmed);
    }

    @Test public void noFocusDimsThemAll() {
        assertEquals(new HashSet<>(Arrays.asList("a", "b")),
            PaneDimPolicy.dimmed(null, Arrays.asList("a", "b")));
    }

    @Test public void aFocusOutsideTheWindowDimsThemAll() {
        assertEquals(new HashSet<>(Arrays.asList("a", "b")),
            PaneDimPolicy.dimmed("z", Arrays.asList("a", "b")));
    }

    @Test public void nullLeavesAreSkipped() {
        assertEquals(Collections.singleton("a"),
            PaneDimPolicy.dimmed("b", Arrays.asList("a", null, "b")));
    }
}
