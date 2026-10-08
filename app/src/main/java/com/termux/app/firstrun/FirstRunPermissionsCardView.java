package com.termux.app.firstrun;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.FocusOutlineRenderer;
import com.termux.app.material.M3;
import com.termux.app.notice.TerminalDress;
import com.termux.app.statusbar.WeatherGeocoder;
import com.termux.app.statusbar.WeatherPlaceSearchView;

import java.util.List;

/**
 * The first thing a fresh install shows: one card, a row per thing the launcher would like, and a
 * Continue.
 *
 * <p>It wears the same dress as the tour's own cards — the terminal's fill, hairline and radius,
 * flat — so the run that follows it opens on the same material. It is not a tour card, though: it
 * is shown before the tour is even offered, it has to come up on an update where no tour is
 * running at all, and its rows carry controls rather than a gesture, so it lives on its own rather
 * than inside {@code TourOverlayView}'s step shell.
 *
 * <p>Unlike the tour overlay this one does take touches, and takes all of them: it is a modal step
 * of the setup, and the chrome underneath it must not be operable while it is up.
 *
 * <p>The weather row asks for a place first and for the device's location second: its control is
 * the same city search Settings offers, with Use my location under it for whoever would rather
 * grant the permission. The search field is the one thing here that wants the system keyboard,
 * and the activity, which owns that keyboard, is told when the field takes and gives up focus.
 */
public final class FirstRunPermissionsCardView extends FrameLayout {

    /** What the card's controls mean; the activity does the asking. */
    public interface Callbacks {
        /**
         * The Allow button on a permission row was tapped, or the weather row's Use my location,
         * which also means the weather should stop following a picked place.
         */
        void onFirstRunPermissionTapped(@NonNull FirstRunPermissionsCard.Item item);

        /** A place was picked from the weather row's search. */
        void onFirstRunWeatherPlacePicked(@NonNull String label, double latitude,
                                          double longitude);

        /** The weather row's search field took focus, and wants the system keyboard, or gave it up. */
        void onFirstRunPlaceSearchFocusChanged(@NonNull EditText field, boolean focused);

        /** The Linux display switch was moved. */
        void onFirstRunDisplayToggled(boolean enabled);

        /** Continue: the card is done with, whatever the user granted. */
        void onFirstRunContinueTapped();
    }

    private static final long CARD_IN_MS = 220L;
    private static final float CARD_RISE_DP = 10f;
    private static final float CARD_MAX_WIDTH_DP = 340f;
    private static final float CARD_SIDE_MARGIN_DP = 20f;
    /** The scrim over the live chrome: enough to say the card is the only thing to answer. */
    private static final int SCRIM_ALPHA = 140;
    /** Stands in for "as tall as it likes": a measure spec carries no unbounded size of its own. */
    private static final int UNBOUNDED_PX = 1 << 24;

    private final float mDensity;
    private final TerminalDress mDress;
    private final int mAccent;
    private final LinearLayout mCard;
    private final LinearLayout mRows;
    private final ScrollView mScroll;
    private final TextView mContinue;

    @Nullable private Callbacks mCallbacks;
    @Nullable private ValueAnimator mCardIn;
    /** Set while the rows are being bound, so restoring a switch does not report a user move. */
    private boolean mBinding;
    /**
     * The weather row's search, kept across rebinds: a permission answered while the user is
     * half-way through typing a city must not wipe what they typed. Built with the first weather
     * row.
     */
    @Nullable private WeatherPlaceSearchView mPlaceSearch;
    /** The rows as last bound, so an unchanged answer does not rebuild the card. */
    @Nullable private List<FirstRunPermissionsCard.Row> mBoundRows;
    /** How tall the rows may grow before they scroll, so Continue always stays on the card. */
    private int mScrollMaxHeight = UNBOUNDED_PX;

    public FirstRunPermissionsCardView(@NonNull Context context) {
        super(context);
        // Reading-order UI over the shell: mirrors with the locale, though the content root it is
        // added to is pinned left to right. The card is centred, so nothing here moves.
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
        mDensity = context.getResources().getDisplayMetrics().density;
        mDress = TerminalDress.stored(context);
        mAccent = FocusOutlineRenderer.resolveAccent(this);
        setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, SCRIM_ALPHA));
        setClickable(true);
        setFocusable(true);

        mCard = new LinearLayout(context);
        mCard.setOrientation(LinearLayout.VERTICAL);
        mCard.setBackground(mDress.background(0));
        mCard.setPadding(dp(18), dp(16), dp(18), dp(10));

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
            android.graphics.Typeface.BOLD));
        title.setTextColor(mDress.textColor);
        title.setText(R.string.first_run_permissions_title);
        mCard.addView(title, matchWrap());

        TextView intro = new TextView(context);
        intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        intro.setTextColor(ColorUtils.setAlphaComponent(mDress.textColor, 204));
        intro.setLineSpacing(dp(2), 1f);
        intro.setText(R.string.first_run_permissions_intro);
        LinearLayout.LayoutParams introParams = matchWrap();
        introParams.topMargin = dp(4);
        mCard.addView(intro, introParams);

        mRows = new LinearLayout(context);
        mRows.setOrientation(LinearLayout.VERTICAL);
        // Three rows at a large font scale on a short phone is taller than the screen, and the
        // weather search's results and the keyboard make it taller still: the rows scroll inside
        // the card rather than pushing Continue off the bottom of it. How tall they may be is
        // worked out in onMeasure, from what the rest of the card leaves them.
        mScroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int offered = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? UNBOUNDED_PX : MeasureSpec.getSize(heightMeasureSpec);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    Math.min(offered, mScrollMaxHeight), MeasureSpec.AT_MOST));
            }
        };
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mScroll.setClipToPadding(false);
        mScroll.addView(mRows, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = matchWrap();
        scrollParams.topMargin = dp(10);
        mCard.addView(mScroll, scrollParams);

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        mContinue = textButton(context, view -> {
            if (mCallbacks != null) mCallbacks.onFirstRunContinueTapped();
        });
        mContinue.setText(R.string.first_run_permissions_continue);
        mContinue.setMinHeight(dp(48));
        mContinue.setMinWidth(dp(48));
        buttonRow.addView(mContinue, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams buttonRowParams = matchWrap();
        buttonRowParams.topMargin = dp(6);
        buttonRowParams.setMarginEnd(-dp(4));
        mCard.addView(buttonRow, buttonRowParams);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        cardParams.leftMargin = dp(CARD_SIDE_MARGIN_DP);
        cardParams.rightMargin = dp(CARD_SIDE_MARGIN_DP);
        addView(mCard, cardParams);
    }

    /** The layout params the activity adds this view to its content view with. */
    @NonNull
    public static FrameLayout.LayoutParams buildLayoutParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /**
     * The system bars' keep-out, and the system keyboard's while it is up for the place search,
     * so the card never rests under the status bar, the gesture bar or the keyboard.
     */
    public void setSystemBarInsets(int top, int bottom) {
        int clampedTop = Math.max(0, top);
        int clampedBottom = Math.max(0, bottom);
        if (getPaddingTop() == clampedTop && getPaddingBottom() == clampedBottom) return;
        setPadding(0, clampedTop, 0, clampedBottom);
    }

    /** Builds the rows as they stand right now; called again after every answer. */
    public void bind(@NonNull List<FirstRunPermissionsCard.Row> rows) {
        // Every resume asks again; only a changed answer rebuilds, so a field being typed in
        // keeps its text, its focus and its keyboard.
        if (rows.equals(mBoundRows)) return;
        mBoundRows = rows;
        mBinding = true;
        mRows.removeAllViews();
        boolean first = true;
        for (FirstRunPermissionsCard.Row row : rows) {
            mRows.addView(rowView(row, first));
            first = false;
        }
        mBinding = false;
        // The card is as wide as the screen allows, up to the width a sentence reads well at.
        requestLayout();
    }

    /** Raises the card, once. */
    public void animateIn() {
        if (mCardIn != null) mCardIn.cancel();
        mCard.setAlpha(0f);
        mCard.setTranslationY(dp(CARD_RISE_DP));
        mCardIn = ValueAnimator.ofFloat(0f, 1f);
        mCardIn.setDuration(CARD_IN_MS);
        mCardIn.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        mCardIn.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            mCard.setAlpha(t);
            mCard.setTranslationY(dp(CARD_RISE_DP) * (1f - t));
        });
        mCardIn.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mCardIn != null) {
            mCardIn.cancel();
            mCardIn = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = MeasureSpec.getSize(widthMeasureSpec) - 2 * dp(CARD_SIDE_MARGIN_DP);
        int max = dp(CARD_MAX_WIDTH_DP);
        ViewGroup.LayoutParams params = mCard.getLayoutParams();
        int wanted = available > max ? max : ViewGroup.LayoutParams.MATCH_PARENT;
        if (params.width != wanted) {
            params.width = wanted;
            mCard.setLayoutParams(params);
        }
        // Measured twice when the card is taller than its room: once unbounded, for what it would
        // like to be, and again with the rows held to what the title and Continue leave them. A
        // card measured straight against the room hands the rows all of it, and Continue none.
        mScrollMaxHeight = UNBOUNDED_PX;
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int room = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop()
                - getPaddingBottom();
            int overflow = mCard.getMeasuredHeight() - room;
            if (overflow > 0)
                mScrollMaxHeight = Math.max(0, mScroll.getMeasuredHeight() - overflow);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Every touch stops here: the chrome below is not operable while the card is up. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    /**
     * One row: a title, a sentence, and a control on the trailing edge. The weather row carries
     * its search full width under the sentence instead, where a city name has room.
     */
    @NonNull
    private View rowView(@NonNull FirstRunPermissionsCard.Row row, boolean first) {
        Context context = getContext();
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        fillLine(line, row);
        LinearLayout.LayoutParams rowParams = matchWrap();
        if (!first) rowParams.topMargin = dp(14);
        if (!row.searchesPlace()) {
            line.setLayoutParams(rowParams);
            return line;
        }
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setLayoutParams(rowParams);
        block.addView(line, matchWrap());
        addPlaceSearch(block, row);
        return block;
    }

    /** The row's words, and its control or its status on the trailing edge. */
    private void fillLine(@NonNull LinearLayout line, @NonNull FirstRunPermissionsCard.Row row) {
        Context context = getContext();
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);

        TextView heading = new TextView(context);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
            android.graphics.Typeface.NORMAL));
        heading.setTextColor(mDress.textColor);
        heading.setText(row.titleRes);
        words.addView(heading, matchWrap());

        TextView copy = new TextView(context);
        copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        copy.setTextColor(ColorUtils.setAlphaComponent(mDress.textColor, 180));
        copy.setLineSpacing(dp(1), 1f);
        copy.setText(row.copyRes);
        LinearLayout.LayoutParams copyParams = matchWrap();
        copyParams.topMargin = dp(1);
        words.addView(copy, copyParams);

        line.addView(words, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // The weather row's button sits under its search; beside the words it only says where
        // the permission stands, and only once there is something to say.
        if (row.searchesPlace() && row.statusRes() == 0) return;
        line.addView(controlFor(row), controlParams());
    }

    /**
     * Under the weather row's words: the place it follows, the search, Use my location, and
     * whose search it is.
     */
    private void addPlaceSearch(@NonNull LinearLayout block,
                                @NonNull FirstRunPermissionsCard.Row row) {
        Context context = getContext();
        if (row.followsPlace()) {
            TextView place = new TextView(context);
            place.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            place.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
            place.setTextColor(mDress.textColor);
            place.setText(context.getString(R.string.settings_weather_location_current,
                row.place));
            LinearLayout.LayoutParams placeParams = matchWrap();
            placeParams.topMargin = dp(4);
            block.addView(place, placeParams);
        }

        WeatherPlaceSearchView search = placeSearch();
        if (search.getParent() instanceof ViewGroup)
            ((ViewGroup) search.getParent()).removeView(search);
        LinearLayout.LayoutParams searchParams = matchWrap();
        searchParams.topMargin = dp(8);
        block.addView(search, searchParams);

        if (row.hasButton()) {
            TextView useLocation = new TextView(context);
            useLocation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            useLocation.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
            useLocation.setTextColor(mAccent);
            useLocation.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            useLocation.setMinHeight(dp(48));
            useLocation.setSingleLine(true);
            useLocation.setEllipsize(TextUtils.TruncateAt.END);
            useLocation.setText(row.buttonRes());
            TypedValue ripple = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                ripple, true) && ripple.resourceId != 0) {
                useLocation.setBackgroundResource(ripple.resourceId);
            }
            useLocation.setOnClickListener(view -> {
                // The device instead of a place: whatever was being typed is dropped, and the
                // keyboard goes before the system's own dialog comes up.
                search.field().clearFocus();
                search.reset();
                if (mCallbacks != null) mCallbacks.onFirstRunPermissionTapped(row.item);
            });
            block.addView(useLocation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView credit = new TextView(context);
        M3.textAppearance(credit, com.google.android.material.R.attr.textAppearanceBodySmall);
        credit.setTextColor(M3.onSurfaceVariant(context));
        credit.setText(R.string.first_run_permissions_weather_search_credit);
        LinearLayout.LayoutParams creditParams = matchWrap();
        if (!row.hasButton()) creditParams.topMargin = dp(4);
        block.addView(credit, creditParams);
    }

    /** The weather row's search, built once and dressed like the card it sits on. */
    @NonNull
    private WeatherPlaceSearchView placeSearch() {
        if (mPlaceSearch != null) return mPlaceSearch;
        WeatherPlaceSearchView search = new WeatherPlaceSearchView(getContext());
        search.setInk(mDress.textColor, ColorUtils.setAlphaComponent(mDress.textColor, 180));
        EditText field = search.field();
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        field.setMinHeight(dp(48));
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable box = new GradientDrawable();
        box.setShape(GradientDrawable.RECTANGLE);
        box.setCornerRadius(dp(8));
        box.setStroke(Math.round(mDress.strokeWidthPx),
            ColorUtils.setAlphaComponent(mDress.textColor, 96));
        field.setBackground(box);
        search.setListener(new WeatherPlaceSearchView.Listener() {
            @Override
            public void onPlacePicked(@NonNull WeatherGeocoder.Result place) {
                search.field().clearFocus();
                search.reset();
                if (mCallbacks != null)
                    mCallbacks.onFirstRunWeatherPlacePicked(place.label(), place.latitude,
                        place.longitude);
            }

            @Override
            public void onSearchFocusChanged(@NonNull EditText focusedField, boolean focused) {
                if (mCallbacks != null)
                    mCallbacks.onFirstRunPlaceSearchFocusChanged(focusedField, focused);
            }
        });
        mPlaceSearch = search;
        return search;
    }

    /** The row's control: a switch, a button to ask with, or the word for an answer given. */
    @NonNull
    private View controlFor(@NonNull FirstRunPermissionsCard.Row row) {
        Context context = getContext();
        if (row.isSwitch) {
            MaterialSwitch toggle = new MaterialSwitch(context);
            toggle.setChecked(row.isOn());
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (mBinding || mCallbacks == null) return;
                mCallbacks.onFirstRunDisplayToggled(checked);
            });
            return toggle;
        }
        if (row.hasButton() && !row.searchesPlace()) {
            TextView button = textButton(context, view -> {
                if (mCallbacks != null) mCallbacks.onFirstRunPermissionTapped(row.item);
            });
            button.setText(row.buttonRes());
            button.setContentDescription(context.getString(row.buttonRes()));
            button.setMinHeight(dp(48));
            button.setMinWidth(dp(48));
            // A refused row keeps its button — a second tap asks again — and says so beside it.
            if (row.statusRes() != 0) {
                LinearLayout stack = new LinearLayout(context);
                stack.setOrientation(LinearLayout.HORIZONTAL);
                stack.setGravity(Gravity.CENTER_VERTICAL);
                stack.addView(statusLabel(row.statusRes()));
                stack.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return stack;
            }
            return button;
        }
        return statusLabel(row.statusRes());
    }

    @NonNull
    private TextView statusLabel(int statusRes) {
        TextView status = new TextView(getContext());
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        status.setTextColor(ColorUtils.setAlphaComponent(mDress.textColor, 160));
        status.setPadding(dp(8), dp(6), dp(8), dp(6));
        if (statusRes != 0) status.setText(statusRes);
        return status;
    }

    @NonNull
    private LinearLayout.LayoutParams controlParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(8));
        return params;
    }

    @NonNull
    private TextView textButton(@NonNull Context context, @NonNull OnClickListener onClick) {
        TextView button = new TextView(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
            android.graphics.Typeface.NORMAL));
        button.setTextColor(mAccent);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        button.setBackground(buttonBackground());
        button.setOnClickListener(onClick);
        return button;
    }

    @NonNull
    private GradientDrawable buttonBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(8));
        background.setColor(ColorUtils.setAlphaComponent(mAccent, 28));
        return background;
    }

    @NonNull
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }
}
