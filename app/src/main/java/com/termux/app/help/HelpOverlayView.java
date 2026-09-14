package com.termux.app.help;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.termux.R;
import com.termux.app.notice.TerminalDress;
import com.termux.app.statusbar.StatusBarLensView;
import com.termux.app.wall.PaneWallPage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** On-demand help; no persistence, gestures, tour state, or continuously running animation. */
public final class HelpOverlayView extends FrameLayout {
    private final HelpTargets targets;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float density;
    private DashPathEffect dash;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::refresh;
    private final Map<View, Rect> childBounds = new HashMap<>();
    private final List<View> cards = new ArrayList<>();
    private final Map<String, TextView> cardViews = new HashMap<>();
    private final Runnable onDismiss;
    private HelpTargets.Snapshot snapshot;
    private HelpLeaderRouter.Result routed;
    private TerminalDress dress;
    private PaneWallPage place;
    private TextView pagePill;
    private int page;
    private int pageCount;
    private int accent;
    private int footerHeight;
    private String signature = "";
    private boolean showing;
    private float downX, downY;
    private boolean moved;

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
    public void show(PaneWallPage place) {
        this.place = place;
        page = 0;
        signature = "";
        dress = TerminalDress.stored(getContext());
        accent = StatusBarLensView.accentFor(getContext(), place);
        if (!showing) getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        showing = true;
        setVisibility(VISIBLE);
        bringToFront();
        requestFocus();
        requestLayout();
        HelpLog.d("show " + place);
    }
    public boolean isShowing() { return showing; }
    public void dismiss() {
        if (!showing) return;
        showing = false;
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        removeAllViews();
        cards.clear(); childBounds.clear(); cardViews.clear();
        snapshot = null; routed = null; signature = "";
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
        if (signature.equals(next)) return;
        signature = next;
        snapshot = measured;
        dress = currentDress; accent = currentAccent;
        cardViews.clear();
        int width = Math.max(1, (snapshot.wall.width() - dp(36)) / 2);
        List<HelpLeaderRouter.Target> inputs = new ArrayList<>();
        for (HelpTargets.Target target : snapshot.targets) {
            TextView card = card(target.copy);
            card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            cardViews.put(target.id, card);
            inputs.add(new HelpLeaderRouter.Target(target.id, box(target.rect), side(target.rect),
                width, card.getMeasuredHeight()));
        }
        // Reserve the bottom of the wall for the close/paging pills, measured at this font scale.
        TextView close = pill(getContext().getString(R.string.help_close));
        close.measure(MeasureSpec.makeMeasureSpec(Math.max(1,(snapshot.wall.width()-dp(24))*2/3-dp(8)), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        footerHeight = close.getMeasuredHeight();
        Rect band = new Rect(snapshot.wall);
        band.top += dp(8);
        band.bottom = Math.max(band.top, band.bottom - close.getMeasuredHeight() - dp(16));
        List<HelpLeaderRouter.Box> obstacles = new ArrayList<>();
        for (HelpTargets.KeyLabel key : snapshot.keys) obstacles.add(box(keyLabelBounds(key.rect)));
        obstacles.add(new HelpLeaderRouter.Box(snapshot.wall.left + dp(12),
            snapshot.wall.bottom - footerHeight - dp(8), snapshot.wall.right - dp(12),
            snapshot.wall.bottom - dp(8)));
        routed = HelpLeaderRouter.route(getWidth(), getHeight(), box(band), dp(12), dp(12), inputs, obstacles);
        pageCount = Math.max(1, routed.pages + routed.unplaced.size());
        page = Math.min(page, pageCount - 1);
        for (HelpLeaderRouter.Target target : routed.unplaced)
            HelpLog.d("copy-only page for " + target.id + ": no collision-free slot");
        HelpLog.d("layout " + place + ": " + snapshot.targets.size() + " targets, " + pageCount + " pages");
        renderPage();
    }
    private HelpLeaderRouter.Side side(Rect r) {
        if (r.bottom <= snapshot.wall.top) return HelpLeaderRouter.Side.ABOVE;
        if (r.top >= snapshot.wall.bottom) return HelpLeaderRouter.Side.BELOW;
        if (r.right <= snapshot.wall.left) return HelpLeaderRouter.Side.LEFT;
        if (r.left >= snapshot.wall.right) return HelpLeaderRouter.Side.RIGHT;
        return HelpLeaderRouter.Side.INSIDE;
    }
    private void renderPage() {
        removeAllViews(); childBounds.clear(); cards.clear();
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
            if (card.getParent() instanceof ViewGroup) ((ViewGroup)card.getParent()).removeView(card);
            scroll.addView(card, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            Rect r = new Rect(snapshot.wall); r.inset(dp(16),dp(8));
            r.bottom = Math.max(r.top+1,r.bottom-footerHeight-dp(16));
            put(scroll,r); cards.add(scroll);
        }
        for (HelpTargets.KeyLabel key : snapshot.keys) {
            TextView label = pill(key.text);
            label.setPadding(dp(1),0,dp(1),0);
            label.setMaxLines(2);
            label.setAutoSizeTextTypeUniformWithConfiguration(6,11,1,android.util.TypedValue.COMPLEX_UNIT_SP);
            label.setBackground(dress.background(key.rect.height()));
            put(label,keyLabelBounds(key.rect));
        }
        TextView close = pill(getContext().getString(R.string.help_close));
        close.setOnClickListener(v -> dismiss());
        pagePill = null;
        int available = Math.max(1,snapshot.wall.width()-dp(24));
        int pagingWidth = 0;
        if (pageCount > 1) {
            pagePill = pill(getContext().getString(R.string.help_page,page+1,pageCount));
            pagePill.setOnClickListener(v -> { page = (page+1)%pageCount; renderPage(); });
            pagePill.measure(MeasureSpec.makeMeasureSpec(available/3,MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            pagingWidth = pagePill.getMeasuredWidth()+dp(8);
        }
        close.measure(MeasureSpec.makeMeasureSpec(Math.max(1,available-pagingWidth),MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        int h = Math.max(close.getMeasuredHeight(),pagePill == null ? 0 : pagePill.getMeasuredHeight());
        int x = snapshot.wall.centerX()-(close.getMeasuredWidth()+pagingWidth)/2;
        int y = snapshot.wall.bottom-h-dp(8);
        put(close,new Rect(x,y,x+close.getMeasuredWidth(),y+h));
        if (pagePill != null) put(pagePill,new Rect(x+close.getMeasuredWidth()+dp(8),y,
            x+close.getMeasuredWidth()+pagingWidth,y+h));
        requestLayout(); invalidate();
    }
    private Rect keyLabelBounds(Rect cap) {
        Rect label = new Rect(cap);
        label.inset(dp(2), dp(2));
        // Text stays on its measured cap, with the outer edge lanes left clear for leaders.
        label.left = Math.max(label.left, dp(12));
        label.right = Math.min(label.right, getWidth() - dp(12));
        return label;
    }

    private void put(View view, Rect rect) {
        // A card can move between its normal page and a copy-only scroll page on remeasurement.
        if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
        addView(view,new LayoutParams(Math.max(1,rect.width()),Math.max(1,rect.height())));
        childBounds.put(view,rect);
    }
    private TextView card(HelpCopy copy) {
        TextView text = new TextView(getContext());
        SpannableString content = new SpannableString(copy.title+"\n"+copy.body);
        content.setSpan(new StyleSpan(Typeface.BOLD),0,copy.title.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setText(content); text.setTextSize(12); text.setTextColor(dress.textColor);
        text.setPadding(dp(10),dp(8),dp(10),dp(8));
        text.setLineSpacing(dp(2),1);
        text.setBackground(dress.background(0));
        return text;
    }
    private TextView pill(String copy) {
        TextView text = new TextView(getContext());
        text.setText(copy); text.setTextSize(12); text.setTextColor(dress.textColor);
        text.setGravity(Gravity.CENTER); text.setPadding(dp(10),dp(8),dp(10),dp(8));
        text.setBackground(dress.background(dp(40)));
        return text;
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        for (Map.Entry<View,Rect> entry : childBounds.entrySet()) {
            View child = entry.getKey(); Rect bounds = entry.getValue();
            child.layout(bounds.left,bounds.top,bounds.right,bounds.bottom);
            if (child.getBackground() instanceof GradientDrawable)
                ((GradientDrawable)child.getBackground()).setCornerRadius(dress.cornerRadiusPx(bounds.height()));
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!showing) return;
        canvas.drawColor(Color.argb(166,0,0,0));
        if (routed == null) return;
        paint.setColor(accent); paint.setStrokeWidth(dp(1)); paint.setStyle(Paint.Style.STROKE);
        for (HelpLeaderRouter.Placement p : routed.placements) if (p.page == page) {
            paint.setPathEffect(null);
            for (HelpLeaderRouter.Segment line : p.lines)
                canvas.drawLine(line.x1,line.y1,line.x2,line.y2,paint);
            HelpTargets.Target target = target(p.target.id);
            RectF bounds = new RectF(target.rect); bounds.inset(dp(2),dp(2));
            if (bounds.isEmpty()) continue;
            paint.setPathEffect(dash);
            float radius = Math.max(0,target.radius-dp(2));
            canvas.drawRoundRect(bounds,radius,radius,paint);
        }
        paint.setPathEffect(null);
    }
    private HelpTargets.Target target(String id) {
        for (HelpTargets.Target t : snapshot.targets) if (t.id.equals(id)) return t;
        throw new IllegalStateException(id);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // Children receive taps and fallback-page scrolling; nothing can pass through this layer.
        super.dispatchTouchEvent(event);
        return true;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=event.getX(); downY=event.getY(); moved=false; break;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX()-downX,event.getY()-downY)
                    > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) moved=true;
                break;
            case MotionEvent.ACTION_UP:
                if (!moved && !insideCard(downX,downY) && !insideCard(event.getX(),event.getY())) {
                    performClick(); dismiss();
                }
                break;
        }
        return true;
    }
    private boolean insideCard(float x,float y) {
        for (View card : cards) { Rect r=childBounds.get(card); if (r != null && r.contains((int)x,(int)y)) return true; }
        return false;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private int dp(float value) { return Math.round(value*density); }
    private static HelpLeaderRouter.Box box(Rect r) { return new HelpLeaderRouter.Box(r.left,r.top,r.right,r.bottom); }
    private static Rect rect(HelpLeaderRouter.Box b) { return new Rect(Math.round(b.left),Math.round(b.top),Math.round(b.right),Math.round(b.bottom)); }
}
