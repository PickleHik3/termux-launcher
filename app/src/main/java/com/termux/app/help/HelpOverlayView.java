package com.termux.app.help;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
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
import android.widget.TextView;
import com.termux.shared.termux.font.NerdFontSpans;
import com.termux.app.chrome.CornerTabGlyphs;
import com.termux.app.chrome.CornerTabGeometry;
import com.google.android.material.color.MaterialColors;
import androidx.core.content.ContextCompat;
import android.graphics.drawable.InsetDrawable;
import androidx.annotation.VisibleForTesting;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The live launcher, dimmed a little, with help drawn over it two ways.
 *
 * <p>The <b>overview</b> is what help opens on: a card on each of the few controls
 * {@link HelpPresentationModel#OVERVIEW_TARGET_IDS} names, drawn all at once and joined to their
 * controls by {@link HelpLeaderRouter}, the extra keys carrying a label each, and two buttons —
 * close, and the way into the guide. Nothing else on the screen is marked, so the screen the
 * reader is looking at stays legible.
 *
 * <p><b>Explore this screen</b> is the other way: every control help can explain is marked, and one
 * card at a time sits on the control the reader tapped. That card is seated by
 * {@link HelpExplorePlacement}, which never lets it cover the control it explains, the toolbar or
 * the system bars; when no seat fits, the listener is told and the topic is read instead.
 *
 * <p>Nothing is read here. A card carries the topic's title and its one instruction, and hands the
 * topic to the help panel through {@link ExploreListener}. Which control is where and what it is
 * called is {@link HelpPresentationModel}'s and {@link HelpTopics}'; this measures the controls,
 * seats what the layout answers, and plays the one gesture a topic carries.
 */
public final class HelpOverlayView extends FrameLayout {

    /** What help's overlay asks the launcher for; everything else it does itself. */
    public interface ExploreListener {
        /** A card tapped in the overview, or "Read topic" on the seated card. */
        void onReadTopic(String topicId);
        /** The overview's Guide button: the reading sheet, at its home page. */
        void onOpenGuide();
        /** The toolbar's Back to help, or Back with nothing selected. */
        void onBackToHelp();
        /** The toolbar's Close help, and the overview's ×. */
        void onCloseHelp();
        /** The selected control is no longer on screen; the topic says so instead. */
        void onTargetGone(String topicId);
        /** No seat for the card on this screen: read the topic rather than shrink it. */
        void onCardDoesNotFit(String topicId);
    }

    /** Which of the two the overlay is drawing. */
    private enum Mode { OVERVIEW, EXPLORE }


    /** One extra key's card: where it sits, the cap it is about, and the line between them. */
    private static final class KeyCard {
        final TextView view;
        final Rect bounds;
        final Rect cap;
        /** Each leg as {x1, y1, x2, y2}; one when the card sits over its key, three otherwise. */
        final List<float[]> lines;
        KeyCard(TextView view, Rect bounds, Rect cap, List<float[]> lines) {
            this.view = view; this.bounds = bounds; this.cap = cap; this.lines = lines;
        }
    }

    /** One control's marker: the topic it opens, the dot the finger lands on, and its colour. */
    private static final class Marker {
        final HelpTopics.Entry entry;
        final Rect target;
        final TextView view;
        final Rect bounds;
        final int color;
        Marker(HelpTopics.Entry entry, Rect target, TextView view, Rect bounds, int color) {
            this.entry = entry; this.target = target; this.view = view;
            this.bounds = bounds; this.color = color;
        }
    }

    /** The dot a finger has to be able to land on. */
    private static final int MARKER_DP = 26;

    private final HelpTargets targets;
    private ExploreListener listener;
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
    /** Everything a tap may land on without changing the selection: markers, card, toolbar. */
    private final List<View> touchable = new ArrayList<>();
    /** Every view put on screen this pass; anything else is what has really gone away. */
    private final Set<View> rendered = new HashSet<>();
    /** The markers, in catalogue order, which is the order they are numbered in. */
    private final List<Marker> markers = new ArrayList<>();
    private final Map<String, TextView> markerViews = new HashMap<>();
    /** What each marker was built from, so a pass that only moved one keeps the same view. */
    private final Map<String, String> markerSpecs = new HashMap<>();
    /** The overview's cards, by target id, and what each was built from, so a moved one is kept. */
    private final Map<String, TextView> cardViews = new HashMap<>();
    private final Map<String, String> cardSpecs = new HashMap<>();
    /** The colour each boxed control shares with its own leader and card. */
    private final Map<String, Integer> boxColors = new HashMap<>();
    /** Where the overview seated its cards this pass, or null. */
    private HelpLeaderRouter.Result routed;
    /** The curated cards this screen had no room for; empty on every layout the launcher ships. */
    private final List<String> unplaced = new ArrayList<>();
    /** One card per extra key, shown in the overview and while the row is the selected control. */
    private final List<KeyCard> keyCards = new ArrayList<>();
    private final Map<Integer, TextView> keyCardViews = new HashMap<>();
    private final Map<Integer, String> keyCardSpecs = new HashMap<>();
    /** The one colour the extra keys' boxes, leaders and cards share. */
    private int keyColor;

    private HelpTargets.Snapshot snapshot;
    private TerminalDress dress;
    private PaneWallPage place;
    private int accent;
    private String signature = "";
    private boolean showing;
    /** Set by {@link #explore}, cleared by the first measurement that can open the model. */
    private boolean pendingOpen;
    /** A topic to select as soon as there is a measurement to select it on. */
    private String pendingSelect;
    /** Whether that pre-selection should play its gesture once it is seated. */
    private boolean pendingGesture;

    /** The seated card, and the one line that joins it to the control. */
    private LinearLayout card;
    private String cardTopicId;
    private Rect cardBounds;
    private HelpLeaderRouter.Segment cardLeader;
    /** Said once per selection, so a layout pass does not report the same shortfall twice. */
    private boolean reportedNoSeat;
    private String announced;

    private LinearLayout toolbar;
    private Rect toolbarBounds;

    /** The overview's own two buttons — × and Guide — and their corner. */
    private LinearLayout buttons;
    private Rect buttonsBounds;
    private Mode mode = Mode.EXPLORE;

    private float downX, downY;
    private boolean moved;
    private ValueAnimator gestureTrace;
    private TourGesture gesture = TourGesture.NONE;
    private Rect gestureRect;
    private float gestureProgress = 1f;
    /** Read once when the gesture starts: a setting is not something onDraw asks about. */
    private boolean gestureReducedMotion;

    public HelpOverlayView(Context context, HelpTargets.ViewFinder finder) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        dash = new DashPathEffect(new float[]{dp(4), dp(3)}, 0);
        targets = new HelpTargets(finder, this);
        // Above every control it marks. The dock, the A-Z row, the extra keys and the keyboard are
        // lifted between 6 and 40dp, and exploration has to wash over all of them; the outline is
        // dropped so the height casts no shadow of its own.
        setElevation(dp(56));
        setTranslationZ(dp(56));
        setOutlineProvider(null);
        setWillNotDraw(false);
        setClickable(true);
        setFocusableInTouchMode(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.help_explore_title));
        setVisibility(GONE);
    }

    public void setExploreListener(ExploreListener listener) {
        this.listener = listener;
    }




    /** The curated overview: a few cards at once, the key labels, and the way into the guide. */
    public void overview(PaneWallPage place) {
        start(place, null, false, Mode.OVERVIEW);
    }

    /** Explore a place. {@code selectTopicId} pre-selects a control, or is null. */
    public void explore(PaneWallPage place, String selectTopicId) {
        start(place, selectTopicId, false, Mode.EXPLORE);
    }

    /** Explore with the topic selected and its own gesture played once over its control. */
    public void demonstrate(PaneWallPage place, String topicId) {
        start(place, topicId, true, Mode.EXPLORE);
    }

    private void start(PaneWallPage place, String selectTopicId, boolean withGesture, Mode mode) {
        this.mode = mode;
        setContentDescription(getContext().getString(mode == Mode.OVERVIEW
            ? R.string.help_centre_title : R.string.help_explore_title));
        this.place = place;
        signature = "";
        announced = null;
        pendingOpen = true;
        pendingSelect = selectTopicId;
        pendingGesture = withGesture;
        dress = TerminalDress.stored(getContext());
        accent = StatusBarLensView.accentFor(getContext(), place);
        if (!showing) getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        showing = true;
        setVisibility(VISIBLE);
        bringToFront();
        requestFocus();
        requestLayout();
        refresh();
        HelpLog.d((mode == Mode.OVERVIEW ? "overview " : "explore ") + place
            + (selectTopicId == null ? "" : " at " + selectTopicId)
            + (withGesture ? " with its gesture" : ""));
    }

    public boolean isShowing() { return showing; }

    public void dismiss() {
        if (!showing) return;
        showing = false;
        stopGesture();
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        removeAllViews();
        touchable.clear(); childBounds.clear(); rendered.clear();
        markers.clear(); markerViews.clear(); markerSpecs.clear();
        card = null; cardTopicId = null; cardBounds = null; cardLeader = null;
        keyCards.clear(); keyCardViews.clear(); keyCardSpecs.clear();
        cardViews.clear(); cardSpecs.clear(); boxColors.clear(); unplaced.clear();
        routed = null;
        toolbar = null; toolbarBounds = null;
        buttons = null; buttonsBounds = null;
        snapshot = null; signature = ""; announced = null;
        pendingSelect = null; pendingGesture = false; reportedNoSeat = false;
        model.clearSelection();
        setVisibility(GONE);
        HelpLog.d("dismiss " + place);
    }

    @Override protected void onDetachedFromWindow() {
        dismiss();
        super.onDetachedFromWindow();
    }

    /**
     * Back with a card up puts the card away; with nothing selected it is the launcher's to answer,
     * which takes the reader to Help home. In the overview it is the launcher's either way, and
     * help closes: the overview is where help opens, so there is nothing behind it.
     */
    public boolean onBackPressed() {
        if (mode == Mode.OVERVIEW) return false;
        if (model.selectedTargetId() == null) return false;
        deselect();
        return true;
    }

    // ---- measurement ------------------------------------------------------------------------

    /**
     * Remeasure on layout changes only. A pass that moved nothing does nothing; a pass that moved
     * something re-marks the controls and re-seats the one card, and a pass that took the selected
     * control away stops rather than point at another one.
     */
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
            + ":" + currentDress.terminalRadiusPx + ":" + currentAccent + ":" + lightMode()
            + ":" + measured.signature();
        boolean movedOnScreen = !signature.equals(next);
        if (!movedOnScreen && !pendingOpen) return;
        signature = next;
        snapshot = measured;
        dress = currentDress;
        accent = currentAccent;
        if (pendingOpen) {
            pendingOpen = false;
            model.open(place, measuredIds());
            String wanted = pendingSelect;
            boolean withGesture = pendingGesture;
            pendingSelect = null;
            pendingGesture = false;
            render();
            if (wanted != null) select(wanted, withGesture);
            return;
        }
        model.remeasure(measuredIds());
        if (model.selectedTargetId() != null && !model.selectedMeasured()) {
            String topicId = model.selectedTopicId();
            HelpLog.d("the control for " + topicId + " is no longer on screen");
            stopGesture();
            model.clearSelection();
            dropCard();
            render();
            if (listener != null) listener.onTargetGone(topicId);
            return;
        }
        render();
    }

    private Set<String> measuredIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (HelpTargets.Target target : snapshot.targets) ids.add(target.id);
        return ids;
    }

    // ---- selection --------------------------------------------------------------------------

    /**
     * Select the control a marker or a tap on the launcher landed on, named by target id or by
     * topic id. A control this screen has no measurement for cannot be pointed at, so its topic is
     * handed back to be read instead.
     */
    @VisibleForTesting
    void select(String idOrTargetId, boolean withGesture) {
        HelpTopics.Entry entry = model.select(idOrTargetId);
        if (entry == null) {
            HelpTopics.Entry wanted = model.topicFor(idOrTargetId);
            String topicId = wanted == null ? idOrTargetId : wanted.id;
            HelpLog.d("nothing to point at for " + topicId + " on " + place);
            if (listener != null) listener.onTargetGone(topicId);
            return;
        }
        reportedNoSeat = false;
        stopGesture();
        render();
        if (withGesture && entry.id.equals(model.selectedTopicId())) startGesture(entry);
    }

    /** Put the card away and leave the screen marked. */
    @VisibleForTesting
    void deselect() {
        if (model.selectedTargetId() == null) return;
        stopGesture();
        model.clearSelection();
        dropCard();
        render();
    }

    private void dropCard() {
        card = null;
        cardTopicId = null;
        cardBounds = null;
        cardLeader = null;
        keyCards.clear();
        keyCardViews.clear();
        keyCardSpecs.clear();
    }

    /** The topic the reader has open on the card, or null. */
    @VisibleForTesting
    String selectedTopicId() { return model.selectedTopicId(); }

    // ---- what is on screen ------------------------------------------------------------------

    private void render() {
        if (snapshot == null) return;
        rendered.clear();
        touchable.clear();
        if (mode == Mode.OVERVIEW) renderOverview(); else renderExplore();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (rendered.contains(child)) continue;
            removeViewAt(i);
            childBounds.remove(child);
        }
        if (mode == Mode.OVERVIEW) {
            announce(getContext().getString(R.string.help_centre_title), "overview");
        } else {
            HelpTopics.Entry reading = model.selected();
            if (reading == null) announce(getContext().getString(R.string.help_explore_title), "explore");
            else announce(getContext().getString(reading.titleRes) + ". "
                + getContext().getString(reading.actionRes), reading.id);
        }
        requestLayout();
        invalidate();
    }

    /**
     * The overview: the two buttons in their corner, a label on every launcher key, and the curated
     * cards seated round them. Nothing else on the screen is marked at all.
     */
    private void renderOverview() {
        markers.clear();
        markerViews.clear();
        markerSpecs.clear();
        card = null; cardTopicId = null; cardBounds = null; cardLeader = null;
        keyCards.clear();
        toolbar = null; toolbarBounds = null;
        placeButtons();
        arrangeOverview();
        if (routed != null) for (HelpLeaderRouter.Placement p : routed.placements) {
            if (p.page != 0) continue;
            TextView view = cardViews.get(p.target.id);
            if (view == null) continue;
            put(view, rect(p.card));
            touchable.add(view);
        }
        for (KeyCard key : keyCards) { put(key.view, key.bounds); touchable.add(key.view); }
        if (buttons != null && buttonsBounds != null) {
            put(buttons, buttonsBounds);
            touchable.add(buttons);
        }
    }

    private void renderExplore() {
        routed = null;
        unplaced.clear();
        cardViews.clear();
        cardSpecs.clear();
        buttons = null;
        buttonsBounds = null;
        HelpTopics.Entry selected = model.selected();
        // The toolbar first: it is the one thing the markers and the card have to work round.
        placeToolbar(selected);
        arrangeMarkers();
        keyCards.clear();
        if (selected == null) {
            dropCard();
        } else if (!seatCard(selected)) {
            String topicId = selected.id;
            HelpLog.d("no seat for " + topicId + "'s card on this screen");
            model.clearSelection();
            dropCard();
            boolean first = !reportedNoSeat;
            reportedNoSeat = true;
            if (first && listener != null) listener.onCardDoesNotFit(topicId);
        }
        for (Marker marker : markers) { put(marker.view, marker.bounds); touchable.add(marker.view); }
        for (KeyCard key : keyCards) { put(key.view, key.bounds); touchable.add(key.view); }
        if (card != null && cardBounds != null) { put(card, cardBounds); touchable.add(card); }
        if (toolbar != null && toolbarBounds != null) { put(toolbar, toolbarBounds); touchable.add(toolbar); }
    }

    // ---- the overview -----------------------------------------------------------------------

    /**
     * The curated cards, seated all at once. Each card takes the shelf between its own control and
     * the wall, in the control's own colour, with one line joining the two; the router keeps them
     * off each other, off the controls they explain, off the key labels and off the two buttons.
     *
     * <p>Run on a layout change, never per frame: the cards are measured once and then only moved.
     */
    private void arrangeOverview() {
        Map<String, TextView> wasView = new HashMap<>(cardViews);
        Map<String, String> wasSpec = new HashMap<>(cardSpecs);
        cardViews.clear(); cardSpecs.clear(); boxColors.clear(); unplaced.clear();
        routed = null;
        boolean light = lightMode();
        Rect band = band();
        int wide = Math.max(1, (snapshot.wall.width() - dp(36)) / 2);
        // A control outside the wall often shares a shelf with another — the prefix keys and the
        // settings cog both sit in the keyboard's bottom row — so its card is a third of the wall's
        // width where a card inside the wall is a half.
        int narrow = Math.max(dp(100), (snapshot.wall.width() - dp(48)) / 3);
        List<HelpLeaderRouter.Target> inputs = new ArrayList<>();
        List<HelpLeaderRouter.Box> boxed = new ArrayList<>();
        List<HelpLeaderRouter.Box> soft = new ArrayList<>();
        Set<String> carded = new HashSet<>();
        for (HelpTopics.Entry entry : model.overview()) {
            Rect target = targetRect(entry.targetId);
            if (target == null) continue;
            carded.add(entry.targetId);
            int color = model.markerColor(accent, entry.id, light);
            boxColors.put(entry.targetId, color);
            int titleColor = model.titleColor(accent, entry.id, dress.fillColor);
            HelpLeaderRouter.Side side = side(target);
            int width = side == HelpLeaderRouter.Side.INSIDE ? wide : narrow;
            String title = getContext().getString(entry.titleRes);
            String body = getContext().getString(overviewAction(entry, side));
            // The same words in the same colours are the same card: a control that moved moves its
            // card, and moving one is not rebuilding it.
            String spec = spec(title, body, titleColor, color);
            TextView view = spec.equals(wasSpec.get(entry.targetId)) ? wasView.get(entry.targetId) : null;
            if (view == null) view = overviewCard(entry, title, body, titleColor, color);
            view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            cardViews.put(entry.targetId, view);
            cardSpecs.put(entry.targetId, spec);
            inputs.add(new HelpLeaderRouter.Target(entry.targetId, box(target), side,
                width, view.getMeasuredHeight()));
            (side == HelpLeaderRouter.Side.INSIDE ? soft : boxed).add(box(target));
        }
        arrangeKeyCards(light, band);
        if (inputs.isEmpty()) return;
        // The key labels and the two buttons are seated by now, and no card may land on either: a
        // label under a card is a label nobody can read, and a button under one cannot be pressed.
        List<HelpLeaderRouter.Box> fixed = new ArrayList<>();
        for (KeyCard key : keyCards) fixed.add(box(key.bounds));
        if (buttonsBounds != null) fixed.add(box(buttonsBounds));
        // A control with a card of its own is never covered — a card over it hides the very thing
        // it is about. One without a card may be, but only when nothing else fits.
        for (HelpTargets.Target target : snapshot.targets)
            if (!carded.contains(target.id)) soft.add(box(target.rect));
        List<HelpLeaderRouter.Box> hard = new ArrayList<>(fixed);
        hard.addAll(boxed);
        routed = HelpLeaderRouter.arrange(box(band), dp(12), dp(12), inputs, hard, soft);
        // One page, always: on a screen too tight for every card to keep clear of every control,
        // the controls give way rather than a card leave the overview.
        if (!boxed.isEmpty() && !onOnePage(routed)) {
            List<HelpLeaderRouter.Box> yielding = new ArrayList<>(soft);
            yielding.addAll(boxed);
            routed = HelpLeaderRouter.arrange(box(band), dp(12), dp(12), inputs, fixed, yielding);
        }
        for (HelpLeaderRouter.Target target : routed.unplaced) unplaced.add(target.id);
        for (HelpLeaderRouter.Placement p : routed.placements) if (p.page > 0) unplaced.add(p.target.id);
        for (String id : unplaced) HelpLog.d("left out of the overview: " + id + ", no room on the page");
        HelpLog.d("overview " + place + ": " + inputs.size() + " cards, " + keyCards.size()
            + " key labels, " + unplaced.size() + " left out");
    }

    /**
     * The one sentence on a control's card: the topic's own instruction, except for a dock that is
     * a rail down one edge of the wall, which is swiped inward off the rail rather than pulled down.
     */
    private int overviewAction(HelpTopics.Entry entry, HelpLeaderRouter.Side side) {
        if ("dock".equals(entry.targetId) && (side == HelpLeaderRouter.Side.LEFT
                || side == HelpLeaderRouter.Side.RIGHT)) return R.string.help_dock_rail_action;
        return entry.actionRes;
    }

    /** What a card is made of; two cards with the same recipe are the same card. */
    private String spec(String title, String body, int titleColor, int borderColor) {
        return title + "" + body + "" + titleColor + ":" + borderColor + ":"
            + dress.fillColor + ":" + dress.strokeColor + ":" + dress.textColor;
    }

    private static boolean onOnePage(HelpLeaderRouter.Result result) {
        if (!result.unplaced.isEmpty()) return false;
        for (HelpLeaderRouter.Placement p : result.placements) if (p.page > 0) return false;
        return true;
    }

    /** One control's card, in its own colour, and a tap on it opens that control's topic. */
    private TextView overviewCard(HelpTopics.Entry entry, String title, String body,
                                  int titleColor, int borderColor) {
        TextView text = new TextView(getContext());
        SpannableString content = new SpannableString(title + "\n" + body);
        content.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        content.setSpan(new ForegroundColorSpan(titleColor), 0, title.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setText(content);
        text.setTextSize(12);
        text.setTextColor(dress.textColor);
        text.setPadding(dp(8), dp(6), dp(8), dp(6));
        text.setLineSpacing(dp(1), 1);
        text.setContentDescription(title + ". " + body);
        android.graphics.drawable.Drawable background = dress.background(0);
        if (background instanceof GradientDrawable)
            ((GradientDrawable) background).setStroke(dp(1.5f), borderColor);
        text.setBackground(background);
        text.setClickable(true);
        text.setFocusable(true);
        final String topicId = entry.id;
        text.setOnClickListener(v -> { if (listener != null) listener.onReadTopic(topicId); });
        return text;
    }

    /**
     * The overview's own chrome: × and Guide, in the corner of the wall that covers the fewest
     * controls, the foot of the screen first. A corner rather than mid-screen, where two buttons
     * read as one more card; and placed before any card, so nothing is laid out under them.
     */
    /** The square a thumb asks for, per button; the pair is two of them in one capsule. */
    private static final int GLYPH_SIZE_DP = 48;
    /** How far the capsule stays in from the wall's edges. */
    private static final int GLYPH_MARGIN_DP = 6;

    /**
     * The × and the guide button: help's own chrome, one capsule in a corner of the wall — the
     * same size and glass a corner tab's buttons wear — in whichever corner covers the fewest
     * controls, the foot of the wall first.
     */
    private void placeButtons() {
        if (buttons == null) buttons = buttons();
        Rect safe = safeArea();
        int height = dp(GLYPH_SIZE_DP);
        int width = height * 2;
        buttons.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        int margin = dp(GLYPH_MARGIN_DP);
        Rect wall = snapshot.wall;
        int left = clamp(wall.left + margin, safe.left, safe.right - width);
        int right = clamp(wall.right - margin - width, safe.left, safe.right - width);
        int top = clamp(wall.top + margin, safe.top, safe.bottom - height);
        int bottom = clamp(wall.bottom - margin - height, safe.top, safe.bottom - height);
        int[][] corners = {{right, bottom}, {left, bottom}, {right, top}, {left, top}};
        int least = Integer.MAX_VALUE;
        for (int[] corner : corners) {
            Rect seat = new Rect(corner[0], corner[1], corner[0] + width, corner[1] + height);
            int covered = covered(seat);
            if (covered < least) { least = covered; buttonsBounds = seat; }
            if (least == 0) break;
        }
    }

    /** The capsule: the × and the guide glyph, two round tab-style buttons sharing one pill. */
    private LinearLayout buttons() {
        Context context = getContext();
        TextView close = glyphButton(context.getString(R.string.help_close_glyph), false,
            context.getString(R.string.help_close_action),
            () -> { if (listener != null) listener.onCloseHelp(); });
        TextView guide = glyphButton(CornerTabGlyphs.CATALOGUE, true,
            context.getString(R.string.help_overview_guide_action),
            () -> { if (listener != null) listener.onOpenGuide(); });
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClickable(true);
        int size = dp(GLYPH_SIZE_DP);
        row.addView(close, new LinearLayout.LayoutParams(size, size));
        row.addView(guide, new LinearLayout.LayoutParams(size, size));
        row.setBackground(glyphGroupBackground());
        return row;
    }

    /** The pill the two buttons share: the tab's glass, so the pair reads as one piece. */
    private GradientDrawable glyphGroupBackground() {
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int surface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));
        GradientDrawable capsule = new GradientDrawable();
        capsule.setShape(GradientDrawable.RECTANGLE);
        capsule.setCornerRadius(dp(GLYPH_SIZE_DP) / 2f);
        capsule.setColor(ColorUtils.setAlphaComponent(surface, 200));
        capsule.setStroke(dp(CornerTabGeometry.TAB_OUTLINE_DP), ColorUtils.setAlphaComponent(primary, 120));
        return capsule;
    }

    /** One round button: the corner tab's own glass and tint, inside a thumb's square. */
    private TextView glyphButton(String glyph, boolean symbols, String description, Runnable onClick) {
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int surface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));
        // Neither × nor a Nerd Font glyph sits in the middle of its line box, so the glyph is
        // drawn on its own ink bounds, centred on the view.
        TextView view = new TextView(context) {
            private final Rect ink = new Rect();
            @Override protected void onDraw(Canvas canvas) {
                CharSequence text = getText();
                if (text == null || text.length() == 0) return;
                String s = text.toString();
                Paint p = getPaint();
                p.setColor(getCurrentTextColor());
                p.getTextBounds(s, 0, s.length(), ink);
                canvas.drawText(s, getWidth() / 2f - ink.exactCenterX(),
                    getHeight() / 2f - ink.exactCenterY(), p);
            }
        };
        view.setText(glyph);
        view.setContentDescription(description);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(symbols ? NerdFontSpans.typeface(context) : Typeface.DEFAULT_BOLD);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, symbols ? 14 : 18);
        view.setTextColor(primary);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(ColorUtils.setAlphaComponent(surface, 232));
        shape.setStroke(dp(CornerTabGeometry.TAB_OUTLINE_DP), ColorUtils.setAlphaComponent(primary, 225));
        // The circle is a tab button's 30dp; the square around it is the 48dp a thumb asks for.
        view.setBackground(new InsetDrawable(shape, dp(9)));
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(v -> onClick.run());
        return view;
    }

    // ---- the markers ------------------------------------------------------------------------

    /**
     * A marker on every measured control that has a topic: a numbered dot on the control's own
     * outline, in the control's colour, named for a reader. Colour is never the only cue — the dot
     * carries a number and the name is on the marker — and two markers never sit on each other.
     */
    private void arrangeMarkers() {
        Map<String, TextView> was = new HashMap<>(markerViews);
        Map<String, String> wasSpec = new HashMap<>(markerSpecs);
        markers.clear();
        markerViews.clear();
        markerSpecs.clear();
        boolean light = lightMode();
        List<HelpTopics.Entry> entries = model.markers();
        List<Rect> taken = new ArrayList<>();
        if (toolbarBounds != null) taken.add(toolbarBounds);
        for (int i = 0; i < entries.size(); i++) {
            HelpTopics.Entry entry = entries.get(i);
            Rect target = targetRect(entry.targetId);
            if (target == null) continue;
            int color = model.markerColor(accent, entry.id, light);
            String label = String.valueOf(i + 1);
            String name = getContext().getString(entry.titleRes);
            String spec = label + ":" + color + ":" + light;
            TextView view = spec.equals(wasSpec.get(entry.targetId)) ? was.get(entry.targetId) : null;
            if (view == null) view = marker(label, color);
            view.setContentDescription(name);
            final String targetId = entry.targetId;
            view.setOnClickListener(v -> select(targetId, false));
            Rect bounds = markerBounds(target, taken);
            taken.add(bounds);
            markers.add(new Marker(entry, target, view, bounds, color));
            markerViews.put(entry.targetId, view);
            markerSpecs.put(entry.targetId, spec);
        }
        HelpLog.d("markers: " + markers.size() + " of " + snapshot.targets.size()
            + " measured controls on " + place);
    }

    /**
     * Where one marker sits: on its control's own outline, at the first anchor no other marker and
     * no toolbar has taken, and always inside the screen. On a screen tight enough that every
     * anchor is taken it goes to the least covered one rather than the first, so two dots that
     * cannot avoid each other still overlap as little as they can.
     */
    private Rect markerBounds(Rect target, List<Rect> taken) {
        int size = dp(MARKER_DP);
        int inset = dp(2);
        int[][] anchors = {
            {target.left - inset, target.top - inset},
            {target.right + inset - size, target.top - inset},
            {target.left - inset, target.bottom + inset - size},
            {target.right + inset - size, target.bottom + inset - size},
            {target.centerX() - size / 2, target.top - inset},
            {target.centerX() - size / 2, target.bottom + inset - size},
            {target.left - inset, target.centerY() - size / 2},
            {target.right + inset - size, target.centerY() - size / 2},
            {target.centerX() - size / 2, target.centerY() - size / 2},
        };
        Rect best = null;
        long least = Long.MAX_VALUE;
        for (int[] anchor : anchors) {
            int left = clamp(anchor[0], dp(2), Math.max(dp(2), getWidth() - size - dp(2)));
            int top = clamp(anchor[1], dp(2), Math.max(dp(2), getHeight() - size - dp(2)));
            Rect bounds = new Rect(left, top, left + size, top + size);
            long covered = 0;
            for (Rect other : taken) {
                int w = Math.min(bounds.right, other.right) - Math.max(bounds.left, other.left);
                int h = Math.min(bounds.bottom, other.bottom) - Math.max(bounds.top, other.top);
                if (w > 0 && h > 0) covered += (long) w * h;
            }
            if (covered == 0) return bounds;
            if (covered < least) { least = covered; best = bounds; }
        }
        return best;
    }

    /** One marker: its number in its control's colour, with the control's name for a reader. */
    private TextView marker(String label, int color) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(HelpPalette.lightSurface(color) ? Color.BLACK : Color.WHITE);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        shape.setStroke(dp(1.5f), ColorUtils.setAlphaComponent(
            lightMode() ? Color.WHITE : Color.BLACK, 160));
        view.setBackground(shape);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    // ---- the one card -----------------------------------------------------------------------

    /**
     * The card for the selected control: its title, its one instruction and "Read topic", seated
     * where it covers neither the control, nor the toolbar, nor the system bars. The extra keys row
     * brings its seven per-key cards with it, and they are seated first.
     *
     * @return false when this screen has no seat for it at a readable size.
     */
    private boolean seatCard(HelpTopics.Entry entry) {
        Rect target = targetRect(entry.targetId);
        if (target == null) return false;
        if (card == null || !entry.id.equals(cardTopicId)) {
            card = card(entry);
            cardTopicId = entry.id;
        }
        if ("keys".equals(entry.targetId)) arrangeKeyCards(lightMode(), band());
        List<HelpLeaderRouter.Box> reserved = new ArrayList<>();
        if (toolbarBounds != null) reserved.add(box(toolbarBounds));
        for (KeyCard key : keyCards) reserved.add(box(key.bounds));
        List<HelpLeaderRouter.Box> others = new ArrayList<>();
        for (HelpTargets.Target other : snapshot.targets)
            if (!other.id.equals(entry.targetId)) others.add(box(other.rect));
        Rect safe = safeArea();
        // Narrower before nowhere: the same words in a narrower card are still the app's own
        // reading size, and shrinking the text is never one of the answers.
        for (int width : cardWidths(safe)) {
            card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(safe.height(), MeasureSpec.AT_MOST));
            int height = card.getMeasuredHeight();
            if (height > safe.height()) continue;
            HelpExplorePlacement.Result seat = HelpExplorePlacement.place(
                new HelpExplorePlacement.Request(box(safe), box(target), reserved, others,
                    width, height, dp(10)));
            if (!seat.fits()) continue;
            cardBounds = rect(seat.card);
            cardLeader = seat.leader;
            HelpLog.d("card for " + entry.id + ": " + seat.seat + " at " + cardBounds.toShortString()
                + (seat.leader == null ? ", no leader" : ", one leader"));
            return true;
        }
        return false;
    }

    /** The widths a card may be asked to fit in, widest first. */
    private List<Integer> cardWidths(Rect safe) {
        int widest = Math.min(dp(320), safe.width() - dp(24));
        List<Integer> widths = new ArrayList<>();
        for (int width : new int[] {widest, widest * 3 / 4, dp(180)}) {
            if (width >= dp(140) && width <= safe.width() - dp(16) && !widths.contains(width))
                widths.add(width);
        }
        if (widths.isEmpty() && safe.width() > dp(80)) widths.add(safe.width() - dp(16));
        return widths;
    }

    /** One control's card: what it is, the one thing to do with it, and the way into its topic. */
    private LinearLayout card(HelpTopics.Entry entry) {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.Drawable background = dress.background(0);
        if (background instanceof GradientDrawable)
            ((GradientDrawable) background).setStroke(dp(1.5f),
                model.markerColor(accent, entry.id, lightMode()));
        panel.setBackground(background);
        TextView heading = new TextView(getContext());
        heading.setText(getContext().getString(entry.titleRes));
        heading.setTextSize(14);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        heading.setTextColor(model.titleColor(accent, entry.id, dress.fillColor));
        panel.addView(heading, rowParams(0));
        TextView action = new TextView(getContext());
        action.setText(getContext().getString(entry.actionRes));
        action.setTextSize(13);
        action.setTextColor(dress.textColor);
        action.setLineSpacing(dp(2), 1f);
        panel.addView(action, rowParams(dp(4)));
        panel.addView(button(getContext().getString(R.string.help_explore_read),
            () -> { if (listener != null) listener.onReadTopic(entry.id); }), rowParams(dp(8)));
        return panel;
    }

    // ---- the toolbar ------------------------------------------------------------------------

    /**
     * Back to help and Close help, inside the system bars, at the edge farthest from the control
     * being explained — so the card has the room beside its own control, and the toolbar is never
     * on top of either.
     */
    private void placeToolbar(HelpTopics.Entry selected) {
        if (toolbar == null) toolbar = toolbar();
        Rect safe = safeArea();
        int width = Math.min(safe.width() - dp(16), dp(360));
        toolbar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(safe.height(), MeasureSpec.AT_MOST));
        int height = toolbar.getMeasuredHeight();
        int left = safe.centerX() - width / 2;
        Rect low = new Rect(left, safe.bottom - dp(8) - height, left + width, safe.bottom - dp(8));
        Rect high = new Rect(left, safe.top + dp(8), left + width, safe.top + dp(8) + height);
        Rect target = selected == null ? null : targetRect(selected.targetId);
        if (target == null) {
            // Nothing selected yet: the edge that covers fewer controls, the foot on a tie.
            toolbarBounds = covered(high) < covered(low) ? high : low;
            return;
        }
        // The edge farthest from the control, and the near edge only when the far one would land
        // on the control itself.
        boolean farIsHigh = target.centerY() > safe.centerY();
        Rect far = farIsHigh ? high : low, near = farIsHigh ? low : high;
        toolbarBounds = !Rect.intersects(far, target) ? far
            : !Rect.intersects(near, target) ? near : far;
        if (Rect.intersects(toolbarBounds, target))
            HelpLog.d("the toolbar has nowhere clear of " + selected.id + "'s control");
    }

    /** How many measured controls a toolbar seat would lie over. */
    private int covered(Rect seat) {
        int n = 0;
        if (snapshot != null) for (HelpTargets.Target t : snapshot.targets) if (Rect.intersects(seat, t.rect)) n++;
        return n;
    }

    private LinearLayout toolbar() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClickable(true);
        row.setPadding(dp(6), dp(6), dp(6), dp(6));
        row.setBackground(dress.background(dp(28)));
        row.addView(button(getContext().getString(R.string.help_explore_back),
            () -> { if (listener != null) listener.onBackToHelp(); }), weighted());
        row.addView(button(getContext().getString(R.string.help_close_action),
            () -> { if (listener != null) listener.onCloseHelp(); }), weighted());
        return row;
    }

    // ---- the extra keys ---------------------------------------------------------------------

    /** Help's own chrome this pass: the exploration toolbar, or the overview's two buttons. */
    private Rect chrome() {
        return mode == Mode.OVERVIEW ? buttonsBounds : toolbarBounds;
    }

    /** The wall, with a little room kept at top and bottom for the key cards' lanes. */
    private Rect band() {
        Rect band = new Rect(snapshot.wall);
        band.top += dp(8);
        band.bottom = Math.max(band.top, band.bottom - dp(8));
        return band;
    }

    /**
     * A card for every extra key: what the key does, and what a swipe up on it does. Seven of them
     * across a phone are too tight for one line, so they alternate between two lanes, and each card
     * takes the room between its neighbours' key centres — which leaves a lane over every key for
     * the other lane's leader to come through. The lanes run along the keys, as rows for a row of
     * keys and as columns for a column of them down one side, and lie on whichever side of the keys
     * has room for both: the side away from the wall first — the keyboard, usually, the biggest
     * washed space on the screen and right there — and over the wall's edge otherwise.
     */
    private void arrangeKeyCards(boolean light, Rect band) {
        Map<Integer, TextView> wasView = new HashMap<>(keyCardViews);
        Map<Integer, String> wasSpec = new HashMap<>(keyCardSpecs);
        keyCards.clear(); keyCardViews.clear(); keyCardSpecs.clear();
        List<HelpTargets.KeyLabel> keys = snapshot.keys;
        if (keys.isEmpty() || getWidth() <= 0) {
            HelpLog.d("key cards: none, " + keys.size() + " keys measured on a "
                + getWidth() + "px overlay");
            return;
        }
        keyColor = model.markerColor(accent, "keys", light);
        int titleColor = model.titleColor(accent, "keys", dress.fillColor);
        // The keys' axis: a row runs along x, a column down one side runs along y. Everything
        // below is measured along that axis and across it, so neither edge is assumed.
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (HelpTargets.KeyLabel key : keys) {
            minX = Math.min(minX, key.rect.centerX()); maxX = Math.max(maxX, key.rect.centerX());
            minY = Math.min(minY, key.rect.centerY()); maxY = Math.max(maxY, key.rect.centerY());
        }
        boolean vertical = maxY - minY > maxX - minX;
        int keysNear = Integer.MAX_VALUE, keysFar = 0;
        int alongKeysLo = Integer.MAX_VALUE, alongKeysHi = Integer.MIN_VALUE;
        for (HelpTargets.KeyLabel key : keys) {
            keysNear = Math.min(keysNear, vertical ? key.rect.left : key.rect.top);
            keysFar = Math.max(keysFar, vertical ? key.rect.right : key.rect.bottom);
            alongKeysLo = Math.min(alongKeysLo, vertical ? key.rect.top : key.rect.left);
            alongKeysHi = Math.max(alongKeysHi, vertical ? key.rect.bottom : key.rect.right);
        }
        int alongLo = dp(12);
        int alongHi = (vertical ? getHeight() - bottomInset() : getWidth()) - dp(12);
        // Help's own chrome is not a control, so it is not in the snapshot, and the cards have to
        // be told about it the way the one card's placement is: a region they may not land on.
        Rect chrome = chrome();
        if (chrome != null) {
            int[] span = keyCardSpan(alongLo, alongHi, alongKeysLo, alongKeysHi,
                vertical ? chrome.top : chrome.left,
                vertical ? chrome.bottom : chrome.right, dp(6));
            alongLo = span[0];
            alongHi = span[1];
        }
        // What the cards may not land on: the other controls, and help's own chrome, which is not
        // a control and so is not in the snapshot.
        List<Rect> blocked = new ArrayList<>();
        for (HelpTargets.Target target : snapshot.targets) blocked.add(target.rect);
        if (chrome != null) blocked.add(chrome);
        // The wall lies on one side of the keys; away is the other, and the cards may reach that
        // way as far as the first thing on the list, and never under the gesture pill, which help
        // washes over but puts nothing beneath.
        int wallMid = vertical ? snapshot.wall.centerX() : snapshot.wall.centerY();
        boolean awayIsHigh = (keysNear + keysFar) / 2 >= wallMid;
        int awayLimit;
        if (awayIsHigh) {
            awayLimit = Math.max(keysFar, (vertical ? getWidth() : getHeight() - bottomInset()) - dp(8));
            for (Rect rect : blocked) {
                int edge = vertical ? rect.left : rect.top;
                if (edge >= keysFar) awayLimit = Math.min(awayLimit, edge - dp(6));
            }
        } else {
            awayLimit = Math.min(keysNear, dp(8));
            for (Rect rect : blocked) {
                int edge = vertical ? rect.right : rect.bottom;
                if (edge <= keysNear) awayLimit = Math.max(awayLimit, edge + dp(6));
            }
        }
        int wallLimit = awayIsHigh ? (vertical ? band.left : band.top) : (vertical ? band.right : band.bottom);
        // Toward the wall the lanes lean on the keys unless another control — the dock, the status
        // bar — stands between the keys and the wall; then they lean on the far side of that
        // control instead, and the leaders cross it. A card over a control hides it; a line does not.
        int wallNear = awayIsHigh ? keysNear : keysFar;
        for (Rect rect : blocked) {
            int low = vertical ? rect.left : rect.top;
            int high = vertical ? rect.right : rect.bottom;
            if (awayIsHigh && high <= keysNear && low >= wallLimit) wallNear = Math.min(wallNear, low - dp(6));
            if (!awayIsHigh && low >= keysFar && high <= wallLimit) wallNear = Math.max(wallNear, high + dp(6));
        }
        int[] centers = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++)
            centers[i] = vertical ? keys.get(i).rect.centerY() : keys.get(i).rect.centerX();
        int[][] slots = keyCardSlots(centers, alongLo, alongHi, dp(10));
        List<TextView> views = new ArrayList<>();
        List<String> specs = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        // A row's cards fill their slots and are as tall as the tallest; a column's cards are one
        // lane wide and as long as their own text.
        int laneWidth = Math.max(dp(96), Math.min(dp(132), (snapshot.wall.width() - dp(48)) / 3));
        int thickness = vertical ? laneWidth : 0;
        for (int i = 0; i < keys.size(); i++) {
            HelpTargets.KeyLabel key = keys.get(i);
            int room = slots[i][1] - slots[i][0];
            // Too many keys for a card each; the row's own card says what the row is for instead.
            if (room < dp(56)) {
                HelpLog.d("key cards: none, key " + i + " has only " + room + "px of shelf");
                keyCards.clear();
                return;
            }
            String spec = key.text + "|" + titleColor + ":" + keyColor + ":" + dress.fillColor;
            TextView keyCard = spec.equals(wasSpec.get(i)) ? wasView.get(i) : null;
            if (keyCard == null) keyCard = keyCard(key, titleColor, keyColor);
            measureKeyCard(keyCard, vertical ? laneWidth : room, key.secondary == null ? 1 : 2);
            if (vertical) {
                if (keyCard.getMeasuredHeight() > room) {
                    HelpLog.d("key cards: none, key " + i + " needs " + keyCard.getMeasuredHeight()
                        + "px of a " + room + "px slot");
                    keyCards.clear();
                    return;
                }
                lengths.add(keyCard.getMeasuredHeight());
            } else {
                lengths.add(keyCard.getMeasuredHeight());
                thickness = Math.max(thickness, keyCard.getMeasuredHeight());
            }
            views.add(keyCard);
            specs.add(spec);
        }
        int[] lanes = keyCardLanes(awayIsHigh ? wallNear : keysNear, awayIsHigh ? keysFar : wallNear,
            wallLimit, awayLimit, awayIsHigh, thickness, dp(10), dp(14));
        boolean away = lanes[2] == 1;
        boolean cardsHigh = away == awayIsHigh;
        for (int i = 0; i < views.size(); i++) {
            int start = i % 2 == 0 ? lanes[0] : lanes[1];
            Rect bounds;
            if (vertical) {
                int length = lengths.get(i);
                int along = Math.max(slots[i][0], Math.min(slots[i][1] - length, centers[i] - length / 2));
                bounds = new Rect(start, along, start + thickness, along + length);
            } else {
                // A card is only as tall as its own text: a key with no swipe-up action has one
                // line, and it hugs the lane's edge nearest the keys so its leader stays short.
                int height = lengths.get(i);
                int top = cardsHigh ? start + thickness - height : start;
                bounds = new Rect(slots[i][0], top, slots[i][1], top + height);
            }
            Rect cap = keys.get(i).rect;
            keyCards.add(new KeyCard(views.get(i), bounds, cap, keyLeader(bounds, cap, vertical, cardsHigh)));
            keyCardViews.put(i, views.get(i));
            keyCardSpecs.put(i, specs.get(i));
        }
        // Whatever the lanes worked out, help's own chrome keeps its room: a label under a button
        // is a label the reader cannot read, and the row's own card still says what the row does.
        if (chrome != null) {
            for (KeyCard key : keyCards) {
                if (!Rect.intersects(key.bounds, chrome)) continue;
                HelpLog.d("key cards: none, help's own buttons have the room they need at "
                    + key.bounds.toShortString());
                keyCards.clear(); keyCardViews.clear(); keyCardSpecs.clear();
                return;
            }
        }
        HelpLog.d("key cards: " + keyCards.size() + (vertical ? " beside the column" : " along the row")
            + " at " + keysNear + "-" + keysFar + (away ? ", away from the wall" : ", toward the wall")
            + ", lanes at " + lanes[0] + " and " + lanes[1] + ", " + thickness + "px thick, away limit "
            + awayLimit);
    }

    /**
     * The room the cards have along the keys' axis, once a region they may not land on — the
     * exploration toolbar — is taken out of it. A region past one end of the keys takes that end;
     * a region straddling the keys is left to the lanes across the axis, which already keep clear
     * of it, because trimming there would cost every card its slot.
     */
    @VisibleForTesting
    static int[] keyCardSpan(int lo, int hi, int keysLo, int keysHi,
                             int blockedLo, int blockedHi, int clearance) {
        if (blockedHi <= keysLo) return new int[] {Math.max(lo, blockedHi + clearance), hi};
        if (blockedLo >= keysHi) return new int[] {lo, Math.min(hi, blockedLo - clearance)};
        return new int[] {lo, hi};
    }

    /**
     * Where the two lanes of key cards start, across the keys' axis: the lane against the keys
     * first, then the one behind it, then 1 when both lie on the side away from the wall and 0 when
     * they lie on the wall's. {@link #keyCardRows} answers for keys whose away side is the high one
     * — a row above the keyboard; keys whose away side is the low one — a row along the top, a
     * column whose wall is to its right — ask the same question in a mirror and turn the answer
     * back.
     */
    @VisibleForTesting
    static int[] keyCardLanes(int keysNear, int keysFar, int wallLimit, int awayLimit,
                              boolean awayIsHigh, int thickness, int gap, int leader) {
        if (awayIsHigh) return keyCardRows(keysNear, keysFar, wallLimit, awayLimit, thickness, gap, leader);
        int[] mirrored = keyCardRows(-keysFar, -keysNear, -wallLimit, -awayLimit, thickness, gap, leader);
        return new int[] {-(mirrored[0] + thickness), -(mirrored[1] + thickness), mirrored[2]};
    }

    /**
     * Where the two rows of key cards go: the row against the keys first, then the one behind it,
     * then 1 when both sit under the keys and 0 when they sit over them. Under is the first
     * answer — the keyboard is the biggest washed space on the screen and it is right there — and
     * over is what is left when the keyboard's own keys start too close to the row.
     */
    @VisibleForTesting
    static int[] keyCardRows(int keysTop, int keysBottom, int ceiling, int floor,
                             int rowHeight, int gap, int leader) {
        if (floor - keysBottom >= 2 * rowHeight + gap + leader) {
            int near = keysBottom + leader;
            return new int[] {near, near + rowHeight + gap, 1};
        }
        int near = keysTop - leader - rowHeight;
        int far = near - gap - rowHeight;
        if (far < ceiling) {
            far = ceiling;
            near = Math.max(far + rowHeight + gap, near);
        }
        return new int[] {near, far, 0};
    }

    /**
     * The room each key's card gets: from its left neighbour's key centre to its right
     * neighbour's, less the clearance that keeps a lane open over every key. Cards of one row
     * never meet, because between any two of them lies the key whose card is in the other row —
     * and that key's leader comes down the lane between them.
     */
    @VisibleForTesting
    static int[][] keyCardSlots(int[] centers, int left, int right, int clearance) {
        int[][] slots = new int[centers.length][2];
        for (int i = 0; i < centers.length; i++) {
            slots[i][0] = Math.max(left, i == 0 ? left : centers[i - 1] + clearance);
            slots[i][1] = Math.min(right, i == centers.length - 1 ? right : centers[i + 1] - clearance);
        }
        return slots;
    }

    /**
     * From the card to the cap it names, straight when it can be and with one step when not. The
     * line runs across the keys' axis, from the card's edge that faces the keys to the cap's edge
     * that faces the cards.
     */
    private List<float[]> keyLeader(Rect card, Rect cap, boolean vertical, boolean cardsHigh) {
        List<float[]> lines = new ArrayList<>();
        float from = vertical ? (cardsHigh ? card.left : card.right) : (cardsHigh ? card.top : card.bottom);
        float to = vertical ? (cardsHigh ? cap.right : cap.left) : (cardsHigh ? cap.bottom : cap.top);
        int c = vertical ? cap.centerY() : cap.centerX();
        int lo = (vertical ? card.top : card.left) + dp(8), hi = (vertical ? card.bottom : card.right) - dp(8);
        int l = Math.max(lo, Math.min(hi, c));
        if (l == c) {
            lines.add(vertical ? new float[] {from, c, to, c} : new float[] {c, from, c, to});
            return lines;
        }
        float mid = (from + to) / 2f;
        if (vertical) {
            lines.add(new float[] {from, l, mid, l});
            lines.add(new float[] {mid, l, mid, c});
            lines.add(new float[] {mid, c, to, c});
        } else {
            lines.add(new float[] {l, from, l, mid});
            lines.add(new float[] {l, mid, c, mid});
            lines.add(new float[] {c, mid, c, to});
        }
        return lines;
    }

    /**
     * Measure a key card at {@code width}; when a word has to break to fit — the card at either end
     * of the row only has the room from the screen's edge to its neighbour — the text steps down a
     * size at a time, to 10sp, before the word is allowed to break.
     */
    private void measureKeyCard(TextView card, int width, int lines) {
        for (float sp = 12f; ; sp -= 1f) {
            card.setTextSize(sp);
            card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (card.getLineCount() <= lines || sp <= 10f) return;
        }
    }

    /** One key's card: what it does in the row's colour, and its swipe under it. */
    private TextView keyCard(HelpTargets.KeyLabel key, int titleColor, int borderColor) {
        TextView text = new TextView(getContext());
        String all = key.secondary == null ? key.primary : key.primary + "\n" + key.secondary;
        SpannableString content = new SpannableString(all);
        content.setSpan(new StyleSpan(Typeface.BOLD), 0, key.primary.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        content.setSpan(new ForegroundColorSpan(titleColor), 0, key.primary.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setText(content);
        text.setTextSize(12);
        text.setTextColor(dress.textColor);
        text.setGravity(Gravity.CENTER_HORIZONTAL);
        text.setPadding(dp(6), dp(5), dp(6), dp(5));
        text.setLineSpacing(dp(1), 1);
        text.setMaxLines(3);
        text.setContentDescription(all.replace('\n', ' '));
        android.graphics.drawable.Drawable background = dress.background(0);
        if (background instanceof GradientDrawable)
            ((GradientDrawable) background).setStroke(dp(1.5f), borderColor);
        text.setBackground(background);
        return text;
    }

    // ---- the gesture demonstration ----------------------------------------------------------

    /**
     * One finite pass of the topic's own gesture over its control; exploration stays up throughout.
     * A topic with no gesture plays nothing and simply shows its card.
     */
    private void startGesture(HelpTopics.Entry entry) {
        Rect rect = targetRect(entry.targetId);
        stopGesture();
        if (rect == null) return;
        TourGesture wanted = gestureFor(entry, rect);
        if (wanted == TourGesture.NONE) {
            HelpLog.d("no gesture for " + entry.id);
            return;
        }
        gestureRect = new Rect(rect);
        gesture = wanted;
        gestureReducedMotion = ReducedMotion.isEnabled(getContext());
        if (gestureReducedMotion) {
            // No animation at all on this phone: the cue is drawn where the gesture starts and
            // where it ends, and stays there while exploration is up.
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

    /**
     * The movement the topic carries, turned toward the control as it is actually laid out: a dock
     * that is a rail down one edge is swiped inward off the rail rather than pulled down.
     */
    @VisibleForTesting
    TourGesture gestureFor(HelpTopics.Entry entry, Rect rect) {
        if (entry == null || entry.gesture == null) return TourGesture.NONE;
        if (entry.gesture == TourGesture.DRAG_DOWN && rect != null && snapshot != null) {
            HelpLeaderRouter.Side side = side(rect);
            if (side == HelpLeaderRouter.Side.LEFT) return TourGesture.SWIPE_RIGHT;
            if (side == HelpLeaderRouter.Side.RIGHT) return TourGesture.SWIPE_LEFT;
        }
        return entry.gesture;
    }

    private void stopGesture() {
        if (gestureTrace != null) { gestureTrace.cancel(); gestureTrace = null; }
        gestureRect = null;
        gesture = TourGesture.NONE;
        gestureProgress = 1f;
    }

    /** Whether a demonstration is on screen right now. */
    @VisibleForTesting
    boolean isShowingGesture() {
        return gestureRect != null && gesture != TourGesture.NONE;
    }

    /** Which movement is being played, for a test that asks what a rail gets. */
    @VisibleForTesting
    TourGesture playingGesture() { return gesture; }

    // ---- pieces -----------------------------------------------------------------------------

    /**
     * Which wash exploration is drawn over. The launcher is only dimmed — the reader is looking at
     * their own screen — and the wash is light on a light screen and dark on a dark one.
     */
    private boolean lightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            != Configuration.UI_MODE_NIGHT_YES;
    }

    /** The room the toolbar and the card may use: the screen, less the system bars. */
    private Rect safeArea() {
        Rect safe = new Rect(0, 0, getWidth(), getHeight());
        android.view.WindowInsets insets = getRootWindowInsets();
        if (insets != null) {
            safe.left += Math.max(0, insets.getStableInsetLeft());
            safe.top += Math.max(0, insets.getStableInsetTop());
            safe.right -= Math.max(0, insets.getStableInsetRight());
            safe.bottom -= Math.max(0, insets.getStableInsetBottom());
        }
        if (safe.width() <= 0 || safe.height() <= 0) return new Rect(0, 0, getWidth(), getHeight());
        return safe;
    }

    /** The gesture pill's own strip: exploration washes over it, and puts no card beneath it. */
    private int bottomInset() {
        android.view.WindowInsets insets = getRootWindowInsets();
        if (insets == null) return 0;
        return Math.max(0, Math.max(insets.getStableInsetBottom(),
            insets.getSystemWindowInsetBottom()));
    }

    /** Which edge of the wall a control is past, if any; a dock that is a rail is one of these. */
    private HelpLeaderRouter.Side side(Rect r) {
        if (r.bottom <= snapshot.wall.top) return HelpLeaderRouter.Side.ABOVE;
        if (r.top >= snapshot.wall.bottom) return HelpLeaderRouter.Side.UNDER;
        if (r.right <= snapshot.wall.left) return HelpLeaderRouter.Side.LEFT;
        if (r.left >= snapshot.wall.right) return HelpLeaderRouter.Side.RIGHT;
        return HelpLeaderRouter.Side.INSIDE;
    }

    /** A button of the card or the toolbar: never smaller than a thumb, always named for a reader. */
    private TextView button(String label, Runnable onClick) {
        return button(label, label, onClick);
    }

    /** The same button, for one whose mark is not what a reader should hear — the × is "Close help". */
    private TextView button(String label, String description, Runnable onClick) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setContentDescription(description);
        view.setTextSize(13);
        view.setAllCaps(false);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48));
        view.setMinWidth(dp(48));
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        view.setTextColor(accent);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(accent, 28));
        shape.setCornerRadius(dp(10));
        view.setBackground(shape);
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(v -> onClick.run());
        return view;
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

    /** Said once per selection: a layout pass is not a new thing to read out. */
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

    private float radiusOf(String id) {
        if (snapshot == null) return 0;
        for (HelpTargets.Target target : snapshot.targets) if (target.id.equals(id)) return target.radius;
        return 0;
    }

    private void put(View view, Rect rect) {
        int width = Math.max(1, rect.width()), height = Math.max(1, rect.height());
        if (view.getParent() != this) {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            addView(view, new LayoutParams(width, height));
        } else {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params.width != width || params.height != height) {
                params.width = width; params.height = height;
                view.setLayoutParams(params);
            }
        }
        childBounds.put(view, rect);
        rendered.add(view);
        // Sized and placed now rather than a frame later: a child that waits for the next layout
        // pass is drawn once at no size, and that empty frame is the flash the overlay used to give.
        view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        view.layout(rect.left, rect.top, rect.left + width, rect.top + height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (Map.Entry<View, Rect> entry : childBounds.entrySet()) {
            View child = entry.getKey(); Rect bounds = entry.getValue();
            child.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
            // The markers are round and help's own button rows are capsules; everything else wears
            // the terminal's own corner.
            if (child.getBackground() instanceof GradientDrawable && child != toolbar
                    && child != buttons && !markerViews.containsValue(child))
                ((GradientDrawable) child.getBackground()).setCornerRadius(dress.cornerRadiusPx(bounds.height()));
        }
    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!showing || snapshot == null) return;
        // Dimmed, not covered: the reader is being shown their own screen.
        canvas.drawColor(lightMode() ? Color.argb(104, 255, 255, 255) : Color.argb(102, 0, 0, 0));
        paint.setStrokeWidth(dp(1.5f));
        paint.setStyle(Paint.Style.STROKE);
        if (mode == Mode.OVERVIEW) {
            drawOverview(canvas);
        } else {
            String selected = model.selectedTargetId();
            for (Marker marker : markers) {
                if (marker.entry.targetId.equals(selected)) continue;
                paint.setColor(ColorUtils.setAlphaComponent(onTheWash(marker.color), 150));
                drawBox(canvas, marker.target, radiusOf(marker.entry.targetId));
            }
            drawSelected(canvas, selected);
        }
        paint.setPathEffect(null);
        drawGesture(canvas);
    }

    /**
     * Each box, its leader and its card wear one colour, so a line that passes another card still
     * reads as belonging to its own pair. A control with no card is not marked at all.
     */
    private void drawOverview(Canvas canvas) {
        if (routed != null) for (HelpLeaderRouter.Placement p : routed.placements) {
            if (p.page != 0) continue;
            Rect target = targetRect(p.target.id);
            if (target == null) continue;
            Integer color = boxColors.get(p.target.id);
            paint.setColor(onTheWash(color == null ? accent : color));
            paint.setPathEffect(null);
            for (HelpLeaderRouter.Segment line : p.lines)
                canvas.drawLine(line.x1, line.y1, line.x2, line.y2, paint);
            drawBox(canvas, target, radiusOf(p.target.id));
        }
        // The extra keys share one colour: they are one row, and seven hues along a keyboard
        // would read as seven unrelated things rather than as the keys of one row.
        paint.setColor(keyColor);
        for (KeyCard key : keyCards) {
            paint.setPathEffect(null);
            for (float[] line : key.lines) canvas.drawLine(line[0], line[1], line[2], line[3], paint);
            drawBox(canvas, key.cap, dp(8));
        }
    }

    /** The one highlight, its one short leader, and the extra keys' own cards when they are up. */
    private void drawSelected(Canvas canvas, String selected) {
        if (selected == null) return;
        Rect rect = targetRect(selected);
        if (rect == null) return;
        paint.setColor(onTheWash(model.markerColor(accent, selected, lightMode())));
        paint.setStrokeWidth(dp(2.5f));
        if (cardLeader != null) {
            paint.setPathEffect(null);
            canvas.drawLine(cardLeader.x1, cardLeader.y1, cardLeader.x2, cardLeader.y2, paint);
        }
        drawBox(canvas, rect, radiusOf(selected));
        paint.setStrokeWidth(dp(1.5f));
        // The extra keys share one colour: they are one row, and seven hues along a keyboard
        // would read as seven unrelated things rather than as the keys of one row.
        paint.setColor(keyColor);
        for (KeyCard key : keyCards) {
            paint.setPathEffect(null);
            for (float[] line : key.lines) canvas.drawLine(line[0], line[1], line[2], line[3], paint);
            drawBox(canvas, key.cap, dp(8));
        }
    }

    /** A colour deep enough to be a dash on the light wash; on the dark one it is left alone. */
    private int onTheWash(int color) {
        if (!lightMode()) return color;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return Color.HSVToColor(new float[] {hsv[0], Math.max(hsv[1], 0.85f), Math.min(hsv[2], 0.55f)});
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

    // ---- touch ------------------------------------------------------------------------------

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // Children receive taps; nothing passes through this layer to the launcher underneath.
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
                if (moved || onSomething(downX, downY) || onSomething(event.getX(), event.getY())) break;
                performClick();
                // The overview has nothing to put away: it is all one page, and a stray tap on the
                // launcher underneath must not read as a way out of help.
                if (mode == Mode.OVERVIEW) break;
                // The control itself is as good a marker as its dot; empty space puts the card away.
                String id = targetAt(event.getX(), event.getY());
                if (id != null) select(id, false);
                else deselect();
                break;
        }
        return true;
    }

    private boolean onSomething(float x, float y) {
        for (View view : touchable) {
            Rect r = childBounds.get(view);
            if (r != null && r.contains((int) x, (int) y)) return true;
        }
        return false;
    }

    /** The smallest marked control under the finger, so a badge inside a bar wins over the bar. */
    private String targetAt(float x, float y) {
        String best = null;
        long area = Long.MAX_VALUE;
        for (Marker marker : markers) {
            Rect r = marker.target;
            if (!r.contains((int) x, (int) y)) continue;
            long size = (long) r.width() * r.height();
            if (size < area) { area = size; best = marker.entry.targetId; }
        }
        return best;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private int dp(float value) { return Math.round(value * density); }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
    }

    private static HelpLeaderRouter.Box box(Rect r) {
        return new HelpLeaderRouter.Box(r.left, r.top, r.right, r.bottom);
    }

    private static Rect rect(HelpLeaderRouter.Box b) {
        return new Rect(Math.round(b.left), Math.round(b.top), Math.round(b.right), Math.round(b.bottom));
    }

    /** The marked controls on screen right now, in the order they are numbered. */
    @VisibleForTesting
    List<String> markerTargetIds() {
        List<String> ids = new ArrayList<>();
        for (Marker marker : markers) ids.add(marker.entry.targetId);
        return Collections.unmodifiableList(ids);
    }

    /** Where the seated card is, or null when nothing is selected. */
    @VisibleForTesting
    Rect cardBounds() { return cardBounds == null ? null : new Rect(cardBounds); }

    /** Where the toolbar is. */
    @VisibleForTesting
    Rect toolbarBounds() { return toolbarBounds == null ? null : new Rect(toolbarBounds); }

    /** Where the overview's own two buttons are. */
    @VisibleForTesting
    Rect buttonsBounds() { return buttonsBounds == null ? null : new Rect(buttonsBounds); }

    /** The controls the overview drew a card for, in the order it seated them. */
    @VisibleForTesting
    List<String> overviewCardIds() {
        List<String> ids = new ArrayList<>();
        if (routed != null) for (HelpLeaderRouter.Placement p : routed.placements)
            if (p.page == 0) ids.add(p.target.id);
        return Collections.unmodifiableList(ids);
    }

    /** Where one overview card sits, or null when that control has none. */
    @VisibleForTesting
    Rect overviewCardBounds(String targetId) {
        if (routed == null) return null;
        for (HelpLeaderRouter.Placement p : routed.placements)
            if (p.page == 0 && p.target.id.equals(targetId)) return rect(p.card);
        return null;
    }

    /** The view carrying one overview card, or null when that control has none. */
    @VisibleForTesting
    TextView overviewCardView(String targetId) { return cardViews.get(targetId); }

    /** What the overview could not fit; empty on every layout the launcher ships. */
    @VisibleForTesting
    List<String> unplacedOverviewIds() {
        return Collections.unmodifiableList(new ArrayList<>(unplaced));
    }

    /** The extra keys' own cards, empty unless the extra keys row is the selected control. */
    @VisibleForTesting
    List<Rect> keyCardBounds() {
        List<Rect> out = new ArrayList<>();
        for (KeyCard key : keyCards) out.add(new Rect(key.bounds));
        return out;
    }

    /** The marker dot for one control, or null when that control is not marked. */
    @VisibleForTesting
    View markerView(String targetId) { return markerViews.get(targetId); }

    /** Where one control was measured this pass, for a test that checks nothing covers it. */
    @VisibleForTesting
    Rect measuredRect(String targetId) {
        Rect rect = targetRect(targetId);
        return rect == null ? null : new Rect(rect);
    }
}
