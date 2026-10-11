package com.termux.app.terminal;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommandPaletteContactsTest {

    /** Contacts whose numbers share digits, so tiers decide order. */
    private static final CommandPaletteContacts.Contact SAM =
        new CommandPaletteContacts.Contact("Sam Rivera", 1L, "sam-key");
    private static final CommandPaletteContacts.Contact ANNA =
        new CommandPaletteContacts.Contact("Anna Lee", 2L, "anna-key");
    private static final CommandPaletteContacts.Contact BORING =
        new CommandPaletteContacts.Contact("Boring Name", 3L, "boring-key");
    /** A name that itself carries digits, so a digits query can name it. */
    private static final CommandPaletteContacts.Contact DANA =
        new CommandPaletteContacts.Contact("Dana 555", 4L, "dana-key");

    static {
        SAM.addNumber("+1 (555) 010-1234");
        SAM.addNumber("555-010-9999");
        ANNA.addNumber("555 010 7777");
        BORING.addNumber("555-010-0000");
        DANA.addNumber("555-010-8888");
    }

    private static List<CommandPaletteContacts.Contact> contacts() {
        return new ArrayList<>(Arrays.asList(SAM, ANNA, BORING, DANA));
    }

    private static List<String> names(List<CommandPaletteContacts.Match> matches) {
        List<String> names = new ArrayList<>();
        for (CommandPaletteContacts.Match match : matches) {
            names.add(match.contact.name);
        }
        return names;
    }

    @Test
    public void emptyQueryMatchesNothing() {
        assertEquals(0, CommandPaletteContacts.filter(contacts(), "").size());
        assertEquals(0, CommandPaletteContacts.filter(contacts(), "   ").size());
        assertEquals(0, CommandPaletteContacts.filter(contacts(), null).size());
    }

    @Test
    public void nameMatchesExactThenPrefixThenWordThenSubstring() {
        // Exact name outranks every other tier, whatever the order supplied.
        assertEquals(Arrays.asList("Sam Rivera"),
            names(CommandPaletteContacts.filter(contacts(), "sam rivera")));
        assertEquals(Arrays.asList("Sam Rivera"),
            names(CommandPaletteContacts.filter(contacts(), "sam")));
        // A word prefix finds the second word of a two-word name.
        assertEquals(Arrays.asList("Sam Rivera"),
            names(CommandPaletteContacts.filter(contacts(), "riv")));
        // A substring reaches a name no prefix found.
        assertTrue(names(CommandPaletteContacts.filter(contacts(), "ver")).contains("Sam Rivera"));
    }

    @Test
    public void nameMatchingIsCaseAndSpaceInsensitiveAroundTheQuery() {
        assertEquals(Arrays.asList("Sam Rivera"),
            names(CommandPaletteContacts.filter(contacts(), "  SAM  ")));
    }

    @Test
    public void digitsOnlyQueryMatchesAnyNumberTheContactCarries() {
        // The query's digits reach both of Sam's numbers, so the
        // matched number is the one the digits landed in.
        List<CommandPaletteContacts.Match> matches =
            CommandPaletteContacts.filter(contacts(), "5550109999");
        assertEquals(Arrays.asList("Sam Rivera"), names(matches));
        assertEquals("555-010-9999", matches.get(0).number);

        // A formatted query and a formatted number agree on their digits,
        // and the number dials is the one stored, formatting and all.
        assertEquals("+1 (555) 010-1234",
            CommandPaletteContacts.filter(contacts(), "+1 (555) 010-1234")
                .get(0).number);
        // A query that spells a country code the stored number omits
        // still finds it, both directions.
        assertEquals("555 010 7777",
            CommandPaletteContacts.filter(contacts(), "15550107777")
                .get(0).number);
        assertEquals("555 010 7777",
            CommandPaletteContacts.filter(contacts(), "7777").get(0).number);
    }

    @Test
    public void aNameThatCarriesDigitsOutranksTheNumbersItMatches() {
        // "555" names Dana and lands in every contact's number,
        // but the name is what the row reads, so Dana leads.
        assertEquals(Arrays.asList("Dana 555", "Anna Lee", "Sam Rivera", "Boring Name"),
            names(CommandPaletteContacts.filter(contacts(), "555")));
    }

    @Test
    public void aTierTieGoesToTheShorterNameThenTheSuppliedOrder() {
        // Every name but Dana's contains "e", so three sit in one
        // tier: the shorter name first, and Sam before Boring — the
        // order they were supplied in — when the length ties.
        assertEquals(Arrays.asList("Anna Lee", "Sam Rivera", "Boring Name"),
            names(CommandPaletteContacts.filter(contacts(), "e")));
    }

    @Test
    public void aQueryWithLettersMatchesByNameOnly() {
        // "sam555" names no one, and its digits land in every number,
        // but a query with letters in it is a name, never a number.
        assertEquals(0, CommandPaletteContacts.filter(contacts(), "sam555").size());
        // "dana 555" names Dana, and only Dana, though "555" is in every number.
        assertEquals(Arrays.asList("Dana 555"),
            names(CommandPaletteContacts.filter(contacts(), "dana 555")));
    }

    @Test
    public void aQueryWithFewerThanThreeDigitsMatchesNoNumber() {
        // Two digits land in every number here, so they search no number.
        assertEquals(0, CommandPaletteContacts.filter(contacts(), "01").size());
        assertEquals(0, CommandPaletteContacts.filter(contacts(), "+1").size());
        // The third digit is what makes it a number search.
        assertEquals(4, CommandPaletteContacts.filter(contacts(), "010").size());
    }

    @Test
    public void aCountryCodeFindsTheNumberStoredWithoutItButAShortNumberStaysOut() {
        CommandPaletteContacts.Contact home =
            new CommandPaletteContacts.Contact("Home", 5L, "home-key");
        home.addNumber("98765 43210");
        CommandPaletteContacts.Contact emergency =
            new CommandPaletteContacts.Contact("Emergency", 6L, "emergency-key");
        emergency.addNumber("911");
        List<CommandPaletteContacts.Contact> both = Arrays.asList(home, emergency);

        // The query spells the country code; the stored number omits it.
        List<CommandPaletteContacts.Match> matches =
            CommandPaletteContacts.filter(both, "+91 98765 43210");
        assertEquals(Arrays.asList("Home"), names(matches));
        assertEquals("98765 43210", matches.get(0).number);

        // "911" sits inside this longer query, but a three-digit number
        // is not found by every query that happens to contain it.
        assertEquals(0, CommandPaletteContacts.filter(both, "4911234").size());
        // It is still found by itself.
        assertEquals(Arrays.asList("Emergency"),
            names(CommandPaletteContacts.filter(both, "911")));
    }

    @Test
    public void resultsAreCappedSoTheListStaysScannable() {
        List<CommandPaletteContacts.Contact> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            CommandPaletteContacts.Contact contact =
                new CommandPaletteContacts.Contact("Contact " + i, i, "key" + i);
            contact.addNumber("555-010-" + String.format("%04d", i));
            many.add(contact);
        }
        assertEquals(8, CommandPaletteContacts.filter(many, "contact").size());
    }

    @Test
    public void aContactWithNoNumberStillMatchesByNameButHasNothingToDial() {
        CommandPaletteContacts.Contact numberless =
            new CommandPaletteContacts.Contact("No Number", 4L, "no-number");
        List<CommandPaletteContacts.Match> matches =
            CommandPaletteContacts.filter(
                Collections.singletonList(numberless), "no number");
        assertEquals(1, matches.size());
        assertEquals("", matches.get(0).number);
    }
}
