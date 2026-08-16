package com.termux.app.launcher.data;

import static org.junit.Assert.*;

import com.termux.app.launcher.data.LauncherCategorySortPrompt.AppEntry;
import com.termux.app.launcher.drawer.AppDrawerCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class LauncherCategorySortPromptTest {

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    @Test public void singleAppPromptListsEveryAssignableCategoryAndNoSyntheticOne() {
        String prompt = LauncherCategorySortPrompt.singleAppPrompt("Signal", "org.thoughtcrime");
        for (AppDrawerCategory category : AppDrawerCategory.values()) {
            if (category.synthetic) {
                assertFalse("synthetic slug leaked: " + category.slug,
                    prompt.contains(category.slug));
            } else {
                assertTrue("missing slug: " + category.slug,
                    prompt.contains("- " + category.slug + ": "));
            }
        }
        assertTrue(prompt.contains("App name: Signal"));
        assertTrue(prompt.contains("Package: org.thoughtcrime"));
    }

    @Test public void parseCategoryAcceptsBareSlug() {
        assertEquals("social", LauncherCategorySortPrompt.parseCategory("social"));
    }

    @Test public void parseCategoryFindsSlugInsideSentence() {
        assertEquals("photo_video", LauncherCategorySortPrompt.parseCategory(
            "This app belongs in the photo_video category."));
    }

    @Test public void parseCategoryIsCaseInsensitive() {
        assertEquals("finance", LauncherCategorySortPrompt.parseCategory("FINANCE"));
        assertEquals("games", LauncherCategorySortPrompt.parseCategory("Games"));
    }

    @Test public void parseCategoryRejectsBareNumber() {
        assertNull(LauncherCategorySortPrompt.parseCategory("2"));
    }

    @Test public void parseCategoryRejectsEmptyReply() {
        assertNull(LauncherCategorySortPrompt.parseCategory(""));
        assertNull(LauncherCategorySortPrompt.parseCategory("   "));
        assertNull(LauncherCategorySortPrompt.parseCategory(null));
    }

    @Test public void parseCategoryDoesNotMatchSlugInsideLongerWord() {
        assertNull(LauncherCategorySortPrompt.parseCategory("socializing"));
        assertNull(LauncherCategorySortPrompt.parseCategory("healthy-ish"));
    }

    @Test public void pasteablePromptListsEverySuppliedAppExactlyOnce() {
        List<AppEntry> apps = new ArrayList<>(Arrays.asList(
            new AppEntry("com.example.chat", "Chatter"),
            new AppEntry("com.example.bank", "Bankly"),
            new AppEntry("com.example.maps", "Mapper")));
        String prompt = LauncherCategorySortPrompt.pasteablePrompt(apps);
        for (AppEntry app : apps) {
            assertEquals("package listed wrong number of times: " + app.packageName,
                1, countOccurrences(prompt, app.packageName));
            assertEquals("label listed wrong number of times: " + app.label,
                1, countOccurrences(prompt, app.label));
            assertTrue(prompt.contains(app.packageName + "\t" + app.label));
        }
        assertTrue(prompt.contains("- social: "));
    }

    @Test public void parsePastedReplyMapsWellFormedBlock() {
        Set<String> known = new HashSet<>(Arrays.asList(
            "com.example.chat", "com.example.bank", "com.example.maps"));
        Map<String, String> result = LauncherCategorySortPrompt.parsePastedReply(
            "[social]\n"
                + "com.example.chat\n"
                + "\n"
                + "[finance]\n"
                + "com.example.bank\n"
                + "\n"
                + "[travel]\n"
                + "com.example.maps\n",
            known);
        assertEquals(3, result.size());
        assertEquals("social", result.get("com.example.chat"));
        assertEquals("finance", result.get("com.example.bank"));
        assertEquals("travel", result.get("com.example.maps"));
    }

    @Test public void parsePastedReplyDropsPackageNotInKnownSet() {
        Set<String> known = new HashSet<>(Arrays.asList("com.example.chat"));
        Map<String, String> result = LauncherCategorySortPrompt.parsePastedReply(
            "[social]\n"
                + "com.example.chat\n"
                + "com.hallucinated.app\n",
            known);
        assertEquals(1, result.size());
        assertEquals("social", result.get("com.example.chat"));
        assertFalse(result.containsKey("com.hallucinated.app"));
    }

    @Test public void parsePastedReplyDropsUnknownSectionName() {
        Set<String> known = new HashSet<>(Arrays.asList(
            "com.example.chat", "com.example.wizard"));
        Map<String, String> result = LauncherCategorySortPrompt.parsePastedReply(
            "[social]\n"
                + "com.example.chat\n"
                + "\n"
                + "[wizardry]\n"
                + "com.example.wizard\n",
            known);
        assertEquals(1, result.size());
        assertEquals("social", result.get("com.example.chat"));
        assertFalse(result.containsKey("com.example.wizard"));
    }

    @Test public void parsePastedReplyDropsSyntheticSectionName() {
        Set<String> known = new HashSet<>(Arrays.asList("com.example.chat"));
        Map<String, String> result = LauncherCategorySortPrompt.parsePastedReply(
            "[suggestions]\ncom.example.chat\n", known);
        assertTrue(result.isEmpty());
    }
}
