package com.termux.app.chrome.scene;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The text handed to an AI tool never promises an input the renderer does not declare. */
public class PhotoShaderInstructionsTest {

    private static final Pattern INPUT = Pattern.compile("\\b(i[A-Z]\\w*|tl[A-Z]\\w*)\\b");

    private static String instructions() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path file = root.resolve("src/main/res/raw/photo_shader_instructions.txt");
        if (!Files.isRegularFile(file)) file = root.resolve("app/src/main/res/raw/photo_shader_instructions.txt");
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    public void everyInputTheTextNamesIsDeclaredByTheContract() throws IOException {
        Set<String> declared = new HashSet<>();
        for (ShaderPrelude.Uniform u : ShaderPrelude.UNIFORMS) declared.add(u.name);
        Matcher m = INPUT.matcher(instructions());
        int found = 0;
        while (m.find()) {
            String name = m.group(1);
            if (name.equals("mainImage")) continue;
            found++;
            assertTrue(name + " is in the instructions but not in ShaderPrelude.UNIFORMS", declared.contains(name));
        }
        assertTrue(found > 0);
    }

    @Test
    public void theTextNamesTheInputsTheFeatureIsBuiltOn() throws IOException {
        String text = instructions();
        for (String name : new String[] {"iChannel0", "iChannel1", "iChannelResolution", "tlTouchAge",
            "tlPageOffset"}) {
            assertTrue(name + " is missing from the instructions", text.contains(name));
        }
    }
}
