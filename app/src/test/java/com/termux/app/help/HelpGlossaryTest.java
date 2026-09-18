package com.termux.app.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** The launcher's own words, each defined once and each leading somewhere. */
public class HelpGlossaryTest {

    private final HelpTopics.Text text = new HelpTestText();

    @Test public void theFifteenTermsAreAllThereAndUnique() {
        assertEquals(15, HelpGlossary.all().size());
        List<String> ids = new ArrayList<>();
        for (HelpGlossary.Term term : HelpGlossary.all()) ids.add(term.id);
        assertEquals(ids.size(), new HashSet<>(ids).size());
        for (String id : ids) assertNotNull(id, HelpGlossary.term(id));
        assertNull(HelpGlossary.term("nothing_by_this_name"));
        assertNull(HelpGlossary.term(null));
    }

    @Test public void everyTermHasATitleADefinitionAndOneRealTopic() {
        for (HelpGlossary.Term term : HelpGlossary.all()) {
            assertNotEquals(term.id, 0, term.titleRes);
            assertNotEquals(term.id, 0, term.definitionRes);
            assertNotNull(term.id, HelpTopics.entry(term.topicId));
            assertFalse(term.id, text.get(term.titleRes).isEmpty());
            assertFalse(term.id, text.get(term.definitionRes).isEmpty());
        }
    }

    @Test public void theListIsOrderedByTheTitleTheReaderSees() {
        List<HelpGlossary.Term> sorted = HelpGlossary.alphabetical(text);
        assertEquals(HelpGlossary.all().size(), sorted.size());
        for (int i = 1; i < sorted.size(); i++) {
            String before = text.get(sorted.get(i - 1).titleRes);
            String after = text.get(sorted.get(i).titleRes);
            assertTrue(before + " before " + after, before.compareToIgnoreCase(after) <= 0);
        }
        assertEquals("app_drawer", sorted.get(0).id);
        assertEquals("workspace", sorted.get(sorted.size() - 1).id);
    }

    @Test public void aDefinitionIsOnePlainSentence() {
        for (HelpGlossary.Term term : HelpGlossary.all()) {
            String definition = text.get(term.definitionRes);
            assertTrue(term.id + ": " + definition, definition.length() < 120);
            assertTrue(term.id, definition.endsWith("."));
            for (String jargon : new String[] {"Hyprland", "protocol", "keybind", "X11 socket"}) {
                assertFalse(term.id + " mentions " + jargon, definition.contains(jargon));
            }
        }
    }
}
