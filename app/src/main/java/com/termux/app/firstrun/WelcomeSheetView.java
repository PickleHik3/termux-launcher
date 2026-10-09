package com.termux.app.firstrun;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.FocusOutlineRenderer;
import com.termux.app.chrome.ActionButtonRow;
import com.termux.app.chrome.ShapeTokens;
import com.termux.app.notice.TerminalDress;
import com.termux.app.statusbar.WeatherGeocoder;
import com.termux.app.statusbar.WeatherPlaceSearchView;
import com.termux.app.tour.CoachButtons;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The first-run setup sheet: a bottom sheet over the live home screen with a switch per thing the
 * launcher can turn on, the city search the weather switch reveals, the models the offline voice
 * and AI switch downloads, and the two ways into the run.
 *
 * <p>It decides nothing — {@link WelcomeSheet} does — and it is built once and then only bound, so
 * a switch keeps its own motion and a city half typed keeps its text and its keyboard while
 * everything around it changes. It takes every touch while it is up: it is the step the run is
 * offered on, and the chrome under it is not operable until it is answered.
 *
 * <p>It wears the terminal's dress, like the run's cards, and the theme's accent.
 */
public final class WelcomeSheetView extends FrameLayout {

    /** What the sheet's controls mean; the host applies them. */
    public interface Callbacks {
        void onRowSwitched(@NonNull WelcomeSheet.Row row, boolean on);

        void onChooseModelsTapped();

        void onModelTapped(@NonNull String rowId);

        /** Use my location: the weather should follow the device. */
        void onUseMyLocationTapped();

        void onPlacePicked(@NonNull String label, double latitude, double longitude);

        /** The city search took focus, and wants the system keyboard, or gave it up. */
        void onPlaceSearchFocusChanged(@NonNull EditText field, boolean focused);

        void onShowMeAround();

        void onSkipTheTour();
    }

    /** What the sheet shows beside its model, read from the launcher on every bind. */
    public static final class Facts {
        /** The place the weather follows, or empty while it follows the device. */
        @NonNull public String weatherPlace = "";
        public boolean locationGranted;
        /** Whether downloads wait for Wi-Fi. */
        public boolean wifiOnly = true;
        public long freeBytes;
        /** The phone's line under Choose models: tier, memory, chip and Android version. */
        @NonNull public String deviceLine = "";
    }

    private static final long SHEET_IN_MS = 280L;
    /** The sheet never grows taller than this, however tall the screen. */
    private static final float SHEET_MAX_HEIGHT_DP = 690f;
    /** On a tablet the sheet stops widening here. */
    private static final float SHEET_MAX_WIDTH_DP = 560f;
    private static final float SHEET_RADIUS_DP = 26f;
    /** The gap the sheet always leaves above itself, so the home screen shows it is a sheet. */
    private static final float SHEET_TOP_GAP_DP = 24f;
    private static final float GLYPH_DP = 20f;
    /** Where a row's extras start: past its glyph and the gap after it. */
    private static final float EXTRAS_INDENT_DP = 34f;
    /** The scrim over the live home screen. */
    private static final int SCRIM_ALPHA = 110;
    private static final int BODY_ALPHA = 200;
    /** The sheet's hairline divider, of the ink. */
    private static final int DIVIDER_ALPHA = 24;
    /** Every link holds a thumb's height even when its words are small. */
    private static final float LINK_TOUCH_DP = 48f;

    private final float mDensity;
    private final TerminalDress mDress;
    private final int mAccent;
    private final LinearLayout mSheet;
    private final ScrollView mScroll;
    private final LinearLayout mRows;
    private final Map<WelcomeSheet.Row, MaterialSwitch> mSwitches =
        new EnumMap<>(WelcomeSheet.Row.class);
    private final Map<WelcomeSheet.Row, TextView> mSubtitles =
        new EnumMap<>(WelcomeSheet.Row.class);
    private final Map<String, MaterialCheckBox> mModelBoxes = new HashMap<>();

    @Nullable private Callbacks mCallbacks;
    @Nullable private ValueAnimator mSheetIn;
    /** Set while binding, so restoring a switch or a box does not report a user move. */
    private boolean mBinding;
    private boolean mBuilt;
    private int mSheetBottomPadding;

    @Nullable private LinearLayout mWeatherExtras;
    @Nullable private TextView mWeatherPlace;
    @Nullable private WeatherPlaceSearchView mPlaceSearch;
    @Nullable private TextView mUseLocation;
    @Nullable private LinearLayout mAiExtras;
    @Nullable private TextView mModelsLink;
    @Nullable private LinearLayout mModelsList;
    @Nullable private TextView mDeviceLine;
    @Nullable private TextView mStorageLine;

    public WelcomeSheetView(@NonNull Context context) {
        super(context);
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
        mDensity = context.getResources().getDisplayMetrics().density;
        mDress = TerminalDress.stored(context);
        mAccent = FocusOutlineRenderer.resolveAccent(this);
        setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, SCRIM_ALPHA));
        setClickable(true);
        setFocusable(true);

        mSheet = new LinearLayout(context);
        mSheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(ColorUtils.setAlphaComponent(mDress.fillColor, 255));
        float radius = dp(SHEET_RADIUS_DP);
        background.setCornerRadii(new float[] {radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        mSheet.setBackground(background);
        mSheet.setElevation(ShapeTokens.elevationPx(context, 3));
        mSheet.setClickable(true);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView kicker = text(context, 10.5f, true, mDress.subTextColor);
        kicker.setAllCaps(true);
        kicker.setLetterSpacing(0.12f);
        kicker.setText(R.string.tour_card_kicker);
        singleLine(kicker);
        content.addView(kicker, matchWrap(0));
        TextView title = text(context, 22f, true, mDress.textColor);
        title.setText(R.string.welcome_sheet_title);
        content.addView(title, matchWrap(dp(4)));
        TextView intro = text(context, 13f, false,
            ColorUtils.setAlphaComponent(mDress.textColor, BODY_ALPHA));
        intro.setLineSpacing(0f, 1.25f);
        intro.setText(R.string.welcome_sheet_intro);
        content.addView(intro, matchWrap(dp(4)));
        View divider = new View(context);
        divider.setBackgroundColor(ColorUtils.setAlphaComponent(mDress.textColor, DIVIDER_ALPHA));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(mDensity)));
        dividerParams.topMargin = dp(14);
        content.addView(divider, dividerParams);
        mRows = new LinearLayout(context);
        mRows.setOrientation(LinearLayout.VERTICAL);
        content.addView(mRows, matchWrap(dp(4)));

        // The rows scroll inside the sheet rather than pushing its buttons off a short screen, a
        // large font scale, or a screen the city search's keyboard has taken half of.
        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mScroll.setClipToPadding(false);
        mScroll.setPadding(dp(20), dp(22), dp(20), 0);
        mScroll.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mSheet.addView(mScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ActionButtonRow buttons = new ActionButtonRow(context);
        buttons.setPadding(dp(20), dp(8), dp(20), 0);
        TextView skip = link(context, R.string.welcome_sheet_skip_tour,
            view -> { if (mCallbacks != null) mCallbacks.onSkipTheTour(); });
        buttons.setLeading(skip);
        TextView showMe = CoachButtons.primary(context, R.string.welcome_sheet_show_me_around,
            mAccent, onAccent(), view -> { if (mCallbacks != null) mCallbacks.onShowMeAround(); });
        buttons.addView(showMe);
        mSheet.addView(buttons, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mSheetBottomPadding = dp(18);
        mSheet.setPadding(0, 0, 0, mSheetBottomPadding);

        addView(mSheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** The layout params the host adds this view to its content view with. */
    @NonNull
    public static FrameLayout.LayoutParams buildLayoutParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /**
     * The system bars' keep-out, and the system keyboard's while it is up for the city search: the
     * sheet sits above both and never under the status bar.
     */
    public void setSystemBarInsets(int top, int bottom) {
        int clampedTop = Math.max(0, top);
        int clampedBottom = Math.max(0, bottom);
        boolean changed = getPaddingTop() != clampedTop
            || mSheet.getPaddingBottom() != mSheetBottomPadding + clampedBottom;
        if (!changed) return;
        setPadding(0, clampedTop, 0, 0);
        mSheet.setPadding(0, 0, 0, mSheetBottomPadding + clampedBottom);
    }

    /** Builds the rows once, then shows where every switch, box and line stands. */
    public void bind(@NonNull WelcomeSheet sheet, @NonNull Facts facts) {
        if (!mBuilt) {
            build(sheet);
            mBuilt = true;
        }
        mBinding = true;
        try {
            for (WelcomeSheet.Row row : sheet.rows()) {
                MaterialSwitch toggle = mSwitches.get(row);
                if (toggle != null && toggle.isChecked() != sheet.isOn(row))
                    toggle.setChecked(sheet.isOn(row));
            }
            bindWeather(sheet, facts);
            bindAi(sheet, facts);
        } finally {
            mBinding = false;
        }
    }

    /** Raises the sheet from the bottom edge, once. */
    public void animateIn() {
        if (mSheetIn != null) mSheetIn.cancel();
        if (!FocusOutlineRenderer.animationsEnabled(getContext())) return;
        mSheet.setTranslationY(dp(SHEET_MAX_HEIGHT_DP));
        setAlpha(0f);
        mSheetIn = ValueAnimator.ofFloat(0f, 1f);
        mSheetIn.setDuration(SHEET_IN_MS);
        mSheetIn.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        mSheetIn.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            setAlpha(Math.min(1f, t * 2f));
            float height = mSheet.getHeight() > 0 ? mSheet.getHeight() : dp(SHEET_MAX_HEIGHT_DP);
            mSheet.setTranslationY(height * (1f - t));
        });
        mSheetIn.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mSheetIn != null) {
            mSheetIn.cancel();
            mSheetIn = null;
        }
        super.onDetachedFromWindow();
    }

    /** Every touch stops here: the chrome below is not operable while the sheet is up. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        int sheetWidth = Math.min(width, dp(SHEET_MAX_WIDTH_DP));
        int room = height - getPaddingTop() - dp(SHEET_TOP_GAP_DP);
        int maxHeight = Math.max(0, Math.min(dp(SHEET_MAX_HEIGHT_DP)
            + mSheet.getPaddingBottom() - mSheetBottomPadding, room));
        mSheet.measure(MeasureSpec.makeMeasureSpec(sheetWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int sheetWidth = mSheet.getMeasuredWidth();
        int sheetLeft = (width - sheetWidth) / 2;
        int sheetBottom = bottom - top;
        mSheet.layout(sheetLeft, sheetBottom - mSheet.getMeasuredHeight(),
            sheetLeft + sheetWidth, sheetBottom);
    }

    // ---- the rows ------------------------------------------------------------------------------

    private void build(@NonNull WelcomeSheet sheet) {
        Context context = getContext();
        boolean first = true;
        for (WelcomeSheet.Row row : sheet.rows()) {
            LinearLayout block = new LinearLayout(context);
            block.setOrientation(LinearLayout.VERTICAL);
            block.addView(switchLine(context, row), matchWrap(0));
            if (row == WelcomeSheet.Row.WEATHER) block.addView(weatherExtras(context),
                extrasParams());
            if (row == WelcomeSheet.Row.AI) block.addView(aiExtras(context, sheet),
                extrasParams());
            mRows.addView(block, matchWrap(first ? 0 : dp(2)));
            first = false;
        }
    }

    @NonNull
    private LinearLayout.LayoutParams extrasParams() {
        LinearLayout.LayoutParams params = matchWrap(0);
        params.setMarginStart(dp(EXTRAS_INDENT_DP));
        params.bottomMargin = dp(6);
        return params;
    }

    /** A row's glyph, title, sentence and switch; the whole line moves the switch. */
    @NonNull
    private View switchLine(@NonNull Context context, @NonNull WelcomeSheet.Row row) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(9), 0, dp(9));
        line.setMinimumHeight(dp(LINK_TOUCH_DP));
        ImageView glyph = new ImageView(context);
        glyph.setImageResource(glyphFor(row));
        glyph.setImageTintList(ColorStateList.valueOf(mAccent));
        glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(dp(GLYPH_DP),
            dp(GLYPH_DP));
        glyphParams.setMarginEnd(dp(EXTRAS_INDENT_DP - GLYPH_DP));
        line.addView(glyph, glyphParams);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(context, 14f, false, mDress.textColor);
        heading.setText(titleFor(row));
        singleLine(heading);
        words.addView(heading, matchWrap(0));
        TextView sub = text(context, 12f, false, mDress.subTextColor);
        sub.setLineSpacing(0f, 1.2f);
        if (row != WelcomeSheet.Row.AI) sub.setText(subtitleFor(row));
        words.addView(sub, matchWrap(dp(2)));
        mSubtitles.put(row, sub);
        LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        line.addView(words, wordsParams);

        MaterialSwitch toggle = new MaterialSwitch(context);
        toggle.setContentDescription(context.getString(titleFor(row)));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (mBinding || mCallbacks == null) return;
            mCallbacks.onRowSwitched(row, checked);
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleParams.setMarginStart(dp(12));
        line.addView(toggle, toggleParams);
        mSwitches.put(row, toggle);
        line.setOnClickListener(view -> toggle.toggle());
        return line;
    }

    @DrawableRes
    private static int glyphFor(@NonNull WelcomeSheet.Row row) {
        switch (row) {
            case WALLPAPER: return R.drawable.ic_symbol_palette;
            case WEATHER: return R.drawable.ic_symbol_weather;
            case DISPLAY: return R.drawable.ic_symbol_desktop_windows;
            default: return R.drawable.ic_symbol_ai_star;
        }
    }

    @StringRes
    private static int titleFor(@NonNull WelcomeSheet.Row row) {
        switch (row) {
            case WALLPAPER: return R.string.welcome_row_wallpaper_title;
            case WEATHER: return R.string.welcome_row_weather_title;
            case DISPLAY: return R.string.welcome_row_display_title;
            default: return R.string.welcome_row_ai_title;
        }
    }

    @StringRes
    private static int subtitleFor(@NonNull WelcomeSheet.Row row) {
        switch (row) {
            case WALLPAPER: return R.string.welcome_row_wallpaper_copy;
            case WEATHER: return R.string.welcome_row_weather_copy;
            default: return R.string.welcome_row_display_copy;
        }
    }

    /** Under the weather row: the place it follows, the search, and Use my location. */
    @NonNull
    private View weatherExtras(@NonNull Context context) {
        LinearLayout extras = new LinearLayout(context);
        extras.setOrientation(LinearLayout.VERTICAL);
        mWeatherPlace = text(context, 13f, true, mDress.textColor);
        singleLine(mWeatherPlace);
        extras.addView(mWeatherPlace, matchWrap(0));

        WeatherPlaceSearchView search = new WeatherPlaceSearchView(context);
        search.setInk(mDress.textColor, mDress.subTextColor);
        EditText field = search.field();
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        field.setMinHeight(dp(LINK_TOUCH_DP));
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius(dp(10));
        box.setStroke(Math.round(mDress.strokeWidthPx),
            ColorUtils.setAlphaComponent(mDress.textColor, 64));
        field.setBackground(box);
        search.setListener(new WeatherPlaceSearchView.Listener() {
            @Override
            public void onPlacePicked(@NonNull WeatherGeocoder.Result place) {
                search.field().clearFocus();
                search.reset();
                if (mCallbacks != null)
                    mCallbacks.onPlacePicked(place.label(), place.latitude, place.longitude);
            }

            @Override
            public void onSearchFocusChanged(@NonNull EditText focusedField, boolean focused) {
                if (mCallbacks != null)
                    mCallbacks.onPlaceSearchFocusChanged(focusedField, focused);
            }
        });
        mPlaceSearch = search;
        extras.addView(search, matchWrap(dp(4)));

        // Whose search it is, and the other way to say where: one line, credit then link.
        LinearLayout foot = new LinearLayout(context);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView credit = text(context, 11f, false, mDress.subTextColor);
        credit.setText(R.string.welcome_weather_credit);
        singleLine(credit);
        foot.addView(credit, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mUseLocation = link(context, R.string.welcome_weather_use_location, view -> {
            // The device instead of a place: whatever was being typed is dropped, and the
            // keyboard goes before the system's own question comes up.
            search.field().clearFocus();
            search.reset();
            if (mCallbacks != null) mCallbacks.onUseMyLocationTapped();
        });
        foot.addView(mUseLocation, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        extras.addView(foot, matchWrap(0));
        mWeatherExtras = extras;
        return extras;
    }

    private void bindWeather(@NonNull WelcomeSheet sheet, @NonNull Facts facts) {
        if (mWeatherExtras == null || mWeatherPlace == null || mUseLocation == null) return;
        boolean shown = sheet.showsWeatherSearch();
        setShown(mWeatherExtras, shown);
        if (!shown && mPlaceSearch != null) {
            // Switched off mid-search: the keyboard goes with the field.
            if (mPlaceSearch.field().hasFocus()) mPlaceSearch.field().clearFocus();
            mPlaceSearch.reset();
        }
        String place = facts.weatherPlace.trim();
        setShown(mWeatherPlace, !place.isEmpty());
        if (!place.isEmpty()) setTextIfChanged(mWeatherPlace,
            getContext().getString(R.string.settings_weather_location_current, place));
        // Following the device already, with leave to: nothing to offer.
        setShown(mUseLocation, !place.isEmpty() || !facts.locationGranted);
    }

    /** Under the AI row: Choose models, and the list it opens. */
    @NonNull
    private View aiExtras(@NonNull Context context, @NonNull WelcomeSheet sheet) {
        LinearLayout extras = new LinearLayout(context);
        extras.setOrientation(LinearLayout.VERTICAL);
        mModelsLink = link(context, R.string.welcome_choose_models,
            view -> { if (mCallbacks != null) mCallbacks.onChooseModelsTapped(); });
        mModelsLink.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        extras.addView(mModelsLink, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        mDeviceLine = text(context, 10.5f, false, mDress.subTextColor);
        list.addView(mDeviceLine, matchWrap(0));
        for (TaiWelcomeCard.Row row : sheet.aiRows()) list.addView(modelLine(context, row),
            matchWrap(0));
        mStorageLine = text(context, 11f, false, mAccent);
        mStorageLine.setText(R.string.tai_welcome_reason_storage);
        list.addView(mStorageLine, matchWrap(dp(4)));
        mModelsList = list;
        extras.addView(list, matchWrap(0));
        mAiExtras = extras;
        return extras;
    }

    /** One model: its box, what it does, its name and its size. */
    @NonNull
    private View modelLine(@NonNull Context context, @NonNull TaiWelcomeCard.Row row) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        MaterialCheckBox box = new MaterialCheckBox(context);
        box.setEnabled(row.tickable());
        box.setContentDescription(context.getString(row.titleRes));
        box.setOnCheckedChangeListener((button, checked) -> {
            if (mBinding || mCallbacks == null) return;
            mCallbacks.onModelTapped(row.id);
        });
        line.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        mModelBoxes.put(row.id, box);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(context, 12.5f, false, mDress.textColor);
        name.setText(row.titleRes);
        singleLine(name);
        words.addView(name, matchWrap(0));
        TextView model = text(context, 10.5f, false, mDress.subTextColor);
        model.setText(row.modelNames);
        singleLine(model);
        words.addView(model, matchWrap(0));
        line.addView(words, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView size = text(context, 11f, false, mDress.subTextColor);
        size.setText(row.installed ? context.getString(R.string.tai_welcome_installed)
            : row.sizeText);
        size.setMaxLines(1);
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sizeParams.setMarginStart(dp(8));
        line.addView(size, sizeParams);
        if (row.tickable()) line.setOnClickListener(view -> box.toggle());
        return line;
    }

    private void bindAi(@NonNull WelcomeSheet sheet, @NonNull Facts facts) {
        TextView sub = mSubtitles.get(WelcomeSheet.Row.AI);
        if (sub != null) {
            CharSequence sentence = sheet.aiSize() == WelcomeSheet.AiSize.INSTALLED
                ? getContext().getString(R.string.welcome_row_ai_copy_installed)
                : getContext().getString(R.string.welcome_row_ai_copy,
                    TaiWelcomeCard.formatSize(sheet.selectedBytes()));
            setTextIfChanged(sub, sentence);
        }
        if (mAiExtras == null || mModelsLink == null || mModelsList == null) return;
        setShown(mAiExtras, sheet.showsChooseModels());
        setTextIfChanged(mModelsLink, getContext().getString(sheet.modelsExpanded()
            ? R.string.welcome_hide_models : R.string.welcome_choose_models));
        setShown(mModelsList, sheet.modelsExpanded());
        if (mDeviceLine != null) {
            setShown(mDeviceLine, !facts.deviceLine.isEmpty());
            setTextIfChanged(mDeviceLine, facts.deviceLine);
        }
        for (TaiWelcomeCard.Row row : sheet.aiRows()) {
            MaterialCheckBox box = mModelBoxes.get(row.id);
            // A model already on the phone shows ticked and cannot be changed here.
            boolean ticked = row.installed || sheet.isTicked(row.id);
            if (box != null && box.isChecked() != ticked) box.setChecked(ticked);
        }
        if (mStorageLine != null) setShown(mStorageLine, !sheet.fitsStorage(facts.freeBytes));
    }

    // ---- small things --------------------------------------------------------------------------

    @NonNull
    private TextView text(@NonNull Context context, float sizeSp, boolean strong, int color) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTypeface(Typeface.create(strong ? "sans-serif-medium" : "sans-serif",
            Typeface.NORMAL));
        view.setTextColor(color);
        view.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        return view;
    }

    /** A text link in the accent, a thumb's height tall. */
    @NonNull
    private TextView link(@NonNull Context context, @StringRes int labelRes,
                          @NonNull OnClickListener onClick) {
        TextView link = text(context, 12.5f, true, mAccent);
        link.setText(labelRes);
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setMinHeight(dp(LINK_TOUCH_DP));
        link.setPadding(dp(4), 0, dp(4), 0);
        singleLine(link);
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple,
            true) && ripple.resourceId != 0) link.setBackgroundResource(ripple.resourceId);
        link.setOnClickListener(onClick);
        return link;
    }

    private static void singleLine(@NonNull TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
    }

    private static void setShown(@NonNull View view, boolean shown) {
        int visibility = shown ? VISIBLE : GONE;
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    private static void setTextIfChanged(@NonNull TextView view, @NonNull CharSequence text) {
        if (!TextUtils.equals(view.getText(), text)) view.setText(text);
    }

    private int onAccent() {
        return MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorOnPrimary,
            androidx.core.content.ContextCompat.getColor(getContext(), R.color.termux_on_primary));
    }

    @NonNull
    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }

    /** For tests: the rows' switches as bound. */
    @NonNull
    Map<WelcomeSheet.Row, MaterialSwitch> switches() {
        return mSwitches;
    }
}
