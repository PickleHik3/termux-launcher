package com.termux.app.controlbar;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.statusbar.BatteryChipController;
import com.termux.app.statusbar.BatteryChipState;
import com.termux.app.statusbar.StatusBarStatSmoother;
import com.termux.app.statusbar.SystemStatsController;
import com.termux.app.statusbar.TopPaneFeed;
import com.termux.app.statusbar.WeatherController;

/**
 * The reading slot's brain (#428): which reading the bar shows, what each one reads now, and which
 * sources have to run for that. It builds on v1's sources and starts none of its own: CPU, GPU and
 * memory come from the activity's one {@link SystemStatsController} (through {@link #onStats}), the
 * battery from {@link BatteryChipController}'s sticky broadcast, the weather from the activity's
 * {@link WeatherController} (through {@link #onWeather}) and media from {@link TopPaneFeed}'s
 * observers. A source runs only while the slot is on, the launcher is in front, and the reading
 * (or the open card) is one that needs it; nothing here repaints on a timer, and the view is told
 * only when what it draws changes.
 */
public final class ReadingSlot implements TopPaneFeed.Observer {

    /** What the slot reads of and asks of the activity. */
    public interface Host {
        /** Whether the slot stands ({@code bar.reading}). */
        boolean shown();

        /** The reading remembered from the last tap ({@code bar.reading_kind}). */
        @NonNull ReadingKind remembered();

        /** Remembers the reading a tap chose. */
        void remember(@NonNull ReadingKind kind);

        /** Whether the weather is shown in Fahrenheit. */
        boolean fahrenheit();

        /**
         * The system sampler is wanted (or not) at the bar's cadence, with GPU and hardware read when
         * {@code hardware}; {@code card} when the open card wants the faster cadence.
         */
        void samplerWanted(boolean wanted, boolean hardware, boolean card);

        /** The weather is wanted now: it is fetched when its cache is stale, and answers {@link #onWeather}. */
        void weatherWanted();
    }

    /** Where the slot's answer goes: the view and the open card. */
    public interface Sink {
        /** The slot's visibility and what it shows changed; {@code value} is null while there is none. */
        void onSlot(boolean shown, @Nullable ReadingValue value);

        /** Every reading's value changed, for the open card. */
        void onAll(@NonNull ReadingSet set);
    }

    @NonNull private final Host mHost;
    @NonNull private final Sink mSink;
    @NonNull private final ReadingSet mSet = new ReadingSet();
    @NonNull private final BatteryChipController mBattery;
    @NonNull private final StatusBarStatSmoother mCpu = StatusBarStatSmoother.forCpuPercent();
    @NonNull private final StatusBarStatSmoother mGpu = StatusBarStatSmoother.forGpuPercent();
    @NonNull private final StatusBarStatSmoother mMemory = StatusBarStatSmoother.forMemoryPercent();

    @NonNull private ReadingKind mChosen = ReadingKind.DEFAULT;
    private boolean mOn;
    private boolean mFront = true;
    private boolean mCard;
    private boolean mObservingMedia;
    @Nullable private ReadingValue mDrawn;
    private boolean mDrawnShown;
    private boolean mDrawnOnce;
    private boolean mSamplerWanted;
    private boolean mHardwareWanted;
    private boolean mCardWanted;

    public ReadingSlot(@NonNull Context context, @NonNull Host host, @NonNull Sink sink) {
        mHost = host;
        mSink = sink;
        mBattery = new BatteryChipController(context, state -> {
            if (!mRunning.contains(ReadingKind.BATTERY)) return;
            mSet.put(ReadingKind.BATTERY, ReadingFormat.battery(state.text()));
            changed();
        });
    }

    // ---- what the activity tells it --------------------------------------------------------------

    /** The setting or the remembered reading changed, or the slot is new: everything is read again. */
    public void refresh() {
        mOn = mHost.shown();
        mChosen = mHost.remembered();
        mSet.forgetMissing();
        sync();
        changed();
    }

    /** The launcher came to the front or left it; nothing runs while it is not in front. */
    public void setInFront(boolean front) {
        if (mFront == front) return;
        mFront = front;
        if (front) mSet.forgetMissing();
        sync();
        changed();
    }

    /** The card of every reading opened or closed; it wants every source while it is open. */
    public void setCardOpen(boolean open) {
        if (mCard == open) return;
        mCard = open;
        if (open) mSet.forgetMissing();
        sync();
        changed();
    }

    /** One system sample. Whatever the slot does not show is not even looked at. */
    public void onStats(@NonNull SystemStatsController.Stats stats, long nowMs) {
        if (!mOn) return;
        // Only the readings whose source runs for the slot: a sample the status bar asked for must not
        // leave a number here that nothing will keep current.
        if (mRunning.contains(ReadingKind.CPU)) {
            mCpu.offer(stats.cpuPercent, nowMs);
            mSet.put(ReadingKind.CPU, ReadingFormat.percent(ReadingKind.CPU, mCpu.publishedPercent()));
        }
        if (mRunning.contains(ReadingKind.RAM)) {
            int memory = stats.memTotalKb > 0
                ? (int) Math.round(100.0 * stats.memUsedKb / stats.memTotalKb) : -1;
            mMemory.offer(memory, nowMs);
            mSet.put(ReadingKind.RAM, ReadingFormat.percent(ReadingKind.RAM, mMemory.publishedPercent()));
        }
        if (mRunning.contains(ReadingKind.GPU)) {
            mGpu.offer(stats.hardware != null ? stats.hardware.gpuBusyPercent
                : StatusBarStatSmoother.UNKNOWN, nowMs);
            mSet.put(ReadingKind.GPU, ReadingFormat.percent(ReadingKind.GPU, mGpu.publishedPercent()));
        }
        sync();
        changed();
    }

    /** The weather controller answered. */
    public void onWeather(@NonNull WeatherController.Weather weather) {
        if (!mRunning.contains(ReadingKind.WEATHER)) return;
        mSet.put(ReadingKind.WEATHER, ReadingFormat.weather(weather.valid, weather.currentC, mHost.fahrenheit()));
        changed();
    }

    @Override
    public void onTopPaneFeedChanged() {
        if (!mRunning.contains(ReadingKind.MEDIA)) return;
        mSet.put(ReadingKind.MEDIA, TopPaneFeed.getMedia() != null ? ReadingFormat.media() : null);
        changed();
    }

    /** A tap: the next reading is shown and remembered. */
    public void next() {
        ReadingKind next = mSet.next(mChosen);
        if (next == mChosen) return;
        mChosen = next;
        mHost.remember(next);
        sync();
        changed();
    }

    /** Whether the slot wants the system sampler now (for the activity's consumer check). */
    public boolean wantsSampler() {
        return mSamplerWanted;
    }

    @NonNull
    public ReadingSet readings() {
        return mSet;
    }

    // ---- sources ---------------------------------------------------------------------------------

    /**
     * Whether {@code kind}'s source has to run: the slot is on and in front, and the card is open or
     * {@code kind} is the reading shown (the remembered one, or the next when that is missing).
     * A reading's source is what answers whether it exists, so a tap onto a reading no value has
     * yet starts it.
     */
    private boolean wants(@NonNull ReadingKind kind) {
        if (!mOn || !mFront) return false;
        return mCard || mSet.effective(mChosen) == kind;
    }

    /** The kind the slot shows now. */
    @NonNull
    public ReadingKind shownKind() {
        return mSet.effective(mChosen);
    }

    /** The kind a tap would go to. */
    @NonNull
    public ReadingKind nextKind() {
        return mSet.next(mChosen);
    }

    /** Which kinds' sources are running for the slot: a stop forgets the value, which nothing updates now. */
    @NonNull private final java.util.Set<ReadingKind> mRunning = java.util.EnumSet.noneOf(ReadingKind.class);

    private void transition(@NonNull ReadingKind kind, boolean wanted) {
        if (wanted == mRunning.contains(kind)) return;
        if (wanted) mRunning.add(kind);
        else {
            mRunning.remove(kind);
            mSet.drop(kind);
        }
    }

    /** Starts the sources the shown reading and the card need, and stops the rest. */
    private void sync() {
        for (ReadingKind kind : ReadingKind.values()) transition(kind, wants(kind));
        boolean stats = wants(ReadingKind.CPU) || wants(ReadingKind.RAM) || wants(ReadingKind.GPU);
        boolean hardware = wants(ReadingKind.GPU);
        boolean battery = wants(ReadingKind.BATTERY);
        boolean weather = wants(ReadingKind.WEATHER);
        boolean media = wants(ReadingKind.MEDIA);
        if (stats != mSamplerWanted || hardware != mHardwareWanted || mCard != mCardWanted) {
            if (stats && !mSamplerWanted) {
                // A sampler turned on shows the real value at once, not a glide out of an old one.
                mCpu.reset();
                mGpu.reset();
                mMemory.reset();
            }
            mSamplerWanted = stats;
            mHardwareWanted = hardware;
            mCardWanted = mCard;
            mHost.samplerWanted(stats, hardware, mCard);
        }
        mBattery.setListening(battery);
        if (battery && mBattery.latest() != BatteryChipState.UNKNOWN) {
            mSet.put(ReadingKind.BATTERY, ReadingFormat.battery(mBattery.latest().text()));
        }
        if (media != mObservingMedia) {
            mObservingMedia = media;
            if (media) {
                TopPaneFeed.addObserver(this);
                mSet.put(ReadingKind.MEDIA, TopPaneFeed.getMedia() != null ? ReadingFormat.media() : null);
            } else {
                TopPaneFeed.removeObserver(this);
            }
        }
        if (weather) mHost.weatherWanted();
    }

    /** Hands the view and the card what changed since they were last told. */
    private void changed() {
        ReadingValue value = mOn ? mSet.shown(mChosen) : null;
        if (!mDrawnOnce || mOn != mDrawnShown || !java.util.Objects.equals(value, mDrawn)) {
            mDrawnOnce = true;
            mDrawnShown = mOn;
            mDrawn = value;
            mSink.onSlot(mOn, value);
        }
        if (mCard) mSink.onAll(mSet);
    }

    /** The window is going away: no source stays running for a bar that is gone. */
    public void release() {
        mOn = false;
        sync();
    }
}
