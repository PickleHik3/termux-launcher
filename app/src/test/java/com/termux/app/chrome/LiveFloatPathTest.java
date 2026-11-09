package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Every container on a floating window's glass slab's path to {@code terminal_root_container}
 * can draw its own child for Live blur ({@link LiveBackdrop.Container}); one that cannot would
 * leave out everything beneath the float at its level. Home float: slab, PaneContentFrame, the
 * float's content wrapper, FloatingPaneContainer, HomeFloatLayer, WidgetPaneView,
 * WidgetPaneFrame (wall), the wall; Workspace float: the same up to FloatingPaneContainer, then the pane
 * host and the wall. From the wall up both follow activity_termux.xml.
 */
public class LiveFloatPathTest {

    private static final String[] CODE_CONTAINERS = {
        "com.termux.app.terminal.PaneContentFrame",
        "com.termux.app.chrome.LiveBackdropFrameLayout",
        "com.termux.app.terminal.TerminalPaneController$FloatingPaneContainer",
        "com.termux.app.terminal.HomeFloatLayer",
        "com.termux.app.launcher.widget.WidgetPaneView",
        "com.termux.app.wall.WidgetPaneFrame",
        "com.termux.app.wall.PaneWallLayout",
    };

    @Test
    public void everyContainerTheFloatAddsIsAContainer() throws Exception {
        for (String name : CODE_CONTAINERS) {
            Class<?> type = Class.forName(name, false, getClass().getClassLoader());
            assertTrue(name, LiveBackdrop.Container.class.isAssignableFrom(type));
        }
    }

    @Test
    public void everyLayoutAncestorOfThePaneHostIsAContainer() throws Exception {
        File layout = new File("src/main/res/layout/activity_termux.xml");
        if (!layout.exists()) layout = new File("app/src/main/res/layout/activity_termux.xml");
        assertTrue("layout found", layout.exists());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        org.w3c.dom.Document document = factory.newDocumentBuilder().parse(layout);
        Element host = byId(document.getDocumentElement(), "terminal_pane_host");
        assertNotNull(host);
        List<String> chain = new ArrayList<>();
        boolean reachedRoot = false;
        for (Node node = host; node instanceof Element; node = node.getParentNode()) {
            Element element = (Element) node;
            chain.add(element.getTagName());
            String type = element.getTagName().contains(".")
                ? element.getTagName() : "android.widget." + element.getTagName();
            Class<?> viewClass = Class.forName(type, false, getClass().getClassLoader());
            assertTrue(type + " on the path " + chain,
                LiveBackdrop.Container.class.isAssignableFrom(viewClass));
            if ("terminal_root_container".equals(id(element))) {
                reachedRoot = true;
                break;
            }
        }
        assertTrue("the walk reached terminal_root_container: " + chain, reachedRoot);
        assertFalse(chain.isEmpty());
        assertEquals("com.termux.app.chrome.LiveBackdropFrameLayout", chain.get(0));
    }

    private static String id(Element element) {
        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        return id.startsWith("@+id/") ? id.substring(5) : id;
    }

    private static Element byId(Element element, String id) {
        if (id.equals(id(element))) return element;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element)) continue;
            Element found = byId((Element) child, id);
            if (found != null) return found;
        }
        return null;
    }
}
