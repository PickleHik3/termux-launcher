package com.termux.app.help;

import com.termux.R;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The launcher's real {@code strings.xml}, read straight off disk, so a plain JUnit test can rank
 * and sort the actual product copy without Robolectric or a resource table.
 */
final class HelpTestText implements HelpTopics.Text {

    private static final Pattern STRING = Pattern.compile(
        "<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", Pattern.DOTALL);

    private final Map<Integer, String> byId = new HashMap<>();

    HelpTestText() {
        Map<String, String> byName = read();
        for (Field field : R.string.class.getFields()) {
            try {
                byId.put(field.getInt(null), byName.get(field.getName()));
            } catch (IllegalAccessException ignored) {
                // A resource id that cannot be read simply is not in the map.
            }
        }
    }

    @Override public String get(int res) {
        String value = byId.get(res);
        if (value == null) throw new IllegalArgumentException("no string for resource " + res);
        return value;
    }

    private static Map<String, String> read() {
        File file = null;
        for (String candidate : new String[] {"src/main/res/values/strings.xml",
                                              "app/src/main/res/values/strings.xml",
                                              "../app/src/main/res/values/strings.xml"}) {
            File one = new File(candidate);
            if (one.isFile()) { file = one; break; }
        }
        if (file == null) throw new IllegalStateException(
            "strings.xml not found from " + new File(".").getAbsolutePath());
        String xml;
        try {
            xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Map<String, String> byName = new HashMap<>();
        Matcher matcher = STRING.matcher(xml);
        while (matcher.find()) byName.put(matcher.group(1), unescape(matcher.group(2)));
        return byName;
    }

    private static String unescape(String raw) {
        String text = raw.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#8230;", "…").replace("&#160;", " ");
        if (text.length() > 1 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text;
    }
}
