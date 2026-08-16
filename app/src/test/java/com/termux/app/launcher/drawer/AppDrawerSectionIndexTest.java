package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The A-Z index.
 *
 * <p>The catalogue used here is shaped like the one the launcher actually gets: a body sorted by
 * {@code ResolveInfo.DisplayNameComparator} followed by an <em>appended</em> tail of work and clone
 * profile entries, which is what {@code LauncherAppDataProvider.loadSnapshot} plus
 * {@code addProfileApps} produce. An index built straight over that is a lie, and the two tests that
 * matter here are the ones that pin the sort as the thing which makes it true.
 */
public class AppDrawerSectionIndexTest {

    /** The primary user's apps, alphabetical, as the provider sorts them. */
    private static final String[] BODY = {
        "1Weather", "Calculator", "Camera", "Chrome", "Files", "Maps", "Settings", "Termux",
    };

    /** Work and clone copies, appended after the body exactly as the provider appends them. */
    private static List<LauncherAppEntry> realShapedCatalogue() {
        List<LauncherAppEntry> apps = new ArrayList<>();
        for (String label : BODY) {
            apps.add(personal(label));
        }
        apps.add(work("Chrome"));
        apps.add(work("Slack"));
        apps.add(clone("Camera"));
        apps.add(work("Files"));
        return apps;
    }

    private static LauncherAppEntry personal(String label) {
        return entry(label, "com.example." + label.toLowerCase(), -1, false);
    }

    private static LauncherAppEntry work(String label) {
        return entry(label, "com.example." + label.toLowerCase(), 10, false);
    }

    private static LauncherAppEntry clone(String label) {
        return entry(label, "com.example." + label.toLowerCase(), -1, true);
    }

    private static LauncherAppEntry entry(String label, String pkg, int userId, boolean cloned) {
        AppRef ref = new AppRef(pkg, pkg + ".MainActivity", userId, userId, cloned,
            userId >= 0 ? "Work" : "");
        return new LauncherAppEntry(ref, label, null);
    }

    private static List<String> labelsOf(List<LauncherAppEntry> entries) {
        List<String> labels = new ArrayList<>(entries.size());
        for (LauncherAppEntry entry : entries) {
            labels.add(entry.label);
        }
        return labels;
    }

    @Test
    public void aProfileTailCatalogueComesOutFullyAlphabetical() {
        List<LauncherAppEntry> sorted = AppDrawerSectionIndex.sortByLabel(realShapedCatalogue());
        assertEquals(Arrays.asList(
            "1Weather", "Calculator", "Camera", "Camera", "Chrome", "Chrome", "Files", "Files",
            "Maps", "Settings", "Slack", "Termux"), labelsOf(sorted));
        // Case is not a sort key: a lower-case label sorts among its letter, not after Z.
        List<LauncherAppEntry> mixedCase = AppDrawerSectionIndex.sortByLabel(Arrays.asList(
            personal("Zoom"), personal("aCalendar"), personal("Books")));
        assertEquals(Arrays.asList("aCalendar", "Books", "Zoom"), labelsOf(mixedCase));
        // The input is never mutated — the provider hands out an unmodifiable list.
        List<LauncherAppEntry> source = Collections.unmodifiableList(realShapedCatalogue());
        assertEquals("Chrome", AppDrawerSectionIndex.sortByLabel(source).get(4).label);
        assertEquals("1Weather", source.get(0).label);
    }

    @Test
    public void everyLetterIsAContiguousRunAfterSorting() {
        List<LauncherAppEntry> sorted = AppDrawerSectionIndex.sortByLabel(realShapedCatalogue());
        AppDrawerSectionIndex index = AppDrawerSectionIndex.build(sorted);
        assertEquals(sorted.size(), index.entryCount());

        StringBuilder seen = new StringBuilder();
        char run = '\0';
        for (int position = 0; position < index.entryCount(); position++) {
            char letter = index.letterForPosition(position);
            assertEquals(AppDrawerSectionIndex.letterOf(sorted.get(position)), letter);
            if (letter != run) {
                assertEquals("letter " + letter + " starts a second run at " + position,
                    -1, seen.indexOf(String.valueOf(letter)));
                seen.append(letter);
                run = letter;
                // A run's first position is the scroll target for that letter.
                assertEquals(position, index.firstPositionForLetter(letter));
            }
        }
        assertEquals("#CFMST", seen.toString());

        // And the point of all of it: over the provider's own order the runs are broken, so the
        // index would send the grid to the wrong row for every letter with a profile copy.
        AppDrawerSectionIndex unsorted = AppDrawerSectionIndex.build(realShapedCatalogue());
        assertEquals(1, unsorted.firstPositionForLetter('C'));
        // C's run appears to end at position 4 and then starts again at 8 and again at 10, so a
        // highlight over "every C" would be three separate bands with F, M, S and T inside them.
        assertNotEquals(unsorted.letterForPosition(4), unsorted.letterForPosition(8));
        assertEquals('C', unsorted.letterForPosition(8));
        assertEquals('C', unsorted.letterForPosition(10));
    }

    @Test
    public void theSortIsStableSoTwoCopiesOfAnAppKeepTheProvidersOrder() {
        // The commonest duplicate label there is: the personal and work copies of one app. The
        // provider puts the personal one first, and it must stay first across every refresh, or the
        // grid reshuffles under the user for no visible reason.
        List<LauncherAppEntry> sorted = AppDrawerSectionIndex.sortByLabel(Arrays.asList(
            personal("Files"), work("Files"), clone("Files"), personal("Calculator")));
        assertEquals(Arrays.asList("Calculator", "Files", "Files", "Files"), labelsOf(sorted));
        assertEquals(-1, sorted.get(1).appRef.userId);
        assertEquals(10, sorted.get(2).appRef.userId);
        assertTrue(sorted.get(3).appRef.clonedProfile);
    }

    @Test
    public void visibleLettersAreInAzOrderWithHashLast() {
        AppDrawerSectionIndex index = AppDrawerSectionIndex.build(
            AppDrawerSectionIndex.sortByLabel(realShapedCatalogue()));
        char[] expected = {'C', 'F', 'M', 'S', 'T', '#'};
        char[] actual = new char[index.letterCount()];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = index.letterAt(i);
        }
        assertArrayEquals(expected, actual);
        assertEquals(6, index.letterCount());
        assertEquals(0, index.indexOfLetter('C'));
        assertEquals(5, index.indexOfLetter('#'));
        assertEquals(-1, index.indexOfLetter('B'));
        // Out of range asks nothing of the draw loop.
        assertEquals('\0', index.letterAt(-1));
        assertEquals('\0', index.letterAt(index.letterCount()));
    }

    @Test
    public void firstPositionForLetterIsMinusOneForALetterNoAppHas() {
        AppDrawerSectionIndex index = AppDrawerSectionIndex.build(
            AppDrawerSectionIndex.sortByLabel(realShapedCatalogue()));
        assertEquals(-1, index.firstPositionForLetter('B'));
        assertEquals(-1, index.firstPositionForLetter('Z'));
        // Not a letter at all: nothing to scroll to, and nothing to throw over.
        assertEquals(-1, index.firstPositionForLetter('+'));
        // The lookup is case-insensitive, so a caller holding a lower-case letter still lands.
        assertEquals(index.firstPositionForLetter('C'), index.firstPositionForLetter('c'));
        assertEquals(0, index.firstPositionForLetter('#'));
        assertEquals(1, index.firstPositionForLetter('C'));
    }

    @Test
    public void lettersComeFromTheProvidersOwnNormalisation() {
        // Same rule as the dock's A-Z row and the provider's letter buckets: anything that is not
        // A-Z after upper-casing the first character is '#', so the two surfaces can never disagree.
        assertEquals('T', AppDrawerSectionIndex.letterOf(personal("Termux")));
        assertEquals('A', AppDrawerSectionIndex.letterOf(personal("aCalendar")));
        assertEquals('#', AppDrawerSectionIndex.letterOf(personal("1Weather")));
        assertEquals('#', AppDrawerSectionIndex.letterOf(personal("+Plus")));
        assertEquals('#', AppDrawerSectionIndex.letterOf(personal("Übersicht")));
        assertEquals('#', AppDrawerSectionIndex.letterOf(personal("")));
    }

    @Test
    public void anEmptyCatalogueIsAnEmptyIndex() {
        // The state between a package-change broadcast and the reload finishing.
        assertTrue(AppDrawerSectionIndex.sortByLabel(null).isEmpty());
        assertTrue(AppDrawerSectionIndex.sortByLabel(Collections.emptyList()).isEmpty());
        for (AppDrawerSectionIndex index : new AppDrawerSectionIndex[] {
            AppDrawerSectionIndex.build(null),
            AppDrawerSectionIndex.build(Collections.emptyList())}) {
            assertEquals(0, index.letterCount());
            assertEquals(0, index.entryCount());
            assertEquals(0, index.copyPositionLetters().length);
            assertEquals(-1, index.firstPositionForLetter('A'));
            assertEquals('\0', index.letterAt(0));
            assertEquals('\0', index.letterForPosition(0));
        }
    }

    @Test
    public void positionLettersComeOutAsACopyTheAdapterCanHold() {
        AppDrawerSectionIndex index = AppDrawerSectionIndex.build(
            AppDrawerSectionIndex.sortByLabel(realShapedCatalogue()));
        char[] cached = index.copyPositionLetters();
        assertEquals(index.entryCount(), cached.length);
        assertEquals('#', cached[0]);
        assertEquals('T', cached[cached.length - 1]);
        cached[0] = 'Q';
        assertEquals('#', index.letterForPosition(0));
        // Two calls hand out two arrays, so one adapter cannot corrupt another's.
        assertNotEquals(cached[0], index.copyPositionLetters()[0]);
        assertEquals('\0', index.letterForPosition(-1));
        assertEquals('\0', index.letterForPosition(index.entryCount()));
    }
}
