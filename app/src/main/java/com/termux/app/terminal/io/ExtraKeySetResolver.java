package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

import java.util.Objects;

/**
 * Which key set the bar shows: the target the keys go to, then that target's own set, its shipped
 * set or Global's. Pure; the stored lines come in through {@link Lines}.
 */
public final class ExtraKeySetResolver {

    private ExtraKeySetResolver() {}

    /** Raw property values as stored; {@code null} when the line is absent. */
    public interface Lines {
        @Nullable String get(@NonNull String key);
    }

    public enum Source { OWN, SHIPPED, GLOBAL }

    /** The answer: immutable. */
    public static final class Resolved {
        @NonNull public final ExtraKeysTarget target;
        @NonNull public final Source source;
        @NonNull public final String page1;
        @Nullable public final String page2;
        public final float scale;

        Resolved(@NonNull ExtraKeysTarget target, @NonNull Source source, @NonNull String page1,
                 @Nullable String page2, float scale) {
            this.target = target;
            this.source = source;
            this.page1 = page1;
            this.page2 = page2;
            this.scale = scale;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Resolved)) return false;
            Resolved r = (Resolved) o;
            return target == r.target && source == r.source && page1.equals(r.page1)
                && Objects.equals(page2, r.page2) && Float.compare(scale, r.scale) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(target, source, page1, page2, scale);
        }
    }

    /**
     * The target the keys go to: where the typing claim sends them, so it follows the settled place.
     * On Home it is the focused Home terminal's (Terminal), or Display when that float is a display
     * window, else Home.
     */
    @NonNull
    public static ExtraKeysTarget targetFor(@Nullable PaneWallPage settledPlace,
                                            boolean homeFloatFocused, boolean homeFloatIsDisplay) {
        if (settledPlace == null) return ExtraKeysTarget.TERMINAL;
        switch (settledPlace) {
            case DISPLAY: return ExtraKeysTarget.DISPLAY;
            case ASSISTANT: return ExtraKeysTarget.ASSISTANT;
            case WIDGETS:
                if (homeFloatFocused) {
                    return homeFloatIsDisplay ? ExtraKeysTarget.DISPLAY : ExtraKeysTarget.TERMINAL;
                }
                return ExtraKeysTarget.HOME;
            case TERMINAL:
            default:
                return ExtraKeysTarget.TERMINAL;
        }
    }

    @NonNull
    public static Resolved resolve(@NonNull ExtraKeysTarget target, @NonNull Lines lines) {
        float scale = TermuxSharedProperties.getTerminalToolbarHeightScaleFactorInternalPropertyValueFromValue(
            lines.get(TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR));
        if (target != ExtraKeysTarget.GLOBAL) {
            Float own = TermuxSharedProperties.getTargetExtraKeysHeightFromValue(lines.get(target.heightKey()));
            if (own != null) scale = own;
            String page1 = TermuxSharedProperties.getTargetExtraKeysPageFromValue(lines.get(target.page1Key()));
            if (page1 != null) {
                return new Resolved(target, Source.OWN, page1,
                    TermuxSharedProperties.getTargetExtraKeysPageFromValue(lines.get(target.page2Key())), scale);
            }
            String shipped = builtInPage1(target, lines);
            if (shipped != null) {
                return new Resolved(target, Source.SHIPPED, shipped, null, scale);
            }
        }
        return new Resolved(target, Source.GLOBAL, globalPage1(lines),
            TermuxSharedProperties.getExtraKeys2InternalPropertyValueFromValue(
                lines.get(TermuxPropertyConstants.KEY_EXTRA_KEYS2)),
            scale);
    }

    /** Whether the setup card's Add put the page switcher key on the built-in sets. */
    public static boolean pageSwitcherAdded(@NonNull Lines lines) {
        String value = lines.get(TermuxPropertyConstants.KEY_EXTRA_KEYS_PAGE_SWITCHER_ADDED);
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    /** Whether Global's page 1 is the user's own line rather than the built-in row. */
    public static boolean globalStored(@NonNull Lines lines) {
        String value = lines.get(TermuxPropertyConstants.KEY_EXTRA_KEYS);
        return value != null && !value.trim().isEmpty();
    }

    /**
     * The target's built-in page 1 as the bar shows it: the shipped set, led by the page switcher
     * key once Add put it there (never twice); null for a target with no built-in set.
     */
    @Nullable
    public static String builtInPage1(@NonNull ExtraKeysTarget target, @NonNull Lines lines) {
        String shipped = target.shippedPage1();
        if (shipped == null || target == ExtraKeysTarget.GLOBAL) return shipped;
        return withAddedKey(shipped, lines);
    }

    /** Global's page 1: the user's line, else the built-in row, led by the key once Add put it there. */
    @NonNull
    static String globalPage1(@NonNull Lines lines) {
        String page1 = TermuxSharedProperties.getExtraKeysInternalPropertyValueFromValue(
            lines.get(TermuxPropertyConstants.KEY_EXTRA_KEYS));
        return globalStored(lines) ? page1 : withAddedKey(page1, lines);
    }

    @NonNull
    private static String withAddedKey(@NonNull String builtIn, @NonNull Lines lines) {
        if (!pageSwitcherAdded(lines)) return builtIn;
        String led = PageSwitcherKeyAdd.withKey(builtIn);
        return led == null ? builtIn : led;
    }
}
