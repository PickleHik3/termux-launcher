package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.List;

/**
 * The terminal, Compact and scratchpad font sizes as registry ids ({@code terminal.font_size},
 * {@code terminal.compact_font_size}, {@code terminal.scratchpad_font_size}). Their keys carry
 * the display's id and their range comes from the screen's density, so no plain declaration can
 * hold them. This class owns the range: {@link #range} is the one place the clamp is written, and
 * the store's getters and the backup restore's rescale read it from here. An adapter serves the
 * display it was built for; {@code launcherctl config} builds it for the default display.
 */
public final class FontSizeSettingsAdapter implements SettingAdapter {

    public static final String FONT_SIZE = "terminal.font_size";
    public static final String COMPACT_FONT_SIZE = "terminal.compact_font_size";
    public static final String SCRATCHPAD_FONT_SIZE = "terminal.scratchpad_font_size";

    /** The zoom step, which a default is made divisible by. */
    private static final int STEP = 2;
    /** The largest font size in px, on any density. */
    private static final int MAX = 256;
    /** The default size in dp, made even in px. */
    private static final int DEFAULT_DP = 12;
    /** The smallest size is the default divided by this, so a mistaken zoom leaves visible text. */
    private static final int MIN_DIVISOR = 3;
    private static final int MIN_DP = DEFAULT_DP / MIN_DIVISOR;

    /** A screen's font size limits in px, and its default. */
    public static final class Range {
        public final int min;
        public final int max;
        public final int defaultSize;

        public Range(int min, int max, int defaultSize) {
            this.min = min;
            this.max = max;
            this.defaultSize = defaultSize;
        }

        /** {@code px} held inside the range. */
        public int clamp(int px) {
            return Math.max(min, Math.min(max, px));
        }
    }

    /**
     * The limits for a screen of {@code density} (px per dp): the smallest is {@code MIN_DP} so a
     * mistaken zoom cannot leave invisible text, the default is {@code DEFAULT_DP} made even.
     */
    @NonNull
    public static Range range(float density) {
        int defaultSize = Math.round(DEFAULT_DP * density);
        if (defaultSize % 2 == 1) defaultSize--;
        return new Range((int) (MIN_DP * density), MAX, defaultSize);
    }

    /** The three sizes, each id listed once. */
    private enum Row {
        TERMINAL(FONT_SIZE, TERMUX_APP.KEY_FONTSIZE, SettingDeclaration.ResetGroup.APPEARANCE,
            "Terminal font size", "How large the terminal's text is, in pixels."),
        // Compact's key is a separate one that no reset touches.
        COMPACT(COMPACT_FONT_SIZE, TERMUX_APP.KEY_COMPACT_FONTSIZE, SettingDeclaration.ResetGroup.NONE,
            "Compact font size",
            "How large text is in Compact windows; until you set one it follows the terminal's."),
        SCRATCHPAD(SCRATCHPAD_FONT_SIZE, TERMUX_APP.KEY_FONTSIZE + "_scratchpad",
            SettingDeclaration.ResetGroup.APPEARANCE, "Scratchpad font size",
            "How large text is in the scratchpad; until you set one it follows the terminal's.");

        final String id;
        final String baseKey;
        final SettingDeclaration.ResetGroup resetGroup;
        final String title;
        final String summary;

        Row(String id, String baseKey, SettingDeclaration.ResetGroup resetGroup, String title,
            String summary) {
            this.id = id;
            this.baseKey = baseKey;
            this.resetGroup = resetGroup;
            this.title = title;
            this.summary = summary;
        }

        @Nullable
        static Row of(@NonNull String id) {
            for (Row row : values()) if (row.id.equals(id)) return row;
            return null;
        }
    }

    /**
     * Whether {@code key} (any display's) is one of the {@code group} reset's font size keys: the
     * terminal's and the scratchpad's start with {@code fontsize}, Compact's does not.
     */
    public static boolean resets(@NonNull SettingDeclaration.ResetGroup group, @NonNull String key) {
        for (Row row : Row.values()) {
            if (row.resetGroup == group && key.startsWith(row.baseKey)) return true;
        }
        return false;
    }

    @NonNull private final SettingStore mStore;
    @NonNull private final Range mRange;
    @NonNull private final String mDisplay;

    /**
     * @param display the display's key suffix: "" for the default display, else its id
     */
    public FontSizeSettingsAdapter(@NonNull SettingStore store, float density,
                                   @NonNull String display) {
        mStore = store;
        mRange = range(density);
        mDisplay = display;
    }

    @NonNull
    private String key(@NonNull String id) {
        Row row = Row.of(id);
        return (row == null ? id : row.baseKey) + mDisplay;
    }

    private static boolean owns(@NonNull String id) {
        return Row.of(id) != null;
    }

    @NonNull
    private SettingDeclaration declare(@NonNull Row row) {
        String id = row.id;
        return SettingDeclaration.integer(id, SettingDeclaration.Store.APP_PREFERENCES, key(id),
            mRange.min, mRange.max, STEP, mRange.defaultSize, SettingRefresh.FONT_SIZE)
            .resetBy(row.resetGroup).described(row.title, row.summary)
            .shownAs(RANGE_NOTE, DEFAULT_NOTE);
    }

    /** The range and default in words: the numbers depend on the screen's density. */
    public static final String RANGE_NOTE =
        "From " + fraction(MIN_DIVISOR) + " of the default size up to " + MAX + " px";
    public static final String DEFAULT_NOTE =
        "About " + DEFAULT_DP + " dp of text, whatever the screen";

    @NonNull
    private static String fraction(int divisor) {
        switch (divisor) {
            case 2: return "a half";
            case 3: return "a third";
            case 4: return "a quarter";
            default: return "1/" + divisor;
        }
    }

    @Nullable
    @Override
    public SettingDeclaration declaration(@NonNull String id) {
        Row row = Row.of(id);
        return row == null ? null : declare(row);
    }

    @NonNull
    @Override
    public List<SettingDeclaration> declarations() {
        List<SettingDeclaration> all = new ArrayList<>();
        for (Row row : Row.values()) all.add(declare(row));
        return all;
    }

    /** The stored px, or -1 for nothing usable stored. */
    private int stored(@NonNull String key) {
        String text = mStore.getString(key);
        if (text == null) return -1;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int terminalSize() {
        int px = stored(key(FONT_SIZE));
        return px < 0 ? mRange.defaultSize : mRange.clamp(px);
    }

    @NonNull
    @Override
    public String get(@NonNull SettingDeclaration declaration) {
        if (!owns(declaration.id)) return "";
        if (FONT_SIZE.equals(declaration.id)) return String.valueOf(terminalSize());
        // A Compact or scratchpad size never set (Compact stores 0 for that) follows the terminal's.
        int px = stored(key(declaration.id));
        return String.valueOf(px <= 0 ? terminalSize() : mRange.clamp(px));
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration,
                                          @NonNull String value) {
        if (!owns(declaration.id)) return SettingsRegistry.unknown(declaration.id);
        return parse(value) == null ? outside(declaration) : null;
    }

    @NonNull
    @Override
    public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration,
                                        @NonNull String value) {
        if (!owns(declaration.id)) return SettingsRegistry.unknown(declaration.id);
        Integer px = parse(value);
        if (px == null) return outside(declaration);
        String old = get(declaration);
        mStore.putString(key(declaration.id), String.valueOf(px));
        return new SettingsRegistry.Change(declaration, old, String.valueOf(px));
    }

    @Nullable
    private Integer parse(@NonNull String value) {
        try {
            int px = Integer.parseInt(value.trim());
            return px < mRange.min || px > mRange.max ? null : px;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private static SettingsRegistry.Refusal outside(@NonNull SettingDeclaration declaration) {
        return new SettingsRegistry.Refusal(declaration.id + " is " + declaration.allowed(),
            declaration.allowed());
    }
}
