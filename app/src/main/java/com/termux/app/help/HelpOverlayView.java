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
 * Explore this screen: the live launcher, dimmed a little, with every control help can explain
 * marked, and one card at a time on the control the reader tapped.
 *
 * <p>Nothing is read here. A marker carries its number and its name, the card carries the topic's
 * title and its one instruction, and "Read topic" hands the topic to the help panel through
 * {@link ExploreListener}. The card is seated by {@link HelpExplorePlacement}, which never lets it
 * cover the control it explains, the toolbar or the system bars; when no seat fits, the listener is
 * told and the topic is read instead.
 *
 * <p>Which control is selected and what it is called is {@link HelpPresentationModel}'s and
 * {@link HelpTopics}'; this measures the controls, seats what the placement answers, and plays the
 * one gesture a topic carries.
 */
public final class HelpOverlayView extends FrameLayout {

    /** What exploration asks the launcher for; everything else it does itself. */
    public interface ExploreListener {
        /** "Read topic" on the seated card. */
        void onReadTopic(String topicId);
        /** The toolbar's Back to help, or Back with nothing selected. */
        void onBackToHelp();
        /** The toolbar's Close help. */
        void onCloseHelp();
        /** The selected control is no longer on screen; the topic says so instead. */
        void onTargetGone(String topicId);
        /** No seat for the card on this screen: read the topic rather than shrink it. */
        void onCardDoesNotFit(String topicId);
    }

    /**
     * The launcher's side of "Try it".
     *
     * @deprecated practice belongs to the help panel's controller; kept only so the launcher's
     *     current wiring compiles until it is moved there.
     */
    @Deprecated
    public interface PracticeListener {
        void onPracticeRequested(String lessonId);
    }

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
    /** One card per extra key, shown only while the extra keys row is the selected control. */
    private final List<KeyCard> keyCards = new ArrayList<>();
    private final Map<Integer, TextView> keyCardViews = new HashMap<>();
    private final Map<Integer, String> keyCardSpecs = new HashMap<>();
    /** The one colour the extra keys' boxes, leaders and cards share. */
    private int keyColor;

    private ExploreListener listener;
    private final Runnable onDismiss;
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

    private float downX, downY;
    private boolean moved;
    private ValueAnimator gestureTrace;
    private TourGesture gesture = TourGesture.NONE;
    private Rect gestureRect;
    private float gestureProgress = 1f;
    /** Read once when the gesture starts: a setting is not something onDraw asks about. */
    private boolean gestureReducedMotion;

    public HelpOverlayView(Context context, HelpTargets.ViewFinder finder) {
        this(context, finder, null);
    }

    /**
     * @deprecated the launcher's help wiring moves into the help controller, which uses
     *     {@link #HelpOverlayView(Context, HelpTargets.ViewFinder)} and {@link ExploreListener}.
     *     Kept so the current wiring compiles meanwhile.
     */
    @Deprecated
    public HelpOverlayView(Context context, HelpTargets.ViewFinder finder, Runnable onDismiss) {
        super(context);
        this.onDismiss = onDismiss;
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
        setFocusable(true);
        setContentDescription(context.getString(R.string.help_explore_title));
        setVisibility(GONE);
    }

    public void setExploreListener(ExploreListener listener) {
        this.listener = listener;
    }

    /** @deprecated exploration hands practice to the help panel's controller; this does nothing. */
    @Deprecated
    public void setPracticeListener(PracticeListener listener) { }

    /** @deprecated exploration offers no "Try it" of its own; this does nothing. */
    @Deprecated
    public void setPracticeAvailable(boolean available) { }

    /**
     * @deprecated use {@link #explore(PaneWallPage, String)}; the anchor the floating buttons hung
     *     off went away with them.
     */
    @Deprecated
    public void show(PaneWallPage place) { explore(place, null); }

    /** @deprecated use {@link #explore(PaneWallPage, String)}. */
    @Deprecated
    public void show(PaneWallPage place, Rect anchorOnScreen) { explore(place, null); }

    /** Explore a place. {@code selectTopicId} pre-selects a control, or is null. */
    public void explore(PaneWallPage place, String selectTopicId) {
        start(place, selectTopicId, false);
    }

    /** Explore with the topic selected and its own gesture played once over its control. */
    public void demonstrate(PaneWallPage place, String topicId) {
        start(place, topicId, true);
    }

    private void start(PaneWallPage place, String selectTopicId, boolean withGesture) {
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
        HelpLog.d("explore " + place + (selectTopicId == null ? "" : " at " + selectTopicId)
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
        toolbar = null; toolbarBounds = null;
        snapshot = null; signature = ""; announced = null;
        pendingSelect = null; pendingGesture = false; reportedNoSeat = false;
        model.clearSelection();
        setVisibility(GONE);
        HelpLog.d("dismiss " + place);
        if (onDismiss != null) onDismiss.run();
    }

    @Override protected void onDetachedFromWindow() {
        dismiss();
        super.onDetachedFromWindow();
    }

    /**
     * Back with a card up puts the card away; with nothing selected it is the launcher's to answer,
     * which takes the reader to Help home.
     */
    public boolean onBackPressed() {
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
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (rendered.contains(child)) continue;
            removeViewAt(i);
            childBounds.remove(child);
        }
        HelpTopics.Entry reading = model.selected();
        if (reading == null) announce(getContext().getString(R.string.help_explore_title), "explore");
        else announce(getContext().getString(reading.titleRes) + ". "
            + getContext().getString(reading.actionRes), reading.id);
        requestLayout();
        invalidate();
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
     * Where one marker sits: on a corner of its control's outline, at the first corner no other
     * marker and no toolbar has taken, and always inside the screen.
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
        Rect fallback = null;
        for (int[] anchor : anchors) {
            int left = clamp(anchor[0], dp(2), Math.max(dp(2), getWidth() - size - dp(2)));
            int top = clamp(anchor[1], dp(2), Math.max(dp(2), getHeight() - size - dp(2)));
            Rect bounds = new Rect(left, top, left + size, top + size);
            if (fallback == null) fallback = bounds;
            boolean clear = true;
            for (Rect other : taken) if (Rect.intersects(other, bounds)) { clear = false; break; }
            if (clear) return bounds;
        }
        return fallback;
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
        if (target == null) { toolbarBounds = low; return; }
        // The edge farthest from the control, and the near edge only when the far one would land
        // on the control itself.
        boolean farIsHigh = target.centerY() > safe.centerY();
        Rect far = farIsHigh ? high : low, near = farIsHigh ? low : high;
        toolbarBounds = !Rect.intersects(far, target) ? far
            : !Rect.intersects(near, target) ? near : far;
        if (Rect.intersects(toolbarBounds, target))
            HelpLog.d("the toolbar has nowhere clear of " + selected.id + "'s control");
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
        int alongLo = dp(12);
        int alongHi = (vertical ? getHeight() - bottomInset() : getWidth()) - dp(12);
        int keysNear = Integer.MAX_VALUE, keysFar = 0;
        for (HelpTargets.KeyLabel key : keys) {
            keysNear = Math.min(keysNear, vertical ? key.rect.left : key.rect.top);
            keysFar = Math.max(keysFar, vertical ? key.rect.right : key.rect.bottom);
        }
        // The wall lies on one side of the keys; away is the other. How far the cards may reach
        // there: to the next control they must not cover, and never under the gesture pill, which
        // help washes over but puts nothing beneath.
        int wallMid = vertical ? snapshot.wall.centerX() : snapshot.wall.centerY();
        boolean awayIsHigh = (keysNear + keysFar) / 2 >= wallMid;
        int awayLimit;
        if (awayIsHigh) {
            awayLimit = Math.max(keysFar, (vertical ? getWidth() : getHeight() - bottomInset()) - dp(8));
            for (HelpTargets.Target target : snapshot.targets) {
                int edge = vertical ? target.rect.left : target.rect.top;
                if (edge >= keysFar) awayLimit = Math.min(awayLimit, edge - dp(6));
            }
        } else {
            awayLimit = Math.min(keysNear, dp(8));
            for (HelpTargets.Target target : snapshot.targets) {
                int edge = vertical ? target.rect.right : target.rect.bottom;
                if (edge <= keysNear) awayLimit = Math.max(awayLimit, edge + dp(6));
            }
        }
        int wallLimit = awayIsHigh ? (vertical ? band.left : band.top) : (vertical ? band.right : band.bottom);
        // Toward the wall the lanes lean on the keys unless another control — the dock, the status
        // bar — stands between the keys and the wall; then they lean on the far side of that
        // control instead, and the leaders cross it. A card over a control hides it; a line does not.
        int wallNear = awayIsHigh ? keysNear : keysFar;
        for (HelpTargets.Target target : snapshot.targets) {
            int low = vertical ? target.rect.left : target.rect.top;
            int high = vertical ? target.rect.right : target.rect.bottom;
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
                bounds = new Rect(slots[i][0], start, slots[i][1], start + thickness);
            }
            Rect cap = keys.get(i).rect;
            keyCards.add(new KeyCard(views.get(i), bounds, cap, keyLeader(bounds, cap, vertical, cardsHigh)));
            keyCardViews.put(i, views.get(i));
            keyCardSpecs.put(i, specs.get(i));
        }
        HelpLog.d("key cards: " + keyCards.size() + (vertical ? " beside the column" : " along the row")
            + " at " + keysNear + "-" + keysFar + (away ? ", away from the wall" : ", toward the wall")
            + ", lanes at " + lanes[0] + " and " + lanes[1] + ", " + thickness + "px thick, away limit "
            + awayLimit);
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
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setContentDescription(label);
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
            // The markers are round and the toolbar is a capsule; everything else wears the
            // terminal's own corner.
            if (child.getBackground() instanceof GradientDrawable && child != toolbar
                    && !markerViews.containsValue(child))
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
        String selected = model.selectedTargetId();
        for (Marker marker : markers) {
            if (marker.entry.targetId.equals(selected)) continue;
            paint.setColor(ColorUtils.setAlphaComponent(onTheWash(marker.color), 150));
            drawBox(canvas, marker.target, radiusOf(marker.entry.targetId));
        }
        drawSelected(canvas, selected);
        paint.setPathEffect(null);
        drawGesture(canvas);
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
