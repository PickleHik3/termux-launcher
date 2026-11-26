package com.termux.app.x11;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every distro logo's {@code pathData} must parse the way the platform's VectorDrawable parser
 * (hwui {@code PathParser}) reads it: numbers only, so an arc's two flags need a separator. SVG
 * lets {@code a3 3 0 01-.99.05} pack its flags; Android reads {@code 01} as one number, finds an
 * arc that is not seven numbers long, and throws on inflation — which left every distro app's
 * drawer cell blank, because the badge is painted into the same render as the icon.
 */
public final class DistroGlyphPathDataTest {

    private static final Pattern PATH_DATA = Pattern.compile("pathData=\"([^\"]*)\"");
    private static final Pattern SEGMENT =
        Pattern.compile("([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)");

    @Test
    public void everyLogoParsesAsThePlatformReadsIt() throws IOException {
        List<String> problems = new ArrayList<>();
        int logos = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(drawableDir(), "ic_distro_*.xml")) {
            for (Path file : files) {
                logos++;
                String xml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                Matcher data = PATH_DATA.matcher(xml);
                while (data.find()) {
                    Matcher segment = SEGMENT.matcher(data.group(1));
                    while (segment.find()) {
                        int expected = pointsPerVerb(segment.group(1).charAt(0));
                        int points = platformFloatCount(segment.group(2));
                        boolean valid = expected == 0 ? points == 0 : points % expected == 0;
                        if (!valid) {
                            problems.add(file.getFileName() + ": '" + segment.group(1)
                                + "' carries " + points + " numbers");
                        }
                    }
                }
            }
        }
        assertTrue("no distro logos found", logos > 0);
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    @Test
    public void packedArcFlagsAreCaught() {
        // The shape Simple Icons ships and Android rejects: "0 01-.99.05" is four numbers to it.
        assertTrue(platformFloatCount("3.001 3.001 0 01-.99.05") % 7 != 0);
        assertTrue(platformFloatCount("3.001 3.001 0 0 1 -.99 .05") % 7 == 0);
    }

    /** What hwui expects after each verb; a multiple of it is a valid run. */
    private static int pointsPerVerb(char verb) {
        switch (Character.toLowerCase(verb)) {
            case 'z': return 0;
            case 'h': case 'v': return 1;
            case 'm': case 'l': case 't': return 2;
            case 's': case 'q': return 4;
            case 'c': return 6;
            case 'a': return 7;
            default: throw new IllegalArgumentException("verb " + verb);
        }
    }

    /**
     * How many floats hwui's {@code PathParser::getFloats} reads: a number ends at a space, a
     * comma, a minus or plus not after an exponent, or a second decimal point.
     */
    private static int platformFloatCount(String s) {
        int count = 0;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == ' ' || c == ',' || Character.isWhitespace(c)) {
                i++;
                continue;
            }
            boolean dot = false;
            int j = i;
            for (; j < n; j++) {
                char d = s.charAt(j);
                boolean prevE = j > i && (s.charAt(j - 1) == 'e' || s.charAt(j - 1) == 'E');
                if (d == ' ' || d == ',' || Character.isWhitespace(d)) break;
                if ((d == '-' || d == '+') && j != i && !prevE) break;
                if (d == '.') {
                    if (dot) break;
                    dot = true;
                }
            }
            count++;
            i = j;
        }
        return count;
    }

    private static Path drawableDir() {
        for (String candidate : new String[]{"src/main/res/drawable", "app/src/main/res/drawable"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) return path;
        }
        throw new AssertionError("res/drawable not found from " + Paths.get("").toAbsolutePath());
    }
}
