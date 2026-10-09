package com.termux.app.firstrun;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the first-run setup sheet shows and decides: four switches, the city search the weather
 * switch reveals, and the models the offline voice and AI switch downloads.
 *
 * <p>The sheet is where the run is offered, so every switch starts where the launcher stands —
 * a first launch reads the shipped defaults, a Replay reads what the user has since chosen — and
 * nothing is asked for until the user turns the thing on.
 *
 * <p>The AI switch and its model ticks are one choice seen two ways: ticking a model turns the
 * switch on, unticking the last one turns it off, and turning the switch on ticks this phone's
 * preselection again. The size in the row's sentence is always the sum of what is ticked.
 *
 * <p>Pure, so every rule here is a unit test. The view draws it; the host applies it.
 */
public final class WelcomeSheet {

    /** The sheet's rows, in the order it reads them. */
    public enum Row { WALLPAPER, WEATHER, DISPLAY, AI }

    /** What the AI row's sentence says about its size. */
    public enum AiSize {
        /** The size of the ticked models. */
        SELECTED,
        /** Every model this phone would take is already on it. */
        INSTALLED
    }

    /** Where the launcher stands when the sheet comes up. */
    public static final class Start {
        /** Whether the launcher takes its colours from the wallpaper. */
        public boolean wallpaperColours;
        /** Whether the weather is switched on in the status bar. */
        public boolean weatherEnabled;
        /** The place the weather follows, or empty while it follows the device. */
        @NonNull public String weatherPlace = "";
        /** Whether the weather may read the device's location. */
        public boolean locationGranted;
        /** Whether this build carries the Linux display at all, and whether it is on. */
        public boolean displayOffered;
        public boolean displayOn;
        /** The models this phone is offered, from {@link TaiWelcomeCard#rows}. */
        @NonNull public List<TaiWelcomeCard.Row> aiRows = Collections.emptyList();
    }

    private boolean mWallpaper;
    private boolean mWeather;
    private boolean mDisplay;
    private boolean mAi;
    private boolean mModelsExpanded;
    private final boolean mDisplayOffered;
    @NonNull private final List<TaiWelcomeCard.Row> mAiRows;
    @NonNull private final LinkedHashSet<String> mTicked = new LinkedHashSet<>();

    private WelcomeSheet(@NonNull Start start) {
        mWallpaper = start.wallpaperColours;
        // The weather counts as on only when it has somewhere to be for: switched on with neither
        // a picked place nor the location, it shows nothing, and the sheet should not say otherwise.
        mWeather = start.weatherEnabled
            && (!start.weatherPlace.trim().isEmpty() || start.locationGranted);
        mDisplayOffered = start.displayOffered;
        mDisplay = start.displayOffered && start.displayOn;
        mAiRows = Collections.unmodifiableList(new ArrayList<>(start.aiRows));
        mTicked.addAll(TaiWelcomeCard.initialTicks(mAiRows));
        mAi = !mTicked.isEmpty();
    }

    /** The sheet as the launcher stands. */
    @NonNull
    public static WelcomeSheet from(@NonNull Start start) {
        return new WelcomeSheet(start);
    }

    /** The rows this sheet carries: no display row without a display, no AI row without models. */
    @NonNull
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>(4);
        rows.add(Row.WALLPAPER);
        rows.add(Row.WEATHER);
        if (mDisplayOffered) rows.add(Row.DISPLAY);
        if (!mAiRows.isEmpty()) rows.add(Row.AI);
        return Collections.unmodifiableList(rows);
    }

    public boolean isOn(@NonNull Row row) {
        switch (row) {
            case WALLPAPER: return mWallpaper;
            case WEATHER: return mWeather;
            case DISPLAY: return mDisplay;
            default: return mAi;
        }
    }

    /**
     * Moves a switch. The AI switch turned on ticks this phone's preselection when nothing is
     * ticked, and opens the list when there is no preselection to tick; turned off, it keeps the
     * ticks so turning it back on restores them.
     */
    public void setOn(@NonNull Row row, boolean on) {
        switch (row) {
            case WALLPAPER:
                mWallpaper = on;
                break;
            case WEATHER:
                mWeather = on;
                break;
            case DISPLAY:
                mDisplay = mDisplayOffered && on;
                break;
            default:
                if (mAiRows.isEmpty()) return;
                mAi = on;
                if (on && mTicked.isEmpty()) {
                    mTicked.addAll(TaiWelcomeCard.initialTicks(mAiRows));
                    if (mTicked.isEmpty()) mModelsExpanded = true;
                }
                break;
        }
    }

    /** Whether the weather row shows its city search and Use my location. */
    public boolean showsWeatherSearch() {
        return mWeather;
    }

    /** Whether the AI row offers "Choose models". */
    public boolean showsChooseModels() {
        return mAi && !mAiRows.isEmpty();
    }

    /** Whether the models list is open under the AI row. */
    public boolean modelsExpanded() {
        return showsChooseModels() && mModelsExpanded;
    }

    /** "Choose models" was tapped: the list opens, or closes again. */
    public void toggleModelsExpanded() {
        if (!showsChooseModels()) return;
        mModelsExpanded = !mModelsExpanded;
    }

    @NonNull
    public List<TaiWelcomeCard.Row> aiRows() {
        return mAiRows;
    }

    public boolean isTicked(@NonNull String rowId) {
        return mTicked.contains(rowId);
    }

    /**
     * A model's box was tapped. An installed model cannot be ticked; ticking one turns the switch
     * on, and unticking the last one turns it off.
     */
    public void toggleModel(@NonNull String rowId) {
        TaiWelcomeCard.Row row = aiRow(rowId);
        if (row == null || !row.tickable()) return;
        if (!mTicked.remove(rowId)) mTicked.add(rowId);
        mAi = !mTicked.isEmpty();
        if (!mAi) mModelsExpanded = false;
    }

    @Nullable
    private TaiWelcomeCard.Row aiRow(@NonNull String rowId) {
        for (TaiWelcomeCard.Row row : mAiRows) if (row.id.equals(rowId)) return row;
        return null;
    }

    /** What the ticked models download, in bytes: the size the AI row's sentence names. */
    public long selectedBytes() {
        long bytes = 0L;
        for (TaiWelcomeCard.Row row : mAiRows)
            if (row.tickable() && mTicked.contains(row.id)) bytes += row.downloadBytes;
        return bytes;
    }

    /** Which sentence the AI row reads. */
    @NonNull
    public AiSize aiSize() {
        for (TaiWelcomeCard.Row row : mAiRows) if (row.tickable()) return AiSize.SELECTED;
        return AiSize.INSTALLED;
    }

    /** The ticked rows, for the queue. */
    @NonNull
    public Set<String> tickedRowIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(mTicked));
    }

    /**
     * What leaving the sheet queues: nothing with the switch off, nothing that would not fit the
     * phone's free storage with the card's margin, and otherwise the ticked models smallest first.
     */
    @NonNull
    public List<String> downloadsOnLeave(@NonNull TaiWelcomeCard.Sizes sizes, long freeBytes) {
        if (!mAi || mTicked.isEmpty()) return Collections.emptyList();
        if (!TaiWelcomeCard.footer(mAiRows, mTicked, freeBytes).downloadEnabled)
            return Collections.emptyList();
        return TaiWelcomeCard.downloadOrder(mAiRows, mTicked, sizes);
    }

    /** Whether the ticked models fit the phone's free storage, with the card's margin. */
    public boolean fitsStorage(long freeBytes) {
        return !mAi || mTicked.isEmpty()
            || TaiWelcomeCard.footer(mAiRows, mTicked, freeBytes).downloadEnabled;
    }
}
