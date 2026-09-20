package com.termux.app.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Search over the real copy: the spec's examples, in the spec's order. */
public class HelpSearchTest {

    private final HelpTopics.Text text = new HelpTestText();

    private List<HelpSearch.Result> search(String query) {
        return HelpSearch.search(query, PaneWallPage.TERMINAL, text);
    }

    private List<String> ids(String query) {
        List<String> ids = new ArrayList<>();
        for (HelpSearch.Result result : search(query)) ids.add(result.id);
        return ids;
    }

    private HelpSearch.Result find(String query, String id) {
        for (HelpSearch.Result result : search(query)) if (result.id.equals(id)) return result;
        return null;
    }

    @Test public void aBlankQueryFindsNothingSoHomeCanSuggestInstead() {
        assertTrue(search("").isEmpty());
        assertTrue(search("   ").isEmpty());
        assertTrue(search(null).isEmpty());
    }

    @Test public void tabsFindsWindows() {
        assertTrue(ids("tabs").contains("windows"));
        assertEquals("windows", ids("tabs").get(0));
    }

    @Test public void splitFindsPanesAndKeyboardShortcuts() {
        List<String> ids = ids("split");
        assertTrue(ids.toString(), ids.contains("panes"));
        assertTrue(ids.toString(), ids.contains("shortcuts"));
    }

    @Test public void pasteFindsCopyAndPaste() {
        assertEquals("copy_paste", ids("paste").get(0));
        assertEquals(HelpSearch.Kind.GUIDE, find("paste", "copy_paste").kind);
    }

    @Test public void cannotTypeFindsTheKeyboardRecovery() {
        List<String> ids = ids("can't type");
        assertTrue(ids.toString(), ids.contains("fix_keyboard"));
        assertEquals(HelpSearch.Kind.FIX, find("can't type", "fix_keyboard").kind);
        assertEquals(HelpTopics.Group.MISSING, find("can't type", "fix_keyboard").group);
    }

    @Test public void baseFindsTheTermAndTheAppearanceTopic() {
        List<HelpSearch.Result> results = search("base");
        Set<String> terms = new HashSet<>();
        Set<String> topics = new HashSet<>();
        for (HelpSearch.Result result : results) {
            (result.kind == HelpSearch.Kind.TERM ? terms : topics).add(result.id);
        }
        assertTrue(terms.contains("base"));
        assertTrue(topics.toString(), topics.contains("base_values"));
        assertTrue(topics.toString(), topics.contains("appearance_editor"));
        // An exact term match is not buried under body matches.
        assertEquals(HelpSearch.Kind.TERM, results.get(0).kind);
        assertEquals("base", results.get(0).id);
    }

    @Test public void anExactTitleBeatsAnAliasWhichBeatsTheBody() {
        HelpSearch.Result exact = find("workspaces", "workspaces");
        HelpSearch.Result alias = find("restore", "workspaces");
        assertNotNull(exact);
        assertNotNull(alias);
        assertTrue(exact.score + " > " + alias.score, exact.score > alias.score);
        HelpSearch.Result body = find("permission", "fix_stats");
        assertNotNull(body);
        assertTrue(body.score < alias.score);
    }

    @Test public void theCurrentPlaceOnlyBreaksTiesAndHidesNothing() {
        // The display's own topics are found from the terminal, where their controls do not exist.
        assertTrue(ids("touchpad").contains("touchpad"));
        assertTrue(ids("desktop").contains("start"));
        HelpSearch.Result fromTerminal = find("dock", "dock");
        List<HelpSearch.Result> fromWidgets =
            HelpSearch.search("dock", PaneWallPage.WIDGETS, text);
        HelpSearch.Result elsewhere = null;
        for (HelpSearch.Result result : fromWidgets) if (result.id.equals("dock")) elsewhere = result;
        assertNotNull(fromTerminal);
        assertNotNull(elsewhere);
        assertEquals(fromTerminal.score - 1, elsewhere.score);
        assertEquals(search("dock").size(), fromWidgets.size());
    }

    @Test public void noTopicIsListedTwiceAndEveryRowCanBeDrawn() {
        for (String query : new String[] {"keyboard", "display", "pane", "settings", "help", "a"}) {
            Set<String> seen = new HashSet<>();
            for (HelpSearch.Result result : search(query)) {
                assertTrue(query + ": " + result.id, seen.add(result.kind + "/" + result.id));
                assertFalse(result.title.isEmpty());
                assertFalse(result.excerpt.isEmpty());
                assertTrue(result.score > 0);
            }
        }
    }

    @Test public void resultsAreBestFirst() {
        for (String query : new String[] {"keyboard", "split", "base", "display"}) {
            List<HelpSearch.Result> results = search(query);
            for (int i = 1; i < results.size(); i++) {
                assertTrue(query, results.get(i - 1).score >= results.get(i).score);
            }
        }
    }

    @Test public void everyTopicCanBeFoundByItsOwnTitle() {
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            List<String> ids = ids(text.get(entry.titleRes));
            assertTrue(entry.id + " cannot find itself", ids.contains(entry.id));
        }
        for (HelpGlossary.Term term : HelpGlossary.all()) {
            boolean found = false;
            for (HelpSearch.Result result : search(text.get(term.titleRes))) {
                if (result.kind == HelpSearch.Kind.TERM && result.id.equals(term.id)) found = true;
            }
            assertTrue(term.id + " cannot find itself", found);
        }
    }
}
