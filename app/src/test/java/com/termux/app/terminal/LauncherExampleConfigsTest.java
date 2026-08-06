package com.termux.app.terminal;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * The shipped examples are documentation users uncomment, so a stale line there is a bug
 * that reaches every install. Each commented directive is stripped of its comment marker
 * and pushed through the real parser.
 */
@RunWith(RobolectricTestRunner.class)
public class LauncherExampleConfigsTest {

    private static final Pattern BINDING_DIRECTIVE =
        Pattern.compile("^#\\s?((?:map|unmap)\\s.*)$");
    private static final Pattern FONT_DIRECTIVE = Pattern.compile(
        "^#\\s?((?:font_family|bold_font|italic_font|bold_italic_font|symbol_map|fallback_font"
            + "|disable_ligatures|font_features|font_variations|modify_font|box_drawing_scale"
            + "|box_drawing|powerline_symbols)\\s.*)$");

    @Test
    public void everyCommentedBindingDirectiveParses() throws Exception {
        String directives = uncomment("termux-launcher-bindings.conf", BINDING_DIRECTIVE);
        assertTrue("no directives found in the shipped example",
            directives.split("\n").length > 20);

        TerminalBindingConfig.Result result =
            TerminalBindingConfig.parse(directives, LauncherToolRegistry.getInstance(), true);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertTrue("app.launch example is missing", directives.contains("app.launch com.whatsapp"));
    }

    @Test
    public void everyCommentedFontDirectiveParses() throws Exception {
        String directives = uncomment("fonts.conf", FONT_DIRECTIVE);
        assertTrue("no directives found in the shipped example",
            directives.split("\n").length > 10);

        TerminalFontConfig.Result result = TerminalFontConfig.parse(directives, true);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
    }

    @Test
    public void shippedKeyboardLayoutLoads() throws Exception {
        String layout = asset("keyboard-layout.xml");
        assertTrue(layout.contains("tool:app.command_palette"));
        juloo.keyboard2.KeyboardData.load_string_exn(layout);
    }

    private static String uncomment(String name, Pattern directive) throws IOException {
        StringBuilder result = new StringBuilder();
        for (String line : asset(name).split("\n", -1)) {
            java.util.regex.Matcher matcher = directive.matcher(line);
            if (matcher.matches()) result.append(matcher.group(1)).append('\n');
        }
        return result.toString();
    }

    private static String asset(String name) throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        try (InputStream input = context.getAssets().open("launcher-examples/" + name)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
