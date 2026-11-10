package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.app.Application;
import org.robolectric.annotation.Config;

/** The chat's views lay out and bind every row type without throwing. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AssistantChatViewTest {

    private static final AssistantThreadAdapter.Actions NO_ACTIONS = new AssistantThreadAdapter.Actions() {
        @Override public void copy(@NonNull String text) { }
        @Override public void share(@NonNull String text) { }
        @Override public void retry(long messageId) { }
        @Override public void errorAction(@NonNull AssistantRows.Row row) { }
        @Override public void useE2b(@NonNull AssistantRows.Row row) { }
    };

    private static Context themed() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
    }

    private static AssistantMessage user(long id, String text) {
        return new AssistantMessage(id, 1, AssistantMessage.ROLE_USER, text, AssistantMessage.STATE_COMPLETE, null, id);
    }

    private static AssistantMessage answer(long id, String text, String state, String error) {
        return new AssistantMessage(id, 1, AssistantMessage.ROLE_ASSISTANT, text, state, error, id);
    }

    private static void layout(View view, int widthPx, int heightPx) {
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, widthPx, heightPx);
    }

    @Test
    public void everyRowTypeBindsAndTheMarkdownAnswerBecomesViews() {
        Context context = themed();
        AssistantUi.Palette palette = AssistantUi.Palette.read(new View(context));
        AssistantChatView chat = new AssistantChatView(context, palette, NO_ACTIONS);
        List<AssistantMessage> messages = new ArrayList<>(Arrays.asList(
            user(1, "How do I rename photos?"),
            answer(2, "Use **exiftool**:\n\n1. Install it\n- bullet\n```sh\nexiftool -d '%Y' .\n```\nDone.",
                AssistantMessage.STATE_COMPLETE, null),
            user(3, "And videos?"),
            answer(4, "It stopped half wa", AssistantMessage.STATE_STOPPED, null),
            user(5, "Again"),
            answer(6, "partial", AssistantMessage.STATE_ERROR, AssistantMessage.ERROR_RUNTIME_DIED),
            user(7, "held question"),
            answer(8, "", AssistantMessage.STATE_WAITING, null)));
        AssistantRows.Context rc = new AssistantRows.Context();
        rc.sending = Arrays.asList("just sent");
        List<AssistantRows.Row> rows = AssistantRows.build(messages, 2, rc);
        chat.adapter().submit(rows);
        chat.setTitle("Rename photos");
        chat.setChip(AssistantModelPresenter.Chip.READY, "E2B");
        chat.setRecent(Arrays.asList(new AssistantChatView.Recent(1, "A chat", "Today")));
        layout(chat, 1080, 2000);
        // Every row type is in the list the adapter holds, and the list laid out some of them.
        assertEquals(rows.size(), chat.adapter().getItemCount());
        assertTrue(chat.thread().getChildCount() > 0);
        chat.setChip(AssistantModelPresenter.Chip.NO_MODEL, "");
        chat.composer().setListening(true);
        chat.composer().setLevel(0.8f);
        chat.composer().setListening(false);
        chat.composer().insertDictation("hello there");
        assertEquals("hello there", chat.composer().text());
        chat.composer().insertDictation("again");
        assertEquals("hello there again", chat.composer().text());
    }

    @Test
    public void aMarkdownAnswerHasAParagraphAListAndAScrollableCodeBlock() {
        Context context = themed();
        AssistantUi.Palette palette = AssistantUi.Palette.read(new View(context));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        AssistantMarkdownViews.bind(column, AssistantMarkdown.parse("Intro\n\n- one\n- two\n```sh\nls -l\n```"),
            palette, code -> { });
        assertEquals(4, column.getChildCount());
        View code = column.getChildAt(3);
        TextView copy = AssistantMarkdownViews.copyLabel(code);
        assertTrue(copy != null && copy.getText().length() > 0);
        // Streaming more text reuses the same views.
        View first = column.getChildAt(0);
        AssistantMarkdownViews.bind(column, AssistantMarkdown.parse("Intro more\n\n- one\n- two\n```sh\nls -l\ncd .."),
            palette, c -> { });
        assertTrue(first == column.getChildAt(0));
        assertEquals(4, column.getChildCount());
    }

    @Test
    public void theComposerSendsOnlyWithText() {
        Context context = themed();
        AssistantUi.Palette palette = AssistantUi.Palette.read(new View(context));
        AssistantComposer composer = new AssistantComposer(context, palette);
        final List<String> sent = new ArrayList<>();
        composer.setListener(new AssistantComposer.Listener() {
            @Override public void onSend(@NonNull String text) { sent.add(text); }
            @Override public void onStop() { sent.add("stop"); }
            @Override public void onMic() { }
            @Override public void onCancelDictation() { }
            @Override public void onDoneDictation() { }
            @Override public void onFieldFocused(boolean focused) { }
        });
        layout(composer, 1000, 300);
        composer.setText("   ");
        composer.setGenerating(true);
        composer.setGenerating(false);
        assertEquals(0, sent.size());
    }

    @Test
    public void answerStatusShowsTheStageAndHidesWhenNone() {
        Context context = themed();
        AssistantUi.Palette palette = AssistantUi.Palette.read(new View(context));
        AssistantChatView chat = new AssistantChatView(context, palette, NO_ACTIONS);
        chat.setAnswerStatus(AssistantModelPresenter.Stage.LOADING_MODEL, "Gemma 3n E2B");
        layout(chat, 1080, 2000);
        assertEquals("Loading Gemma 3n E2B", visibleText(chat, "Loading Gemma 3n E2B"));
        chat.setAnswerStatus(AssistantModelPresenter.Stage.THINKING, "Gemma 3n E2B");
        assertEquals("Thinking", visibleText(chat, "Thinking"));
        chat.setAnswerStatus(AssistantModelPresenter.Stage.NONE, "Gemma 3n E2B");
        assertEquals(null, visibleText(chat, "Thinking"));
    }

    private static String visibleText(View root, String wanted) {
        if (root.getVisibility() != View.VISIBLE) return null;
        if (root instanceof TextView && wanted.contentEquals(((TextView) root).getText())) return wanted;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                String r = visibleText(g.getChildAt(i), wanted);
                if (r != null) return r;
            }
        }
        return null;
    }
}
