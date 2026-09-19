package com.termux.app.help;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.termux.R;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.interact.ShareUtils;

/**
 * The help centre, as its own screen. Everything the reader reads — help home, search, the
 * glossary, a term, one topic with its clip — is the same {@link HelpPanelView} the sheet used,
 * hosted under an ordinary toolbar: Up, the page's title, and Search.
 *
 * <p>It is a reading screen and nothing else. It measures no control, borrows no keyboard and
 * changes nothing about the launcher: the three things that do happen on the launcher — explore a
 * screen, show one control, practise a lesson — finish this screen with an action in its result,
 * and {@code TermuxActivity} runs them and brings the reader back to the page they left.
 */
public final class HelpActivity extends AppCompatActivity {

    /** Which place the reader came from, as a {@link PaneWallPage} name; absent = TERMINAL. */
    public static final String EXTRA_PLACE = "com.termux.app.help.extra.PLACE";
    /** The topic to open on, and the topic an action in the result is about; absent = home. */
    public static final String EXTRA_TOPIC = "com.termux.app.help.extra.TOPIC";
    /** A page stack from {@link HelpNavigation#saveState()}, in and out. */
    public static final String EXTRA_NAVIGATION = "com.termux.app.help.extra.NAVIGATION";
    /** What the reader asked the launcher for, in the result: one of the four actions below. */
    public static final String EXTRA_ACTION = "com.termux.app.help.extra.ACTION";
    /** The lesson {@link #ACTION_PRACTICE} is about. */
    public static final String EXTRA_LESSON = "com.termux.app.help.extra.LESSON";

    /** Explore the whole screen the reader came from. */
    public static final String ACTION_EXPLORE = "explore";
    /** Point at one control on that screen. */
    public static final String ACTION_SHOW_ON_SCREEN = "show_on_screen";
    /** Point at it and play its gesture. */
    public static final String ACTION_SHOW_GESTURE = "show_gesture";
    /** Hand the reader over to a practice lesson. */
    public static final String ACTION_PRACTICE = "practice";

    private static final String STATE_NAVIGATION = "help_navigation";
    private static final int MENU_SEARCH = 1;

    /** The whole guide, opened on a place; a topic id opens that page, null opens home. */
    public static Intent intent(Context context, @Nullable PaneWallPage place,
                                @Nullable String topicId, @Nullable Bundle navigation) {
        Intent intent = new Intent(context, HelpActivity.class);
        if (place != null) intent.putExtra(EXTRA_PLACE, place.name());
        if (topicId != null) intent.putExtra(EXTRA_TOPIC, topicId);
        if (navigation != null) intent.putExtra(EXTRA_NAVIGATION, navigation);
        return intent;
    }

    private final HelpNavigation navigation = new HelpNavigation();
    private HelpPanelView panel;
    private Toolbar toolbar;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle saved = savedInstanceState == null ? null
            : savedInstanceState.getBundle(STATE_NAVIGATION);
        if (saved == null) saved = getIntent() == null ? null
            : getIntent().getBundleExtra(EXTRA_NAVIGATION);
        if (!navigation.restoreState(saved)) openFromIntent();

        HelpStyle style = HelpStyle.of(this, navigation.place());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(style.pageColor());
        root.setFitsSystemWindows(true);

        toolbar = new Toolbar(this);
        toolbar.setBackgroundColor(style.pageColor());
        toolbar.setTitleTextColor(style.textColor());
        toolbar.setNavigationContentDescription(R.string.help_back_action);
        root.addView(toolbar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        panel = new HelpPanelView(this);
        panel.setChrome(HelpPanelView.Chrome.SCREEN);
        panel.setListener(listener);
        root.addView(panel, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tintNavigationIcon(style);
        panel.show();
        render();
    }

    /** What the Intent asks for: one topic, or the whole guide from its home page. */
    private void openFromIntent() {
        Intent intent = getIntent();
        PaneWallPage place = place(intent == null ? null : intent.getStringExtra(EXTRA_PLACE));
        String topicId = intent == null ? null : intent.getStringExtra(EXTRA_TOPIC);
        if (topicId != null && HelpTopics.entry(topicId) != null) navigation.openTopic(place, topicId);
        else navigation.open(place);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(STATE_NAVIGATION, navigation.saveState());
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem search = menu.add(Menu.NONE, MENU_SEARCH, Menu.NONE,
            R.string.help_search_field_hint);
        search.setIcon(android.R.drawable.ic_menu_search);
        search.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        tintMenuIcon(search);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SEARCH) {
            openSearch();
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            // Up is Back: the pages are a stack, and at the root of it help is done.
            goBack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override public void onBackPressed() {
        goBack();
    }

    /** An open definition first, then one page, then the screen itself. */
    private void goBack() {
        if (navigation.frame().openTermId != null) {
            navigation.closeTerm();
            render();
            return;
        }
        if (navigation.back()) {
            render();
            return;
        }
        finish();
    }

    private void openSearch() {
        navigation.search();
        render();
        panel.focusSearch();
    }

    private void render() {
        panel.render(navigation, true);
        if (toolbar != null) toolbar.setTitle(panel.pageTitle());
    }

    /**
     * The three things that only the launcher can do. The screen finishes with what was asked for
     * and the page stack it was asked from, and the launcher brings the reader back to it.
     */
    private void finishWith(String action, @Nullable String topicId, @Nullable String lessonId) {
        Intent data = new Intent();
        data.putExtra(EXTRA_ACTION, action);
        if (topicId != null) data.putExtra(EXTRA_TOPIC, topicId);
        if (lessonId != null) data.putExtra(EXTRA_LESSON, lessonId);
        data.putExtra(EXTRA_NAVIGATION, navigation.saveState());
        setResult(RESULT_OK, data);
        finish();
    }

    private final HelpPanelView.Listener listener = new HelpPanelView.Listener() {
        @Override public void onBack() { goBack(); }

        @Override public void onClose() { finish(); }

        @Override public void onHome() {
            navigation.home();
            render();
        }

        @Override public void onSearch() { openSearch(); }

        @Override public void onQueryChanged(String text) {
            navigation.setQuery(text);
            // Only the results move; the field keeps its caret and the keyboard stays up.
            panel.renderList(navigation);
        }

        @Override public void onTextEntry(EditText field) {
            // The system keyboard, asked for plainly: nothing here borrows the launcher's.
            InputMethodManager manager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }

        @Override public void onTopic(String topicId) {
            // A row may name a topic by the target id the explorer knows it as; one lookup
            // answers both, and by id alone so every topic reads on every place.
            HelpTopics.Entry entry = HelpTopics.entry(topicId);
            navigation.topic(entry == null ? topicId : entry.id);
            render();
        }

        @Override public void onGlossary() {
            navigation.glossary();
            render();
        }

        @Override public void onTerm(String termId) {
            if (termId != null && termId.equals(navigation.frame().openTermId))
                navigation.closeTerm();
            else navigation.openTerm(termId);
            render();
        }

        @Override public void onExplore() { finishWith(ACTION_EXPLORE, null, null); }

        @Override public void onShowOnScreen(String topicId) {
            finishWith(ACTION_SHOW_ON_SCREEN, topicId, null);
        }

        @Override public void onShowGesture(String topicId) {
            finishWith(ACTION_SHOW_GESTURE, topicId, null);
        }

        @Override public void onTryIt(@Nullable String topicId, String lessonId) {
            if (lessonId == null) return;
            finishWith(ACTION_PRACTICE, topicId, lessonId);
        }

        @Override public void onLink(String url) { ShareUtils.openUrl(HelpActivity.this, url); }

        @Override public void onScroll(int y) { navigation.setScroll(y); }
    };

    private static PaneWallPage place(@Nullable String name) {
        if (name == null) return PaneWallPage.TERMINAL;
        for (PaneWallPage page : PaneWallPage.values()) if (page.name().equals(name)) return page;
        return PaneWallPage.TERMINAL;
    }

    /** The toolbar is dressed from the launcher's own colours, so its glyphs are dressed too. */
    private void tintNavigationIcon(HelpStyle style) {
        Drawable icon = toolbar.getNavigationIcon();
        if (icon != null) icon.setColorFilter(style.textColor(), PorterDuff.Mode.SRC_IN);
    }

    private void tintMenuIcon(MenuItem item) {
        Drawable icon = item.getIcon();
        if (icon == null) return;
        icon.setColorFilter(HelpStyle.of(this, navigation.place()).textColor(),
            PorterDuff.Mode.SRC_IN);
    }

    // ---- for the tests -----------------------------------------------------------------------

    @VisibleForTesting
    HelpPanelView panel() {
        return panel;
    }

    @VisibleForTesting
    HelpNavigation navigation() {
        return navigation;
    }

    @VisibleForTesting
    String title() {
        CharSequence title = toolbar == null ? null : toolbar.getTitle();
        return title == null ? "" : title.toString();
    }

    @VisibleForTesting
    View toolbarSearch() {
        return toolbar == null ? null : toolbar.findViewById(MENU_SEARCH);
    }
}
