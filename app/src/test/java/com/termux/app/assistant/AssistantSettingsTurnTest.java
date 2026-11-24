package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.FeatureRequest;
import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
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

/**
 * The Assistant's settings turn through its engine: a fake transport for the model, the real
 * registry on an in-memory store, and the cards the turn leaves.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AssistantSettingsTurnTest {
    private static final SettingDeclaration TEXT_SIZE = SettingDeclarations.TEXT_SIZE;

    private static final class Transport implements ChatTransport {
        interface Script {
            void run(String body, FeatureRequest.Sink sink) throws Exception;
        }

        Script script = (body, sink) -> { };
        final List<String> bodies = new ArrayList<>();

        @NonNull
        @Override
        public Call start(@NonNull String body, @NonNull FeatureRequest.Sink sink) {
            bodies.add(body);
            return new Call() {
                @Override
                public void cancel() {
                }

                @Override
                public void await() throws Exception {
                    script.run(body, sink);
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
    private final List<SettingRefresh> refreshed = new ArrayList<>();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        file = new File(context.getCacheDir(), "settings_turn_" + System.nanoTime() + ".db");
        store = new AssistantChatStore(file);
        transport = new Transport();
        SettingStore memory = new InMemorySettingStore();
        registry = new SettingsRegistry(which -> memory, SettingDeclarations.ALL);
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

    private static FeatureRequest.Event toolCall(String name, String arguments) throws Exception {
        JSONObject call = new JSONObject().put("index", 0).put("id", "call_0").put("type", "function")
            .put("function", new JSONObject().put("name", name).put("arguments", arguments));
        JSONObject chunk = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
            .put("index", 0).put("delta", new JSONObject().put("tool_calls", new JSONArray().put(call)))
            .put("finish_reason", JSONObject.NULL)));
        return FeatureRequest.Event.of(chunk);
    }

    private static FeatureRequest.Event toolCall(int index, String name, String arguments) throws Exception {
        JSONObject call = new JSONObject().put("index", index).put("id", "call_" + index)
            .put("type", "function")
            .put("function", new JSONObject().put("name", name).put("arguments", arguments));
        JSONObject chunk = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
            .put("index", 0).put("delta", new JSONObject().put("tool_calls", new JSONArray().put(call)))
            .put("finish_reason", JSONObject.NULL)));
        return FeatureRequest.Event.of(chunk);
    }

    private SettingsCard ask(String message) {
        long chat = store.createChat(1);
        assertTrue(engine.send(chat, message));
        List<AssistantMessage> all = store.messages(chat);
        AssistantMessage answer = all.get(all.size() - 1);
        assertEquals(AssistantMessage.STATE_COMPLETE, answer.state);
        return cards.get(answer.id);
    }

    private String shown(int bodyIndex) throws Exception {
        return new JSONObject(transport.bodies.get(bodyIndex)).getJSONArray("messages")
            .getJSONObject(0).getString("content");
    }

    private String stored(String id) {
        return registry.get(id);
    }

    private static final String SET_TEXT_UP = "{\"id\":\"text.size\",\"value\":\"up\"}";

    @Test
    public void aProperToolCallBecomesACard() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(toolCall("set_setting", SET_TEXT_UP));
            sink.onEvent(FeatureRequest.Event.text("", "tool_calls"));
        };
        SettingsCard card = ask("make the text bigger");
        assertNotNull(card);
        assertEquals(SettingsCard.Kind.CHANGES, card.kind);
        assertEquals(1, card.items.size());
        assertEquals("Text size", card.items.get(0).title);
        assertEquals("100", card.items.get(0).oldLabel);
        assertEquals("105", card.items.get(0).newLabel);
        assertEquals(SettingsCard.State.PROPOSED, card.state());
    }

    @Test
    public void aLooseTextCallBecomesTheSameCard() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(toolCall("set_setting", SET_TEXT_UP));
            sink.onEvent(FeatureRequest.Event.done());
        };
        SettingsCard proper = ask("make the text bigger");
        String[] loose = {
            "set_setting{id:<|\"|>text.size<|\"|>,value:<|\"|>up<|\"|>}",
            "set_setting(id=\"text.size\", value=\"up\")",
            "set_setting {\"id\": \"text.size\", \"value\": \"up\"}",
            "```json\n{\"name\": \"set_setting\", \"arguments\": {\"id\": \"text.size\", \"value\": \"up\"}}\n```",
        };
        for (String text : loose) {
            transport.script = (body, sink) -> {
                sink.onEvent(FeatureRequest.Event.text(text));
                sink.onEvent(FeatureRequest.Event.done());
            };
            SettingsCard card = ask("make the text bigger");
            assertNotNull(text, card);
            assertEquals(text, proper.summary(), card.summary());
            assertEquals(text, SettingsCard.Kind.CHANGES, card.kind);
        }
    }

    @Test
    public void aCallStreamedInPiecesIsOneCall() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(toolCall("set_setting", "{\"id\":\"text.size\","));
            sink.onEvent(toolCall("", "\"value\":\"up\"}"));
        };
        SettingsCard card = ask("make the text bigger");
        assertEquals(SettingsCard.Kind.CHANGES, card.kind);
        assertEquals("105", card.items.get(0).newLabel);
    }

    @Test
    public void anInventedIdIsDropped() throws Exception {
        transport.script = (body, sink) ->
            sink.onEvent(toolCall("set_setting", "{\"id\":\"text.bigness\",\"value\":\"120\"}"));
        SettingsCard card = ask("make the text bigger");
        assertEquals(SettingsCard.Kind.NO_MATCH, card.kind);
        assertEquals("make the text bigger", card.links.get(0).query);
        assertEquals("100", stored("text.size"));
    }

    @Test
    public void noCallsAndNoTextIsNoMatchingSettingNotAnError() throws Exception {
        transport.script = (body, sink) -> { };
        SettingsCard card = ask("make the text bigger");
        assertEquals(SettingsCard.Kind.NO_MATCH, card.kind);
        assertEquals("make the text bigger", card.links.get(0).query);
    }

    @Test
    public void theNoMatchWordsInTheStringsFileAreTheStoredOnes() {
        android.content.Context context = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        assertEquals(SettingsCard.NO_MATCH_TEXT, context.getString(com.termux.R.string.assistant_card_no_match));
    }

    @Test
    public void anIdOffThisTurnsShortlistIsDropped() throws Exception {
        assertFalse(shortlistIds("make the text bigger").contains("theme.mode"));
        transport.script = (body, sink) ->
            sink.onEvent(toolCall("set_setting", "{\"id\":\"theme.mode\",\"value\":\"dark\"}"));
        SettingsCard card = ask("make the text bigger");
        assertEquals(SettingsCard.Kind.NO_MATCH, card.kind);
        assertEquals("system", stored("theme.mode"));
    }

    @Test
    public void anOutOfRangeValueIsDropped() throws Exception {
        String[] values = {"9999", "79", "huge", ""};
        for (String value : values) {
            transport.script = (body, sink) -> sink.onEvent(toolCall("set_setting",
                "{\"id\":\"text.size\",\"value\":\"" + value + "\"}"));
            SettingsCard card = ask("make the text bigger");
            assertEquals(value, SettingsCard.Kind.NO_MATCH, card.kind);
        }
        assertEquals("100", stored("text.size"));
    }

    @Test
    public void aValidChangeSurvivesNextToAnInvalidOne() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(toolCall(0, "set_setting", "{\"id\":\"nope\",\"value\":\"1\"}"));
            sink.onEvent(toolCall(1, "set_setting", SET_TEXT_UP));
        };
        SettingsCard card = ask("make the text bigger");
        assertEquals(SettingsCard.Kind.CHANGES, card.kind);
        assertEquals(1, card.items.size());
    }

    @Test
    public void upIsOneStepFromWhereTheSettingIs() throws Exception {
        registry.set("text.size", "120");
        transport.script = (body, sink) ->
            sink.onEvent(toolCall("set_setting", "{\"id\":\"text.size\",\"value\":\"down\"}"));
        SettingsCard card = ask("make the text a little smaller");
        assertEquals("120", card.items.get(0).oldLabel);
        assertEquals("115", card.items.get(0).newLabel);
        assertEquals("120", stored("text.size"));
    }

    @Test
    public void twoChangesMakeOneCard() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(toolCall(0, "set_setting", SET_TEXT_UP));
            sink.onEvent(toolCall(1, "set_setting", "{\"id\":\"theme.mode\",\"value\":\"dark\"}"));
        };
        SettingsCard card = ask("make the text bigger and switch to dark mode");
        assertEquals(SettingsCard.Kind.CHANGES, card.kind);
        assertEquals(2, card.items.size());
        assertEquals("Theme: System → Dark", card.items.get(1).line());
    }

    @Test
    public void aThirdChangeIsDropped() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(toolCall(0, "set_setting", SET_TEXT_UP));
            sink.onEvent(toolCall(1, "set_setting", "{\"id\":\"theme.mode\",\"value\":\"dark\"}"));
            sink.onEvent(toolCall(2, "set_setting",
                "{\"id\":\"terminal.contrast\",\"value\":\"harder\"}"));
        };
        SettingsCard card = ask("bigger text, dark mode and harder contrast");
        assertEquals(2, card.items.size());
    }

    @Test
    public void aSettingThatIsNotOfferedIsNeverSentAndShowsAsALink() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(FeatureRequest.Event.done());
        SettingsCard card = ask("stop the glitching");
        String request = transport.bodies.get(0);
        assertFalse(request, request.contains("motion.idle_animations"));
        assertFalse(request, request.contains("Idle animations"));
        assertNotNull(card);
        assertEquals(SettingsCard.Kind.LINKS, card.kind);
        assertEquals("Idle animations", card.links.get(0).title);
        assertEquals("Idle animations", card.links.get(0).query);
    }

    @Test
    public void aModelThatNamesAnUnofferedSettingChangesNothing() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(toolCall("set_setting",
            "{\"id\":\"motion.idle_animations\",\"value\":\"false\"}"));
        SettingsCard card = ask("stop the glitching");
        assertTrue(card.kind != SettingsCard.Kind.CHANGES);
        assertEquals("false", stored("motion.idle_animations"));
    }

    private SettingsCard askWithText(String text, String message) {
        transport.script = (body, sink) -> {
            sink.onEvent(FeatureRequest.Event.text(text));
            sink.onEvent(FeatureRequest.Event.done());
        };
        return ask(message);
    }

    @Test
    public void aCallWrittenOnALineBecomesTheSameCard() {
        String[] lines = {"set_setting text.size up", "set_setting text.size \"up\"", "set_setting text.size 105",
            "  - set_setting `text.size` 'up'"};
        for (String text : lines) {
            SettingsCard card = askWithText(text, "make the text bigger");
            assertNotNull(text, card);
            assertEquals(text, "Text size: 100 → 105", card.summary());
        }
        SettingsCard off = askWithText("set_setting motion.animation_speed \"off\"", "turn off animations");
        assertTrue(off.summary(), off.summary().startsWith("Animation speed: Normal → Off"));
        SettingsCard smaller = askWithText("set_setting text.size 95", "make it a bit smaller");
        assertTrue(smaller.summary(), smaller.summary().startsWith("Text size: 100 → 95"));
    }

    @Test
    public void twoCallLinesMakeOneCardAndAThirdIsDropped() {
        SettingsCard two = askWithText("set_setting text.size up\nset_setting theme.mode dark",
            "bigger text and dark mode");
        assertEquals(2, two.items.size());
        assertEquals("Text size", two.items.get(0).title);
        assertEquals("Dark", two.items.get(1).newLabel);
        SettingsCard three = askWithText(
            "set_setting text.size up\nset_setting theme.mode dark\nset_setting motion.animation_speed off",
            "bigger text and dark mode and no animation");
        assertEquals(2, three.items.size());
    }

    @Test
    public void aCallLineOffTheShortlistOrOutOfRangeIsDropped() {
        String[] bad = {"set_setting motion.animation_speed \"faster\"", "set_setting text.size 900",
            "set_setting nonsense.id up"};
        for (String text : bad) {
            SettingsCard card = askWithText(text, "make the text bigger");
            assertTrue(text, card == null || card.kind != SettingsCard.Kind.CHANGES);
        }
    }

    @Test
    public void anAskUserLineIsTheQuestion() {
        long chat = store.createChat(1);
        transport.script = (body, sink) -> sink.onEvent(FeatureRequest.Event.text(
            "ask_user \"Bigger in the terminal or everywhere?\""));
        engine.send(chat, "make the text bigger");
        List<AssistantMessage> all = store.messages(chat);
        assertEquals("Bigger in the terminal or everywhere?", all.get(1).text);
    }

    @Test
    public void aSettingsIdInShownTextIsItsTitle() {
        AssistantSettingsTurn.Shortlist list = AssistantSettingsTurn.lookup(registry, "show the weather");
        assertNotNull(list.find("status.weather"));
        String said = AssistantSettingsTurn.titled(list,
            "Do you want to turn on or off `status.weather`? Or status.weather, or text.size.");
        assertFalse(said, said.contains("status.weather"));
        assertTrue(said, said.startsWith("Do you want to turn on or off Weather in the status bar? Or "));
        long chat = store.createChat(1);
        transport.script = (body, sink) -> sink.onEvent(FeatureRequest.Event.text(
            "Do you want to turn on or off `status.weather`?"));
        engine.send(chat, "show the weather");
        assertEquals("Do you want to turn on or off Weather in the status bar?",
            store.messages(chat).get(1).text);
    }

    @Test
    public void thePlainChatTextIsKept() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(FeatureRequest.Event.text("It is sunny."));
        long chat = store.createChat(1);
        engine.send(chat, "what is the weather today");
        List<AssistantMessage> all = store.messages(chat);
        assertEquals("It is sunny.", all.get(1).text);
        assertNull(cards.get(all.get(1).id));
    }

    @Test
    public void aQuestionIsKeptAsText() throws Exception {
        transport.script = (body, sink) ->
            sink.onEvent(toolCall("ask_user", "{\"question\":\"Bigger in the terminal or everywhere?\"}"));
        long chat = store.createChat(1);
        engine.send(chat, "make the text bigger");
        List<AssistantMessage> all = store.messages(chat);
        assertEquals("Bigger in the terminal or everywhere?", all.get(1).text);
        assertNull(cards.get(all.get(1).id));
    }

    @Test
    public void theRequestCarriesOnlyTheShortlist() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(FeatureRequest.Event.text("ok"));
        long chat = store.createChat(1);
        engine.send(chat, "make the text bigger");
        JSONObject body = new JSONObject(transport.bodies.get(0));
        JSONArray tools = body.getJSONArray("tools");
        JSONArray ids = tools.getJSONObject(0).getJSONObject("function").getJSONObject("parameters")
            .getJSONObject("properties").getJSONObject("id").getJSONArray("enum");
        assertTrue(ids.length() <= AssistantSettingsTurn.SHORTLIST);
        assertEquals("text.size", ids.getString(0));
        String system = shown(0);
        assertTrue(system, system.contains("text.size | Text size"));
        assertTrue(system, system.contains("now: 100"));
        assertTrue(system, system.contains("number 80 to 150"));
        assertFalse(system, system.contains("theme.mode"));
    }

    @Test
    public void nothingChangesUntilApplyAndApplyGoesThroughTheRegistryAndTheApplier() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(toolCall("set_setting", SET_TEXT_UP));
        long chat = store.createChat(1);
        engine.send(chat, "make the text bigger");
        long answer = store.messages(chat).get(1).id;
        assertEquals("100", stored("text.size"));
        assertTrue(refreshed.isEmpty());

        SettingsApplier applier = new SettingsApplier(new SettingsApplier.Host() {
            @Override
            public boolean isLauncherResumed() {
                return true;
            }

            @Override
            public void run(@NonNull SettingRefresh refresh) {
                refreshed.add(refresh);
            }
        });
        assertTrue(cards.apply(answer, registry, applier));
        assertEquals("105", stored("text.size"));
        assertEquals(SettingRefresh.TEXT_SIZE, refreshed.get(0));
        assertEquals(SettingsCard.State.APPLIED, cards.get(answer).state());
        assertFalse("a second tap writes nothing", cards.apply(answer, registry, applier));
        assertEquals(1, refreshed.size());
    }

    @Test
    public void aCardsWordsAreProductCopy() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(toolCall("set_setting", SET_TEXT_UP));
        SettingsCard card = ask("make the text bigger");
        String shown = card.summary();
        assertFalse(shown, shown.contains("text.size"));
        assertFalse(shown.toLowerCase().contains("gemma"));
        assertEquals("Text size: 100 → 105", shown);
    }

    @Test
    public void theKeywordLookupFindsAProblemInThePersonsWords() {
        List<String> ids = shortlistIds("my eyes hurt at night");
        assertTrue(ids.toString(), ids.contains("theme.mode"));
        assertTrue(ids.toString(), ids.contains("terminal.contrast"));
        assertTrue(shortlistIds("make the text bigger").get(0).equals("text.size"));
        assertTrue(shortlistIds("zzzz qqqq").isEmpty());
    }

    private List<String> shortlistIds(String message) {
        List<String> ids = new ArrayList<>();
        for (AssistantSettingsTurn.Candidate c : AssistantSettingsTurn.lookup(registry, message).offered) {
            ids.add(c.declaration.id);
        }
        return ids;
    }
}
