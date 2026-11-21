package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.os.Build;

import androidx.core.graphics.PathParser;

import com.termux.app.page.PageKind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PageGlyphSlotsTest {

    private static final Pattern PATH_DATA = Pattern.compile("android:pathData=\"([^\"]*)\"");

    /** The path data of {@code kind}'s {@code ic_rl_pg_*.xml}, in document order. */
    private static List<String> drawablePaths(PageKind kind) throws IOException {
        String name = "ic_rl_pg_" + kind.name().toLowerCase(Locale.ROOT) + ".xml";
        File file = new File("src/main/res/drawable/" + name);
        if (!file.exists()) file = new File("app/src/main/res/drawable/" + name);
        Matcher m = PATH_DATA.matcher(new String(Files.readAllBytes(file.toPath()),
            StandardCharsets.UTF_8));
        List<String> out = new ArrayList<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test public void theOutlinesAreTheDrawablesPathData() throws IOException {
        for (PageKind kind : PageKind.values()) {
            List<String> expected = new ArrayList<>();
            expected.add(PageGlyphSlots.outline(kind));
            String dot = PageGlyphSlots.dot(kind);
            if (dot != null) expected.add(dot);
            assertEquals(kind.name(), expected, drawablePaths(kind));
        }
    }

    @Test public void boxesAreInsideTheViewport() {
        float v = PageGlyphSlots.VIEWPORT;
        for (PageKind kind : PageKind.values()) {
            float[] b = PageGlyphSlots.box(kind);
            assertTrue(kind.name(), b[0] >= 0f && b[1] >= 0f && b[2] <= v && b[3] <= v);
            assertTrue(kind.name(), b[2] > b[0] && b[3] > b[1]);
        }
    }

    private static float distanceToBox(float x, float y, float[] b) {
        float dx = Math.max(Math.max(b[0] - x, 0f), x - b[2]);
        float dy = Math.max(Math.max(b[1] - y, 0f), y - b[3]);
        return (float) Math.hypot(dx, dy);
    }

    @Test public void noStrokeReachesIntoTheNumberBox() {
        // The box keeps the number's air from every stroke's edge.
        float half = PageGlyphSlots.STROKE / 2f + PageGlyphSlots.NUMBER_AIR;
        float[] pos = new float[2];
        for (PageKind kind : PageKind.values()) {
            float[] box = PageGlyphSlots.box(kind);
            PathMeasure measure = new PathMeasure(
                PathParser.createPathFromPathData(PageGlyphSlots.outline(kind)), false);
            int samples = 0;
            do {
                float length = measure.getLength();
                for (float d = 0f; d <= length; d += 0.05f) {
                    measure.getPosTan(d, pos, null);
                    samples++;
                    assertTrue(kind + " at " + pos[0] + "," + pos[1],
                        distanceToBox(pos[0], pos[1], box) >= half - 1e-3f);
                }
            } while (measure.nextContour());
            assertTrue(kind.name(), samples > 100);
            String dot = PageGlyphSlots.dot(kind);
            if (dot != null) {
                RectF r = new RectF();
                Path p = PathParser.createPathFromPathData(dot);
                p.computeBounds(r, true);
                assertTrue(kind.name(), !RectF.intersects(r, new RectF(box[0], box[1], box[2], box[3])));
            }
        }
    }
}
