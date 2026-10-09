package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.material.M3;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A city search for the weather: a text field, a line saying what the search is doing, and the
 * matches under it. Settings' Location dialog and the first-run card both use it, so there is one
 * search and one way of picking.
 *
 * <p>Typing is debounced, so a search goes out once the user pauses rather than per letter, and
 * every search carries a generation: an answer that arrives after the text has changed again is
 * dropped instead of replacing newer results. A search still queued when a newer one is made is
 * cancelled before it starts; one already on the wire cannot be interrupted, and is simply ignored.
 *
 * <p>The view stores nothing. A tapped match goes to {@link Listener#onPlacePicked}, and the host
 * decides what picking means; the host also owns the system keyboard, which it is told about
 * through {@link Listener#onSearchFocusChanged}.
 */
public final class WeatherPlaceSearchView extends LinearLayout {

    /** What the host does with the search. */
    public interface Listener {
        /** A match was tapped. */
        void onPlacePicked(@NonNull WeatherGeocoder.Result place);

        /**
         * The field took focus or gave it up. A host that has to clear the way for the system
         * keyboard first, or put its own back afterwards, does it here.
         */
        void onSearchFocusChanged(@NonNull EditText field, boolean focused);
    }

    /** Long enough to wait out a word being typed, short enough to read as live. */
    private static final long DEBOUNCE_MS = 300L;
    /** How long the search thread waits for another search before it goes away. */
    private static final long IDLE_THREAD_SECONDS = 15L;

    /**
     * One worker for every search view in the process, in order. Its thread is a daemon and times
     * out when idle, so a search view that is simply dropped leaves nothing running behind it.
     */
    private static final ThreadPoolExecutor SEARCHES = searchExecutor();

    private final float mDensity;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final EditText mInput;
    private final TextView mStatus;
    private final LinearLayout mResults;
    private final Rect mRevealRect = new Rect();
    @ColorInt private int mInk;
    @ColorInt private int mSecondaryInk;

    @Nullable private Listener mListener;
    @Nullable private Future<?> mPending;
    /** Bumped by every keystroke and by detaching; an answer for an older one is dropped. */
    private int mGeneration;
    /** Set while the text is changed from code, so that change does not go out as a search. */
    private boolean mSettingText;
    /** Set when fresh results are in, so the first one is scrolled into view once laid out. */
    private boolean mRevealResults;

    private final Runnable mSearch = this::search;

    public WeatherPlaceSearchView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        mDensity = context.getResources().getDisplayMetrics().density;
        mInk = M3.onSurface(context);
        mSecondaryInk = M3.onSurfaceVariant(context);

        mInput = new EditText(context);
        mInput.setSingleLine(true);
        mInput.setHint(R.string.settings_weather_location_search_hint);
        mInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        mInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        addView(mInput, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        mStatus = new TextView(context);
        mStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mStatus.setPadding(0, dp(8), 0, dp(4));
        mStatus.setVisibility(GONE);
        addView(mStatus, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        mResults = new LinearLayout(context);
        mResults.setOrientation(VERTICAL);
        addView(mResults, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        // Fresh results land under a field the keyboard may have pushed to the bottom of a short
        // scroller: the first match is brought into view once it has a place on the screen.
        mResults.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (!mRevealResults || mResults.getChildCount() == 0) return;
            mRevealResults = false;
            View first = mResults.getChildAt(0);
            post(() -> {
                if (first.getParent() != mResults) return;
                mRevealRect.set(0, 0, first.getWidth(), first.getHeight());
                first.requestRectangleOnScreen(mRevealRect);
            });
        });

        applyInk();

        mInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (mSettingText) return;
                mGeneration++;
                mMainHandler.removeCallbacks(mSearch);
                mMainHandler.postDelayed(mSearch, DEBOUNCE_MS);
            }
        });
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            mMainHandler.removeCallbacks(mSearch);
            search();
            return true;
        });
        mInput.setOnFocusChangeListener((v, focused) -> {
            if (mListener != null) mListener.onSearchFocusChanged(mInput, focused);
        });
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** The text field, for a host that dresses it to match its own surface. */
    @NonNull
    public EditText field() {
        return mInput;
    }

    /** The two colours the field, the status line and the matches are drawn in. */
    public void setInk(@ColorInt int ink, @ColorInt int secondaryInk) {
        if (mInk == ink && mSecondaryInk == secondaryInk) return;
        mInk = ink;
        mSecondaryInk = secondaryInk;
        applyInk();
    }

    /** Says something on the status line; empty takes the line away. */
    public void showStatus(@NonNull CharSequence text) {
        mStatus.setText(text);
        mStatus.setVisibility(text.length() == 0 ? GONE : VISIBLE);
    }

    /** Back to an empty field with nothing under it, and no search left running. */
    public void reset() {
        cancelSearch();
        mSettingText = true;
        try {
            mInput.setText("");
        } finally {
            mSettingText = false;
        }
        mResults.removeAllViews();
        showStatus("");
    }

    /** Drops whatever search is waiting or on the wire; its answer will not be shown. */
    public void cancelSearch() {
        mGeneration++;
        mMainHandler.removeCallbacks(mSearch);
        if (mPending != null) mPending.cancel(false);
        mPending = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelSearch();
        super.onDetachedFromWindow();
    }

    /** Runs on the main thread once typing pauses; the search itself goes to the worker. */
    private void search() {
        String typed = mInput.getText().toString().trim();
        int generation = ++mGeneration;
        if (mPending != null) mPending.cancel(false);
        mPending = null;
        if (WeatherGeocoder.searchName(typed).length() < 2) {
            mResults.removeAllViews();
            showStatus(typed.isEmpty() ? ""
                : getContext().getString(R.string.settings_weather_location_type_more));
            return;
        }
        showStatus(getContext().getString(R.string.settings_weather_location_searching));
        try {
            mPending = SEARCHES.submit(() -> {
                List<WeatherGeocoder.Result> results =
                    WeatherGeocoder.search(typed, WeatherGeocoder.PICKER_COUNT);
                mMainHandler.post(() -> {
                    if (generation != mGeneration || !isAttachedToWindow()) return;
                    showResults(results);
                });
            });
        } catch (RejectedExecutionException e) {
            showStatus(getContext().getString(R.string.settings_weather_location_network_error));
        }
    }

    private void showResults(@Nullable List<WeatherGeocoder.Result> results) {
        mResults.removeAllViews();
        if (results == null) {
            showStatus(getContext().getString(R.string.settings_weather_location_network_error));
            return;
        }
        if (results.isEmpty()) {
            showStatus(getContext().getString(R.string.settings_weather_location_no_results));
            return;
        }
        showStatus("");
        for (WeatherGeocoder.Result result : results) mResults.addView(row(result));
        mRevealResults = true;
    }

    @NonNull
    private View row(@NonNull WeatherGeocoder.Result result) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            && ripple.resourceId != 0) {
            row.setBackgroundResource(ripple.resourceId);
        }
        int padV = dp(10);
        row.setPadding(0, padV, 0, padV);

        TextView title = new TextView(context);
        title.setText(result.name.isEmpty() ? result.label() : result.name);
        M3.textAppearance(title, com.google.android.material.R.attr.textAppearanceTitleMedium);
        title.setTextColor(mInk);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title);

        String detail = result.detail();
        if (!detail.isEmpty()) {
            TextView sub = new TextView(context);
            sub.setText(detail);
            M3.textAppearance(sub, com.google.android.material.R.attr.textAppearanceBodySmall);
            sub.setTextColor(mSecondaryInk);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(sub);
        }

        row.setOnClickListener(v -> {
            if (mListener != null) mListener.onPlacePicked(result);
        });
        return row;
    }

    private void applyInk() {
        mInput.setTextColor(mInk);
        mInput.setHintTextColor(mSecondaryInk);
        mStatus.setTextColor(mSecondaryInk);
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }

    @NonNull
    private static ThreadPoolExecutor searchExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, IDLE_THREAD_SECONDS,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "weather-place-search");
                t.setDaemon(true);
                return t;
            });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
