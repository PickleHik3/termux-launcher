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

    @Test public void theHealthDescriptionNamesSportAndWorkouts() {
        // pong 2026-10-05: E2B went from 15/18 to 17/18 on an 18-app set with this wording (Strava, Calm).
        String prompt = LauncherCategorySortPrompt.singleAppPrompt("Strava", "com.strava");
        assertTrue(prompt.contains("- health: health, fitness, sport, workouts and medical\n"));
        assertFalse(prompt.contains("health, fitness and medical"));
    }

    @Test public void singleAppPromptListsEveryAssignableCategoryAndNoSyntheticOne() {
        String prompt = LauncherCategorySortPrompt.singleAppPrompt("Signal", "org.thoughtcrime");
        for (AppDrawerCategory category : AppDrawerCategory.values()) {
            if (category.synthetic || category == AppDrawerCategory.DESKTOPS
                || category == AppDrawerCategory.LINUX_APPS) {
                assertFalse("slug offered that no Android app belongs in: " + category.slug,
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

    // ------------------------------------------------------------------ batches

    private static List<AppEntry> eight() {
        List<AppEntry> apps = new ArrayList<>();
        for (int i = 0; i < 8; i++) apps.add(new AppEntry("com.google.android.apps.app" + i, "App number " + i));
        return apps;
    }

    @Test public void batchPromptSendsTheCategoriesOnceAndEveryAppOnce() {
        List<AppEntry> apps = eight();
        String prompt = LauncherCategorySortPrompt.batchPrompt(apps);
        assertEquals(1, countOccurrences(prompt, "- social: "));
        assertEquals(1, countOccurrences(prompt, "- health: health, fitness, sport, workouts and medical\n"));
        for (AppEntry app : apps) assertTrue(prompt.contains("- " + app.packageName + " (" + app.label + ")\n"));
    }

    @Test public void batchPromptCutsLongLabelsAndKeepsThemOnOneLine() {
        String label = "An extraordinarily long application label\nwith a second line";
        String prompt = LauncherCategorySortPrompt.batchPrompt(Arrays.asList(new AppEntry("com.example.long", label)));
        String shown = LauncherCategorySortPrompt.shortLabel(label);
        assertEquals(LauncherCategorySortPrompt.LABEL_MAX_CHARS, shown.length());
        assertFalse(shown.contains("\n"));
        assertTrue(prompt.contains("- com.example.long (" + shown + ")\n"));
    }

    @Test public void estimateIsPessimisticForAsciiAndForWideText() {
        assertEquals(0, LauncherCategorySortPrompt.estimateTokens(""));
        assertEquals(1, LauncherCategorySortPrompt.estimateTokens("ab"));
        // 12 characters at 2.5 a token, rounded up.
        assertEquals(5, LauncherCategorySortPrompt.estimateTokens("com.whatsapp"));
        assertEquals(4, LauncherCategorySortPrompt.estimateTokens("微信"));
    }

    @Test public void aBatchOfEightTypicalAppsFitsTheSortingWindowWithItsMargin() {
        List<AppEntry> apps = eight();
        int prompt = LauncherCategorySortPrompt.estimateTokens(LauncherCategorySortPrompt.batchPrompt(apps));
        int reply = LauncherCategorySortPrompt.batchMaxTokens(apps);
        assertTrue(LauncherCategorySortPrompt.fitsWindow(apps));
        assertTrue(prompt + reply <= 1024 - LauncherCategorySortPrompt.WINDOW_MARGIN_TOKENS);
        // The cap is every app's "package: information_reading" line plus the headroom.
        int lines = 0;
        for (AppEntry app : apps)
            lines += LauncherCategorySortPrompt.estimateTokens(app.packageName + ": information_reading\n");
        assertEquals(LauncherCategorySortPrompt.REPLY_HEADROOM_TOKENS + lines, reply);
    }

    @Test public void eightAppsWithVeryLongNamesDoNotFit() {
        List<AppEntry> apps = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            apps.add(new AppEntry("com.example.an.unusually.long.package.name.for.an.app" + i,
                "一二三四五六七八九十一二三四五六"));
        }
        assertFalse(LauncherCategorySortPrompt.fitsWindow(apps));
        assertTrue(LauncherCategorySortPrompt.fitsWindow(apps.subList(0, 1)));
    }

    @Test public void parseBatchReplyReadsTheAskedFormat() {
        List<AppEntry> asked = Arrays.asList(new AppEntry("com.whatsapp", "WhatsApp"),
            new AppEntry("com.strava", "Strava"));
        Map<String, String> result = LauncherCategorySortPrompt.parseBatchReply(
            "com.whatsapp: social\ncom.strava: health\n", asked);
        assertEquals(2, result.size());
        assertEquals("social", result.get("com.whatsapp"));
        assertEquals("health", result.get("com.strava"));
    }

    @Test public void parseBatchReplyToleratesWhatSmallModelsWrite() {
        List<AppEntry> asked = Arrays.asList(
            new AppEntry("com.whatsapp", "WhatsApp"),
            new AppEntry("com.strava", "Strava"),
            new AppEntry("com.example.hub", "Games Hub"),
            new AppEntry("org.mozilla.firefox", "Firefox"),
            new AppEntry("com.example.bank", "Bankly"),
            new AppEntry("com.example.notes", "Notes"));
        Map<String, String> result = LauncherCategorySortPrompt.parseBatchReply(
            "Here are the categories:\n"
                + "- **com.whatsapp**: Social\n"
                + "2. `com.strava` -> HEALTH.\n"
                // The echoed label names a category; the answer after it is what counts.
                + "  * com.example.hub (Games Hub): utilities\n"
                + "org.mozilla.firefox:   utilities\n"
                + "Bankly: finance\n"
                + "com.example.notes: wizardry\n",
            asked);
        assertEquals("social", result.get("com.whatsapp"));
        assertEquals("health", result.get("com.strava"));
        assertEquals("utilities", result.get("com.example.hub"));
        assertEquals("utilities", result.get("org.mozilla.firefox"));
        assertEquals("finance", result.get("com.example.bank"));
        // An unknown id is no answer.
        assertFalse(result.containsKey("com.example.notes"));
        assertEquals(5, result.size());
    }

    @Test public void parseBatchReplyNeverReturnsWhatWasNotAskedAndKeepsTheFirstAnswer() {
        List<AppEntry> asked = Arrays.asList(new AppEntry("com.foo", "Foo"),
            new AppEntry("com.foo.bar", "Foo Bar"));
        Map<String, String> result = LauncherCategorySortPrompt.parseBatchReply(
            "com.invented.app: games\n"
                + "com.foo.bar: travel\n"
                + "com.foo: finance\n"
                + "com.foo: games\n",
            asked);
        assertEquals(2, result.size());
        assertEquals("travel", result.get("com.foo.bar"));
        assertEquals("finance", result.get("com.foo"));
        assertTrue(LauncherCategorySortPrompt.parseBatchReply(null, asked).isEmpty());
        assertTrue(LauncherCategorySortPrompt.parseBatchReply("", asked).isEmpty());
    }

    @Test public void parseBatchReplyKeepsOtherAsAnAnswer() {
        List<AppEntry> asked = Arrays.asList(new AppEntry("com.example.odd", "Odd"));
        assertEquals("other", LauncherCategorySortPrompt.parseBatchReply("com.example.odd: other", asked)
            .get("com.example.odd"));
    }

    @Test public void theLinuxGroupsAreNeitherOfferedNorAccepted() {
        assertFalse(LauncherCategorySortPrompt.categorySlugs().contains("desktops"));
        assertFalse(LauncherCategorySortPrompt.categorySlugs().contains("linux_apps"));
        assertNull(LauncherCategorySortPrompt.parseCategory("linux_apps"));
        assertNull(LauncherCategorySortPrompt.parseCategory("desktops"));
        Map<String, String> result = LauncherCategorySortPrompt.parsePastedReply(
            "[linux_apps]\ncom.example.term\n[desktops]\ncom.example.vnc\n",
            new HashSet<>(Arrays.asList("com.example.term", "com.example.vnc")));
        assertTrue(result.isEmpty());
    }

    @Test public void parsePastedReplyDropsSyntheticSectionName() {
        Set<String> known = new HashSet<>(Arrays.asList("com.example.chat"));
        Map<String, String> result = LauncherCategorySortPrompt.parsePastedReply(
            "[suggestions]\ncom.example.chat\n", known);
        assertTrue(result.isEmpty());
    }
}
