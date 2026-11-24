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
     * The limits for a screen of {@code density} (px per dp): the smallest is 4dp so a mistaken
     * zoom cannot leave invisible text, the default is 12dp made even.
     */
    @NonNull
    public static Range range(float density) {
        int defaultSize = Math.round(12 * density);
        if (defaultSize % 2 == 1) defaultSize--;
        return new Range((int) (4f * density), MAX, defaultSize);
    }

    /**
     * The reset group of each id: the terminal and scratchpad sizes go with the appearance reset,
     * Compact's (a separate, never-reset key) stays.
     */
    @NonNull
    private static SettingDeclaration.ResetGroup resetGroup(@NonNull String id) {
        return COMPACT_FONT_SIZE.equals(id) ? SettingDeclaration.ResetGroup.NONE
            : SettingDeclaration.ResetGroup.APPEARANCE;
    }

    /**
     * Whether {@code key} (any display's) is one of the {@code group} reset's font size keys: the
     * terminal's and the scratchpad's start with {@code fontsize}, Compact's does not.
     */
    public static boolean resets(@NonNull SettingDeclaration.ResetGroup group, @NonNull String key) {
        for (String id : new String[] {FONT_SIZE, COMPACT_FONT_SIZE, SCRATCHPAD_FONT_SIZE}) {
            if (resetGroup(id) == group && key.startsWith(baseKey(id))) return true;
        }
        return false;
    }

    @NonNull
    private static String baseKey(@NonNull String id) {
        switch (id) {
            case FONT_SIZE: return TERMUX_APP.KEY_FONTSIZE;
            case COMPACT_FONT_SIZE: return TERMUX_APP.KEY_COMPACT_FONTSIZE;
            default: return TERMUX_APP.KEY_FONTSIZE + "_scratchpad";
        }
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
        return baseKey(id) + mDisplay;
    }

    private static boolean owns(@NonNull String id) {
        return FONT_SIZE.equals(id) || COMPACT_FONT_SIZE.equals(id)
            || SCRATCHPAD_FONT_SIZE.equals(id);
    }

    @NonNull
    private SettingDeclaration declare(@NonNull String id) {
        return SettingDeclaration.integer(id, SettingDeclaration.Store.APP_PREFERENCES, key(id),
            mRange.min, mRange.max, STEP, mRange.defaultSize, SettingRefresh.FONT_SIZE)
            .resetBy(resetGroup(id));
    }

    @Nullable
    @Override
    public SettingDeclaration declaration(@NonNull String id) {
        return owns(id) ? declare(id) : null;
    }

    @NonNull
    @Override
    public List<SettingDeclaration> declarations() {
        List<SettingDeclaration> all = new ArrayList<>();
        all.add(declare(FONT_SIZE));
        all.add(declare(COMPACT_FONT_SIZE));
        all.add(declare(SCRATCHPAD_FONT_SIZE));
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
