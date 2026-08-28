package com.termux.app.settings;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TermuxPropertiesFileTest {

    @Test
    public void aKeyWithRegexMetacharactersIsMatchedLiterally() {
        // Unquoted, "extra.keys" would also match "extraXkeys" and "[" would not compile at all.
        List<String> lines = Arrays.asList("extraXkeys=1", "extra.keys = 2", "# extra.keys=3");

        List<String> out = TermuxPropertiesFile.withProperty(lines, "extra.keys", "true");

        assertEquals(Arrays.asList("extraXkeys=1", "extra.keys=true", "# extra.keys=3"), out);
        assertEquals(Arrays.asList("x=1", "a[0]=v"),
            TermuxPropertiesFile.withProperty(Collections.singletonList("x=1"), "a[0]", "v"));
    }

    @Test
    public void anIndentedAssignmentIsReplacedInPlaceAndUnrelatedLinesSurvive() {
        List<String> lines = Arrays.asList("# comment", "  night-mode = dark", "bell-character=beep");

        List<String> out = TermuxPropertiesFile.withProperty(lines, "night-mode", "light");

        assertEquals(Arrays.asList("# comment", "night-mode=light", "bell-character=beep"), out);
    }

    @Test
    public void anAbsentKeyIsAppendedOnce() {
        assertEquals(Arrays.asList("a=1", "b=2"),
            TermuxPropertiesFile.withProperty(Collections.singletonList("a=1"), "b", "2"));
        assertEquals(Collections.singletonList("b=2"),
            TermuxPropertiesFile.withProperty(Collections.emptyList(), "b", "2"));
    }
}
