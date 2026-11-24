package com.termux.app.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The look grid as registry ids: {@code look.<property>} is Global's value, {@code
 * look.<element>.<property>} an element's own ({@code look.dock.opacity}), and {@code
 * look.background.<opacity|blur|dim>} the Terminal's Background. Nothing is declared per property:
 * every read and save is a {@code launcherctl look} request answered by {@link LookCommand}, so
 * the clamps, the element rules and the refusals are that verb's, word for word. A save runs
 * {@code onChanged} (the chrome re-dress) itself, so the applier adds nothing.
 *
 * <p>Values are text in the verb's own words, trimmed to what a setter takes back: a blur is its
 * stop ({@code 3}, not {@code 3 (8dp)}), a Background blur its dp, a colour its name.</p>
 */
public final class LookSettingsAdapter implements SettingAdapter {
    /** The summary of a property Shine replaced; `config list` keeps it, the settings reference leaves it out. */
    static final String RETIRED = "No longer used; Shine replaced it.";

    private static final String PREFIX = "look.";
    private static final String BACKGROUND = "background";
    /** What a surface may own; the others are Global's only and so listed there. */
    private static final List<String> LISTED_ELEMENTS = Arrays.asList("status", "dock", "keyboard");

    @NonNull private final LookGrid mGrid;
    @NonNull private final LookCommand.TerminalBackground mBackground;
    @NonNull private final Runnable mOnChanged;

    LookSettingsAdapter(@NonNull LookGrid grid, @NonNull LookCommand.TerminalBackground background,
                        @NonNull Runnable onChanged) {
        mGrid = grid;
        mBackground = background;
        mOnChanged = onChanged;
    }

    /** What an id names: a property and the element it is read at, or null element for Global. */
    private static final class Target {
        @NonNull final String property;
        @Nullable final String element;

        Target(@NonNull String property, @Nullable String element) {
            this.property = property;
            this.element = element;
        }
    }

    @Nullable
    private static Target parse(@NonNull String id) {
        if (!id.startsWith(PREFIX)) return null;
        String[] parts = id.substring(PREFIX.length()).split("\\.", -1);
        if (parts.length > 2) return null;
        String property = parts[parts.length - 1].trim().toLowerCase(Locale.ROOT);
        String element = parts.length == 2 ? parts[0].trim().toLowerCase(Locale.ROOT) : null;
        if (property.isEmpty() || (element != null && element.isEmpty())) return null;
        if (BACKGROUND.equals(element)) {
            return LookCommand.Setting.forKey(LookCommand.backgroundKey(property)) == null
                ? null : new Target(property, element);
        }
        if (property.equals("dim")) return new Target(property, element);
        LookGrid.Property known = LookGrid.Property.forKey(LookCommand.gridKey(property));
        return known == null ? null : new Target(property, element);
    }

    @Nullable
    @Override
    public SettingDeclaration declaration(@NonNull String id) {
        Target target = parse(id);
        return target == null ? null : declare(id, target);
    }

    @NonNull
    @Override
    public List<SettingDeclaration> declarations() {
        List<SettingDeclaration> all = new ArrayList<>();
        for (LookGrid.Property property : LookGrid.Property.values()) {
            all.add(declare(PREFIX + LookCommand.name(property),
                new Target(LookCommand.name(property), null)));
        }
        for (String element : LISTED_ELEMENTS) {
            for (LookGrid.Property property : new LookGrid.Property[]{
                    LookGrid.Property.OPACITY, LookGrid.Property.FROST}) {
                all.add(declare(PREFIX + element + "." + LookCommand.name(property),
                    new Target(LookCommand.name(property), element)));
            }
        }
        for (LookCommand.Setting setting : LookCommand.Setting.values()) {
            all.add(declare(PREFIX + BACKGROUND + "." + setting.key,
                new Target(setting.key, BACKGROUND)));
        }
        return all;
    }

    /** The id's range and choices, read off the property the look grid already holds. */
    @NonNull
    private static SettingDeclaration declare(@NonNull String id, @NonNull Target target) {
        return describe(declareRange(id, target), target);
    }

    /** The name and one sentence the settings reference page shows for {@code target}. */
    @NonNull
    private static SettingDeclaration describe(@NonNull SettingDeclaration declaration,
                                               @NonNull Target target) {
        String what;
        String how;
        switch (target.property) {
            case "blur": what = "blur"; how = "How frosted the glass is, from clear to heavily frosted."; break;
            case "grain": what = "grain"; how = "How much film grain sits on the glass, in percent."; break;
            case "opacity": what = "opacity"; how = "How solid the glass is, in percent."; break;
            case "tint": what = "tint"; how = "How strongly the theme colour tints the glass, in percent."; break;
            case "colour": what = "tint colour"; how = "Which theme colour tints the glass."; break;
            case "depth": what = "depth"; how = "How strongly the glass bends the wallpaper at its edge, in dp."; break;
            case "edge": what = "edge"; how = RETIRED; break;
            case "corners": what = "corners"; how = "How round the corners are, in dp; the lowest value follows each surface's style."; break;
            case "rim": what = "rim"; how = RETIRED; break;
            case "shine": what = "shine"; how = "How much light catches the glass's rim and lens."; break;
            case "vibrancy": what = "vibrancy"; how = "How much the glass lifts the colours behind it."; break;
            case "margin": what = "edge gap"; how = "The gap between the glass and the screen edge, in dp."; break;
            case "dim": what = "wallpaper dim"; how = "How much the wallpaper behind the terminal is darkened, in percent."; break;
            default: what = target.property; how = ""; break;
        }
        if (BACKGROUND.equals(target.element)) {
            String background = "blur".equals(target.property)
                ? "How blurred the wallpaper behind the terminal panes is, in dp."
                : "opacity".equals(target.property)
                ? "How solid the terminal panes' background is, in percent." : how;
            return declaration.described("Terminal background " + what, background);
        }
        if (target.element == null) return declaration.described("Global " + what, how);
        String surface = "status".equals(target.element) ? "Status bar" : capitalize(target.element);
        return declaration.described(surface + " " + what,
            "The " + surface.toLowerCase(Locale.ROOT) + "'s own " + what
                + "; until you set one it follows the Global look.");
    }

    @NonNull
    private static String capitalize(@NonNull String word) {
        return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }

    @NonNull
    private static SettingDeclaration declareRange(@NonNull String id, @NonNull Target target) {
        if (BACKGROUND.equals(target.element) || target.property.equals("dim")) {
            LookCommand.Setting setting = LookCommand.Setting.forKey(
                LookCommand.backgroundKey(target.property));
            if (setting == null) return SettingDeclaration.text(id, "", SettingRefresh.LISTENER);
            return SettingDeclaration.integer(id, SettingDeclaration.Store.APP_PREFERENCES, id,
                setting.min, setting.max, 1, setting.shipped, SettingRefresh.LISTENER);
        }
        LookGrid.Property property = LookGrid.Property.forKey(LookCommand.gridKey(target.property));
        if (property == LookGrid.Property.TINT_HUE) {
            return SettingDeclaration.choice(id, SettingDeclaration.Store.APP_PREFERENCES, id,
                Arrays.asList("primary", "secondary", "tertiary"),
                Arrays.asList("primary", "secondary", "tertiary"),
                LookCommand.render(property, property.shipped), true, SettingRefresh.LISTENER);
        }
        if (property == LookGrid.Property.RIM) {
            return SettingDeclaration.choice(id, SettingDeclaration.Store.APP_PREFERENCES, id,
                Arrays.asList("off", "hairline", "bright"),
                Arrays.asList("off", "hairline", "bright"),
                LookCommand.render(property, property.shipped), true, SettingRefresh.LISTENER);
        }
        return SettingDeclaration.integer(id, SettingDeclaration.Store.APP_PREFERENCES, id,
            property.min, property.max, 1, property.clamp(property.shipped),
            SettingRefresh.LISTENER);
    }

    @NonNull
    @Override
    public String get(@NonNull SettingDeclaration declaration) {
        Target target = parse(declaration.id);
        if (target == null) return "";
        try {
            JSONObject answer = LookCommand.handle(mGrid, mBackground,
                request("get", target, null), () -> { });
            return answer.optBoolean("ok") ? plain(answer.optString("value", "")) : "";
        } catch (JSONException e) {
            return "";
        }
    }

    /**
     * A surface's own opacity or blur is an override only while the grid holds it; one that
     * follows Global is left out of a settings file, so it still follows after an apply. Global's
     * values and the Terminal's Background are always their own.
     */
    @Override
    public boolean overrides(@NonNull SettingDeclaration declaration) {
        Target target = parse(declaration.id);
        if (target == null || target.element == null || BACKGROUND.equals(target.element))
            return true;
        LookGrid.Element element = LookGrid.Element.forKey(target.element);
        LookGrid.Property property = LookGrid.Property.forKey(LookCommand.gridKey(target.property));
        return element == null || property == null || mGrid.has(property, element);
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration,
                                          @NonNull String value) {
        // The verb's own answer over a scratch grid: the same refusals, nothing saved or re-dressed.
        LookGrid scratch = new LookGrid(new LookGrid.MemoryStore());
        SettingsRegistry.Outcome outcome = run(declaration, value, scratch,
            new ScratchBackground(), () -> { });
        return outcome.refusal();
    }

    /** A Terminal Background that remembers nothing past a check. */
    private static final class ScratchBackground implements LookCommand.TerminalBackground {
        private final int[] mValues = new int[LookCommand.Setting.values().length];

        @Override public int get(@NonNull LookCommand.Setting setting) {
            return mValues[setting.ordinal()];
        }

        @Override public void set(@NonNull LookCommand.Setting setting, int number) {
            mValues[setting.ordinal()] = number;
        }
    }

    @NonNull
    @Override
    public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration,
                                        @NonNull String value) {
        return run(declaration, value, mGrid, mBackground, mOnChanged);
    }

    @NonNull
    private SettingsRegistry.Outcome run(@NonNull SettingDeclaration declaration,
                                         @NonNull String value, @NonNull LookGrid grid,
                                         @NonNull LookCommand.TerminalBackground background,
                                         @NonNull Runnable onChanged) {
        Target target = parse(declaration.id);
        if (target == null)
            return SettingsRegistry.unknown(declaration.id);
        SettingsRegistry.Refusal outside = outsideRange(declaration, target, value);
        if (outside != null) return outside;
        String old = get(declaration);
        try {
            JSONObject answer = LookCommand.handle(grid, background,
                request("set", target, value), onChanged);
            if (!answer.optBoolean("ok")) {
                String allowed = declaration.kind == SettingDeclaration.Kind.TEXT ? null
                    : declaration.allowed();
                return new SettingsRegistry.Refusal(answer.optString("message", "refused"), allowed);
            }
            return new SettingsRegistry.Change(declaration, old,
                plain(answer.optString("value", "")));
        } catch (JSONException e) {
            return new SettingsRegistry.Refusal("not a look value: " + e.getMessage(), null);
        }
    }

    /**
     * The registry's refusal for a whole number outside the property's own range. The {@code look}
     * verb clamps such a number; a registry door refuses it, so a file or a script cannot save a
     * value other than the one it named. Words that are no number, and a blur written in dp, are
     * the verb's to judge.
     */
    @Nullable
    private static SettingsRegistry.Refusal outsideRange(@NonNull SettingDeclaration declaration,
                                                         @NonNull Target target,
                                                         @NonNull String value) {
        if (declaration.kind != SettingDeclaration.Kind.INTEGER) return null;
        String text = value.trim().toLowerCase(Locale.ROOT);
        boolean background = BACKGROUND.equals(target.element) || target.property.equals("dim");
        if (text.endsWith("dp")) {
            if (!background) return null;
            text = text.substring(0, text.length() - 2);
        }
        int number;
        try {
            number = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
        if (number >= declaration.min && number <= declaration.max) return null;
        return new SettingsRegistry.Refusal(declaration.id + " is " + declaration.allowed(),
            declaration.allowed());
    }

    @NonNull
    private static JSONObject request(@NonNull String op, @NonNull Target target,
                                      @Nullable String value) throws JSONException {
        JSONObject request = new JSONObject().put("op", op).put("property", target.property);
        if (target.element != null) request.put("element", target.element);
        if (value != null) request.put("value", value);
        return request;
    }

    /** {@code 3 (8dp)} as {@code 3} and {@code 12dp} as {@code 12}: the number a setter takes back. */
    @NonNull
    static String plain(@NonNull String text) {
        int space = text.indexOf(' ');
        String head = space < 0 ? text : text.substring(0, space);
        return head.endsWith("dp") ? head.substring(0, head.length() - 2) : head;
    }
}
