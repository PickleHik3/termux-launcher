package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cleanup eval ({@code project-docs/verification/voice-cleanup-eval/}) scores a Python copy of
 * the app's pipeline. {@code cleanup_eval.py expect} writes what that copy decides for every case at
 * both levels, before any model call, to {@code pipeline.jsonl}: the language, the text with its
 * fillers stripped, and the gate's groups. The app must decide the same, or the eval measures
 * something the app does not do. Regenerate the file after changing either side.
 */
public class VoiceCleanupEvalParityTest {

    private static final String EVAL = "project-docs/verification/voice-cleanup-eval/";

    /** The file from the app module (Gradle's working directory) or the repository root. */
    private static File evalFile(String name) {
        File file = new File("../" + EVAL + name);
        return file.isFile() ? file : new File(EVAL + name);
    }

    private static List<String> lines(String name) throws Exception {
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(evalFile(name).toPath(), StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) lines.add(line);
        }
        return lines;
    }

    @Test
    public void theAppDecidesWhatTheEvalsCopyDecides() throws Exception {
        Map<String, String> raws = new HashMap<>();
        for (String line : lines("cases.jsonl")) {
            JSONObject testCase = new JSONObject(line);
            raws.put(testCase.getString("id"), testCase.getString("raw"));
        }
        int checked = 0;
        for (String line : lines("pipeline.jsonl")) {
            JSONObject expected = new JSONObject(line);
            String raw = raws.get(expected.getString("id"));
            assertNotNull(expected.getString("id"), raw);
            String level = expected.getString("level");
            String where = expected.getString("id") + " (" + level + ")";

            String language = VoiceTextLanguage.guess(raw.trim());
            assertEquals(where, expected.isNull("language") ? null : expected.getString("language"), language);
            String stripped = VoiceFillers.strip(raw.trim(), language);
            assertEquals(where, expected.getString("stripped"), stripped);

            List<VoiceCleanupGate.Group> groups = language == null
                ? Collections.<VoiceCleanupGate.Group>emptyList() : VoiceCleanupGate.groups(stripped, level);
            JSONArray want = expected.getJSONArray("groups");
            assertEquals(where, want.length(), groups.size());
            for (int i = 0; i < want.length(); i++) {
                JSONObject group = want.getJSONObject(i);
                assertEquals(where + " group " + i, group.getString("text"), groups.get(i).text);
                assertEquals(where + " group " + i, group.getBoolean("send"), groups.get(i).send);
                List<String> flags = new ArrayList<>();
                JSONArray wantFlags = group.getJSONArray("flags");
                for (int f = 0; f < wantFlags.length(); f++) flags.add(wantFlags.getString(f));
                assertEquals(where + " group " + i, flags, groups.get(i).flags);
            }
            checked++;
        }
        assertEquals("every case at both levels", raws.size() * 2, checked);
    }
}
