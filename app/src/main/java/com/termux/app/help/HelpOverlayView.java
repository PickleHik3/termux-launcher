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
import android.graphics.drawable.InsetDrawable;
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
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.chrome.CornerTabGeometry;
import com.termux.app.chrome.CornerTabGlyphs;
import com.termux.app.notice.TerminalDress;
import com.termux.app.statusbar.StatusBarLensView;
import com.termux.app.tour.TourFingerPainter;
import com.termux.app.tour.TourFingerTrace;
import com.termux.app.tour.TourGesture;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.font.NerdFontSpans;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Help, drawn: the guide over every control of the place, the topic chooser, or one topic.
 *
 * <p>Help opens on the guide, with nothing round it but two floating buttons beside where the ?
 * was — a × that closes, and a book that opens the catalogue and closes it again. The guide is one
 * page and carries no chrome of its own, so the boxes, the cards and the leaders have the whole
 * wall between them.
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
    /** What each card was built from, so a re-measure that moved nothing keeps the same view. */
    private final Map<String, String> cardSpecs = new HashMap<>();
    /** Every view put on screen this pass; anything else is what has really gone away. */
    private final Set<View> rendered = new HashSet<>();
    /** One card per extra key, with the cap it names and the line that joins the two. */
    private final List<KeyCard> keyCards = new ArrayList<>();
    private final Map<Integer, TextView> keyCardViews = new HashMap<>();
    private final Map<Integer, String> keyCardSpecs = new HashMap<>();
    /** The one colour the extra keys' boxes, leaders and cards share. */
    private int keyColor;
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
    private int accent;
    /** Where the ? the user pressed was, in screen coordinates; null when nothing named it. */
    private Rect anchorOnScreen;
    /** Guide entries the router could not fit on the one page; kept for the log and a test. */
    private final List<String> unplaced = new ArrayList<>();
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
        // Above every control it explains. The dock, the A-Z row, the extra keys and the keyboard
        // are lifted between 6 and 40dp, and the guide has to wash over all of them; the outline
        // is dropped so the height casts no shadow of its own.
        setElevation(dp(56));
        setTranslationZ(dp(56));
        setOutlineProvider(null);
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
        show(place, null);
    }

    /**
     * Open help for a place, told where the ? that opened it was so the floating buttons can sit
     * beside it. A null anchor — Settings, the palette, a tab already gone — falls back to the
     * corner the tab comes out of.
     */
    public void show(PaneWallPage place, Rect anchorOnScreen) {
        this.place = place;
        this.anchorOnScreen = anchorOnScreen == null ? null : new Rect(anchorOnScreen);
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
        cards.clear(); childBounds.clear(); cardViews.clear(); cardSpecs.clear();
        rendered.clear(); keyCards.clear(); keyCardViews.clear(); keyCardSpecs.clear();
        snapshot = null; routed = null; signature = ""; announced = null;
        anchorOnScreen = null; unplaced.clear();
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
            + ":" + currentDress.terminalRadiusPx + ":" + currentAccent + ":" + lightMode()
            + ":" + measured.signature();
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
        Map<String, TextView> wasView = new HashMap<>(cardViews);
        Map<String, String> wasSpec = new HashMap<>(cardSpecs);
        cardViews.clear(); cardSpecs.clear(); boxColors.clear();
        boolean light = lightMode();
        int width = Math.max(1, (snapshot.wall.width() - dp(36)) / 2);
        List<HelpLeaderRouter.Target> inputs = new ArrayList<>();
        List<HelpLeaderRouter.Box> soft = new ArrayList<>();
        int count = snapshot.targets.size();
        for (int i = 0; i < count; i++) {
            HelpTargets.Target target = snapshot.targets.get(i);
            if (HelpTopics.topicOnly(target.id)) continue;
            int color = overviewColor(target.id, i, count, light);
            boxColors.put(target.id, color);
            int title = titleColor(target.id, i, count);
            // The same words in the same colours are the same card: a stat that changed width
            // moves the cards it shares the wall with, and moving one is not rebuilding it.
            String spec = spec(target.copy.title, target.copy.body, title, color);
            TextView card = spec.equals(wasSpec.get(target.id)) ? wasView.get(target.id) : null;
            if (card == null) card = card(target.copy, title, color);
            card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            cardViews.put(target.id, card);
            cardSpecs.put(target.id, spec);
            HelpLeaderRouter.Side side = side(target.rect);
            inputs.add(new HelpLeaderRouter.Target(target.id, box(target.rect), side,
                width, card.getMeasuredHeight()));
            if (side == HelpLeaderRouter.Side.INSIDE) soft.add(box(target.rect));
        }
        arrangeKeyCards(light);
        // The whole band is the cards': the guide reserves nothing at the bottom, which is the
        // room that used to push a hint onto a second page.
        Rect band = new Rect(snapshot.wall);
        band.top += dp(8);
        band.bottom = Math.max(band.top, band.bottom - dp(8));
        List<HelpLeaderRouter.Box> obstacles = new ArrayList<>();
        if (keyCards.isEmpty()) {
            for (HelpTargets.KeyLabel key : snapshot.keys) obstacles.add(box(keyLabelBounds(key.rect)));
        } else {
            for (KeyCard key : keyCards) obstacles.add(box(key.bounds));
        }
        routed = HelpLeaderRouter.arrange(box(band), dp(12), dp(12), inputs, obstacles, soft);
        // The key cards take the foot of the band, and one page still comes first: on a wall too
        // short for both they give way rather than push a hint off the guide.
        if (!keyCards.isEmpty() && !onOnePage(routed)) {
            List<HelpLeaderRouter.Box> yielding = new ArrayList<>(soft);
            yielding.addAll(obstacles);
            routed = HelpLeaderRouter.arrange(box(band), dp(12), dp(12), inputs,
                Collections.<HelpLeaderRouter.Box>emptyList(), yielding);
        }
        // One page, always: a hint with no room on it is left out of the guide and said so in the
        // log, and its topic is still there in the catalogue.
        unplaced.clear();
        for (HelpLeaderRouter.Target target : routed.unplaced) unplaced.add(target.id);
        for (HelpLeaderRouter.Placement p : routed.placements) if (p.page > 0) unplaced.add(p.target.id);
        for (String id : unplaced) HelpLog.d("left out of the guide: " + id + ", no room on the page");
        HelpLog.d("layout " + place + ": " + snapshot.targets.size() + " targets, "
            + unplaced.size() + " left out");
    }

    /** What the guide could not fit; empty on every layout the launcher ships. */
    @VisibleForTesting
    List<String> unplacedGuideIds() {
        return Collections.unmodifiableList(new ArrayList<>(unplaced));
    }

    /** The view carrying one hint's card right now, or null when the control is not on screen. */
    @VisibleForTesting
    TextView guideCardView(String id) {
        return cardViews.get(id);
    }

    /** A control's colour comes from its identity in the catalogue, not from what else is up. */
    private int overviewColor(String id, int index, int count, boolean light) {
        HelpTopics.Entry entry = HelpTopics.entry(place, id);
        return entry == null ? HelpPalette.boxColor(accent, index, count, light)
            : model.overviewColor(accent, entry, light);
    }

    /**
     * Which wash help is drawn over. The guide dims the screen so its boxes and cards carry the
     * eye; on a light screen the dim is a light one, and the dashes deepen to match.
     */
    private boolean lightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            != Configuration.UI_MODE_NIGHT_YES;
    }

    /** What a card is made of; two cards with the same recipe are the same card. */
    private String spec(String title, String body, int titleColor, int borderColor) {
        return title + "\u0001" + body + "\u0001" + titleColor + ":" + borderColor + ":"
            + dress.fillColor + ":" + dress.strokeColor + ":" + dress.textColor;
    }

    private static boolean onOnePage(HelpLeaderRouter.Result result) {
        if (!result.unplaced.isEmpty()) return false;
        for (HelpLeaderRouter.Placement p : result.placements) if (p.page > 0) return false;
        return true;
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

    /**
     * A card for every extra key, in the room above the row: what the key does, and what a swipe
     * up on it does. Seven of them across a phone are too tight for one line, so they alternate
     * between two rows, and each card takes the room between its neighbours' key centres — which
     * leaves a lane over every key for the other row's leader to come down through. The rows sit
     * clear of the dock and the A-Z row, whose own boxes have to stay readable under them.
     */
    private void arrangeKeyCards(boolean light) {
        Map<Integer, TextView> wasView = new HashMap<>(keyCardViews);
        Map<Integer, String> wasSpec = new HashMap<>(keyCardSpecs);
        keyCards.clear(); keyCardViews.clear(); keyCardSpecs.clear();
        List<HelpTargets.KeyLabel> keys = snapshot.keys;
        if (keys.isEmpty() || getWidth() <= 0) return;
        HelpTopics.Entry entry = HelpTopics.entry(place, "keys");
        keyColor = entry == null ? onTheWash(accent) : model.overviewColor(accent, entry, light);
        int titleColor = entry == null ? HelpPalette.titleColor(accent, 0, 1, dress.fillColor)
            : HelpPalette.titleColor(accent, entry.identityIndex,
                HelpTopics.sizeFor(entry.place), dress.fillColor);
        int left = dp(12), right = getWidth() - dp(12);
        int clearance = dp(10);
        int limit = snapshot.wall.bottom;
        for (HelpTargets.KeyLabel key : keys) limit = Math.min(limit, key.rect.top);
        Rect dock = targetRect("dock");
        if (dock != null) limit = Math.min(limit, dock.top);
        Rect az = targetRect("az");
        if (az != null) limit = Math.min(limit, az.top);
        int[] centers = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++) centers[i] = keys.get(i).rect.centerX();
        int[][] slots = keyCardSlots(centers, left, right, clearance);
        List<TextView> views = new ArrayList<>();
        List<String> specs = new ArrayList<>();
        int rowHeight = 0;
        for (int i = 0; i < keys.size(); i++) {
            HelpTargets.KeyLabel key = keys.get(i);
            // Too many keys for a card each; the caps keep their own small labels instead.
            if (slots[i][1] - slots[i][0] < dp(56)) { keyCards.clear(); return; }
            String spec = spec(key.primary, key.secondary == null ? "" : key.secondary,
                titleColor, keyColor);
            TextView card = spec.equals(wasSpec.get(i)) ? wasView.get(i) : null;
            if (card == null) card = keyCard(key, titleColor, keyColor);
            card.measure(MeasureSpec.makeMeasureSpec(slots[i][1] - slots[i][0], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            rowHeight = Math.max(rowHeight, card.getMeasuredHeight());
            views.add(card);
            specs.add(spec);
        }
        int lowerTop = limit - dp(14) - rowHeight;
        int upperTop = lowerTop - dp(10) - rowHeight;
        if (upperTop < dp(4)) {
            upperTop = dp(4);
            lowerTop = Math.max(lowerTop, upperTop + rowHeight + dp(10));
        }
        for (int i = 0; i < views.size(); i++) {
            int top = i % 2 == 0 ? upperTop : lowerTop;
            Rect bounds = new Rect(slots[i][0], top, slots[i][1], top + rowHeight);
            Rect cap = keys.get(i).rect;
            keyCards.add(new KeyCard(views.get(i), bounds, cap, keyLeader(bounds, cap)));
            keyCardViews.put(i, views.get(i));
            keyCardSpecs.put(i, specs.get(i));
        }
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

    /** Down from the card to the cap it names, straight when it can be and with one step when not. */
    private List<float[]> keyLeader(Rect card, Rect cap) {
        List<float[]> lines = new ArrayList<>();
        int cx = cap.centerX();
        int lx = Math.max(card.left + dp(8), Math.min(card.right - dp(8), cx));
        if (lx == cx) {
            lines.add(new float[] {cx, card.bottom, cx, cap.top});
            return lines;
        }
        float mid = (card.bottom + cap.top) / 2f;
        lines.add(new float[] {lx, card.bottom, lx, mid});
        lines.add(new float[] {lx, mid, cx, mid});
        lines.add(new float[] {cx, mid, cx, cap.top});
        return lines;
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

    // ---- rendering ---------------------------------------------------------------------------

    /**
     * What is on screen, brought up to date in place. Children that are still wanted keep their
     * view — a re-measure that only moved a card must not blank the wall for a frame first — and
     * only the ones this pass did not ask for are taken away.
     */
    private void render() {
        rendered.clear(); cards.clear();
        switch (model.mode()) {
            case TOPICS: renderTopics(); break;
            case TOPIC: renderTopic(); break;
            default: renderGuide(); break;
        }
        placeGlyphs();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (rendered.contains(child)) continue;
            removeViewAt(i);
            childBounds.remove(child);
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
        // Weighted like the topic's text: a long list scrolls, the buttons under it stay whole.
        panel.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout row = buttonRow();
        row.addView(button(getContext().getString(R.string.help_show_basics), !model.basicsOnly(),
            () -> command(model.showBasics())), weighted());
        // Show all puts the popup away and leaves the guide standing, which is where help began.
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
        // The sentences scroll rather than push the buttons off the wall at a large font scale.
        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(line(getContext().getString(entry.purposeRes), true,
            model.topicHighlightColor(accent)), rowParams(dp(6)));
        body.addView(line(getContext().getString(entry.actionRes), false, dress.textColor),
            rowParams(dp(4)));
        int revealRes = model.revealRes();
        if (revealRes != 0)
            body.addView(line(getContext().getString(revealRes), false,
                ColorUtils.setAlphaComponent(dress.textColor, 199)), rowParams(dp(6)));
        HelpTopics.Entry related = HelpTopics.entry(place, model.relatedTopicId());
        if (related != null) body.addView(chip(related), rowParams(dp(8)));
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.addView(body, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT));
        // Weighted, so a panel taller than the wall takes the shortfall out of the text it can
        // scroll and never out of the buttons under it.
        panel.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout first = buttonRow();
        first.addView(button(getContext().getString(R.string.help_back_to_topics), true,
            () -> command(model.backToTopics())), weighted());
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

    /** The guide: every control of the place boxed, with its hint, on one page and nothing else. */
    private void renderGuide() {
        if (routed == null) return;
        for (HelpLeaderRouter.Placement p : routed.placements) if (p.page == 0) {
            TextView card = cardViews.get(p.target.id);
            put(card, rect(p.card)); cards.add(card);
        }
        if (keyCards.isEmpty()) {
            for (HelpTargets.KeyLabel key : snapshot.keys) {
                TextView label = pill(key.text);
                label.setPadding(dp(1), 0, dp(1), 0);
                label.setMaxLines(2);
                label.setAutoSizeTextTypeUniformWithConfiguration(6, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
                label.setBackground(dress.background(key.rect.height()));
                put(label, keyLabelBounds(key.rect));
            }
        } else {
            for (KeyCard key : keyCards) { put(key.view, key.bounds); cards.add(key.view); }
        }
        announce(getContext().getString(R.string.help_header, placeName()), "guide");
    }

    // ---- the two floating buttons ---------------------------------------------------------

    /**
     * The × and the catalogue, beside where the ? the user pressed was: help's own chrome, the same
     * size and glass a corner tab's buttons wear, in every mode.
     */
    private void placeGlyphs() {
        if (snapshot == null) return;
        Rect anchor = anchorBounds();
        int size = dp(48);
        int gap = dp(8);
        int width = size * 2 + gap;
        int left = clamp(anchor.centerX() - width / 2, dp(4), getWidth() - width - dp(4));
        // Below the anchor when it sits in the top half of the screen, above it when below, so the
        // pair never lands off the edge the tab came out of.
        int top = anchor.centerY() < getHeight() / 2 ? anchor.bottom + gap : anchor.top - gap - size;
        top = clamp(top, dp(4), getHeight() - size - dp(4));
        TextView close = glyphButton(getContext().getString(R.string.help_close_glyph), false,
            getContext().getString(R.string.help_close_action), () -> command(model.close()));
        TextView catalogue = glyphButton(CornerTabGlyphs.CATALOGUE, true,
            getContext().getString(R.string.help_topics_action), this::toggleCatalogue);
        put(close, new Rect(left, top, left + size, top + size));
        put(catalogue, new Rect(left + size + gap, top, left + width, top + size));
        cards.add(close);
        cards.add(catalogue);
    }

    /** The catalogue button both ways: it opens the chooser, and it puts it away again. */
    private void toggleCatalogue() {
        command(model.mode() == HelpPresentationModel.Mode.OVERVIEW
            ? model.backToTopics() : model.showAll());
    }

    /**
     * Where the buttons hang off: the ? the user pressed, or — for help opened from Settings or the
     * palette — the corner a tab would have come out of.
     */
    private Rect anchorBounds() {
        if (anchorOnScreen != null) {
            int[] origin = new int[2];
            getLocationOnScreen(origin);
            Rect local = new Rect(anchorOnScreen);
            local.offset(-origin[0], -origin[1]);
            if (!local.isEmpty()) return local;
        }
        Rect corner = targetRect("corners");
        if (corner != null) return new Rect(corner);
        Rect wall = snapshot.wall;
        int size = dp(32);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int left = rtl ? wall.left : wall.right - size;
        return new Rect(left, wall.top, left + size, wall.top + size);
    }

    /** One floating button: the corner tab's own glass and tint, round, inside a thumb's square. */
    private TextView glyphButton(String glyph, boolean symbols, String description, Runnable onClick) {
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int surface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));
        TextView view = new TextView(context);
        view.setText(glyph);
        view.setContentDescription(description);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(symbols ? NerdFontSpans.typeface(context) : Typeface.DEFAULT_BOLD);
        // In dp, not sp: these are marks on a button the size of the tab's, not text to read.
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, symbols ? 14 : 18);
        view.setTextColor(primary);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(ColorUtils.setAlphaComponent(surface, 232));
        shape.setStroke(dp(CornerTabGeometry.TAB_OUTLINE_DP),
            ColorUtils.setAlphaComponent(primary, 225));
        // The circle is a tab button's 30dp; the square around it is the 48dp a thumb asks for.
        view.setBackground(new InsetDrawable(shape, dp(9)));
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(v -> onClick.run());
        return view;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
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

    /** The panel's own row: the title alone — the × beside it is help's one way out. */
    private View header(String title) {
        TextView text = new TextView(getContext());
        text.setText(title);
        text.setTextSize(14);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextColor(dress.textColor);
        return text;
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
        int width = Math.max(1, rect.width()), height = Math.max(1, rect.height());
        if (view.getParent() != this) {
            // A card can move between its normal page and a copy-only scroll page on remeasurement.
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
        // pass is drawn once at no size, and that empty frame is the flash the guide used to give.
        view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        view.layout(rect.left, rect.top, rect.left + width, rect.top + height);
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
        // The wash the boxes are read against: dark on a dark screen, light on a light one.
        canvas.drawColor(lightMode() ? Color.argb(172, 255, 255, 255) : Color.argb(166, 0, 0, 0));
        paint.setStrokeWidth(dp(1.5f)); paint.setStyle(Paint.Style.STROKE);
        if (model.mode() == HelpPresentationModel.Mode.OVERVIEW) drawGuide(canvas);
        else if (model.mode() == HelpPresentationModel.Mode.TOPIC) drawTopic(canvas);
        paint.setPathEffect(null);
        drawGesture(canvas);
    }

    /** One box, on the one control the topic is about. */
    private void drawTopic(Canvas canvas) {
        Rect rect = targetRect(model.highlightTargetId());
        if (rect == null) return;
        paint.setColor(onTheWash(model.topicHighlightColor(accent)));
        drawBox(canvas, rect, radiusOf(model.highlightTargetId()));
    }

    /** A colour deep enough to be a dash on the light wash; on the dark one it is left alone. */
    private int onTheWash(int color) {
        if (!lightMode()) return color;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return Color.HSVToColor(new float[] {hsv[0], Math.max(hsv[1], 0.85f), Math.min(hsv[2], 0.55f)});
    }

    /**
     * Each box, its leader and its card wear one colour, so a line that passes another card still
     * reads as belonging to its own pair.
     */
    private void drawGuide(Canvas canvas) {
        if (routed == null) return;
        for (HelpLeaderRouter.Placement p : routed.placements) if (p.page == 0) {
            HelpTargets.Target target = target(p.target.id);
            Integer color = boxColors.get(target.id);
            paint.setColor(color == null ? onTheWash(accent) : color);
            paint.setPathEffect(null);
            for (HelpLeaderRouter.Segment line : p.lines)
                canvas.drawLine(line.x1, line.y1, line.x2, line.y2, paint);
            drawBox(canvas, target.rect, target.radius);
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
