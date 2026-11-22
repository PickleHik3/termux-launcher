package com.termux.app.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The window's view tree in {@code activity_termux.xml} (ADR 0028 section 6): every top-level view
 * sits in a named layer host, the hosts stand in the table's order, and the layer number is the
 * only Z between layers, so no top-level view and no host carries an elevation.
 */
public class WindowLayoutTest {

    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    private static Element root() throws Exception {
        File layout = new File("src/main/res/layout/activity_termux.xml");
        assertTrue("run from the app module: " + layout.getAbsolutePath(), layout.exists());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(layout);
        return document.getDocumentElement();
    }

    private static List<Element> children(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) out.add((Element) node);
        }
        return out;
    }

    private static String id(Element element) {
        String id = element.getAttributeNS(ANDROID, "id");
        return id.startsWith("@+id/") ? id.substring(5) : id;
    }

    private static List<String> ids(List<Element> elements) {
        List<String> out = new ArrayList<>();
        for (Element element : elements) out.add(id(element));
        return out;
    }

    private static Element byId(Element from, String id) {
        if (id(from).equals(id)) return from;
        for (Element child : children(from)) {
            Element found = byId(child, id);
            if (found != null) return found;
        }
        return null;
    }

    @Test
    public void theWindowRootHoldsTheShellThenTheEditorThenTheNotices() throws Exception {
        Element root = root();
        assertEquals("window_layers_root", id(root));
        assertEquals(Arrays.asList("activity_termux_root_view", "window_layer_editor",
            "window_layer_notices"), ids(children(root)));
    }

    @Test
    public void theShellsContainerHoldsGroundBodyChromeAndSheetsInOrder() throws Exception {
        Element container = byId(root(), "terminal_root_container");
        assertNotNull(container);
        assertEquals(Arrays.asList("window_layer_ground", "activity_termux_root_relative_layout",
            "window_layer_chrome", "window_layer_sheets"), ids(children(container)));
    }

    @Test
    public void everyTopLevelViewSitsInANamedLayerHost() throws Exception {
        Element container = byId(root(), "terminal_root_container");
        assertEquals(Arrays.asList("wallpaper_backdrop", "ground_frost", "ground_strip",
            "docked_frame_glass", "terminal_status_bar_background", "deck_status_band"),
            ids(children(byId(container, "window_layer_ground"))));
        assertEquals(Arrays.asList("home_pill_layer", "page_switch_layer"),
            ids(children(byId(container, "window_layer_chrome"))));
        assertEquals(Arrays.asList("app_drawer_host", "command_palette_host", "terminal_sheet_host",
            "terminal_rename_chip_host"), ids(children(byId(container, "window_layer_sheets"))));
    }

    @Test
    public void noLayerHostAndNoTopLevelViewCarriesAnElevation() throws Exception {
        Element root = root();
        List<Element> checked = new ArrayList<>(children(root));
        Element container = byId(root, "terminal_root_container");
        for (Element layer : children(container)) {
            checked.add(layer);
            if (!id(layer).equals("activity_termux_root_relative_layout")) {
                checked.addAll(children(layer));
            }
        }
        checked.addAll(children(byId(container, "activity_termux_root_relative_layout")));
        List<String> offenders = new ArrayList<>();
        for (Element element : checked) {
            if (element.hasAttributeNS(ANDROID, "elevation")
                || element.hasAttributeNS(ANDROID, "translationZ")) {
                offenders.add(id(element));
            }
        }
        assertEquals("Z orders nothing between layers; offenders: " + offenders, 0, offenders.size());
    }

    @Test
    public void insideTheBodyChildOrderReplacesTheOldElevations() throws Exception {
        Element body = byId(root(), "activity_termux_root_relative_layout");
        List<String> ids = ids(children(body));
        assertTrue(ids.indexOf("floating_keyboard_host") < ids.indexOf("terminal_find_bar_host"));
        assertTrue(ids.indexOf("terminal_find_bar_host")
            < ids.indexOf("surface_tuning_gesture_overlay"));
        // The A-Z scrub's labels drew over everything in the body (Z 80), so they are the last child.
        assertEquals(ids.size() - 1, ids.indexOf("apps_bar_az_label_overlay"));
        assertEquals(ids.size() - 2, ids.indexOf("surface_tuning_gesture_overlay"));
    }

    @Test
    public void theAccessoryStacksFxViewsStandByChildOrderNotZ() throws Exception {
        Element stack = byId(root(), "accessory_stack_container");
        List<String> ids = ids(children(stack));
        // The underlay drew behind the dock's glass (Z -8) and the overlay over the keyboard (Z 60).
        assertEquals("apps_bar_az_fx_underlay", ids.get(0));
        assertEquals("apps_bar_az_fx_overlay", ids.get(ids.size() - 1));
        assertTrue(ids.indexOf("accessory_keyboard_column") < ids.indexOf("apps_bar_az_fx_overlay"));
        for (Element child : children(stack)) {
            assertTrue(id(child) + " carries an elevation",
                !child.hasAttributeNS(ANDROID, "elevation"));
        }
        List<Element> az = children(byId(root(), "apps_bar_az_host"));
        // The scrub row drew over the hint row (Z 48 against 11).
        assertEquals(Arrays.asList("keybind_hint_dock_row", "apps_bar_az_row"), ids(az));
        for (Element child : az) {
            assertTrue(id(child) + " carries an elevation",
                !child.hasAttributeNS(ANDROID, "elevation"));
        }
    }

    @Test
    public void theEditorHostHoldsThePanelHostFirstSoThePickerStandsOverIt() throws Exception {
        Element editor = byId(root(), "window_layer_editor");
        assertEquals(Arrays.asList("editor_panel_host"), ids(children(editor)));
    }
}
