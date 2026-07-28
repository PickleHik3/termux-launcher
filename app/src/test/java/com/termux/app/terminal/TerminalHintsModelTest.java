package com.termux.app.terminal;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TerminalHintsModelTest {

    @Test
    public void extractsAllSupportedTypesNewestFirst() {
        String text = "See https://example.com/docs.\n"
            + "failure at src/main/App.java:42:7 hash deadbeef\n"
            + "open ./build/report.json\n";
        List<TerminalHintsModel.Hint> hints = TerminalHintsModel.extract(text);
        assertEquals(TerminalHintsModel.Type.PATH, hints.get(0).type);
        assertEquals("./build/report.json", hints.get(0).value);
        assertTrue(hints.stream().anyMatch(h -> h.type == TerminalHintsModel.Type.URL
            && h.value.equals("https://example.com/docs")));
        assertTrue(hints.stream().anyMatch(h -> h.type == TerminalHintsModel.Type.LINE
            && h.value.equals("src/main/App.java:42:7")));
        assertTrue(hints.stream().anyMatch(h -> h.type == TerminalHintsModel.Type.HASH
            && h.value.equals("deadbeef")));
    }

    @Test
    public void nestedPathsInsideUrlsAreNotDuplicated() {
        List<TerminalHintsModel.Hint> hints = TerminalHintsModel.extract("https://host/a/b/file.txt");
        assertEquals(1, hints.size());
        assertEquals(TerminalHintsModel.Type.URL, hints.get(0).type);
    }

    @Test
    public void duplicateValuesAndOutputAreBounded() {
        StringBuilder text = new StringBuilder("./same.txt ./same.txt ");
        for (int i = 0; i < 100; i++) text.append("./path").append(i).append(".txt ");
        List<TerminalHintsModel.Hint> hints = TerminalHintsModel.extract(text.toString());
        assertEquals(TerminalHintsModel.MAX_HINTS, hints.size());
        assertEquals('a', hints.get(0).label);
        assertEquals('0', hints.get(hints.size() - 1).label);
    }
}
