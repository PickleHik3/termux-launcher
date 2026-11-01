package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.LayoutStore;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** {@code launcherctl layout get/set} (ADR 0020 §10): whole documents, validated, field named. */
public class LayoutCommandTest {

    private final Map<String, String> written = new HashMap<>();
    private final LayoutStore store = new LayoutStore(new LayoutStore.Storage() {
        @Nullable @Override public String read(@NonNull String key) { return written.get(key); }
        @Override public void write(@NonNull Map<String, String> documents) {
            written.putAll(documents);
        }
    }, false);

    @Test
    public void getThenSetRoundTripsOneKindAndLeavesTheOthers() throws Exception {
        store.load();
        JSONObject home = new JSONObject(LayoutCommand.get(store, "home"));
        home.put("search_field", true);
        assertNull(LayoutCommand.set(store, "home", home.toString()));

        assertTrue(new JSONObject(LayoutCommand.get(store, "home")).getBoolean("search_field"));
        assertTrue(written.containsKey("home"));
        assertEquals("the Terminal's document is not rewritten", null, written.get("terminal"));
        assertTrue(new JSONObject(LayoutCommand.get(store, "terminal")).optBoolean(
            "search_field", false) == false);
    }

    @Test
    public void aBadFieldIsRefusedByNameAndNothingIsWritten() {
        store.load();
        String refused = LayoutCommand.set(store, "display",
            "{\"version\":1,\"search_field\":\"yes\"}");
        assertNotNull(refused);
        assertTrue(refused, refused.contains("search_field"));
        assertTrue(written.isEmpty());
    }

    @Test
    public void anUnknownNameIsRefusedWithTheNames() {
        store.load();
        assertNull(LayoutCommand.get(store, "page2"));
        String refused = LayoutCommand.refusal("page2", "{}");
        assertNotNull(refused);
        for (LayoutKind kind : LayoutKind.values()) assertTrue(refused.contains(kind.key));
        assertTrue(refused.contains("sizes"));
    }

    @Test
    public void theSharedSizesAreADocumentToo() throws Exception {
        store.load();
        assertNotNull(LayoutCommand.get(store, "sizes"));
        assertNotNull(LayoutCommand.refusal("sizes", "not json"));
        assertNull(LayoutCommand.set(store, "sizes", LayoutCommand.get(store, "sizes")));
    }

    private void assertRefusedNaming(String name, String json, String field) {
        String refused = LayoutCommand.refusal(name, json);
        assertNotNull(json, refused);
        assertTrue(refused, refused.contains(field));
        assertTrue(refused, refused.contains("replaces the whole document"));
    }

    @Test
    public void unreadableValuesAreRefusedWithTheFieldNamedAndNothingIsStored() {
        store.load();
        assertRefusedNaming("home", "{\"version\":1,\"dock_design\":\"nope\"}", "dock_design");
        assertRefusedNaming("home", "{\"version\":1,\"drawer_tile_position\":\"left\"}",
            "drawer_tile_position");
        assertRefusedNaming("home", "{\"version\":1,\"portrait\":{\"status_bar\":\"middle\"}}",
            "status_bar");
        assertRefusedNaming("home", "{\"version\":1,\"landscape\":{\"keyboard_form\":\"big\"}}",
            "keyboard_form");
        assertRefusedNaming("home", "{\"version\":1,\"portrait\":{\"apps_row_restore_edge\":\"hidden\"}}",
            "apps_row_restore_edge");
        assertRefusedNaming("home", "{\"version\":1,\"portrait\":{\"extra_keys_order\":-1}}",
            "extra_keys_order");
        assertRefusedNaming("home", "{\"version\":1,\"portrait\":{\"extra_keys_order\":1.5}}",
            "extra_keys_order");
        assertRefusedNaming("sizes", "{\"version\":1,\"icon_size\":\"huge\"}", "icon_size");
        assertRefusedNaming("sizes", "{\"version\":1,\"portrait\":{\"dock_height\":99}}",
            "dock_height");
        assertNotNull(LayoutCommand.set(store, "home", "{\"version\":1,\"dock_design\":\"nope\"}"));
        assertTrue(written.isEmpty());
    }

    @Test
    public void aFullValidDocumentAndLegacyDockIdsAreAccepted() throws Exception {
        store.load();
        assertNull(LayoutCommand.refusal("home", LayoutCommand.get(store, "home")));
        assertNull(LayoutCommand.refusal("home",
            "{\"version\":1,\"dock_design\":\"peek_glass\",\"drawer_tile_position\":\"end\","
                + "\"future_field\":\"anything\",\"portrait\":{\"status_bar\":\"hidden\","
                + "\"az_bar\":\"left\",\"keyboard_mode\":\"resize\",\"keyboard_form\":\"split\","
                + "\"apps_row_order\":2,\"apps_row_restore_edge\":\"top\"}}"));
        assertNull(LayoutCommand.refusal("sizes",
            "{\"version\":1,\"icon_size\":\"xl\",\"portrait\":{\"dock_height\":1.0,"
                + "\"keyboard_chin\":8,\"wall_strip_height\":80}}"));
        for (com.termux.app.dock.DockStyle style : com.termux.app.dock.DockStyle.values())
            assertNull(style.storageValue(), LayoutCommand.refusal("home",
                "{\"version\":1,\"dock_design\":\"" + style.storageValue() + "\"}"));
    }
}
