package com.termux.app.help;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Every "Full documentation" link a help topic carries leads to a page and a heading that exist. */
public class HelpDocLinksTest {

    private static final Pattern HEADING = Pattern.compile("^#+\\s+(.*)$", Pattern.MULTILINE);

    private static File docs() {
        for (String candidate : new String[] {"../docs/en", "docs/en", "../../docs/en"}) {
            File dir = new File(candidate);
            if (dir.isDirectory()) return dir;
        }
        throw new AssertionError("docs/en not found from " + new File(".").getAbsolutePath());
    }

    /** The anchor GitHub gives a heading: lower case, punctuation dropped, spaces to hyphens. */
    static String slug(String heading) {
        String lower = heading.trim().toLowerCase(Locale.ROOT).replaceAll("[`*_]", "");
        return lower.replaceAll("[^\\p{L}\\p{N}\\- ]", "").replace(' ', '-');
    }

    @Test public void everyTopicsDocumentAndHeadingExists() throws IOException {
        File dir = docs();
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            if (entry.docPath == null) continue;
            int hash = entry.docPath.indexOf('#');
            String file = hash < 0 ? entry.docPath : entry.docPath.substring(0, hash);
            File page = new File(dir, file);
            assertTrue(entry.id + " links to a page that is not there: " + entry.docPath,
                page.isFile());
            if (hash < 0) continue;
            String text = new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8);
            Set<String> anchors = new HashSet<>();
            Matcher matcher = HEADING.matcher(text);
            while (matcher.find()) anchors.add(slug(matcher.group(1)));
            assertTrue(entry.id + " links to a heading that is not there: " + entry.docPath,
                anchors.contains(entry.docPath.substring(hash + 1)));
        }
    }
}
