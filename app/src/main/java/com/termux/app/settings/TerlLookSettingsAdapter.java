package com.termux.app.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookIds;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlLooks;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Terl's look model as registry ids (ADR 0041), over {@link TerlLookStore}: {@code look} (the
 * Look), {@code look.corners}, {@code look.tint}, {@code look.custom.<property>} and the own value
 * of each surface that may hold one ({@code look.control-bar.opacity}, {@code look.keyboard.blur},
 * {@code look.windows.*}), which can {@code follow} Global. A write lands in the store, whose
 * listeners the screen and an edit mode session already watch, so it shows at once and an open
 * session sees it as written from outside.
 *
 * <p>Under Terl these ids shadow v1's look grid ids of the same name ({@code look.keyboard.opacity},
 * {@code look.corners}, {@code look.tint}); put in front of the v1 adapter only while Terl is on.
 */
public final class TerlLookSettingsAdapter implements SettingAdapter {

    @NonNull private final TerlLookStore mStore;

    public TerlLookSettingsAdapter(@NonNull TerlLookStore store) {
        mStore = store;
    }

    /** Whether {@code id} is one this adapter answers for (a shape check, no I/O). */
    public static boolean claims(@NonNull String id) {
        return TerlLookIds.LOOK.equals(id) || TerlLookIds.CORNERS.equals(id)
            || TerlLookIds.TINT.equals(id)
            || TerlLookIds.parseCustom(id) != null
            || TerlLookIds.parseOwn(id) != null;
    }

    @Nullable
    @Override
    public SettingDeclaration declaration(@NonNull String id) {
        if (!claims(id)) return null;
        if (TerlLookIds.LOOK.equals(id)) {
            List<String> looks = mStore.lookIds();
            String first = looks.contains(TerlLooks.FROSTED) ? TerlLooks.FROSTED : looks.get(0);
            return SettingDeclaration.choice(id, SettingDeclaration.Store.APP_PREFERENCES, id, looks,
                looks, first, false, SettingRefresh.LISTENER).described("Look",
                "The Look every surface wears unless it holds its own value. Wearing one clears the own values.");
        }
        if (TerlLookIds.CORNERS.equals(id)) {
            return SettingDeclaration.integer(id, SettingDeclaration.Store.APP_PREFERENCES, id, 0,
                StoredTerlLookStore.MAX_CORNER_DP, 1, StoredTerlLookStore.DEFAULT_CORNER_DP,
                SettingRefresh.LISTENER).described("Corners",
                "How round every surface's corners are, in dp.");
        }
        if (TerlLookIds.TINT.equals(id)) {
            return SettingDeclaration.choice(id, SettingDeclaration.Store.APP_PREFERENCES, id,
                TerlLookStore.TINTS, TerlLookStore.TINTS, TerlLookStore.TINT_AUTO, false,
                SettingRefresh.LISTENER).described("Tint colour",
                "Which of the wallpaper's colours tints the glass, or none.");
        }
        TerlProperty custom = TerlLookIds.parseCustom(id);
        if (custom != null) {
            return range(id, custom).described("Custom " + custom.slug,
                "The " + custom.slug + " Custom gives all glass while it is the Look.");
        }
        TerlLookStore.OwnValue own = TerlLookIds.parseOwn(id);
        if (own == null) return null;
        String surface = own.surface == TerlSurface.CONTROL_BAR ? "Control bar"
            : own.surface == TerlSurface.KEYBOARD ? "Keyboard" : "Windows";
        return range(id, own.property).described(surface + " " + own.property.slug,
            "The " + surface.toLowerCase(java.util.Locale.ROOT) + "'s own " + own.property.slug
                + "; until you set one it follows the Look.");
    }

    @NonNull
    private SettingDeclaration range(@NonNull String id, @NonNull TerlProperty property) {
        return SettingDeclaration.integer(id, SettingDeclaration.Store.APP_PREFERENCES, id,
            property.min, property.max, 1, property == TerlProperty.OPACITY ? 60 : property.min,
            SettingRefresh.LISTENER);
    }

    @NonNull
    @Override
    public List<SettingDeclaration> declarations() {
        List<String> ids = new ArrayList<>();
        ids.add(TerlLookIds.LOOK);
        ids.add(TerlLookIds.CORNERS);
        ids.add(TerlLookIds.TINT);
        for (TerlProperty property : TerlProperty.values()) ids.add(TerlLookIds.ofCustom(property));
        for (TerlSurface surface : TerlSurface.values()) {
            if (!surface.acceptsOwn) continue;
            for (TerlProperty property : TerlProperty.values()) ids.add(TerlLookIds.of(surface, property));
        }
        List<SettingDeclaration> all = new ArrayList<>();
        for (String id : ids) {
            SettingDeclaration declaration = declaration(id);
            if (declaration != null) all.add(declaration);
        }
        return all;
    }

    @NonNull
    @Override
    public String get(@NonNull SettingDeclaration declaration) {
        String id = declaration.id;
        if (TerlLookIds.LOOK.equals(id)) return mStore.look();
        if (TerlLookIds.CORNERS.equals(id)) return String.valueOf(mStore.cornerRadiusDp());
        if (TerlLookIds.TINT.equals(id)) return mStore.tint();
        TerlProperty custom = TerlLookIds.parseCustom(id);
        if (custom != null) return String.valueOf(mStore.customValue(custom));
        TerlLookStore.OwnValue own = TerlLookIds.parseOwn(id);
        return own == null ? "" : String.valueOf(mStore.value(own.surface, own.property));
    }

    /** A surface's own value is an override only while it holds one; everything else always is. */
    @Override
    public boolean overrides(@NonNull SettingDeclaration declaration) {
        TerlLookStore.OwnValue own = TerlLookIds.parseOwn(declaration.id);
        return own == null || mStore.holdsOwn(own.surface, own.property);
    }

    @Override
    public boolean canFollow(@NonNull SettingDeclaration declaration) {
        return TerlLookIds.parseOwn(declaration.id) != null;
    }

    @NonNull
    @Override
    public SettingsRegistry.Outcome follow(@NonNull SettingDeclaration declaration) {
        TerlLookStore.OwnValue own = TerlLookIds.parseOwn(declaration.id);
        if (own == null) {
            return new SettingsRegistry.Refusal(declaration.id + " has no Look to follow", null);
        }
        String old = get(declaration);
        mStore.followGlobal(own.surface, own.property);
        return new SettingsRegistry.Change(declaration, old, get(declaration));
    }

    /** {@code default} on an own value is following; the others go to their shipped value. */
    @NonNull
    @Override
    public SettingsRegistry.Outcome reset(@NonNull SettingDeclaration declaration) {
        return canFollow(declaration) ? follow(declaration)
            : set(declaration, declaration.defaultValue());
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal checkReset(@NonNull SettingDeclaration declaration) {
        return canFollow(declaration) ? null : check(declaration, declaration.defaultValue());
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration,
                                          @NonNull String value) {
        String text = value.trim();
        if (declaration.kind == SettingDeclaration.Kind.INTEGER) {
            int number;
            try {
                number = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return new SettingsRegistry.Refusal(declaration.id + " is " + declaration.allowed(),
                    declaration.allowed());
            }
            if (number < declaration.min || number > declaration.max) {
                return new SettingsRegistry.Refusal(declaration.id + " is " + declaration.allowed(),
                    declaration.allowed());
            }
            return null;
        }
        if (!declaration.choices.contains(text)) {
            return new SettingsRegistry.Refusal(declaration.id + " is " + declaration.allowed(),
                declaration.allowed());
        }
        return null;
    }

    @NonNull
    @Override
    public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration,
                                        @NonNull String value) {
        SettingsRegistry.Refusal refusal = check(declaration, value);
        if (refusal != null) return refusal;
        String old = get(declaration);
        String text = value.trim();
        String id = declaration.id;
        if (TerlLookIds.LOOK.equals(id)) {
            // Writing the Look is wearing it: the own values it clears go, as a pick in edit mode does.
            mStore.pick(text, true);
        } else if (TerlLookIds.CORNERS.equals(id)) {
            mStore.setCornerRadiusDp(Integer.parseInt(text));
        } else if (TerlLookIds.TINT.equals(id)) {
            mStore.setTint(text);
        } else {
            TerlProperty custom = TerlLookIds.parseCustom(id);
            if (custom != null) {
                mStore.setCustomValue(custom, Integer.parseInt(text));
            } else {
                TerlLookStore.OwnValue own = TerlLookIds.parseOwn(id);
                if (own == null) return SettingsRegistry.unknown(id);
                mStore.setOwn(own.surface, own.property, Integer.parseInt(text));
            }
        }
        return new SettingsRegistry.Change(declaration, old, get(declaration));
    }
}
