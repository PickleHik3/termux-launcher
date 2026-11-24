package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The settings reference page in the guides is {@link SettingsReference}'s output, so the help
 * about a setting cannot go stale: a declaration that changes fails this until the page is
 * regenerated.
 */
public class SettingsReferenceTest {

    private static final String REGENERATE = "REGENERATE_SETTINGS_REFERENCE";

    private static File page() {
        for (String candidate : new String[] {"../docs/en", "docs/en", "../../docs/en"}) {
            File dir = new File(candidate);
            if (dir.isDirectory()) return new File(dir, SettingsReference.PAGE);
        }
        throw new AssertionError("docs/en not found from " + new File(".").getAbsolutePath());
    }

    @Test public void thePageInTheTreeIsTheGeneratorsOutput() throws IOException {
        String expected = SettingsReference.render();
        File page = page();
        if (System.getenv(REGENERATE) != null) {
            Files.write(page.toPath(), expected.getBytes(StandardCharsets.UTF_8));
            return;
        }
        String actual = page.isFile()
            ? new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8) : "";
        assertEquals("docs/en/" + SettingsReference.PAGE + " is out of date. Regenerate it with: "
            + REGENERATE + "=1 ./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest"
            + " --tests 'com.termux.launcherctl.SettingsReferenceTest' and commit the page.",
            expected, actual);
    }

    @Test public void everySettingConfigListShowsIsDescribedInPlainWords() {
        SettingsRegistry registry = SettingsReference.registry();
        int count = 0;
        for (SettingsRegistry.Entry entry : registry.list()) {
            count++;
            String id = entry.declaration.id;
            assertFalse(id + " has no title", entry.declaration.title.isEmpty());
            assertFalse(id + " has no summary", entry.declaration.summary.isEmpty());
            assertTrue(id + "'s summary is one sentence ending in a full stop",
                entry.declaration.summary.endsWith("."));
        }
        assertTrue("the reference lists settings", count > 30);
    }

    @Test public void thePageNamesEverySettingConfigListShows() {
        String text = SettingsReference.render();
        for (SettingsRegistry.Entry entry : SettingsReference.registry().list())
            assertTrue(entry.declaration.id + " is on the page",
                text.contains("`" + entry.declaration.id + "`"));
    }
}
