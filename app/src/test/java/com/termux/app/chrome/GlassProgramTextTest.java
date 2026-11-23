package com.termux.app.chrome;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The program's text stays well formed: a shader that fails to compile drops every glass surface
 * to plain on the phone, and the JVM cannot compile AGSL. (Moved from GlassMotionLightTest when
 * the speed spring went, #318.)
 */
public class GlassProgramTextTest {

    @Test
    public void theProgramTextIsBalanced() {
        String agsl = GlassRefraction.AGSL;
        int braces = 0, parens = 0;
        for (int i = 0; i < agsl.length(); i++) {
            char c = agsl.charAt(i);
            if (c == '{') braces++;
            else if (c == '}') braces--;
            else if (c == '(') parens++;
            else if (c == ')') parens--;
            assertTrue("no brace closes before it opens, at " + i, braces >= 0);
            assertTrue("no paren closes before it opens, at " + i, parens >= 0);
        }
        assertEquals(0, braces);
        assertEquals(0, parens);
        assertFalse("no exponent a float printed as Java writes it", agsl.matches("(?s).*[0-9]E-?[0-9].*"));
    }

    @Test
    public void everyUniformTheProgramDeclaresHasAWriter() throws Exception {
        File source = new File("src/main/java/com/termux/app/chrome/GlassRefraction.java");
        if (!source.isFile()) source = new File("app/" + source.getPath());
        assumeTrue("the source is reachable from the test's directory", source.isFile());
        String java = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("uniform (\\w+) (\\w+);").matcher(GlassRefraction.AGSL);
        int seen = 0;
        while (m.find()) {
            seen++;
            String type = m.group(1), name = m.group(2);
            String writer = "shader".equals(type) ? "setInputShader(\"" + name + "\""
                : "setFloatUniform(\"" + name + "\"";
            assertTrue(name + " has a writer", java.contains(writer));
            assertTrue(type + " is an AGSL type", type.matches("shader|float[234]?|half[234]?"));
        }
        assertTrue(seen > 20);
    }
}
