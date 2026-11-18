package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.page.PageKind;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.LayoutStore;
import com.termux.app.place.layout.LayoutsJson;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Terminal, Display and Assistant pages are one Workspace (ADR 0025): one layout document, one
 * dress, whichever page asks or writes; each place keeps only its remembered keyboard.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WorkspaceLayoutTest {

    private Application app;
    private PlaceLayoutStore store;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = app.getSharedPreferences("place-workspace", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        LayoutStore.prefs(app).edit().clear().commit();
        LayoutStore.shared(app).load();
        store = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    @Test
    public void theRetiredScopesRouteToTheWorkspacesDocument() {
        for (String scope : new String[]{"", "display.", "assistant."}) {
            LayoutDocumentPrefs.Route route =
                LayoutDocumentPrefs.route("layout." + scope + "portrait.bar_order");
            assertSame(scope, LayoutKind.TERMINAL, route.kind);
        }
        for (PageKind kind : new PageKind[]{PageKind.TERMINAL, PageKind.DISPLAY,
                PageKind.ASSISTANT}) {
            LayoutDocumentPrefs.Route route = LayoutDocumentPrefs.route(
                PlaceLayoutStore.kindKey(kind, PlaceOrientation.PORTRAIT, Element.APPS));
            assertSame(kind.name(), LayoutKind.TERMINAL, route.kind);
        }
        assertSame(LayoutKind.HOME, LayoutDocumentPrefs.route(
            PlaceLayoutStore.kindKey(PageKind.HOME, PlaceOrientation.PORTRAIT, Element.APPS)).kind);
    }

    @Test
    public void eachPlaceKeepsItsOwnRememberedKeyboard() {
        assertSame(LayoutKind.TERMINAL, LayoutDocumentPrefs.route(PlaceLayoutStore.memoryKey(
            PageKind.TERMINAL, LayoutsJson.KEYBOARD_OPEN)).kind);
        assertNull(LayoutDocumentPrefs.route(PlaceLayoutStore.memoryKey(
            PageKind.DISPLAY, LayoutsJson.KEYBOARD_OPEN)));
        assertNull(LayoutDocumentPrefs.route(PlaceLayoutStore.memoryKey(
            PageKind.ASSISTANT, LayoutsJson.KEYBOARD_OPEN)));
        store.setKeyboardOpen(PageKind.ASSISTANT, true);
        assertTrue(store.wasKeyboardOpen(PageKind.ASSISTANT));
        assertFalse(store.wasKeyboardOpen(PageKind.TERMINAL));
        assertFalse(store.wasKeyboardOpen(PageKind.DISPLAY));
    }

    @Test
    public void everyWorkspacePageWearsTheWorkspacesLayoutAndDress() {
        assertSame(LayoutVariant.NORMAL, store.variantFor(PageKind.TERMINAL));
        assertSame(LayoutVariant.NORMAL, store.variantFor(PageKind.DISPLAY));
        assertSame(LayoutVariant.NORMAL, store.variantFor(PageKind.ASSISTANT));
        // A change of what the Workspace shows, made from the Assistant page, shows everywhere.
        boolean shown = !store.kindShows(PageKind.TERMINAL, PlaceOrientation.PORTRAIT,
            Element.APPS);
        store.setKindShows(PageKind.ASSISTANT, PlaceOrientation.PORTRAIT, Element.APPS, shown);
        for (PageKind kind : new PageKind[]{PageKind.TERMINAL, PageKind.DISPLAY,
                PageKind.ASSISTANT}) {
            assertEquals(kind.name(), shown,
                store.kindShows(kind, PlaceOrientation.PORTRAIT, Element.APPS));
        }
        assertEquals(store.resolve(PlaceOrientation.PORTRAIT, PageKind.TERMINAL),
            store.resolve(PlaceOrientation.PORTRAIT, PageKind.ASSISTANT));
        assertEquals(store.resolve(PlaceOrientation.PORTRAIT, PageKind.TERMINAL),
            store.resolve(PlaceOrientation.PORTRAIT, PageKind.DISPLAY));
    }

    /** An old install's own Display and Assistant documents are read by nothing and kept. */
    @Test
    public void theOldDocumentsLoadWithoutHarmAndStayForADowngrade() {
        String assistant = "{\"version\":1,\"portrait\":{\"bar_order\":\"x\"},\"landscape\":{}}";
        LayoutStore.prefs(app).edit()
            .putString(LayoutKind.ASSISTANT.key, assistant)
            .putString(LayoutKind.DISPLAY.key, "{not json").commit();
        LayoutStore documents = LayoutStore.shared(app);
        documents.load();
        documents.update(LayoutKind.TERMINAL,
            layout -> layout.with(PlaceOrientation.PORTRAIT, "bar_order", "terminal"));
        assertEquals("terminal", documents.current().kind(LayoutKind.ASSISTANT).portrait()
            .string("bar_order"));
        assertEquals(assistant, LayoutStore.prefs(app).getString(LayoutKind.ASSISTANT.key, null));
        assertEquals("{not json", LayoutStore.prefs(app).getString(LayoutKind.DISPLAY.key, null));
    }
}
