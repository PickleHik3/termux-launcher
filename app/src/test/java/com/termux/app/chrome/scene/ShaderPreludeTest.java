package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** The Shadertoy contract: dialect detection, the prelude, the generated main and error lines. */
public class ShaderPreludeTest {

    private static final String SHADERTOY = ""
        + "void mainImage(out vec4 fragColor, in vec2 fragCoord) {\n"
        + "    vec2 uv = fragCoord / iResolution.xy;\n"
        + "    fragColor = vec4(uv, 0.5 + 0.5 * sin(iTime), 1.0);\n"
        + "}\n";

    private static final String SHADER_EDITOR = ""
        + "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
        + "precision highp float;\n"
        + "#else\n"
        + "precision mediump float;\n"
        + "#endif\n"
        + "\n"
        + "uniform vec2 resolution;\n"
        + "uniform float time;\n"
        + "\n"
        + "void main(void) {\n"
        + "    vec2 uv = gl_FragCoord.xy / resolution.xy;\n"
        + "    gl_FragColor = vec4(uv, sin(time), 1.0);\n"
        + "}\n";

    private static String line(String source, int n) {
        return source.split("\n", -1)[n - 1];
    }

    @Test
    public void mainImageCompilesAsEs3WithAGeneratedMain() {
        ShaderPrelude.Built b = ShaderPrelude.build(SHADERTOY);
        assertEquals(ShaderPrelude.Flavour.ES3_MAIN_IMAGE, b.flavour);
        assertEquals("#version 300 es", line(b.source, 1));
        assertTrue(b.source.contains("uniform vec3 iResolution;"));
        assertTrue(b.source.contains("uniform float iTime;"));
        // Only what the text names is declared.
        assertFalse(b.source.contains("iMouse"));
        assertFalse(b.source.contains("uniform vec2 resolution;"));
        assertTrue(b.source.contains("out vec4 tlFragColor;"));
        assertTrue(b.source.contains("mainImage(c, gl_FragCoord.xy);"));
        // The user's line 2 sits right after the prelude.
        assertEquals("    vec2 uv = fragCoord / iResolution.xy;", line(b.source, b.lineOffset + 2));
        assertEquals(2, b.userLine(b.lineOffset + 2));
        assertEquals(-1, b.userLine(1));
    }

    @Test
    public void voidMainCompilesAsEs1AndKeepsTheUsersOwnUniforms() {
        ShaderPrelude.Built b = ShaderPrelude.build(SHADER_EDITOR);
        assertEquals(ShaderPrelude.Flavour.ES1_MAIN, b.flavour);
        assertFalse(b.source.startsWith("#version"));
        // Declared by the user: not declared again (a redeclaration does not compile).
        assertEquals(1, count(b.source, "uniform vec2 resolution;"));
        assertEquals(1, count(b.source, "uniform float time;"));
        assertFalse(b.source.contains("tlFragColor"));
        assertTrue(b.source.contains("precision mediump float;"));
        assertEquals("    gl_FragColor = vec4(uv, sin(time), 1.0);", line(b.source, b.lineOffset + 12));
    }

    @Test
    public void aVersionLineStaysFirstAndThePreludeFollows() {
        String src = "// a comment\n#version 300 es\nprecision highp float;\nout vec4 o;\n"
            + "uniform vec2 resolution;\nvoid main() { o = vec4(gl_FragCoord.xy / resolution, 0.0, 1.0); }\n";
        ShaderPrelude.Built b = ShaderPrelude.build(src);
        assertEquals(ShaderPrelude.Flavour.ES3_MAIN, b.flavour);
        assertEquals("#version 300 es", line(b.source, 1));
        assertEquals(2, b.userLine(1));
        // The moved line is blank in place, so the user's numbering holds.
        assertEquals("", line(b.source, b.lineOffset + 2));
        assertEquals("out vec4 o;", line(b.source, b.lineOffset + 4));
        assertEquals(1, count(b.source, "#version"));
    }

    @Test
    public void version100WithMainImageWrapsWithGlFragColor() {
        ShaderPrelude.Built b = ShaderPrelude.build("#version 100\n" + SHADERTOY);
        assertEquals(ShaderPrelude.Flavour.ES1_MAIN_IMAGE, b.flavour);
        assertTrue(b.source.contains("gl_FragColor = vec4(c.rgb, 1.0);"));
        assertFalse(b.source.contains("tlFragColor"));
    }

    @Test
    public void extensionLinesMoveToTheTop() {
        String src = "#extension GL_OES_standard_derivatives : enable\nuniform vec2 resolution;\n"
            + "void main() { gl_FragColor = vec4(fwidth(gl_FragCoord.x)); }\n";
        ShaderPrelude.Built b = ShaderPrelude.build(src);
        assertEquals("#extension GL_OES_standard_derivatives : enable", line(b.source, 1));
        assertEquals(1, b.userLine(1));
        assertTrue(b.source.indexOf("precision") > b.source.indexOf("#extension"));
    }

    @Test
    public void aliasesAndExtrasHaveTheContractTypes() {
        String src = "void mainImage(out vec4 c, in vec2 p) {\n"
            + "  c = vec4(touch / resolution, battery + tlBattery + tlKeyboard + tlDark + tlPageOffset, 1.0);\n"
            + "  c.rgb += tlColor0 + tlColor1 + tlColor2 + iChannelResolution[0] + vec3(offset, date.w);\n"
            + "  c.r += float(pointerCount) + pointers[0].x + iMouse.x + iDate.x + float(iFrame);\n"
            + "  c += texture(iChannel0, p);\n"
            + "}\n";
        String s = ShaderPrelude.build(src).source;
        assertTrue(s.contains("uniform vec2 resolution;"));
        assertTrue(s.contains("uniform vec2 touch;"));
        assertTrue(s.contains("uniform float battery;"));
        assertTrue(s.contains("uniform vec2 offset;"));
        assertTrue(s.contains("uniform vec4 date;"));
        assertTrue(s.contains("uniform int pointerCount;"));
        assertTrue(s.contains("uniform vec3 pointers[10];"));
        assertTrue(s.contains("uniform vec3 iChannelResolution[4];"));
        assertTrue(s.contains("uniform sampler2D iChannel0;"));
        assertTrue(s.contains("uniform vec3 tlColor2;"));
        assertTrue(s.contains("uniform float tlDark;"));
        assertTrue(s.contains("uniform vec4 iMouse;"));
        assertTrue(s.contains("uniform int iFrame;"));
    }

    @Test
    public void aShadertoyLocalOrDefineSuppressesTheAlias() {
        String src = "#define time iTime\nfloat offset(float x) { return x; }\n"
            + "void mainImage(out vec4 c, in vec2 p) { float resolution = 2.0; c = vec4(time * offset(resolution)); }\n";
        String s = ShaderPrelude.build(src).source;
        assertFalse(s.contains("uniform float time;"));
        assertFalse(s.contains("uniform vec2 offset;"));
        assertFalse(s.contains("uniform vec2 resolution;"));
        assertTrue(s.contains("uniform float iTime;"));
    }

    @Test
    public void commentsDoNotCount() {
        String src = "// uses iMouse? no.\n/* void main() */\n"
            + "void mainImage(out vec4 c, in vec2 p) { c = vec4(1.0); }\n";
        ShaderPrelude.Built b = ShaderPrelude.build(src);
        assertEquals(ShaderPrelude.Flavour.ES3_MAIN_IMAGE, b.flavour);
        assertFalse(b.source.contains("uniform vec4 iMouse;"));
    }

    @Test
    public void uniformListsCountAsDeclarations() {
        String src = "uniform vec2 resolution, touch;\nuniform vec3 pointers[10];\n"
            + "void main() { gl_FragColor = vec4(touch / resolution, pointers[0].x, 1.0); }\n";
        String s = ShaderPrelude.build(src).source;
        assertEquals(1, count(s, "touch;"));
        assertEquals(1, count(s, "pointers[10]"));
    }

    @Test
    public void errorsMapBackToTheUsersLines() {
        ShaderPrelude.Built b = ShaderPrelude.build(SHADERTOY);
        int compiled = b.lineOffset + 3;
        String adreno = "ERROR: 0:" + compiled + ": 'foo' : undeclared identifier\n"
            + "ERROR: 1 compilation errors.  No code generated.\n";
        List<ShaderErrors.Error> a = ShaderErrors.parse(adreno, b);
        assertEquals(1, a.size());
        assertEquals(3, a.get(0).line);
        assertEquals("'foo' : undeclared identifier", a.get(0).message);
        assertEquals("Line 3: 'foo' : undeclared identifier", a.get(0).toString());

        String mali = "0:" + compiled + ": L0002: Undeclared variable 'foo'\n";
        assertEquals(3, ShaderErrors.parse(mali, b).get(0).line);

        String mesa = "0:" + (b.lineOffset + 2) + "(12): error: `uv' redeclared\n"
            + "0:" + (b.lineOffset + 1) + "(1): warning: unused\n";
        List<ShaderErrors.Error> m = ShaderErrors.parse(mesa, b);
        assertEquals(1, m.size());
        assertEquals(2, m.get(0).line);
        assertEquals("`uv' redeclared", m.get(0).message);
    }

    @Test
    public void anErrorInTheGeneratedMainHasNoUserLine() {
        ShaderPrelude.Built b = ShaderPrelude.build("void notMain() {}\n");
        int past = b.lineOffset + 10;
        List<ShaderErrors.Error> e = ShaderErrors.parse("ERROR: 0:" + past + ": 'mainImage' : no matching overloaded function found", b);
        assertEquals(-1, e.get(0).line);
        assertEquals("'mainImage' : no matching overloaded function found", e.get(0).toString());
    }

    @Test
    public void aLogWithoutLocationsKeepsItsLines() {
        ShaderPrelude.Built b = ShaderPrelude.build(SHADERTOY);
        List<ShaderErrors.Error> e = ShaderErrors.parse("Link failed: too many uniforms\n", b);
        assertEquals(1, e.size());
        assertEquals(-1, e.get(0).line);
        assertTrue(ShaderErrors.parse("", b).isEmpty());
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) n++;
        return n;
    }
}
