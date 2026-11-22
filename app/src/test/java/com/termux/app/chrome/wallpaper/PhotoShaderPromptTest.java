package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.scene.ShaderErrors;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** The prompt Send to AI copies, and how the chat's answer is read back. */
public class PhotoShaderPromptTest {

    private static final String WISHES = "My wishes (optional; follow them within the rules above):";

    private static String template() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path file = root.resolve("src/main/res/raw/photo_shader_instructions.txt");
        if (!Files.isRegularFile(file)) file = root.resolve("app/src/main/res/raw/photo_shader_instructions.txt");
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    // --- the prompt ---

    @Test
    public void withDepthTheImageLineSaysPhotoLeftDepthRight() throws IOException {
        String prompt = PhotoShaderPrompt.fill(template(), true);
        assertTrue(prompt.contains(PhotoShaderPrompt.IMAGE_WITH_DEPTH));
        assertFalse(prompt.contains(PhotoShaderPrompt.IMAGE_NO_DEPTH));
        assertFalse(prompt.contains(PhotoShaderPrompt.PLACEHOLDER));
    }

    @Test
    public void withoutDepthTheImageLineSaysThePhotoAlone() throws IOException {
        String prompt = PhotoShaderPrompt.fill(template(), false);
        assertTrue(prompt.contains(PhotoShaderPrompt.IMAGE_NO_DEPTH));
        assertFalse(prompt.contains(PhotoShaderPrompt.IMAGE_WITH_DEPTH));
        assertFalse(prompt.contains(PhotoShaderPrompt.PLACEHOLDER));
    }

    @Test
    public void bothEndOnTheWishesLineWithNoBlankLineAfterIt() throws IOException {
        for (boolean depth : new boolean[] {true, false}) {
            String prompt = PhotoShaderPrompt.fill(template(), depth);
            assertTrue(prompt.endsWith(WISHES + "\n"));
            assertFalse(prompt.endsWith("\n\n"));
        }
    }

    @Test
    public void trailingBlankLinesAndWindowsLineEndsInTheTemplateAreNormalised() {
        String prompt = PhotoShaderPrompt.fill("A\r\n{IMAGE}\r\n" + WISHES + "\r\n\r\n  \n", false);
        assertEquals("A\n" + PhotoShaderPrompt.IMAGE_NO_DEPTH + "\n" + WISHES + "\n", prompt);
    }

    // --- the answer ---

    @Test
    public void fencedBlockWithALanguageTagDropsTheProseAround() {
        String answer = "Here you go:\n\n```glsl\nvoid mainImage(out vec4 c, in vec2 p) { c = vec4(1); }\n```\n\nEnjoy!";
        assertEquals("void mainImage(out vec4 c, in vec2 p) { c = vec4(1); }", PhotoShaderPrompt.extractCode(answer));
    }

    @Test
    public void fencedBlockWithoutALanguageTag() {
        assertEquals("// a\nvoid mainImage() {}", PhotoShaderPrompt.extractCode("```\n// a\nvoid mainImage() {}\n```"));
    }

    @Test
    public void noFenceIsTheTextAsIsTrimmed() {
        assertEquals("void mainImage() {}", PhotoShaderPrompt.extractCode("  \n void mainImage() {}\n\n"));
    }

    @Test
    public void twoFencesTakeTheFirst() {
        String answer = "```glsl\nfirst\n```\ntext\n```glsl\nsecond\n```";
        assertEquals("first", PhotoShaderPrompt.extractCode(answer));
    }

    @Test
    public void anUnclosedFenceRunsToTheEnd() {
        assertEquals("void mainImage() {}", PhotoShaderPrompt.extractCode("```glsl\r\nvoid mainImage() {}\r\n"));
    }

    @Test
    public void emptyInputIsEmpty() {
        assertEquals("", PhotoShaderPrompt.extractCode(null));
        assertEquals("", PhotoShaderPrompt.extractCode(""));
        assertEquals("", PhotoShaderPrompt.extractCode("   \n "));
        assertEquals("", PhotoShaderPrompt.extractCode("```glsl\n```"));
    }

    // --- the error report ---

    @Test
    public void errorReportIsTheIntroABlankLineThenEveryError() {
        String report = PhotoShaderPrompt.errorReport("Fix it.", Arrays.asList(
            new ShaderErrors.Error(3, "'x' : undeclared identifier"), new ShaderErrors.Error(-1, "Link failed")));
        assertTrue(report.startsWith("Fix it.\n\n"));
        String[] lines = report.split("\n", -1);
        assertEquals(4, lines.length);
        assertEquals(new ShaderErrors.Error(3, "'x' : undeclared identifier").toString(), lines[2]);
        assertEquals("Link failed", lines[3]);
        assertEquals(lines[2], PhotoShaderPrompt.firstError(Arrays.asList(
            new ShaderErrors.Error(3, "'x' : undeclared identifier"))));
        assertEquals("", PhotoShaderPrompt.firstError(null));
    }
}
