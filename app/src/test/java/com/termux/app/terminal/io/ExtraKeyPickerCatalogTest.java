package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.extrakeys.ExtraKeysConstants;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;

public class ExtraKeyPickerCatalogTest {

    @Test
    public void commonLeadsWithTheKeysTheWireframeShows() {
        List<String> common = ExtraKeyPickerCatalog.keys(ExtraKeyPickerCatalog.Category.COMMON);
        assertEquals("ESC", common.get(0));
        assertEquals("TAB", common.get(1));
        assertEquals("CTRL", common.get(2));
        assertEquals("ALT", common.get(3));
    }

    @Test
    public void otherListsFunctionKeysInNumericOrder() {
        List<String> other = ExtraKeyPickerCatalog.keys(ExtraKeyPickerCatalog.Category.OTHER);
        assertTrue(other.indexOf("F2") < other.indexOf("F10"));
        assertTrue(other.contains("KEYBOARD"));
    }

    @Test
    public void everyTerminalKeyIsFindable() {
        for (String key : ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS.keySet()) {
            assertTrue(key, ExtraKeyPickerCatalog.allKeys().contains(key));
        }
    }

    @Test
    public void searchIgnoresCaseAndAnEmptyQueryListsAll() {
        assertTrue(ExtraKeyPickerCatalog.search("pgu").contains("PGUP"));
        assertEquals(ExtraKeyPickerCatalog.allKeys(), ExtraKeyPickerCatalog.search("  "));
    }

    @Test
    public void noKeyIsListedTwice() {
        List<String> all = ExtraKeyPickerCatalog.allKeys();
        assertEquals(all.size(), new HashSet<>(all).size());
    }
}
