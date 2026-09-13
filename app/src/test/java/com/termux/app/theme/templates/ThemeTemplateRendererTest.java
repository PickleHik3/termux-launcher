package com.termux.app.theme.templates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Properties;

/** The placeholder rules: every format, and every way a template can be refused. */
public class ThemeTemplateRendererTest {

    private static Properties palette() {
        Properties palette = new Properties();
        palette.setProperty("primary", "#4080C0");
        palette.setProperty("terminal_color0", "#000000");
        palette.setProperty("mode", "light");
        return palette;
    }

    private static String render(String template) {
        ThemeTemplateRenderer.Result result = ThemeTemplateRenderer.render(template, palette());
        assertTrue(String.valueOf(result.failure), result.isSuccess());
        return result.text;
    }

    @Test
    public void rendersEveryFormat() {
        assertEquals("#4080c0", render("{{ colors.primary.dark.hex }}"));
        assertEquals("4080c0", render("{{ colors.primary.dark.hex_stripped }}"));
        assertEquals("rgb(64, 128, 192)", render("{{ colors.primary.dark.rgb }}"));
        assertEquals("rgba(64, 128, 192, 1.0)", render("{{ colors.primary.dark.rgba }}"));
        assertEquals("64", render("{{ colors.primary.dark.red }}"));
        assertEquals("128", render("{{ colors.primary.dark.green }}"));
        assertEquals("192", render("{{ colors.primary.dark.blue }}"));
    }

    @Test
    public void whitespaceInsideTheBracesIsOptional() {
        assertEquals("#4080c0", render("{{colors.primary.dark.hex}}"));
        assertEquals("#4080c0", render("{{   colors.primary.dark.hex   }}"));
        assertEquals("#4080c0\t#4080c0", render("{{colors.primary.dark.hex}}\t{{ colors.primary.dark.hex }}"));
    }

    @Test
    public void everyModeReadsTheOnePalette() {
        assertEquals("#4080c0", render("{{ colors.primary.default.hex }}"));
        assertEquals("#4080c0", render("{{ colors.primary.dark.hex }}"));
        assertEquals("#4080c0", render("{{ colors.primary.light.hex }}"));
    }

    @Test
    public void rendersTheModeItself() {
        assertEquals("light", render("{{ mode }}"));
        assertEquals("light", render("{{mode}}"));
    }

    @Test
    public void aPaletteWithoutAModeIsDark() {
        Properties palette = palette();
        palette.remove("mode");
        ThemeTemplateRenderer.Result result = ThemeTemplateRenderer.render("{{ mode }}", palette);
        assertTrue(result.isSuccess());
        assertEquals("dark", result.text);
        assertEquals("dark", ThemeTemplateRenderer.modeOf(palette));
    }

    @Test
    public void keepsTheTextAroundThePlaceholders() {
        assertEquals("color = \"#4080c0\"\nbg = \"#000000\"\n",
            render("color = \"{{ colors.primary.dark.hex }}\"\nbg = \"{{ colors.terminal_color0.dark.hex }}\"\n"));
    }

    @Test
    public void anUnknownTokenFailsTheWholeTemplate() {
        assertFailure("theme = {{ colors.no_such_role.dark.hex }}", "no_such_role");
    }

    @Test
    public void anUnknownFormatFailsTheWholeTemplate() {
        assertFailure("theme = {{ colors.primary.dark.hsl }}", "hsl");
    }

    @Test
    public void anUnknownModeFailsTheWholeTemplate() {
        assertFailure("theme = {{ colors.primary.midnight.hex }}", "midnight");
    }

    @Test
    public void aMalformedColourPlaceholderFailsTheWholeTemplate() {
        assertFailure("theme = {{ colors.primary.hex }}", "colors.primary.hex");
        assertFailure("theme = {{ colors.primary.dark.hex.extra }}", "colors.primary.dark.hex.extra");
    }

    @Test
    public void leavesEveryOtherPlaceholderAlone() {
        assertEquals("{{ .Path }} {{ if .Root }}#{{ end }}",
            render("{{ .Path }} {{ if .Root }}#{{ end }}"));
        assertEquals("{{ palette.primary.dark.hex }}", render("{{ palette.primary.dark.hex }}"));
    }

    @Test
    public void substitutesInsideAGoTemplateWithoutTouchingIt() {
        assertEquals("\"foreground\": \"#4080c0\",\n"
                + "\"template\": \" {{ .Segments.Git.Working }} {{ if .Root }}root{{ end }} \"\n",
            render("\"foreground\": \"{{ colors.primary.default.hex }}\",\n"
                + "\"template\": \" {{ .Segments.Git.Working }} {{ if .Root }}root{{ end }} \"\n"));
    }

    @Test
    public void aBlockFailsTheWholeTemplate() {
        ThemeTemplateRenderer.Result result =
            ThemeTemplateRenderer.render("<* if dark *>x<* endif *>", palette());
        assertFalse(result.isSuccess());
        assertNull(result.text);
        assertTrue(result.failure.contains("<*"));
    }

    @Test
    public void aValueThatIsNotAColourFailsTheWholeTemplate() {
        Properties palette = palette();
        palette.setProperty("primary", "not-a-colour");
        ThemeTemplateRenderer.Result result =
            ThemeTemplateRenderer.render("{{ colors.primary.dark.hex }}", palette);
        assertFalse(result.isSuccess());
        assertNull(result.text);
    }

    @Test
    public void aMissingTemplateFileFails() {
        assertFalse(ThemeTemplateRenderer.render(null, palette()).isSuccess());
    }

    private static void assertFailure(String template, String mentioned) {
        ThemeTemplateRenderer.Result result = ThemeTemplateRenderer.render(template, palette());
        assertFalse(result.isSuccess());
        assertNull(result.text);
        assertTrue(result.failure, result.failure.contains(mentioned));
    }
}
