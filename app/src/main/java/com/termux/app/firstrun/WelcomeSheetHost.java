package com.termux.app.firstrun;

import android.app.Activity;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiTierPolicy;
import com.termux.app.tour.FirstBootTour;

import java.util.List;
import java.util.Set;

/**
 * Puts the setup sheet over the home screen, applies what it is told, and hands the run its two
 * ways on. The run owns when the sheet is up ({@link FirstBootTour.SetupHost}); the launcher owns
 * the things only it can do — permissions, the weather, the display, the colours — through
 * {@link Env}.
 *
 * <p>What is applied when: the weather and the display as their switches move, because the user
 * can see them change behind the sheet; the wallpaper's colours as the sheet is left, because
 * they restyle the whole launcher; and the models as the sheet is left, through the same queue
 * and Wi-Fi rule as the "Models for this phone" card, which the sheet stands in for and so marks
 * as shown.
 */
public final class WelcomeSheetHost implements FirstBootTour.SetupHost {

    /** What only the launcher can do for the sheet. */
    public interface Env {
        /** The view the sheet is added over. */
        @NonNull ViewGroup host();

        boolean wallpaperColoursOn();

        /** Takes the launcher's colours from the wallpaper, or not; restyles when that changed. */
        void applyWallpaperColours(boolean on);

        /** Asks the system to let the launcher read the wallpaper, when it still may ask. */
        void askForWallpaperRead();

        boolean weatherEnabled();

        void setWeatherEnabled(boolean on);

        /** The place the weather follows, or empty while it follows the device. */
        @NonNull String weatherPlace();

        void setWeatherPlace(@NonNull String label, double latitude, double longitude);

        boolean locationGranted();

        /** The weather follows the device: the picked place is dropped and the location asked for. */
        void useMyLocation();

        boolean displayOffered();

        boolean displayOn();

        void setDisplayOn(boolean on);

        /** The city search took focus and wants the system keyboard, or gave it up. */
        void onPlaceSearchFocusChanged(@NonNull EditText field, boolean focused);
    }

    @NonNull private final Activity mActivity;
    @NonNull private final Env mEnv;
    @Nullable private WelcomeSheetView mView;
    @Nullable private WelcomeSheet mSheet;
    @Nullable private FirstBootTour.SetupCallbacks mCallbacks;

    public WelcomeSheetHost(@NonNull Activity activity, @NonNull Env env) {
        mActivity = activity;
        mEnv = env;
    }

    /** Whether the sheet is up. */
    public boolean isShowing() {
        return mView != null;
    }

    @Override
    public void showSetup(@NonNull FirstBootTour.SetupCallbacks callbacks) {
        mCallbacks = callbacks;
        if (mView != null || mActivity.isFinishing() || mActivity.isDestroyed()) return;
        // The sheet asks the models card's question, so the card is not raised after the run.
        TaiWelcomeCardHost.markShown(mActivity);
        WelcomeSheet.Start start = new WelcomeSheet.Start();
        start.wallpaperColours = mEnv.wallpaperColoursOn();
        start.weatherEnabled = mEnv.weatherEnabled();
        start.weatherPlace = mEnv.weatherPlace();
        start.locationGranted = mEnv.locationGranted();
        start.displayOffered = mEnv.displayOffered();
        start.displayOn = mEnv.displayOn();
        start.aiRows = aiRows();
        mSheet = WelcomeSheet.from(start);
        WelcomeSheetView view = new WelcomeSheetView(mActivity);
        view.setCallbacks(new SheetCallbacks());
        mEnv.host().addView(view, WelcomeSheetView.buildLayoutParams());
        mView = view;
        android.view.WindowInsets current = mEnv.host().getRootWindowInsets();
        if (current != null)
            applyInsets(WindowInsetsCompat.toWindowInsetsCompat(current, mEnv.host()));
        refresh();
        view.animateIn();
    }

    @Override
    public void dismissSetup() {
        WelcomeSheetView view = mView;
        mView = null;
        mSheet = null;
        if (view != null && view.getParent() instanceof ViewGroup)
            ((ViewGroup) view.getParent()).removeView(view);
    }

    /** Shows where everything stands now: after a permission answer, and on every resume. */
    public void refresh() {
        WelcomeSheetView view = mView;
        WelcomeSheet sheet = mSheet;
        if (view == null || sheet == null) return;
        WelcomeSheetView.Facts facts = new WelcomeSheetView.Facts();
        facts.weatherPlace = mEnv.weatherPlace();
        facts.locationGranted = mEnv.locationGranted();
        facts.wifiOnly = TaiWelcomeDownloads.wifiOnly(mActivity);
        facts.freeBytes = mActivity.getFilesDir().getUsableSpace();
        facts.deviceLine = deviceLine();
        view.bind(sheet, facts);
    }

    /**
     * The sheet keeps clear of the system bars and of the system keyboard: the city search brings
     * the keyboard up, and the sheet has to sit above it rather than under it.
     */
    public void applyInsets(@NonNull WindowInsetsCompat insets) {
        WelcomeSheetView view = mView;
        if (view == null) return;
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        int ime = insets.isVisible(WindowInsetsCompat.Type.ime())
            ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
        view.setSystemBarInsets(bars.top, Math.max(bars.bottom, ime));
    }

    @NonNull
    private List<TaiWelcomeCard.Row> aiRows() {
        try {
            TaiTierPolicy.Env env = TaiTierPolicy.Env.forDevice(mActivity);
            Set<String> installed = new TaiModelStore(mActivity).getInstalledUserModels().keySet();
            return TaiWelcomeCard.rows(env, installed, TaiWelcomeDownloads.catalogSizes());
        } catch (RuntimeException unreadable) {
            // A phone whose model store cannot be read is offered no models, not a broken row.
            return java.util.Collections.emptyList();
        }
    }

    @NonNull
    private String deviceLine() {
        TaiTierPolicy.Env env = TaiTierPolicy.Env.forDevice(mActivity);
        String chip = TaiWelcomeCard.chipName(
            Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER : null,
            Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : null, Build.HARDWARE);
        TaiWelcomeCard.Header header = TaiWelcomeCard.header(env, chip, Build.VERSION.RELEASE);
        return header.chip.isEmpty()
            ? mActivity.getString(R.string.tai_welcome_header_no_chip, header.tierNumber,
                header.ramGb, header.androidVersion)
            : mActivity.getString(R.string.tai_welcome_header, header.tierNumber, header.ramGb,
                header.chip, header.androidVersion);
    }

    /** Leaving the sheet, either way: what is applied on the way out, then the run. */
    private void leave(boolean showMeAround) {
        WelcomeSheet sheet = mSheet;
        FirstBootTour.SetupCallbacks callbacks = mCallbacks;
        if (sheet == null) return;
        boolean wallpaper = sheet.isOn(WelcomeSheet.Row.WALLPAPER);
        // The weather is left as the sheet says it is: a switch that read off because the weather
        // had nowhere to be for really is off once the sheet is answered.
        mEnv.setWeatherEnabled(sheet.isOn(WelcomeSheet.Row.WEATHER));
        List<String> models = sheet.downloadsOnLeave(TaiWelcomeDownloads.catalogSizes(),
            mActivity.getFilesDir().getUsableSpace());
        // Nothing starts here: the closing card offers the download, and asks first on mobile data.
        TaiWelcomeDownloads.rememberQueued(mActivity, models, models.isEmpty()
            ? TaiWelcomeDownloads.Start.NOTHING : TaiWelcomeDownloads.Start.CHOSEN);
        dismissSetup();
        // The run's first card is stored before anything below can restyle the launcher.
        if (callbacks != null) {
            if (showMeAround) callbacks.onShowMeAround();
            else callbacks.onSkipTheTour();
        }
        mEnv.applyWallpaperColours(wallpaper);
        if (wallpaper) mEnv.askForWallpaperRead();
    }

    /** The view's controls, applied to the model and to the launcher. */
    private final class SheetCallbacks implements WelcomeSheetView.Callbacks {
        @Override
        public void onRowSwitched(@NonNull WelcomeSheet.Row row, boolean on) {
            WelcomeSheet sheet = mSheet;
            if (sheet == null) return;
            sheet.setOn(row, on);
            if (row == WelcomeSheet.Row.WEATHER) mEnv.setWeatherEnabled(on);
            if (row == WelcomeSheet.Row.DISPLAY) mEnv.setDisplayOn(on);
            refresh();
        }

        @Override
        public void onChooseModelsTapped() {
            if (mSheet == null) return;
            mSheet.toggleModelsExpanded();
            refresh();
        }

        @Override
        public void onModelTapped(@NonNull String rowId) {
            if (mSheet == null) return;
            mSheet.toggleModel(rowId);
            refresh();
        }

        @Override
        public void onUseMyLocationTapped() {
            mEnv.useMyLocation();
            refresh();
        }

        @Override
        public void onPlacePicked(@NonNull String label, double latitude, double longitude) {
            mEnv.setWeatherPlace(label, latitude, longitude);
            refresh();
        }

        @Override
        public void onPlaceSearchFocusChanged(@NonNull EditText field, boolean focused) {
            mEnv.onPlaceSearchFocusChanged(field, focused);
        }

        @Override
        public void onShowMeAround() {
            leave(true);
        }

        @Override
        public void onSkipTheTour() {
            leave(false);
        }
    }
}
