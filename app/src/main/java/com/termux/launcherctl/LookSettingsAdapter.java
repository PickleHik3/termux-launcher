package com.termux.launcherctl;

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
            return LookCommand.Setting.forKey(property.equals("frost") ? "blur" : property) == null
                ? null : new Target(property, element);
        }
        if (property.equals("dim")) return new Target(property, element);
        LookGrid.Property known = LookGrid.Property.forKey(property.equals("colour") ? "tint_hue"
            : property.equals("blur") ? "frost" : property);
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
        if (BACKGROUND.equals(target.element) || target.property.equals("dim")) {
            LookCommand.Setting setting = LookCommand.Setting.forKey(
                target.property.equals("frost") ? "blur" : target.property);
            if (setting == null) return SettingDeclaration.text(id, "", SettingRefresh.LISTENER);
            return SettingDeclaration.integer(id, SettingDeclaration.Store.APP_PREFERENCES, id,
                setting.min, setting.max, 1, setting.shipped, SettingRefresh.LISTENER);
        }
        LookGrid.Property property = LookGrid.Property.forKey(
            target.property.equals("colour") ? "tint_hue"
                : target.property.equals("blur") ? "frost" : target.property);
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

    @NonNull
    @Override
    public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration,
                                        @NonNull String value) {
        Target target = parse(declaration.id);
        if (target == null)
            return new SettingsRegistry.Refusal("no setting is called \"" + declaration.id + "\"",
                null);
        String old = get(declaration);
        try {
            JSONObject answer = LookCommand.handle(mGrid, mBackground,
                request("set", target, value), mOnChanged);
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
