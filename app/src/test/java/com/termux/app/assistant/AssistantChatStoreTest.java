package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.util.List;
import android.app.Application;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AssistantChatStoreTest {
    private File file;
    private AssistantChatStore store;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        file = new File(context.getCacheDir(), "assistant_test_" + System.nanoTime() + ".db");
        store = new AssistantChatStore(file);
    }

    @After
    public void tearDown() {
        store.close();
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private long chatWith(String userText, String answer, long t) {
        long chat = store.createChat(t);
        store.appendMessage(chat, AssistantMessage.ROLE_USER, userText, AssistantMessage.STATE_COMPLETE, t + 1);
        store.appendMessage(chat, AssistantMessage.ROLE_ASSISTANT, answer, AssistantMessage.STATE_COMPLETE, t + 2);
        return chat;
    }

    @Test
    public void createAndReadBack() {
        long chat = chatWith("hello there", "hi", 1000);
        AssistantChat read = store.chat(chat);
        assertNotNull(read);
        assertEquals("hello there", read.title);
        assertEquals(1000, read.createdMs);
        assertEquals(1002, read.updatedMs);
        List<AssistantMessage> messages = store.messages(chat);
        assertEquals(2, messages.size());
        assertEquals(AssistantMessage.ROLE_USER, messages.get(0).role);
        assertEquals("hi", messages.get(1).text);
        assertNull(store.chat(chat + 99));
    }

    @Test
    public void titleIsFirstLineTrimmedAndCapped() {
        long chat = store.createChat(1);
        String longLine = new String(new char[100]).replace('\0', 'a');
        store.appendMessage(chat, AssistantMessage.ROLE_USER, "  \n" + longLine + "\nsecond", "complete", 2);
        String title = store.chat(chat).title;
        assertEquals(AssistantChatStore.TITLE_MAX_CHARS, title.length());
        assertTrue(title.endsWith("…"));
        // A later user message never renames the chat.
        store.appendMessage(chat, AssistantMessage.ROLE_USER, "other", "complete", 3);
        assertEquals(title, store.chat(chat).title);
        assertEquals("a b c", AssistantChatStore.titleFrom("a \t b   c\nd"));
    }

    @Test
    public void updateMessageChangesTextStateAndError() {
        long chat = store.createChat(1);
        long id = store.appendMessage(chat, AssistantMessage.ROLE_ASSISTANT, "", AssistantMessage.STATE_STREAMING, 2);
        assertTrue(store.updateMessage(id, "partial", AssistantMessage.STATE_ERROR, "timeout", 50));
        AssistantMessage m = store.message(id);
        assertEquals("partial", m.text);
        assertEquals(AssistantMessage.STATE_ERROR, m.state);
        assertEquals("timeout", m.errorCode);
        assertEquals(50, store.chat(chat).updatedMs);
        assertTrue(store.updateMessage(id, "done", AssistantMessage.STATE_COMPLETE, null, 60));
        assertNull(store.message(id).errorCode);
        assertEquals(false, store.updateMessage(id + 99, "x", "complete", null, 1));
    }

    @Test
    public void listIsNewestUpdatedFirstAndLatestLimits() {
        long a = chatWith("first", "x", 100);
        long b = chatWith("second", "y", 200);
        long c = chatWith("third", "z", 300);
        store.appendMessage(a, AssistantMessage.ROLE_USER, "bump", "complete", 400);
        List<AssistantChat> all = store.listChats(null);
        assertEquals(a, all.get(0).id);
        assertEquals(c, all.get(1).id);
        assertEquals(b, all.get(2).id);
        assertEquals(2, store.latestChats(2).size());
        assertEquals(0, store.latestChats(0).size());
    }

    @Test
    public void searchMatchesTitleAndMessageTextIgnoringCase() {
        long a = chatWith("Recipe for BREAD", "mix flour", 100);
        long b = chatWith("weather", "It will be Sunny", 200);
        chatWith("other", "nothing", 300);
        assertEquals(1, store.listChats("bread").size());
        assertEquals(a, store.listChats("bread").get(0).id);
        assertEquals(b, store.listChats("SUNNY").get(0).id);
        assertEquals(0, store.listChats("absent").size());
        assertEquals(3, store.listChats("  ").size());
        // Non-ASCII case folds too.
        long c = chatWith("ÉTÉ", "x", 400);
        assertEquals(c, store.listChats("été").get(0).id);
    }

    @Test
    public void deleteChatCascadesAndDeleteAllClears() {
        long a = chatWith("one", "x", 100);
        long b = chatWith("two", "y", 200);
        store.deleteChat(a);
        assertNull(store.chat(a));
        assertEquals(0, store.messages(a).size());
        assertEquals(2, store.messages(b).size());
        store.deleteAllChats();
        assertEquals(0, store.listChats(null).size());
        assertEquals(0, store.messages(b).size());
    }

    @Test
    public void appendToMissingChatFails() {
        assertEquals(-1L, store.appendMessage(42, "user", "x", "complete", 1));
    }

    @Test
    public void streamingMessageBecomesStoppedWhenReopened() {
        long chat = store.createChat(1);
        long id = store.appendMessage(chat, AssistantMessage.ROLE_ASSISTANT, "half an ans", AssistantMessage.STATE_STREAMING, 2);
        long done = store.appendMessage(chat, AssistantMessage.ROLE_ASSISTANT, "ok", AssistantMessage.STATE_COMPLETE, 3);
        store.close();
        AssistantChatStore reopened = new AssistantChatStore(file);
        try {
            assertEquals(AssistantMessage.STATE_STOPPED, reopened.message(id).state);
            assertEquals("half an ans", reopened.message(id).text);
            assertEquals(AssistantMessage.STATE_COMPLETE, reopened.message(done).state);
        } finally {
            reopened.close();
        }
    }

    @Test
    public void freshDatabaseHasTheSpeedColumns() {
        long chat = store.createChat(1);
        long id = store.appendMessage(chat, AssistantMessage.ROLE_ASSISTANT, "", AssistantMessage.STATE_STREAMING, 2);
        assertNull(store.message(id).durationMs);
        store.updateMessage(id, "hi", AssistantMessage.STATE_COMPLETE, null, 3, 4100L, 500L, 51);
        AssistantMessage m = store.message(id);
        assertEquals(Long.valueOf(4100), m.durationMs);
        assertEquals(Long.valueOf(500), m.ttftMs);
        assertEquals(Integer.valueOf(51), m.outputTokens);
        store.updateMessage(id, "hi", AssistantMessage.STATE_COMPLETE, null, 4, null, null, null);
        assertNull(store.message(id).durationMs);
        assertNull(store.message(id).outputTokens);
    }

    @Test
    public void versionOneDatabaseUpgradesAndKeepsItsMessages() {
        android.database.sqlite.SQLiteDatabase old =
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null);
        old.execSQL("CREATE TABLE chats (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', "
            + "created_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE, "
            + "role TEXT NOT NULL, text TEXT NOT NULL DEFAULT '', state TEXT NOT NULL, "
            + "error_code TEXT, created_ms INTEGER NOT NULL)");
        old.execSQL("INSERT INTO chats (id, title, created_ms, updated_ms) VALUES (1, 't', 1, 1)");
        old.execSQL("INSERT INTO messages (chat_id, role, text, state, created_ms) "
            + "VALUES (1, 'assistant', 'old answer', 'complete', 2)");
        old.setVersion(1);
        old.close();
        List<AssistantMessage> messages = store.messages(1);
        assertEquals(1, messages.size());
        assertEquals("old answer", messages.get(0).text);
        assertNull(messages.get(0).durationMs);
        assertNull(messages.get(0).outputTokens);
        store.updateMessage(messages.get(0).id, "old answer", AssistantMessage.STATE_COMPLETE, null, 5,
            1000L, 100L, 20);
        assertEquals(Integer.valueOf(20), store.message(messages.get(0).id).outputTokens);
    }

    private void createVersion(int version, boolean withSpeed) {
        android.database.sqlite.SQLiteDatabase old =
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null);
        old.execSQL("CREATE TABLE chats (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', "
            + "created_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE, "
            + "role TEXT NOT NULL, text TEXT NOT NULL DEFAULT '', state TEXT NOT NULL, "
            + "error_code TEXT, created_ms INTEGER NOT NULL"
            + (withSpeed ? ", duration_ms INTEGER, ttft_ms INTEGER, output_tokens INTEGER" : "") + ")");
        old.execSQL("INSERT INTO chats (id, title, created_ms, updated_ms) VALUES (1, 't', 1, 1)");
        old.execSQL("INSERT INTO messages (chat_id, role, text, state, created_ms) "
            + "VALUES (1, 'assistant', 'old answer', 'complete', 2)");
        old.setVersion(version);
        old.close();
    }

    private void assertThinkingColumnsWork() {
        List<AssistantMessage> messages = store.messages(1);
        assertEquals(1, messages.size());
        assertEquals("old answer", messages.get(0).text);
        assertNull(messages.get(0).thinking);
        assertNull(messages.get(0).thinkingMs);
        assertTrue(store.setThinking(messages.get(0).id, "hmm", 1500L));
        assertEquals("hmm", store.message(messages.get(0).id).thinking);
        assertEquals(Long.valueOf(1500), store.message(messages.get(0).id).thinkingMs);
        assertTrue(store.setThinking(messages.get(0).id, null, null));
        assertNull(store.message(messages.get(0).id).thinking);
        assertNull(store.message(messages.get(0).id).thinkingMs);
    }

    @Test
    public void versionOneDatabaseUpgradesToThinking() {
        createVersion(1, false);
        assertThinkingColumnsWork();
    }

    @Test
    public void versionTwoDatabaseUpgradesToThinking() {
        createVersion(2, true);
        assertThinkingColumnsWork();
    }

    @Test
    public void freshDatabaseHasThinking() {
        long chat = chatWith("q", "a", 1);
        long id = store.messages(chat).get(1).id;
        assertTrue(store.setThinking(id, "t", 10L));
        assertEquals("t", store.message(id).thinking);
        assertEquals(Long.valueOf(10), store.message(id).thinkingMs);
    }
}
