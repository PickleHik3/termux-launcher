package com.termux.app.launcher.data;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedIconOverride;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LauncherAppIconOverridesParseTest {

    private static String entry(String pkg, String activity, int userId, String pack, String drawable) {
        return "{\"packageName\":\"" + pkg + "\",\"activityName\":\"" + activity + "\","
            + "\"userId\":" + userId + ",\"iconOverride\":{\"sourceType\":\"icon_pack\","
            + "\"iconPackPackage\":\"" + pack + "\",\"drawableName\":\"" + drawable + "\"}}";
    }

    @Test
    public void overridesAreKeyedByTheAppsStableId() {
        Map<String, PinnedIconOverride> parsed = LauncherConfigRepository.parseAppIconOverrides(
            "[" + entry("com.a", "com.a.Main", -1, "pack.one", "a_icon") + ","
                + entry("com.b", "com.b.Main", 10, "pack.two", "b_icon") + "]");

        PinnedIconOverride a = parsed.get(new AppRef("com.a", "com.a.Main").stableId());
        assertEquals("pack.one", a.iconPackPackage);
        assertEquals("a_icon", a.drawableName);
        PinnedIconOverride b = parsed.get(
            new AppRef("com.b", "com.b.Main", 10, -1L, false, "").stableId());
        assertEquals("pack.two", b.iconPackPackage);
        // The same app in the personal profile has no override.
        assertNull(parsed.get(new AppRef("com.b", "com.b.Main").stableId()));
    }

    @Test
    public void theFirstEntryForAnAppWinsEvenWhenItIsUnusable() {
        Map<String, PinnedIconOverride> parsed = LauncherConfigRepository.parseAppIconOverrides(
            "[" + entry("com.a", "com.a.Main", -1, "", "a_icon") + ","
                + entry("com.a", "com.a.Main", -1, "pack.two", "later") + "]");
        String id = new AppRef("com.a", "com.a.Main").stableId();
        assertTrue(parsed.containsKey(id));
        assertNull(parsed.get(id));
    }

    @Test
    public void emptyOrMalformedJsonReadsAsNoOverrides() {
        assertTrue(LauncherConfigRepository.parseAppIconOverrides(null).isEmpty());
        assertTrue(LauncherConfigRepository.parseAppIconOverrides("").isEmpty());
        assertTrue(LauncherConfigRepository.parseAppIconOverrides("[]").isEmpty());
        assertTrue(LauncherConfigRepository.parseAppIconOverrides("{not json").isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void theParsedMapCannotBeEdited() {
        LauncherConfigRepository.parseAppIconOverrides(
            "[" + entry("com.a", "com.a.Main", -1, "pack.one", "a_icon") + "]").clear();
    }
}
