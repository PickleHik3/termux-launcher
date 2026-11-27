package com.termux.app.barsearch;

import static org.junit.Assert.assertEquals;

import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.x11.X11Apps;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The bar search's sections, their order and the filter (#435). */
public class SearchSectionsTest {

    private static LauncherAppEntry android(String label) {
        return new LauncherAppEntry(new AppRef("com.example." + label.toLowerCase(), "Main"), label, null);
    }

    private static LauncherAppEntry linux(String label) {
        return new LauncherAppEntry(X11Apps.ref(label.toLowerCase() + ".desktop"), label, null);
    }

    private static LauncherAppEntry terminal() {
        return new LauncherAppEntry(LauncherEntries.terminal(), "Terminal", null);
    }

    private static LauncherAppEntry display() {
        return new LauncherAppEntry(LauncherEntries.display(), "Display", null);
    }

    private static LauncherAppEntry assistant() {
        return new LauncherAppEntry(LauncherEntries.assistant(), "Assistant", null);
    }

    private static List<String> labels(List<SearchSections.Row> rows) {
        List<String> out = new ArrayList<>();
        for (SearchSections.Row row : rows) out.add(row.entry == null ? "<all>" : row.entry.label);
        return out;
    }

    @Test
    public void nothingTypedListsAllAppsThenTerminalThenLinuxThenAndroid() {
        List<LauncherAppEntry> catalogue = Arrays.asList(android("Maps"), linux("Gimp"), assistant(),
            display(), terminal(), android("Clock"), linux("Firefox"));
        List<SearchSections.Row> rows = SearchSections.arrange(catalogue, "");
        assertEquals(Arrays.asList("<all>", "Terminal", "Assistant", "Gimp", "Display", "Firefox",
            "Maps", "Clock"), labels(rows));
        assertEquals(SearchSections.Section.ALL_APPS, rows.get(0).section);
        assertEquals(SearchSections.Section.TERMINAL, rows.get(1).section);
        assertEquals(SearchSections.Section.LINUX, rows.get(3).section);
        assertEquals(SearchSections.Section.ANDROID, rows.get(6).section);
    }

    @Test
    public void typingFiltersAllThreeAndDropsAllApps() {
        List<LauncherAppEntry> catalogue = Arrays.asList(android("Terminus"), linux("Terminator"),
            terminal(), android("Maps"), linux("Gimp"));
        List<String> labels = labels(SearchSections.arrange(catalogue, "term"));
        assertEquals("Terminal", labels.get(0));
        assertEquals(true, labels.contains("Terminator"));
        assertEquals(true, labels.contains("Terminus"));
        assertEquals(false, labels.contains("Maps"));
        assertEquals(false, labels.contains("<all>"));
        assertEquals(true, labels.indexOf("Terminator") < labels.indexOf("Terminus"));
    }

    @Test
    public void aBlankQueryIsNothingTyped() {
        List<LauncherAppEntry> catalogue = Arrays.asList(android("Maps"), terminal());
        assertEquals(Arrays.asList("<all>", "Terminal", "Maps"), labels(SearchSections.arrange(catalogue, "  ")));
    }

    @Test
    public void aSectionWithNoMatchIsAbsent() {
        List<LauncherAppEntry> catalogue = Arrays.asList(android("Maps"), terminal(), linux("Gimp"));
        List<SearchSections.Row> rows = SearchSections.arrange(catalogue, "maps");
        assertEquals(Arrays.asList("Maps"), labels(rows));
        assertEquals(SearchSections.Section.ANDROID, rows.get(0).section);
    }
}
