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

    @Test
    public void withoutPropertyDropsEveryAssignmentAndKeepsCommentsAndOthers() {
        List<String> lines = Arrays.asList("# extra-keys-home=1", "a=1", "  extra-keys-home = [[ESC]]",
            "extra-keys-home2=x", "extra-keys-home=again");

        assertEquals(Arrays.asList("# extra-keys-home=1", "a=1", "extra-keys-home2=x"),
            TermuxPropertiesFile.withoutProperty(lines, "extra-keys-home"));
    }

    @Test
    public void withoutPropertyMatchesTheKeyLiterallyAndLeavesAbsentKeysAlone() {
        List<String> lines = Arrays.asList("extraXkeys=1", "extra.keys=2");

        assertEquals(Collections.singletonList("extraXkeys=1"),
            TermuxPropertiesFile.withoutProperty(lines, "extra.keys"));
        assertEquals(lines, TermuxPropertiesFile.withoutProperty(lines, "absent"));
    }

    @Test
    public void removalFindsAKeyWrittenWithEqualsColonOrSpaceAndTakesItsContinuationLines() {
        List<String> lines = Arrays.asList("! note", "a=1", "  extra-keys-terminal: [[ESC]]",
            "extra-keys-terminal2 [[B]]", "extra-keys-terminal\t[[C], \\", "    [D]]", "z=9", "extra-keys-terminal");

        assertEquals(Arrays.asList("! note", "a=1", "extra-keys-terminal2 [[B]]", "z=9"),
            TermuxPropertiesFile.withoutProperty(lines, "extra-keys-terminal"));
    }

    @Test
    public void replacementTakesTheContinuationLinesAndOtherLinesStayByteForByte() {
        List<String> lines = Arrays.asList("# k=old", "k : first \\", "  second", "  x = \\\\", "y=2");

        assertEquals(Arrays.asList("# k=old", "k=new", "  x = \\\\", "y=2"),
            TermuxPropertiesFile.withProperty(lines, "k", "new"));
    }

    @Test
    public void aLineInsideAnotherValuesContinuationIsNotAKey() {
        List<String> lines = Arrays.asList("a=1 \\", "k=2", "k=3");

        assertEquals(Arrays.asList("a=1 \\", "k=2"),
            TermuxPropertiesFile.withoutProperty(lines, "k"));
    }
}
