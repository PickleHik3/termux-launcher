package com.termux.app.fragments.settings.termux;

import android.content.Context;
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
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.material.M3;
import com.termux.app.statusbar.WeatherGeocoder;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Picks the place the weather follows, from live Open-Meteo search results under a text field.
 *
 * <p>Typing is debounced, so a search goes out once the user pauses rather than per letter, and
 * every search carries a generation: an answer that arrives after the text has changed again is
 * dropped instead of replacing newer results. A search still queued when a newer one is made is
 * cancelled before it starts; one already on the wire cannot be interrupted, and is simply ignored.
 *
 * <p>Tapping a result stores its label and coordinates together, so the weather is fetched for
 * exactly the place that was tapped without searching again. "Use device location" clears both.
 */
final class WeatherPlacePickerDialog {

    /** Long enough to wait out a word being typed, short enough to read as live. */
    private static final long DEBOUNCE_MS = 300L;

    private final Context mContext;
    @Nullable private final Runnable mOnChanged;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    // Daemon thread so a search left running when the dialog goes never keeps the process alive.
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "weather-place-search");
        t.setDaemon(true);
        return t;
    });
    @Nullable private Future<?> mPending;
    /** Bumped by every keystroke and by dismissal; an answer for an older one is dropped. */
    private int mGeneration;
    private boolean mDismissed;

    private EditText mInput;
    private TextView mStatus;
    private LinearLayout mResults;
    private AlertDialog mDialog;

    private final Runnable mSearch = this::search;

    private WeatherPlacePickerDialog(@NonNull Context context, @Nullable Runnable onChanged) {
        mContext = context;
        mOnChanged = onChanged;
    }

    /** Shows the picker; {@code onChanged} runs after a place is picked or cleared. */
    static void show(@NonNull Context context, @Nullable Runnable onChanged) {
        new WeatherPlacePickerDialog(context, onChanged).build().show();
    }

    @NonNull
    private AlertDialog build() {
        float density = mContext.getResources().getDisplayMetrics().density;
        int padH = Math.round(24 * density);
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padH, Math.round(8 * density), padH, 0);

        mInput = new EditText(mContext);
        mInput.setSingleLine(true);
        mInput.setHint(R.string.settings_weather_location_search_hint);
        mInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        mInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        layout.addView(mInput);

        mStatus = new TextView(mContext);
        mStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mStatus.setTextColor(M3.onSurfaceVariant(mContext));
        mStatus.setPadding(0, Math.round(8 * density), 0, Math.round(4 * density));
        layout.addView(mStatus);

        mResults = new LinearLayout(mContext);
        mResults.setOrientation(LinearLayout.VERTICAL);
        layout.addView(mResults, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // A custom view is not scrolled by the dialog; wrap it so eight results and the buttons
        // stay reachable above the keyboard.
        ScrollView scroll = new ScrollView(mContext);
        scroll.addView(layout);

        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(mContext, false);
        String current = preferences == null ? "" : preferences.getStatusWidgetWeatherLocation();
        showStatus(current.isEmpty() ? mContext.getString(R.string.settings_weather_location_summary_device)
            : mContext.getString(R.string.settings_weather_location_current, current));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mContext)
            .setTitle(R.string.settings_weather_location_title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null);
        // Only offered when there is something to go back from.
        if (!current.isEmpty()) {
            builder.setNeutralButton(R.string.settings_weather_location_use_device, (d, w) -> {
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(mContext, false);
                if (prefs != null) prefs.clearStatusWidgetWeatherPlace();
                if (mOnChanged != null) mOnChanged.run();
            });
        }
        mDialog = builder.create();
        mDialog.setOnDismissListener(d -> {
            mDismissed = true;
            mGeneration++;
            mMainHandler.removeCallbacks(mSearch);
            mExecutor.shutdownNow();
        });

        mInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
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
        mDialog.setOnShowListener(d -> mInput.requestFocus());
        return mDialog;
    }

    /** Runs on the main thread once typing pauses; the search itself goes to the worker. */
    private void search() {
        if (mDismissed) return;
        String typed = mInput.getText().toString().trim();
        int generation = ++mGeneration;
        if (mPending != null) mPending.cancel(false);
        mPending = null;
        if (WeatherGeocoder.searchName(typed).length() < 2) {
            mResults.removeAllViews();
            showStatus(typed.isEmpty() ? "" : mContext.getString(R.string.settings_weather_location_type_more));
            return;
        }
        showStatus(mContext.getString(R.string.settings_weather_location_searching));
        try {
            mPending = mExecutor.submit(() -> {
                List<WeatherGeocoder.Result> results =
                    WeatherGeocoder.search(typed, WeatherGeocoder.PICKER_COUNT);
                mMainHandler.post(() -> {
                    if (mDismissed || generation != mGeneration) return;
                    showResults(results);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // The dialog went between the keystroke and here; nothing is listening.
        }
    }

    private void showResults(@Nullable List<WeatherGeocoder.Result> results) {
        mResults.removeAllViews();
        if (results == null) {
            showStatus(mContext.getString(R.string.settings_weather_location_network_error));
            return;
        }
        if (results.isEmpty()) {
            showStatus(mContext.getString(R.string.settings_weather_location_no_results));
            return;
        }
        showStatus("");
        for (WeatherGeocoder.Result result : results) mResults.addView(row(result));
    }

    @NonNull
    private View row(@NonNull WeatherGeocoder.Result result) {
        float density = mContext.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        TypedValue ripple = new TypedValue();
        if (mContext.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            && ripple.resourceId != 0) {
            row.setBackgroundResource(ripple.resourceId);
        }
        int padV = Math.round(10 * density);
        row.setPadding(0, padV, 0, padV);

        TextView title = new TextView(mContext);
        title.setText(result.name.isEmpty() ? result.label() : result.name);
        M3.textAppearance(title, com.google.android.material.R.attr.textAppearanceTitleMedium);
        title.setTextColor(M3.onSurface(mContext));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title);

        String detail = result.detail();
        if (!detail.isEmpty()) {
            TextView sub = new TextView(mContext);
            sub.setText(detail);
            M3.textAppearance(sub, com.google.android.material.R.attr.textAppearanceBodySmall);
            sub.setTextColor(M3.onSurfaceVariant(mContext));
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(sub);
        }

        row.setOnClickListener(v -> {
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(mContext, false);
            if (prefs != null) {
                prefs.setStatusWidgetWeatherPlace(result.label(), result.latitude, result.longitude);
            }
            if (mOnChanged != null) mOnChanged.run();
            mDialog.dismiss();
        });
        return row;
    }

    private void showStatus(@NonNull String text) {
        mStatus.setText(text);
        mStatus.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
