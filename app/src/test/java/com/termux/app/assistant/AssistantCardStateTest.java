package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.FeatureRequest;
import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingStore;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** A settings card keeps its state: Undo, "changed since", the chat store and the next request. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AssistantCardStateTest {
    private static final class Transport implements ChatTransport {
        final List<String> bodies = new ArrayList<>();
        boolean call = true;

        @NonNull
        @Override
        public Call start(@NonNull String body, @NonNull FeatureRequest.Sink sink) {
            bodies.add(body);
            boolean makeCall = call;
            return new Call() {
                @Override
                public void cancel() {
                }

                @Override
                public void await() throws Exception {
                    if (makeCall) {
                        JSONObject c = new JSONObject().put("index", 0).put("id", "call_0").put("type", "function")
                            .put("function", new JSONObject().put("name", "set_setting")
                                .put("arguments", "{\"id\":\"text.size\",\"value\":\"up\"}"));
                        JSONObject chunk = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                            .put("index", 0).put("delta", new JSONObject().put("tool_calls",
                                new JSONArray().put(c))).put("finish_reason", JSONObject.NULL)));
                        sink.onEvent(FeatureRequest.Event.of(chunk));
                    } else {
                        sink.onEvent(FeatureRequest.Event.text("Sunny."));
                        sink.onEvent(FeatureRequest.Event.done());
                    }
                }
            };
        }
    }

    private File file;
    private AssistantChatStore store;
    private Transport transport;
    private AssistantEngine engine;
    private AssistantCards cards;
    private SettingsRegistry registry;
    private SettingsApplier applier;
    private final List<SettingRefresh> refreshed = new ArrayList<>();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        file = new File(context.getCacheDir(), "card_state_" + System.nanoTime() + ".db");
        store = new AssistantChatStore(file);
        transport = new Transport();
        SettingStore memory = new InMemorySettingStore();
        registry = new SettingsRegistry(which -> memory, SettingDeclarations.ALL);
        applier = new SettingsApplier(new SettingsApplier.Host() {
            @Override
            public boolean isLauncherResumed() {
                return true;
            }

            @Override
            public void run(@NonNull SettingRefresh refresh) {
                refreshed.add(refresh);
            }
        });
        cards = new AssistantCards();
        engine = new AssistantEngine(store, transport, () -> 4096, Runnable::run, Runnable::run,
            Runnable::run, new AtomicLong(1000)::get, () -> registry, cards);
    }

    @After
    public void tearDown() {
        store.close();
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /** Asks for bigger text on a chat; returns the answer's message id. */
    private long askBigger(long chat) {
        transport.call = true;
        assertTrue(engine.send(chat, "make the text bigger"));
        List<AssistantMessage> all = store.messages(chat);
        return all.get(all.size() - 1).id;
    }

    @Test
    public void applyThenUndoRestoresTheOldValuesThroughTheRegistry() {
        long answer = askBigger(store.createChat(1));
        assertTrue(cards.apply(answer, registry, applier));
        assertEquals("105", registry.get("text.size"));
        SettingsCard card = cards.get(answer);
        assertEquals(SettingsCard.State.APPLIED, card.state());
        refreshed.clear();
        assertTrue(cards.undo(answer, registry, applier));
        assertEquals("100", registry.get("text.size"));
        assertEquals(SettingsCard.State.UNDONE, card.state());
        assertFalse("the launcher refreshes as it does from Settings", refreshed.isEmpty());
        assertFalse("Undo is once", cards.undo(answer, registry, applier));
    }

    @Test
    public void anOutsideChangeDisablesUndoAndTheCardSaysSo() {
        long answer = askBigger(store.createChat(1));
        cards.apply(answer, registry, applier);
        registry.set("text.size", "130");
        SettingsCard card = cards.get(answer);
        assertTrue(cards.refresh(answer, card, registry));
        assertEquals(SettingsCard.State.CHANGED_SINCE, card.state());
        assertFalse(cards.undo(answer, registry, applier));
        assertEquals("nothing is written", "130", registry.get("text.size"));
        assertEquals(SettingsCard.State.CHANGED_SINCE, card.state());
        registry.set("text.size", "105");
        cards.refresh(answer, card, registry);
        assertEquals("set back, Undo works again", SettingsCard.State.APPLIED, card.state());
    }

    @Test
    public void undoChecksTheValuesItselfWhenNothingRefreshedFirst() {
        long answer = askBigger(store.createChat(1));
        cards.apply(answer, registry, applier);
        registry.set("text.size", "90");
        assertFalse(cards.undo(answer, registry, applier));
        assertEquals(SettingsCard.State.CHANGED_SINCE, cards.get(answer).state());
        assertEquals("90", registry.get("text.size"));
    }

    @Test
    public void aCardsStateSurvivesTheStoreAndAReopen() {
        long chat = store.createChat(1);
        long answer = askBigger(chat);
        cards.apply(answer, registry, applier);
        store.close();

        AssistantChatStore reopened = new AssistantChatStore(file);
        try {
            AssistantCards fresh = new AssistantCards();
            fresh.attach(reopened);
            fresh.adopt(reopened.messages(chat));
            SettingsCard card = fresh.all().get(answer);
            assertNotNull(card);
            assertEquals(SettingsCard.State.APPLIED, card.state());
            assertEquals("Text size: 100 → 105", card.items.get(0).line());
            assertEquals("105", card.items.get(0).written);
            assertTrue("Undo still works after a restart", fresh.undo(answer, registry, applier));
            assertEquals("100", registry.get("text.size"));

            AssistantCards again = new AssistantCards();
            again.attach(reopened);
            assertEquals(SettingsCard.State.UNDONE, again.get(answer).state());
        } finally {
            reopened.close();
        }
    }

    @Test
    public void aStoredChatWithoutCardDataStillLoads() {
        store.close();
        // A version 3 file: no card column.
        SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(file, null);
        old.execSQL("CREATE TABLE chats (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', "
            + "created_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, chat_id INTEGER NOT NULL, "
            + "role TEXT NOT NULL, text TEXT NOT NULL DEFAULT '', state TEXT NOT NULL, error_code TEXT, "
            + "created_ms INTEGER NOT NULL, duration_ms INTEGER, ttft_ms INTEGER, output_tokens INTEGER, "
            + "thinking TEXT, thinking_ms INTEGER)");
        old.execSQL("INSERT INTO chats (title, created_ms, updated_ms) VALUES ('t', 1, 1)");
        old.execSQL("INSERT INTO messages (chat_id, role, text, state, created_ms) "
            + "VALUES (1, 'assistant', 'Text size: 100 → 105', 'complete', 1)");
        old.setVersion(3);
        old.close();

        AssistantChatStore migrated = new AssistantChatStore(file);
        try {
            List<AssistantMessage> messages = migrated.messages(1);
            assertEquals(1, messages.size());
            assertEquals("Text size: 100 → 105", messages.get(0).text);
            assertNull(messages.get(0).card);
        } finally {
            migrated.close();
        }
        assertNull(SettingsCard.fromJson("not json"));
        assertNull(SettingsCard.fromJson("{\"kind\":\"GONE\",\"state\":\"APPLIED\"}"));
    }

    @Test
    public void onlyAOneLineNoteOfAnAppliedChangeReachesTheNextRequest() throws Exception {
        long chat = store.createChat(1);
        long answer = askBigger(chat);
        cards.apply(answer, registry, applier);
        transport.call = false;
        assertTrue(engine.send(chat, "what is the weather"));
        String second = assistantTexts(transport.bodies.get(transport.bodies.size() - 1));
        assertEquals("Applied: Text size: 100 → 105", second);

        // An offered card, and an undone one, send nothing of themselves.
        long other = askBigger(store.createChat(2));
        transport.call = false;
        long otherChat = store.message(other).chatId;
        assertTrue(engine.send(otherChat, "what is the weather"));
        assertEquals("", assistantTexts(transport.bodies.get(transport.bodies.size() - 1)));
        cards.apply(other, registry, applier);
        cards.undo(other, registry, applier);
        assertTrue(engine.send(otherChat, "and tomorrow"));
        assertEquals("", assistantTexts(transport.bodies.get(transport.bodies.size() - 1)));
    }

    @Test
    public void anOfferedOrUndoneCardTakesItsRequestWithIt() throws Exception {
        long chat = store.createChat(1);
        long answer = askBigger(chat);
        transport.call = false;
        assertTrue(engine.send(chat, "what is the weather"));
        assertAlternates(transport.bodies.get(transport.bodies.size() - 1), "what is the weather");

        cards.apply(answer, registry, applier);
        cards.undo(answer, registry, applier);
        assertTrue(engine.send(chat, "and tomorrow"));
        assertAlternates(transport.bodies.get(transport.bodies.size() - 1), "and tomorrow");
    }

    @Test
    public void anAppliedCardKeepsItsRequestAndNote() throws Exception {
        long chat = store.createChat(1);
        long answer = askBigger(chat);
        cards.apply(answer, registry, applier);
        transport.call = false;
        assertTrue(engine.send(chat, "what is the weather"));
        JSONArray messages = new JSONObject(transport.bodies.get(transport.bodies.size() - 1))
            .getJSONArray("messages");
        List<String> roles = new ArrayList<>();
        for (int i = 0; i < messages.length(); i++) roles.add(messages.getJSONObject(i).getString("role"));
        assertTrue(roles.toString(), roles.contains("assistant"));
        assertEquals("user", roles.get(roles.indexOf("assistant") - 1));
    }

    /** No two user messages in a row, and the last one is {@code lastUser}. */
    private static void assertAlternates(String body, String lastUser) throws Exception {
        JSONArray messages = new JSONObject(body).getJSONArray("messages");
        String previous = "";
        for (int i = 0; i < messages.length(); i++) {
            String role = messages.getJSONObject(i).getString("role");
            assertFalse("two user turns in a row: " + body, role.equals("user") && previous.equals("user"));
            previous = role;
        }
        assertEquals(lastUser, messages.getJSONObject(messages.length() - 1).getString("content"));
    }

    private static String assistantTexts(String body) throws Exception {
        JSONArray messages = new JSONObject(body).getJSONArray("messages");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject m = messages.getJSONObject(i);
            if (!"assistant".equals(m.getString("role"))) continue;
            String content = m.getString("content");
            if (content.equals("Sunny.")) continue;
            if (out.length() > 0) out.append('\n');
            out.append(content);
        }
        return out.toString();
    }
}
