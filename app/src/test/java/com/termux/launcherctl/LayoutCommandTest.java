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
}
