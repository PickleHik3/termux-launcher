package com.termux.app.place.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.place.PlaceOrientation;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutsJsonTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

    @Test
    public void aDocumentRoundTrips() throws Exception {
        KindLayout home = KindLayout.EMPTY
            .withWide(LayoutsJson.DOCK_DESIGN, "peek_glass")
            .withWide(LayoutsJson.SEARCH_FIELD, true)
            .withWide(LayoutsJson.KEYBOARD_ROWS, 2)
            .with(PORTRAIT, "status_bar", "top")
            .with(PORTRAIT, "apps_row_order", 3)
            .with(LANDSCAPE, LayoutsJson.WIDGET_COLUMNS, 6);
        KindLayout read = LayoutsJson.parseKind(LayoutsJson.write(home));
        assertEquals(home, read);
        assertEquals("peek_glass", read.wide().string(LayoutsJson.DOCK_DESIGN));
        assertEquals(3, read.portrait().integer("apps_row_order", -1));
    }

    @Test
    public void sizesRoundTripTheirUnits() throws Exception {
        Sizes sizes = Sizes.EMPTY.with(PORTRAIT, LayoutsJson.KEYBOARD_HEIGHT, 0.85f)
            .with(PORTRAIT, LayoutsJson.KEYBOARD_CHIN, 8)
            .with(LANDSCAPE, LayoutsJson.DOCK_HEIGHT, 1.2f);
        Sizes read = LayoutsJson.parseSizes(LayoutsJson.write(sizes));
        assertEquals(sizes, read);
        assertEquals(0.85f, read.orientation(PORTRAIT).decimal(LayoutsJson.KEYBOARD_HEIGHT, 0f), 1e-6f);
        assertEquals(8, read.orientation(PORTRAIT).integer(LayoutsJson.KEYBOARD_CHIN, 0));
    }

    @Test
    public void anUnknownFieldIsKeptOnRewrite() throws Exception {
        String newer = "{\"version\":1,\"future_wide\":{\"a\":1},\"dock_design\":\"glass_tray\","
            + "\"portrait\":{\"status_bar\":\"top\",\"future_field\":[1,2]},\"landscape\":{}}";
        KindLayout read = LayoutsJson.parseKind(newer);
        KindLayout edited = read.with(PORTRAIT, "status_bar", "bottom");
        JSONObject written = new JSONObject(LayoutsJson.write(edited));
        assertEquals(1, written.getJSONObject("future_wide").getInt("a"));
        assertEquals(2, written.getJSONObject("portrait").getJSONArray("future_field").length());
        assertEquals("bottom", written.getJSONObject("portrait").getString("status_bar"));
    }

    @Test
    public void aBadDocumentIsReplacedByItsShippedOneAndTheOthersAreKept() {
        Map<String, String> file = new HashMap<>();
        file.put(LayoutKind.HOME.key, "{not json");
        file.put(LayoutKind.TERMINAL.key,
            LayoutsJson.write(KindLayout.EMPTY.withWide(LayoutsJson.DOCK_DESIGN, "taskbar")));
        file.put(LayoutKind.TERMINAL_MINIMAL.key, "{\"portrait\":{}}");
        // A retired document (ADR 0025) is never read, bad or not: no warning for it.
        file.put(LayoutKind.DISPLAY.key, "{not json");
        List<String> warnings = new ArrayList<>();
        Layouts layouts = LayoutsJson.read(new LayoutsJson.Source() {
            @Override public String read(String key) { return file.get(key); }
            @Override public void warn(String message) { warnings.add(message); }
        });
        assertEquals(LayoutsJson.shippedDefault(LayoutKind.HOME), layouts.kind(LayoutKind.HOME));
        assertEquals(LayoutsJson.shippedDefault(LayoutKind.TERMINAL_MINIMAL),
            layouts.kind(LayoutKind.TERMINAL_MINIMAL));
        assertEquals("the Display wears the Workspace's", layouts.kind(LayoutKind.TERMINAL),
            layouts.kind(LayoutKind.DISPLAY));
        assertEquals("taskbar",
            layouts.kind(LayoutKind.TERMINAL).wide().string(LayoutsJson.DOCK_DESIGN));
        assertEquals(2, warnings.size());
    }

    @Test
    public void theShippedDocumentsHoldNothingSoEveryFieldReadsItsDefault() {
        for (LayoutKind kind : LayoutKind.values())
            assertEquals(KindLayout.EMPTY, LayoutsJson.shippedDefault(kind));
    }

    @Test
    public void validationNamesTheField() {
        assertNull(LayoutsJson.validate(LayoutKind.HOME,
            "{\"version\":1,\"search_field\":true,\"portrait\":{\"widget_rows\":5}}"));
        String wrongType = LayoutsJson.validate(LayoutKind.HOME,
            "{\"version\":1,\"search_field\":\"yes\"}");
        assertNotNull(wrongType);
        assertTrue(wrongType, wrongType.contains("search_field"));
        String grid = LayoutsJson.validate(LayoutKind.TERMINAL,
            "{\"version\":1,\"portrait\":{\"widget_rows\":5}}");
        assertNotNull(grid);
        assertTrue(grid, grid.contains("widget_rows"));
        assertNotNull(LayoutsJson.validate(LayoutKind.HOME, "[]"));
    }
}
