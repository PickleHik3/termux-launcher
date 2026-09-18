package com.termux.app.help;

import android.content.Context;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.WindowInsetsCompat;
import com.termux.R;
import com.termux.app.tour.TourGesture;
import com.termux.app.wall.PaneWallPage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The help centre the reader reads: one solid panel over a scrim, with a pinned header and a
 * scrolling body. Help home, search, the glossary and one topic are the four pages; which of them
 * is showing, what was typed and where the body is scrolled to are all {@link HelpNavigation}'s,
 * and this draws that frame and reports what was tapped.
 *
 * <p>The panel is a document: it may cover launcher controls, a tap outside it changes nothing,
 * and nothing typed into it reaches the terminal underneath. The only thing it measures for itself
 * is its own width — bounded and centred on a wide screen, inset within the system bars on a tall
 * one — and the keyboard's inset, which shortens the body rather than the header.
 */
public final class HelpPanelView extends FrameLayout {

    /** Everything the reader can ask for from a page. The controller answers all of it. */
    public interface Listener {
        void onBack();
        void onClose();
        void onHome();
        void onSearch();
        void onQueryChanged(String text);
        /** The field wants the configured keyboard; the controller owns the input hand-off. */
        void onTextEntry(EditText field);
        void onTopic(String topicId);
        void onGlossary();
        /** Expand a term where it stands, or collapse the one already open. */
        void onTerm(String termId);
        void onExplore();
        void onShowOnScreen(String topicId);
        void onShowGesture(String topicId);
        /** A lesson to hand over, and the topic to come back to; a bare lesson passes null. */
        void onTryIt(@Nullable String topicId, String lessonId);
        void onLink(String url);
        void onScroll(int y);
    }

    /** Where the deeper pages live — the base the display guide's own link already uses. */
    @VisibleForTesting
    static final String DOCS_BASE = "https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/";
    @VisibleForTesting
    static final String DOCS_INDEX = "https://github.com/PickleHik3/termux-launcher/tree/dev/docs";
    @VisibleForTesting
    static final String SUPPORT = "https://github.com/PickleHik3/termux-launcher/issues";

    private final LinearLayout panel;
    private final LinearLayout header;
    private final ScrollView scroll;
    private final LinearLayout body;
    /** The list under a field, rebuilt on every keystroke while the field above it stays put. */
    private final LinearLayout list;
    /** The two text fields outlive a render, so typing keeps its caret and its keyboard. */
    private EditText searchField;
    private EditText filterField;
    /** Named views of the last render, so a reader's finger and a test find the same thing. */
    private final Map<String, View> named = new LinkedHashMap<>();

    private HelpStyle style;
    private HelpTopics.Text text;
    private Listener listener;
    /** The stack the last render drew, so the page can redraw itself when only it changed. */
    private HelpNavigation navigation;
    private PaneWallPage place = PaneWallPage.TERMINAL;
    private Set<String> measured = Collections.emptySet();
    private boolean practiceAvailable;
    /** Help home's practice list, open where it stands; not a page, so not a navigation frame. */
    private boolean practiceListOpen;
    /** The glossary's own filter. It is not the search query and does not outlive the page. */
    private String termFilter = "";
    private final Rect insets = new Rect();
    private int panelWidth = -1;

    public HelpPanelView(Context context) {
        super(context);
        style = HelpStyle.of(context, place);
        text = HelpSearch.text(context);
        setBackgroundColor(style.scrimColor());
        setClickable(true);
        setFocusable(true);
        // In touch mode a plain focusable is refused focus, and the strokes of a hardware keyboard
        // would go on reaching the terminal underneath while help is up.
        setFocusableInTouchMode(true);
        setContentDescription(context.getString(R.string.help_accessibility));
        // Above every control it covers, like the guide it replaces: the dock, the keys row and
        // the keyboard are all lifted, and a document has to wash over all of them.
        setElevation(style.dp(56));
        setTranslationZ(style.dp(56));
        setOutlineProvider(null);

        panel = style.column();
        panel.setBackground(style.panelBackground());
        panel.setPadding(style.dp(14), style.dp(10), style.dp(14), style.dp(10));
        panel.setClickable(true);
        panel.setFocusable(true);
        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
            Gravity.CENTER_HORIZONTAL));

        header = style.row();
        panel.addView(header, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        body = style.column();
        scroll.addView(body, new FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        list = style.column();

        scroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (listener != null && isShowing()) listener.onScroll(scroll.getScrollY());
        });
        setVisibility(GONE);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void show() {
        setVisibility(VISIBLE);
        bringToFront();
        requestFocus();
        // Asked for rather than waited on: the keyboard's inset is what the body has to clear.
        requestApplyInsets();
    }

    public void hide() {
        setVisibility(GONE);
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    // ---- rendering ---------------------------------------------------------------------------

    /**
     * Draw the frame on top of the navigation stack.
     *
     * @param measuredTargetIds the target ids measured on this pass; a topic whose control is not
     *                          among them still reads, with the line that says where it went.
     */
    public void render(HelpNavigation navigation, @Nullable Set<String> measuredTargetIds,
                       boolean practiceAvailable) {
        this.navigation = navigation;
        this.place = navigation.place();
        this.measured = measuredTargetIds == null ? Collections.<String>emptySet() : measuredTargetIds;
        this.practiceAvailable = practiceAvailable;
        // Re-read every pass: the dress and the accent move with the theme and the place.
        style = HelpStyle.of(getContext(), place);
        text = HelpSearch.text(getContext());
        setBackgroundColor(style.scrimColor());
        panel.setBackground(style.panelBackground());
        named.clear();
        header.removeAllViews();
        body.removeAllViews();
        list.removeAllViews();
        detach(list);

        HelpNavigation.Frame frame = navigation.frame();
        buildHeader(frame);
        switch (frame.screen) {
            case SEARCH:
                searchPage(navigation);
                break;
            case GLOSSARY:
                glossaryPage();
                break;
            case TOPIC:
                topicPage(frame);
                break;
            default:
                homePage();
                break;
        }
        final int y = frame.scroll;
        scroll.scrollTo(0, y);
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    /** A keystroke: only the list under the field, so the caret and the keyboard stay where they are. */
    public void renderList(HelpNavigation navigation) {
        list.removeAllViews();
        if (navigation.screen() == HelpNavigation.Screen.SEARCH) results(navigation.query());
        else if (navigation.screen() == HelpNavigation.Screen.GLOSSARY) terms();
    }

    private void buildHeader(HelpNavigation.Frame frame) {
        boolean linkedTopic = frame.screen == HelpNavigation.Screen.TOPIC
            && navigation != null && navigation.depth() == 1;
        if (linkedTopic) {
            // A topic opened by a Learn more link has nothing under it: Back and Close both
            // return the reader to what they were doing, so the header's own control is the one
            // that leads further into help.
            header.addView(add(string(R.string.help_home_action), style.headerButton(
                string(R.string.help_home_label), string(R.string.help_home_action),
                () -> { if (listener != null) listener.onHome(); })));
        } else if (frame.screen != HelpNavigation.Screen.HOME) {
            header.addView(add(string(R.string.help_back_action), style.headerButton(
                string(R.string.help_back_glyph), string(R.string.help_back_action),
                () -> { if (listener != null) listener.onBack(); })));
        }
        header.addView(style.title(headerTitle(frame)), style.filling());
        if (frame.screen == HelpNavigation.Screen.TOPIC
            || frame.screen == HelpNavigation.Screen.GLOSSARY) {
            header.addView(add(string(R.string.help_search_field_hint), style.headerButton(
                string(R.string.help_search_label), string(R.string.help_search_field_hint),
                () -> { if (listener != null) listener.onSearch(); })),
                style.beside(style.dp(4)));
        }
        header.addView(add(string(R.string.help_close_action), style.headerButton(
            string(R.string.help_close_glyph), string(R.string.help_close_action),
            () -> { if (listener != null) listener.onClose(); })),
            style.beside(style.dp(4)));
    }

    private String headerTitle(HelpNavigation.Frame frame) {
        if (frame.screen == HelpNavigation.Screen.TOPIC) {
            HelpTopics.Entry entry = HelpTopics.entry(place, frame.id);
            if (entry != null) return text.get(entry.titleRes);
        }
        if (frame.screen == HelpNavigation.Screen.GLOSSARY) return string(R.string.help_home_glossary);
        return string(R.string.help_centre_title);
    }

    // ---- help home ---------------------------------------------------------------------------

    private void homePage() {
        body.addView(add(string(R.string.help_search_field_hint),
            style.fieldButton(string(R.string.help_search_field_hint),
                () -> { if (listener != null) listener.onSearch(); })), style.stacked(style.dp(6)));

        List<HelpTopics.Entry> here = HelpTopics.onScreen(place, measured);
        body.addView(style.sectionLabel(
            getContext().getString(R.string.help_home_on_this_screen, placeName())));
        for (HelpTopics.Entry entry : here) body.addView(topicRow(entry, null), style.stacked(style.dp(6)));
        body.addView(add(string(R.string.help_home_explore),
            style.button(string(R.string.help_home_explore), true,
                () -> { if (listener != null) listener.onExplore(); })), style.stacked(style.dp(10)));

        body.addView(style.sectionLabel(string(R.string.help_home_browse)));
        int section = 0;
        for (HelpTopics.Group group : HelpTopics.Group.values()) {
            List<HelpTopics.Entry> topics = HelpTopics.inGroup(group);
            if (topics.isEmpty()) continue;
            if (group.sectionRes != group.labelRes && group.sectionRes != section)
                body.addView(style.sectionLabel(text.get(group.sectionRes)));
            section = group.sectionRes;
            body.addView(style.groupLabel(text.get(group.labelRes)));
            for (HelpTopics.Entry entry : topics)
                body.addView(topicRow(entry, null), style.stacked(style.dp(6)));
        }

        body.addView(style.divider(), style.hairline(style.dp(14)));
        body.addView(add(string(R.string.help_home_glossary),
            style.link(string(R.string.help_home_glossary),
                () -> { if (listener != null) listener.onGlossary(); })));
        body.addView(add(string(R.string.help_home_practice),
            style.link(string(R.string.help_home_practice), () -> {
                practiceListOpen = !practiceListOpen;
                if (navigation != null) render(navigation, measured, practiceAvailable);
            })));
        if (practiceListOpen) practiceList();
        body.addView(add(string(R.string.help_docs_link),
            style.link(string(R.string.help_docs_link),
                () -> { if (listener != null) listener.onLink(DOCS_INDEX); })));
        body.addView(add(string(R.string.help_support_link),
            style.link(string(R.string.help_support_link),
                () -> { if (listener != null) listener.onLink(SUPPORT); })));
    }

    /** The four lessons, each with the sentence that says what it teaches, and Start. */
    private void practiceList() {
        for (String lessonId : HelpTopics.LESSON_IDS) {
            int titleRes = lessonTitle(lessonId);
            int summaryRes = lessonSummary(lessonId);
            if (titleRes == 0) continue;
            LinearLayout row = style.row();
            row.addView(style.row(string(titleRes), string(summaryRes), null, true, null),
                style.filling());
            final String lesson = lessonId;
            row.addView(add(string(titleRes), style.button(string(R.string.help_practice_start),
                practiceAvailable, () -> {
                    if (listener != null) listener.onTryIt(null, lesson);
                })), style.beside(style.dp(6)));
            body.addView(row, style.stacked(style.dp(6)));
        }
    }

    // ---- search ------------------------------------------------------------------------------

    /**
     * The search page's field takes the caret and asks for the keyboard at once: the reader who
     * tapped "Search help" has already said they want to type, and should not have to say it twice.
     */
    public void focusSearch() {
        if (searchField == null || searchField.getParent() == null) return;
        searchField.requestFocus();
        if (listener != null) listener.onTextEntry(searchField);
    }

    private void searchPage(HelpNavigation navigation) {
        if (searchField == null) {
            searchField = style.field(string(R.string.help_search_field_hint));
            watch(searchField);
        }
        detach(searchField);
        if (!searchField.getText().toString().equals(navigation.query()))
            searchField.setText(navigation.query());
        body.addView(add(string(R.string.help_search_field_hint), searchField),
            style.stacked(style.dp(6)));
        body.addView(list, style.stacked(style.dp(6)));
        results(navigation.query());
    }

    private void results(String query) {
        if (query == null || query.trim().isEmpty()) {
            list.addView(style.sectionLabel(string(R.string.help_search_suggested)));
            for (HelpTopics.Entry entry : HelpTopics.onScreen(place, measured))
                list.addView(topicRow(entry, null), style.stacked(style.dp(6)));
            return;
        }
        List<HelpSearch.Result> found = HelpSearch.search(query, place, text);
        if (found.isEmpty()) {
            list.addView(style.body(string(R.string.help_search_empty)));
            list.addView(add(string(R.string.help_home_browse),
                style.link(string(R.string.help_home_browse),
                    () -> { if (listener != null) listener.onHome(); })));
            list.addView(add(string(R.string.help_support_link),
                style.link(string(R.string.help_support_link),
                    () -> { if (listener != null) listener.onLink(SUPPORT); })));
            return;
        }
        for (HelpSearch.Result result : found) {
            final HelpSearch.Result found1 = result;
            list.addView(add(result.title, style.row(result.title, result.excerpt,
                kindLabel(result.kind), true, () -> open(found1))), style.stacked(style.dp(6)));
        }
    }

    private void open(HelpSearch.Result result) {
        if (listener == null) return;
        if (result.kind != HelpSearch.Kind.TERM) {
            listener.onTopic(result.id);
            return;
        }
        // A term's own answer is already in the result; its topic is where it is used.
        HelpGlossary.Term term = HelpGlossary.term(result.id);
        if (term != null && term.topicId != null) listener.onTopic(term.topicId);
        else listener.onGlossary();
    }

    private String kindLabel(HelpSearch.Kind kind) {
        if (kind == HelpSearch.Kind.TERM) return string(R.string.help_search_kind_term);
        if (kind == HelpSearch.Kind.FIX) return string(R.string.help_search_kind_fix);
        return string(R.string.help_search_kind_guide);
    }

    // ---- glossary ----------------------------------------------------------------------------

    private void glossaryPage() {
        if (filterField == null) {
            filterField = style.field(string(R.string.help_glossary_filter_hint));
            filterField.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    termFilter = s.toString();
                    list.removeAllViews();
                    terms();
                }
            });
            focusHandOff(filterField);
        }
        detach(filterField);
        body.addView(add(string(R.string.help_glossary_filter_hint), filterField),
            style.stacked(style.dp(6)));
        body.addView(list, style.stacked(style.dp(6)));
        terms();
    }

    private void terms() {
        String needle = termFilter == null ? "" : termFilter.trim().toLowerCase();
        for (HelpGlossary.Term term : HelpGlossary.alphabetical(text)) {
            String title = text.get(term.titleRes);
            String definition = text.get(term.definitionRes);
            if (!needle.isEmpty() && !title.toLowerCase().contains(needle)
                && !definition.toLowerCase().contains(needle)) continue;
            HelpTopics.Entry topic = term.topicId == null ? null : HelpTopics.entry(term.topicId);
            final String topicId = topic == null ? null : topic.id;
            list.addView(add(title, style.row(title, definition, null, true, () -> {
                if (listener != null && topicId != null) listener.onTopic(topicId);
            })), style.stacked(style.dp(6)));
        }
    }

    // ---- one topic ---------------------------------------------------------------------------

    private void topicPage(HelpNavigation.Frame frame) {
        HelpTopics.Entry entry = HelpTopics.entry(place, frame.id);
        if (entry == null) {
            body.addView(style.body(string(R.string.help_topic_unavailable)));
            return;
        }
        body.addView(style.body(text.get(entry.summaryRes)));
        body.addView(style.instruction(text.get(entry.actionRes)));
        int step = 1;
        for (Integer stepRes : entry.stepsRes) {
            body.addView(style.body(getContext().getString(R.string.help_topic_step,
                step++, text.get(stepRes))));
        }
        if (entry.wayBackRes != 0) body.addView(style.body(text.get(entry.wayBackRes)));

        boolean hidden = entry.targetId != null && !measured.contains(entry.targetId);
        if (hidden) {
            body.addView(add(string(R.string.help_topic_not_visible),
                style.note(string(R.string.help_topic_not_visible))), style.stacked(style.dp(10)));
            if (entry.revealRes != 0) body.addView(style.body(text.get(entry.revealRes)));
            else {
                // No way to un-hide it, so the route onward is the topic next to it.
                String onward = HelpTopics.relatedIdOn(place, entry.id);
                HelpTopics.Entry next = onward == null ? null : HelpTopics.entry(onward);
                if (next != null) body.addView(topicRow(next, null), style.stacked(style.dp(6)));
            }
        }

        LinearLayout actions = style.row();
        final String id = entry.id;
        if (!hidden && entry.targetId != null) {
            actions.addView(add(string(R.string.help_show_on_screen),
                style.button(string(R.string.help_show_on_screen), true,
                    () -> { if (listener != null) listener.onShowOnScreen(id); })));
        }
        if (!hidden && entry.gesture != null && entry.gesture != TourGesture.NONE) {
            actions.addView(add(string(R.string.help_show_gesture),
                style.button(string(R.string.help_show_gesture), true,
                    () -> { if (listener != null) listener.onShowGesture(id); })),
                style.beside(style.dp(6)));
        }
        if (entry.lessonId != null && practiceAvailable) {
            final String lesson = entry.lessonId;
            actions.addView(add(string(R.string.help_try_it),
                style.button(string(R.string.help_try_it), true,
                    () -> { if (listener != null) listener.onTryIt(id, lesson); })),
                style.beside(style.dp(6)));
        }
        if (actions.getChildCount() > 0) body.addView(actions, style.stacked(style.dp(12)));

        if (!entry.relatedIds.isEmpty()) {
            body.addView(style.sectionLabel(string(R.string.help_topic_related)));
            for (String relatedId : entry.relatedIds) {
                HelpTopics.Entry related = HelpTopics.entry(relatedId);
                if (related != null) body.addView(topicRow(related, null), style.stacked(style.dp(6)));
            }
        }
        if (!entry.termIds.isEmpty()) {
            body.addView(style.sectionLabel(string(R.string.help_topic_words)));
            for (String termId : entry.termIds) {
                HelpGlossary.Term term = HelpGlossary.term(termId);
                if (term == null) continue;
                final String openId = termId;
                body.addView(add(text.get(term.titleRes), style.link(text.get(term.titleRes),
                    () -> { if (listener != null) listener.onTerm(openId); })));
                if (!termId.equals(frame.openTermId)) continue;
                // Expanded where it stands: nothing floats over what the reader was reading.
                body.addView(style.note(text.get(term.definitionRes)));
                body.addView(add(string(R.string.help_topic_open_glossary),
                    style.link(string(R.string.help_topic_open_glossary),
                        () -> { if (listener != null) listener.onGlossary(); })));
            }
        }
        if (entry.docPath != null) {
            final String url = DOCS_BASE + entry.docPath;
            body.addView(add(string(R.string.help_docs_link),
                style.link(string(R.string.help_docs_link),
                    () -> { if (listener != null) listener.onLink(url); })));
        }
    }

    // ---- pieces ------------------------------------------------------------------------------

    private View topicRow(HelpTopics.Entry entry, String label) {
        final String id = entry.id;
        return add(text.get(entry.titleRes), style.row(text.get(entry.titleRes),
            text.get(entry.summaryRes), label, true,
            () -> { if (listener != null) listener.onTopic(id); }));
    }

    private void watch(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (listener != null) listener.onQueryChanged(s.toString());
            }
        });
        focusHandOff(field);
    }

    /** A field the reader touches asks the controller for the configured keyboard, never for one of its own. */
    private void focusHandOff(EditText field) {
        field.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && listener != null)
                listener.onTextEntry(field);
            return false;
        });
        field.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && listener != null) listener.onTextEntry(field);
        });
    }

    private static void detach(View view) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }

    private View add(String name, View view) {
        named.put(name, view);
        return view;
    }

    private String string(int res) {
        return res == 0 ? "" : getContext().getString(res);
    }

    private String placeName() {
        int res = place == PaneWallPage.WIDGETS ? R.string.help_place_home
            : place == PaneWallPage.DISPLAY ? R.string.help_place_display
            : R.string.help_place_terminal;
        return getContext().getString(res);
    }

    private static int lessonTitle(String lessonId) {
        switch (lessonId) {
            case HelpTopics.LESSON_FIND_HELP: return R.string.help_lesson_find_help_title;
            case HelpTopics.LESSON_FIND_APPS: return R.string.help_lesson_find_apps_title;
            case HelpTopics.LESSON_KEYBOARD: return R.string.help_lesson_keyboard_title;
            case HelpTopics.LESSON_FIND_ACTION: return R.string.help_lesson_find_action_title;
            default: return 0;
        }
    }

    private static int lessonSummary(String lessonId) {
        switch (lessonId) {
            case HelpTopics.LESSON_FIND_HELP: return R.string.help_lesson_find_help_summary;
            case HelpTopics.LESSON_FIND_APPS: return R.string.help_lesson_find_apps_summary;
            case HelpTopics.LESSON_KEYBOARD: return R.string.help_lesson_keyboard_summary;
            case HelpTopics.LESSON_FIND_ACTION: return R.string.help_lesson_find_action_summary;
            default: return 0;
        }
    }

    // ---- geometry ----------------------------------------------------------------------------

    @Override public WindowInsets onApplyWindowInsets(WindowInsets windowInsets) {
        takeInsets(WindowInsetsCompat.toWindowInsetsCompat(windowInsets, this));
        return windowInsets;
    }

    private void takeInsets(WindowInsetsCompat compat) {
        androidx.core.graphics.Insets bars =
            compat.getInsets(WindowInsetsCompat.Type.systemBars());
        androidx.core.graphics.Insets cutout =
            compat.getInsets(WindowInsetsCompat.Type.displayCutout());
        androidx.core.graphics.Insets ime = compat.getInsets(WindowInsetsCompat.Type.ime());
        int top = Math.max(bars.top, cutout.top);
        int bottom = Math.max(Math.max(bars.bottom, cutout.bottom), ime.bottom);
        if (insets.left == bars.left && insets.top == top && insets.right == bars.right
            && insets.bottom == bottom) return;
        insets.set(bars.left, top, bars.right, bottom);
        panelWidth = -1;
        requestLayout();
    }

    /**
     * The launcher's content consumes the window insets before they reach a sibling on the decor
     * view, so the sheet reads the window's own: on the phone the dispatched ones never arrive and
     * the header sat under the status bar.
     */
    private void readWindowInsets() {
        WindowInsets root = getRootWindowInsets();
        if (root != null) takeInsets(WindowInsetsCompat.toWindowInsetsCompat(root, this));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        readWindowInsets();
        int available = MeasureSpec.getSize(widthSpec) - insets.left - insets.right;
        boolean wide = MeasureSpec.getSize(widthSpec) > MeasureSpec.getSize(heightSpec);
        int margin = style.dp(12);
        int width = wide
            ? Math.min(Math.max(style.dp(160), available - margin * 2), style.dp(HelpStyle.MAX_WIDTH_DP))
            : Math.max(style.dp(160), available - margin * 2);
        LayoutParams params = (LayoutParams) panel.getLayoutParams();
        if (panelWidth != width || params.topMargin != insets.top + margin) {
            panelWidth = width;
            params.width = width;
            params.topMargin = insets.top + margin;
            params.bottomMargin = insets.bottom + margin + style.dp(2);
            panel.setLayoutParams(params);
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    /** A tap outside the panel changes nothing: help is a document, not a menu. */
    @Override public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    /**
     * Nothing typed while reading reaches the terminal. Back and Escape are the launcher's to
     * route — the overlay registry owns the order help closes in.
     */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (super.dispatchKeyEvent(event)) return true;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) return false;
        if (code == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_UP && listener != null) listener.onBack();
            return true;
        }
        return true;
    }

    // ---- for the tests -----------------------------------------------------------------------

    /** A row, button or link of the current page by the name a reader would hear. */
    @VisibleForTesting
    View named(String name) {
        return named.get(name);
    }

    @VisibleForTesting
    List<String> names() {
        return new ArrayList<>(named.keySet());
    }

    /** Everything the current page says, for a test that cares about a sentence, not a view. */
    @VisibleForTesting
    String pageText() {
        StringBuilder out = new StringBuilder();
        collect(panel, out);
        return out.toString();
    }

    private static void collect(View view, StringBuilder out) {
        if (view instanceof TextView) out.append(((TextView) view).getText()).append('\n');
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), out);
    }
}
