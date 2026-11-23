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
 * The rest look is the thin rim's (#314, {@link GlassRim}) under the fixed key light, and the
 * glass changes only with its page's pose (#318, {@link GlassPose}): at rest every pose term is
 * skipped, so the rest look is the program's own. On pong a narrow glint at rest read as a bright
 * smear in the top-left corner of every glass surface (the user, 2026-10-20). The program cannot
 * run on the JVM, so its rest path is held to its text and its Java twins to the rim's arithmetic.
 *
 * <p>Until #318 this pinned the speed spring's glint weight (Living glass) at zero at rest; the
 * spring is gone, so the pin is now that every read of a pose uniform sits behind the pose's own
 * gate, and that the rest pose writes the rest values.</p>
 */
public class GlassRestLookTest {

    /** Pong's density. */
    private static final float DENSITY = 2.625f;
    private static final float D = (float) Math.sqrt(0.5);

    /** The rest lobe, as the thin-edge experiment (86d023b6d) wrote it: c² toward the key light. */
    private static float restLobe(float nx, float ny) {
        float c = nx * -0.552f + ny * -0.834f;
        return c > 0f ? c * c : 0f;
    }

    // ------------------------------------------------------------------------- the Java twins

    @Test
    public void theRestConstantsAreTheFixedLight() {
        assertEquals(2f, GlassRefraction.Look.KEY_EXPONENT, 0f);
        assertEquals(-0.552f, GlassRefraction.KEY_LIGHT_X, 0f);
        assertEquals(-0.834f, GlassRefraction.KEY_LIGHT_Y, 0f);
    }

    @Test
    public void theRestLobeIsTheThinEdgesAllRound() {
        assertEquals(0.97f, GlassRefraction.rimLobe(-D, -D), 0.01f);
        assertEquals(0.70f, GlassRefraction.rimLobe(0f, -1f), 0.01f);
        assertEquals(0.30f, GlassRefraction.rimLobe(-1f, 0f), 0.01f);
        assertEquals(0f, GlassRefraction.rimLobe(D, D), 0f);
        for (int deg = 0; deg < 360; deg++) {
            float nx = (float) Math.cos(Math.toRadians(deg)), ny = (float) Math.sin(Math.toRadians(deg));
            assertEquals("at " + deg, restLobe(nx, ny), GlassRefraction.rimLobe(nx, ny), 1e-6f);
            // Every side keeps the baseline whatever the lobe.
            assertTrue(GlassRim.DEFAULT.white(GlassRefraction.rimLobe(nx, ny)) >= GlassRim.DEFAULT.base);
        }
    }

    // ---------------------------------------------------------------------------- the pose

    @Test
    public void theRestPoseWritesTheValuesThatSkipEveryPoseTerm() {
        float[] u = new float[GlassPoseOptics.UNIFORMS];
        GlassPoseOptics.active().uniforms(new GlassPose(), GlassSlab.DEFAULT, DENSITY, u, new float[4]);
        // turned is uPose.xy != 0, scaled is uPoseLight.x != 1, the glint is uGlint.w > 0.
        assertEquals(0f, u[0], 0f);
        assertEquals(0f, u[1], 0f);
        assertEquals(1f, u[4], 0f);
        assertEquals(0f, u[15], 0f);
        // A press rig's last twentieth of a degree is rest, so its last frame is the rest look.
        GlassPose pose = new GlassPose();
        GlassPose.fromViewProperties(GlassPose.TILT_REST_DEG * 0.9f, 0f, 1f, pose);
        assertTrue(pose.isRest());
    }

    // ---------------------------------------------------------------------------- the program

    @Test
    public void theProgramsRestPathIsTheRimUnderTheFixedLight() {
        String agsl = GlassRefraction.AGSL;
        String[] rest = {
            "const float2 kKeyLight = float2(-0.552, -0.834);\n",
            "const float kKeyExp = 2.0;\n",
            "float rimLobe(float c) {\n"
                + "    return c > 0.0 ? pow(c, kKeyExp) : 0.0;\n"
                + "}\n",
            "            float rc = dot(n, kKeyLight);\n"
                + "            float lobe = rimLobe(rc);\n"
                + "            float away = rc < 0.0 ? rc * rc : 0.0;\n",
            "    bool turned = uPose.x != 0.0 || uPose.y != 0.0;\n",
            "    bool scaled = uPoseLight.x != 1.0;\n",
            "            float3 hv = kHalf;\n",
        };
        for (String line : rest) assertTrue(line, agsl.contains(line));
        // The speed spring and everything it drove are gone.
        for (String gone : new String[] {"uLag", "uTilt", "uKeyLight", "glintW", "liveK", "kLag",
                "kTilt", "kLive", "kKeyPeak", "kCounter", "tiltOffset", "bendV"}) {
            assertFalse(gone, agsl.contains(gone));
        }
    }

    @Test
    public void everyPoseUniformInMainIsReadBehindThePosesGate() {
        String agsl = GlassRefraction.AGSL;
        int main = agsl.indexOf("half4 main(");
        assertTrue(main > 0);
        Matcher m = Pattern.compile("uPose\\w*|uGlint").matcher(agsl);
        m.region(main, agsl.length());
        int reads = 0;
        while (m.find()) {
            int at = m.start();
            reads++;
            String line = lineAt(agsl, at);
            if (line.contains("bool turned = ") || line.contains("bool scaled = ")) continue;
            assertTrue("read at '" + line.trim() + "' sits under if (turned...) or if (scaled)",
                underPoseGate(agsl, main, at));
        }
        assertTrue(reads > 6);
    }

    /** The line of {@code text} holding index {@code at}. */
    private static String lineAt(String text, int at) {
        int start = text.lastIndexOf('\n', at) + 1;
        int end = text.indexOf('\n', at);
        return text.substring(start, end < 0 ? text.length() : end);
    }

    /** Whether some block enclosing {@code at} (inside main) opens on a line naming the pose's gate. */
    private static boolean underPoseGate(String text, int main, int at) {
        int depth = 0;
        for (int i = at; i > main; i--) {
            char c = text.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                if (depth == 0) {
                    String opener = lineAt(text, i);
                    if (opener.contains("turned") || opener.contains("scaled")) return true;
                } else {
                    depth--;
                }
            }
        }
        return false;
    }

    @Test
    public void everyProgramStartsAtRest() throws Exception {
        File source = new File("src/main/java/com/termux/app/chrome/GlassRefraction.java");
        if (!source.isFile()) source = new File("app/" + source.getPath());
        assumeTrue("the source is reachable from the test's directory", source.isFile());
        String java = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
        int ctor = java.indexOf("        private Program(float density) {");
        int end = java.indexOf("\n        }\n", ctor);
        assertTrue(ctor > 0 && end > ctor);
        assertTrue("the constructor writes the rest pose", java.substring(ctor, end).contains("clearPose();"));
    }
}
