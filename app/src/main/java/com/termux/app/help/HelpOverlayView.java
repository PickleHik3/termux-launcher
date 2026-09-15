package com.termux.app.help;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.notice.TerminalDress;
import com.termux.app.statusbar.StatusBarLensView;
import com.termux.app.tour.TourFingerPainter;
import com.termux.app.tour.TourFingerTrace;
import com.termux.app.tour.TourGesture;
import com.termux.app.wall.PaneWallPage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Help, drawn: the topic chooser, one topic, or the all-controls overview.
 *
 * <p>What is showing and what a button does is {@link HelpPresentationModel}'s; this measures the
 * controls, renders the answer and carries out what the model asks for. No persistence, no tour
 * state, and no continuously running animation — the one thing that moves is the single pass
 * "Show gesture" plays over the control it is explaining.
 */
public final class HelpOverlayView extends FrameLayout {

    /** The launcher's side of "Try it": help is already down by the time this is called. */
    public interface PracticeListener {
        void onPracticeRequested(String lessonId);
    }

    private final HelpTargets targets;
    private final HelpPresentationModel model = new HelpPresentationModel();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    /** The box being drawn, reused: onDraw runs a frame at a time and allocates nothing. */
    private final RectF boxBounds = new RectF();
    private final float[] fingerPoint = new float[2];
    private final float[] trailPoint = new float[2];
    private float density;
    private DashPathEffect dash;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::refresh;
    private final Map<View, Rect> childBounds = new HashMap<>();
    /** Everything a tap may land on without closing help: the cards, and the panel. */
    private final List<View> cards = new ArrayList<>();
    private final Map<String, TextView> cardViews = new HashMap<>();
    /** The colour each hint's box and card share, by target id. */
    private final Map<String, Integer> boxColors = new HashMap<>();
    private final Runnable onDismiss;
    private PracticeListener practiceListener;
    /** Whether the launcher can take a "Try it" right now; a run already up cannot. */
    private boolean practiceAvailable = true;
    private HelpTargets.Snapshot snapshot;
    private HelpLeaderRouter.Result routed;
    private TerminalDress dress;
    private PaneWallPage place;
    private int pageCount = 1;
    private int accent;
    private int footerHeight;
    private String signature = "";
    private boolean showing;
    /** Set by {@link #show}, cleared by the first measurement that can open the model. */
    private boolean pendingOpen;
    /** What was last read out, so a layout pass does not announce it again. */
    private String announced;
    private float downX, downY;
    private boolean moved;
    private ValueAnimator gestureTrace;
    private TourGesture gesture = TourGesture.NONE;
    private Rect gestureRect;
    private float gestureProgress = 1f;
    /** Read once when the gesture starts: a setting is not something onDraw asks about. */
    private boolean gestureReducedMotion;

    public HelpOverlayView(Context context, HelpTargets.ViewFinder finder, Runnable onDismiss) {
        super(context);
        this.onDismiss = onDismiss;
        density = getResources().getDisplayMetrics().density;
        dash = new DashPathEffect(new float[]{dp(4), dp(3)}, 0);
        targets = new HelpTargets(finder, this);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.help_accessibility));
        setVisibility(GONE);
    }

    /** Where "Try it" sends the user. */
    public void setPracticeListener(PracticeListener listener) {
        this.practiceListener = listener;
    }

    /**
     * Whether "Try it" is offered at all. Set before {@link #show}: a first-run tour already
     * partway through a lesson cannot take one, and a dead button is kinder than a lost run.
     */
    public void setPracticeAvailable(boolean available) {
        this.practiceAvailable = available;
    }

    /** "Try it" needs a lesson to hand over and a launcher in a state to take it. */
    private boolean canTryIt() {
        return practiceAvailable && model.canTryIt();
    }

    public void show(PaneWallPage place) {
        this.place = place;
        signature = "";
        announced = null;
        pendingOpen = true;
        dress = TerminalDress.stored(getContext());
        accent = StatusBarLensView.accentFor(getContext(), place);
        if (!showing) getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        showing = true;
        setVisibility(VISIBLE);
        bringToFront();
        requestFocus();
        requestLayout();
        refresh();
        HelpLog.d("show " + place);
    }

    public boolean isShowing() { return showing; }

    public void dismiss() {
        if (!showing) return;
        showing = false;
        stopGesture();
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        removeAllViews();
        cards.clear(); childBounds.clear(); cardViews.clear();
        snapshot = null; routed = null; signature = ""; announced = null;
        setVisibility(GONE);
        HelpLog.d("dismiss " + place);
        onDismiss.run();
    }

    @Override protected void onDetachedFromWindow() {
        dismiss();
        super.onDetachedFromWindow();
    }

    /** Child rebuilds only follow changed measurements; their own layout pass is a no-op here. */
    public void refresh() {
        if (!showing || getWidth() <= 0 || getHeight() <= 0) return;
        float currentDensity = getResources().getDisplayMetrics().density;
        if (currentDensity != density) {
            density = currentDensity;
            dash = new DashPathEffect(new float[]{dp(4), dp(3)}, 0);
        }
        TerminalDress currentDress = TerminalDress.stored(getContext());
        int currentAccent = StatusBarLensView.accentFor(getContext(), place);
        HelpTargets.Snapshot measured = targets.measure(place);
        String next = getWidth() + ":" + getHeight() + ":"
            + getResources().getConfiguration().fontScale + ":" + density + ":"
            + currentDress.fillColor + ":" + currentDress.strokeColor + ":" + currentDress.textColor
            + ":" + currentDress.terminalRadiusPx + ":" + currentAccent + ":" + measured.signature();
        boolean movedOnScreen = !signature.equals(next);
        if (!movedOnScreen && !pendingOpen) return;
        if (movedOnScreen) {
            signature = next;
            snapshot = measured;
            dress = currentDress; accent = currentAccent;
            arrangeOverview();
        }
        if (pendingOpen) {
            pendingOpen = false;
            model.open(place, measurableIds());
        } else {
            model.remeasure(measurableIds());
        }
        model.setPageCount(pageCount);
        render();
    }

    private Set<String> measurableIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (HelpTargets.Target target : snapshot.targets) ids.add(target.id);
        return ids;
    }

    // ---- the overview's arrangement ---------------------------------------------------------

    /** The existing arranged cards and leaders: measured once per layout, drawn per page. */
    private void arrangeOverview() {
        cardViews.clear(); boxColors.clear();
        int width = Math.max(1, (snapshot.wall.width() - dp(36)) / 2);
        List<HelpLeaderRouter.Target> inputs = new ArrayList<>();
        List<HelpLeaderRouter.Box> soft = new ArrayList<>();
        int count = snapshot.targets.size();
        for (int i = 0; i < count; i++) {
            HelpTargets.Target target = snapshot.targets.get(i);
            if (HelpTopics.topicOnly(target.id)) continue;
            int color = overviewColor(target.id, i, count);
            boxColors.put(target.id, color);
            TextView card = card(target.copy, titleColor(target.id, i, count), color);
            card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            cardViews.put(target.id, card);
            HelpLeaderRouter.Side side = side(target.rect);
            inputs.add(new HelpLeaderRouter.Target(target.id, box(target.rect), side,
                width, card.getMeasuredHeight()));
            if (side == HelpLeaderRouter.Side.INSIDE) soft.add(box(target.rect));
        }
        // Reserve the bottom of the wall for the footer, measured at this font scale.
        View footer = overviewFooter(true, true);
        footer.measure(MeasureSpec.makeMeasureSpec(panelWidth(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        footerHeight = footer.getMeasuredHeight();
        Rect band = new Rect(snapshot.wall);
        band.top += dp(8);
        band.bottom = Math.max(band.top, band.bottom - footerHeight - dp(16));
        List<HelpLeaderRouter.Box> obstacles = new ArrayList<>();
        for (HelpTargets.KeyLabel key : snapshot.keys) obstacles.add(box(keyLabelBounds(key.rect)));
        obstacles.add(new HelpLeaderRouter.Box(snapshot.wall.left + dp(12),
            snapshot.wall.bottom - footerHeight - dp(8), snapshot.wall.right - dp(12),
            snapshot.wall.bottom - dp(8)));
        routed = HelpLeaderRouter.arrange(box(band), dp(12), dp(12), inputs, obstacles, soft);
        pageCount = Math.max(1, routed.pages + routed.unplaced.size());
        for (HelpLeaderRouter.Target target : routed.unplaced)
            HelpLog.d("copy-only page for " + target.id + ": no collision-free slot");
        HelpLog.d("layout " + place + ": " + snapshot.targets.size() + " targets, " + pageCount + " pages");
    }

    /** A control's colour comes from its identity in the catalogue, not from what else is up. */
    private int overviewColor(String id, int index, int count) {
        HelpTopics.Entry entry = HelpTopics.entry(place, id);
        return entry == null ? HelpPalette.boxColor(accent, index, count)
            : model.overviewColor(accent, entry);
    }

    private int titleColor(String id, int index, int count) {
        HelpTopics.Entry entry = HelpTopics.entry(place, id);
        if (entry == null) return HelpPalette.titleColor(accent, index, count, dress.fillColor);
        return HelpPalette.titleColor(accent, entry.identityIndex,
            HelpTopics.sizeFor(entry.place), dress.fillColor);
    }

    private HelpLeaderRouter.Side side(Rect r) {
        if (r.bottom <= snapshot.wall.top) return HelpLeaderRouter.Side.ABOVE;
        if (r.top >= snapshot.wall.bottom) return HelpLeaderRouter.Side.BELOW;
        if (r.right <= snapshot.wall.left) return HelpLeaderRouter.Side.LEFT;
        if (r.left >= snapshot.wall.right) return HelpLeaderRouter.Side.RIGHT;
        return HelpLeaderRouter.Side.INSIDE;
    }

    // ---- rendering ---------------------------------------------------------------------------

    private void render() {
        removeAllViews(); childBounds.clear(); cards.clear();
        switch (model.mode()) {
            case TOPICS: renderTopics(); break;
            case TOPIC: renderTopic(); break;
            default: renderOverview(); break;
        }
        requestLayout(); invalidate();
    }

    /** The chooser: what this place can explain, in a list the user picks one thing out of. */
    private void renderTopics() {
        LinearLayout panel = panel();
        String title = getContext().getString(R.string.help_header, placeName());
        panel.addView(header(title));
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        HelpTopics.Group group = null;
        for (HelpTopics.Entry entry : model.entries()) {
            if (entry.group != group) {
                group = entry.group;
                list.addView(sectionLabel(getContext().getString(group.labelRes)), rowParams(dp(8)));
            }
            list.addView(chip(entry), rowParams(dp(4)));
        }
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.addView(list, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT));
        LinearLayout row = buttonRow();
        row.addView(button(getContext().getString(R.string.help_show_basics), !model.basicsOnly(),
            () -> command(model.showBasics())), weighted());
        row.addView(button(getContext().getString(R.string.help_show_all), true,
            () -> command(model.showAll())), weighted());
        panel.addView(row, rowParams(dp(8)));
        placePanel(panel, null);
        announce(title, "topics");
    }

    /** One topic: what the control is, how to use it, and one box on the control itself. */
    private void renderTopic() {
        HelpTopics.Entry entry = model.selected();
        if (entry == null) { command(model.backToTopics()); return; }
        LinearLayout panel = panel();
        String title = getContext().getString(entry.titleRes);
        panel.addView(header(title));
        panel.addView(line(getContext().getString(entry.purposeRes), true,
            model.topicHighlightColor(accent)), rowParams(dp(6)));
        panel.addView(line(getContext().getString(entry.actionRes), false, dress.textColor),
            rowParams(dp(4)));
        int revealRes = model.revealRes();
        if (revealRes != 0)
            panel.addView(line(getContext().getString(revealRes), false,
                ColorUtils.setAlphaComponent(dress.textColor, 199)), rowParams(dp(6)));
        HelpTopics.Entry related = HelpTopics.entry(place, model.relatedTopicId());
        if (related != null) panel.addView(chip(related), rowParams(dp(8)));
        LinearLayout first = buttonRow();
        first.addView(button(getContext().getString(R.string.help_back_to_topics), true,
            () -> command(model.backToTopics())), weighted());
        first.addView(button(getContext().getString(R.string.help_close), true,
            () -> command(model.close())), weighted());
        panel.addView(first, rowParams(dp(10)));
        LinearLayout second = buttonRow();
        second.addView(button(getContext().getString(R.string.help_show_gesture),
            model.canShowGesture(), () -> command(model.showGesture())), weighted());
        second.addView(button(getContext().getString(R.string.help_try_it), canTryIt(),
            () -> command(model.tryIt())), weighted());
        panel.addView(second, rowParams(dp(6)));
        placePanel(panel, targetRect(model.highlightTargetId()));
        announce(title + " " + getContext().getString(entry.purposeRes), entry.id);
    }

    /** The all-controls reference, page by page, with the way back to the chooser. */
    private void renderOverview() {
        if (routed == null) return;
        int page = model.page();
        for (HelpLeaderRouter.Placement p : routed.placements) if (p.page == page) {
            TextView card = cardViews.get(p.target.id);
            put(card, rect(p.card)); cards.add(card);
        }
        if (page >= routed.pages && !routed.unplaced.isEmpty()) {
            // A wall-sized target cannot share that band with a card. Keep the explanation
            // reachable, scrollable, and honest: never draw an invented or crossing leader.
            HelpLeaderRouter.Target target = routed.unplaced.get(page - routed.pages);
            TextView card = cardViews.get(target.id);
            ScrollView scroll = new ScrollView(getContext());
            scroll.setFillViewport(false);
            if (card.getParent() instanceof ViewGroup) ((ViewGroup) card.getParent()).removeView(card);
            scroll.addView(card, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            Rect r = new Rect(snapshot.wall); r.inset(dp(16), dp(8));
            r.bottom = Math.max(r.top + 1, r.bottom - footerHeight - dp(16));
            put(scroll, r); cards.add(scroll);
        }
        for (HelpTargets.KeyLabel key : snapshot.keys) {
            TextView label = pill(key.text);
            label.setPadding(dp(1), 0, dp(1), 0);
            label.setMaxLines(2);
            label.setAutoSizeTextTypeUniformWithConfiguration(6, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            label.setBackground(dress.background(key.rect.height()));
            put(label, keyLabelBounds(key.rect));
        }
        View footer = overviewFooter(page > 0, page < pageCount - 1);
        int width = panelWidth();
        footer.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int left = snapshot.wall.centerX() - width / 2;
        int top = snapshot.wall.bottom - footer.getMeasuredHeight() - dp(8);
        put(footer, new Rect(left, top, left + width, top + footer.getMeasuredHeight()));
        cards.add(footer);
        announce(getContext().getString(R.string.help_header, placeName()), "overview" + page);
    }

    /** The section the page being read belongs to, named by the first card the router put on it. */
    private int currentSectionLabelRes() {
        return model.sectionLabelResFor(firstIdOnPage());
    }

    /** The id of the first card on the page being read, or null when the router placed none. */
    private String firstIdOnPage() {
        if (routed == null) return null;
        int page = model.page();
        for (HelpLeaderRouter.Placement p : routed.placements) if (p.page == page) return p.target.id;
        int copyOnly = page - routed.pages;
        return copyOnly >= 0 && copyOnly < routed.unplaced.size()
            ? routed.unplaced.get(copyOnly).id : null;
    }

    /** The overview's own band: where the reader is, the way through, and the ways out. */
    private View overviewFooter(boolean hasPrevious, boolean hasNext) {
        LinearLayout panel = panel();
        int sectionRes = currentSectionLabelRes();
        if (sectionRes != 0) panel.addView(sectionLabel(getContext().getString(sectionRes)));
        LinearLayout paging = buttonRow();
        paging.addView(button(getContext().getString(R.string.help_previous), hasPrevious,
            () -> command(model.previous())), weighted());
        paging.addView(button(getContext().getString(R.string.help_next), hasNext,
            () -> command(model.next())), weighted());
        panel.addView(paging, rowParams(dp(6)));
        LinearLayout ways = buttonRow();
        ways.addView(button(getContext().getString(R.string.help_show_topics), true,
            () -> command(model.backToTopics())), weighted());
        ways.addView(button(getContext().getString(R.string.help_close), true,
            () -> command(model.close())), weighted());
        panel.addView(ways, rowParams(dp(6)));
        return panel;
    }

    /** Whatever the model asked for, done. Reading help asks for nothing. */
    private void command(HelpPresentationModel.Effect effect) {
        switch (effect.kind) {
            case CLOSE:
                dismiss();
                return;
            case CLOSE_AND_PRACTICE:
                String lesson = effect.lessonId;
                dismiss();
                if (practiceListener != null) practiceListener.onPracticeRequested(lesson);
                return;
            case DEMONSTRATE:
                startGesture(effect.targetId);
                return;
            default:
                stopGesture();
                render();
        }
    }

    // ---- the gesture demonstration -----------------------------------------------------------

    /** One finite pass of the topic's gesture over its control; help stays up throughout. */
    private void startGesture(String targetId) {
        Rect rect = targetRect(targetId);
        stopGesture();
        if (rect == null) return;
        gestureRect = new Rect(rect);
        gesture = gestureFor(targetId);
        gestureReducedMotion = ReducedMotion.isEnabled(getContext());
        if (gestureReducedMotion) {
            // No animation at all on this phone: the cue is drawn where the gesture starts and
            // where it ends, and stays there while help is up.
            gestureProgress = 1f;
            invalidate();
            return;
        }
        gestureProgress = 0f;
        gestureTrace = ValueAnimator.ofFloat(0f, 1f);
        gestureTrace.setDuration(TourFingerTrace.TRACE_MS);
        gestureTrace.addUpdateListener(animator -> {
            gestureProgress = (float) animator.getAnimatedValue();
            invalidate();
        });
        gestureTrace.start();
    }

    private void stopGesture() {
        if (gestureTrace != null) { gestureTrace.cancel(); gestureTrace = null; }
        gestureRect = null;
        gesture = TourGesture.NONE;
        gestureProgress = 1f;
    }

    /** Whether a demonstration is on screen right now. */
    boolean isShowingGesture() {
        return gestureRect != null && gesture != TourGesture.NONE;
    }

    /** The movement each control is used with; everything that is tapped is a tap. */
    static TourGesture gestureFor(String topicId) {
        if (topicId == null) return TourGesture.TAP;
        switch (topicId) {
            case "dock": return TourGesture.DRAG_DOWN;
            case "status": return TourGesture.SWIPE_RIGHT;
            case "space": return TourGesture.SWIPE_UP;
            case "az": return TourGesture.SCRUB;
            default: return TourGesture.TAP;
        }
    }

    // ---- pieces ------------------------------------------------------------------------------

    private String placeName() {
        int res = place == PaneWallPage.WIDGETS ? R.string.help_place_home
            : place == PaneWallPage.DISPLAY ? R.string.help_place_display
            : R.string.help_place_terminal;
        return getContext().getString(res);
    }

    private int panelWidth() {
        Rect wall = snapshot == null ? new Rect(0, 0, getWidth(), getHeight()) : snapshot.wall;
        return Math.max(dp(120), wall.width() - dp(32));
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(dress.background(0));
        panel.setPadding(dp(14), dp(12), dp(14), dp(10));
        panel.setClickable(true);
        panel.setFocusable(true);
        return panel;
    }

    /** The panel's own row: the title, and the Close that is never further than one tap. */
    private View header(String title) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = new TextView(getContext());
        text.setText(title);
        text.setTextSize(14);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextColor(dress.textColor);
        row.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(button(getContext().getString(R.string.help_close), true,
            () -> command(model.close())));
        return row;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextSize(11);
        label.setAllCaps(true);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setTextColor(ColorUtils.setAlphaComponent(dress.textColor, 168));
        return label;
    }

    private TextView line(String text, boolean bold, int color) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return view;
    }

    /** One topic in the chooser: its name, and the sentence that says what it is for. */
    private TextView chip(HelpTopics.Entry entry) {
        TextView view = new TextView(getContext());
        String title = getContext().getString(entry.titleRes);
        SpannableString content = new SpannableString(title + "\n"
            + getContext().getString(entry.purposeRes));
        content.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        content.setSpan(new ForegroundColorSpan(model.topicHighlightColor(accent)), 0,
            title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(content);
        view.setTextSize(12);
        view.setTextColor(dress.textColor);
        view.setLineSpacing(dp(1), 1f);
        view.setMinHeight(dp(48));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        view.setContentDescription(title);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(accent, 20));
        shape.setCornerRadius(dp(10));
        view.setBackground(shape);
        view.setClickable(true);
        view.setOnClickListener(v -> command(model.selectTopic(entry.id)));
        return view;
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = dp(3);
        params.rightMargin = dp(3);
        return params;
    }

    private LinearLayout.LayoutParams rowParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    /** A button of the panel: never smaller than a thumb, and always named for a reader. */
    private TextView button(String label, boolean enabled, Runnable onClick) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setContentDescription(label);
        view.setTextSize(13);
        view.setAllCaps(false);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48));
        view.setMinWidth(dp(48));
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        view.setTextColor(enabled ? accent : ColorUtils.setAlphaComponent(dress.textColor, 97));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(accent, enabled ? 28 : 12));
        shape.setCornerRadius(dp(10));
        view.setBackground(shape);
        view.setEnabled(enabled);
        view.setFocusable(enabled);
        view.setClickable(enabled);
        if (enabled) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /**
     * The panel, in the half of the wall the control is not in, so the user can read the sentence
     * and see the box it is about at the same time.
     */
    private void placePanel(View panel, Rect avoid) {
        Rect wall = snapshot.wall;
        int width = panelWidth();
        int maxHeight = Math.max(dp(80), wall.height() - dp(24));
        panel.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
        int height = Math.min(panel.getMeasuredHeight(), maxHeight);
        boolean atTheTop = avoid != null && avoid.centerY() > wall.centerY();
        int top = atTheTop ? wall.top + dp(12) : wall.bottom - height - dp(12);
        int left = wall.centerX() - width / 2;
        put(panel, new Rect(left, top, left + width, top + height));
        cards.add(panel);
        panel.requestFocus();
    }

    /** Said once per topic: a layout pass is not a new thing to read out. */
    private void announce(String text, String key) {
        if (key == null || key.equals(announced)) return;
        announced = key;
        announceForAccessibility(text);
    }

    private Rect targetRect(String id) {
        if (id == null || snapshot == null) return null;
        for (HelpTargets.Target target : snapshot.targets) if (target.id.equals(id)) return target.rect;
        return null;
    }

    private Rect keyLabelBounds(Rect cap) {
        Rect label = new Rect(cap);
        label.inset(dp(2), dp(2));
        // Text stays on its measured cap, clear of the screen's edges.
        label.left = Math.max(label.left, dp(12));
        label.right = Math.min(label.right, getWidth() - dp(12));
        return label;
    }

    private void put(View view, Rect rect) {
        // A card can move between its normal page and a copy-only scroll page on remeasurement.
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        addView(view, new LayoutParams(Math.max(1, rect.width()), Math.max(1, rect.height())));
        childBounds.put(view, rect);
    }

    /** A card in its hint's colour: the title and the border match the box on the control. */
    private TextView card(HelpCopy copy, int titleColor, int borderColor) {
        TextView text = new TextView(getContext());
        SpannableString content = new SpannableString(copy.title + "\n" + copy.body);
        content.setSpan(new StyleSpan(Typeface.BOLD), 0, copy.title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        content.setSpan(new ForegroundColorSpan(titleColor), 0, copy.title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setText(content); text.setTextSize(12); text.setTextColor(dress.textColor);
        text.setPadding(dp(8), dp(6), dp(8), dp(6));
        text.setLineSpacing(dp(1), 1);
        android.graphics.drawable.Drawable background = dress.background(0);
        if (background instanceof GradientDrawable)
            ((GradientDrawable) background).setStroke(dp(1.5f), borderColor);
        text.setBackground(background);
        return text;
    }

    private TextView pill(String copy) {
        TextView text = new TextView(getContext());
        text.setText(copy); text.setTextSize(12); text.setTextColor(dress.textColor);
        text.setGravity(Gravity.CENTER); text.setPadding(dp(10), dp(8), dp(10), dp(8));
        text.setBackground(dress.background(dp(40)));
        return text;
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (Map.Entry<View, Rect> entry : childBounds.entrySet()) {
            View child = entry.getKey(); Rect bounds = entry.getValue();
            child.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
            if (child.getBackground() instanceof GradientDrawable)
                ((GradientDrawable) child.getBackground()).setCornerRadius(dress.cornerRadiusPx(bounds.height()));
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!showing || snapshot == null) return;
        canvas.drawColor(Color.argb(166, 0, 0, 0));
        paint.setStrokeWidth(dp(1.5f)); paint.setStyle(Paint.Style.STROKE);
        if (model.mode() == HelpPresentationModel.Mode.OVERVIEW) drawOverview(canvas);
        else if (model.mode() == HelpPresentationModel.Mode.TOPIC) drawTopic(canvas);
        paint.setPathEffect(null);
        drawGesture(canvas);
    }

    /** One box, on the one control the topic is about. */
    private void drawTopic(Canvas canvas) {
        Rect rect = targetRect(model.highlightTargetId());
        if (rect == null) return;
        paint.setColor(model.topicHighlightColor(accent));
        drawBox(canvas, rect, radiusOf(model.highlightTargetId()));
    }

    /**
     * Each box, its leader and its card wear one colour, so a line that passes another card still
     * reads as belonging to its own pair.
     */
    private void drawOverview(Canvas canvas) {
        if (routed == null) return;
        int page = model.page();
        for (HelpLeaderRouter.Placement p : routed.placements) if (p.page == page) {
            HelpTargets.Target target = target(p.target.id);
            Integer color = boxColors.get(target.id);
            paint.setColor(color == null ? accent : color);
            paint.setPathEffect(null);
            for (HelpLeaderRouter.Segment line : p.lines)
                canvas.drawLine(line.x1, line.y1, line.x2, line.y2, paint);
            drawBox(canvas, target.rect, target.radius);
        }
    }

    private void drawBox(Canvas canvas, Rect rect, float radius) {
        boxBounds.set(rect); boxBounds.inset(dp(2), dp(2));
        if (boxBounds.isEmpty()) return;
        paint.setPathEffect(dash);
        float corner = Math.max(0, radius - dp(2));
        canvas.drawRoundRect(boxBounds, corner, corner, paint);
        paint.setPathEffect(null);
    }

    private void drawGesture(Canvas canvas) {
        Rect rect = gestureRect;
        if (rect == null || gesture == TourGesture.NONE) return;
        if (gestureReducedMotion) {
            TourFingerPainter.drawStaticCue(canvas, paint, arrowPath, gesture, rect.left, rect.top,
                rect.right, rect.bottom, density, accent, fingerPoint, trailPoint);
            return;
        }
        if (gestureProgress >= 1f) { stopGesture(); return; }
        TourFingerPainter.draw(canvas, paint, gesture, rect.left, rect.top, rect.right, rect.bottom,
            density, gestureProgress, accent, fingerPoint, trailPoint);
    }

    private float radiusOf(String id) {
        if (snapshot == null) return 0;
        for (HelpTargets.Target target : snapshot.targets) if (target.id.equals(id)) return target.radius;
        return 0;
    }

    private HelpTargets.Target target(String id) {
        for (HelpTargets.Target t : snapshot.targets) if (t.id.equals(id)) return t;
        throw new IllegalStateException(id);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // Children receive taps and panel scrolling; nothing can pass through this layer.
        super.dispatchTouchEvent(event);
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); moved = false; break;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX() - downX, event.getY() - downY)
                    > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) moved = true;
                break;
            case MotionEvent.ACTION_UP:
                if (!moved && !insideCard(downX, downY) && !insideCard(event.getX(), event.getY())) {
                    performClick(); command(model.close());
                }
                break;
        }
        return true;
    }

    private boolean insideCard(float x, float y) {
        for (View card : cards) { Rect r = childBounds.get(card); if (r != null && r.contains((int) x, (int) y)) return true; }
        return false;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private int dp(float value) { return Math.round(value * density); }

    private static HelpLeaderRouter.Box box(Rect r) { return new HelpLeaderRouter.Box(r.left, r.top, r.right, r.bottom); }

    private static Rect rect(HelpLeaderRouter.Box b) { return new Rect(Math.round(b.left), Math.round(b.top), Math.round(b.right), Math.round(b.bottom)); }
}
