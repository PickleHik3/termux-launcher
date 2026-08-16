package com.termux.shared.termux.extrakeys;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ExtraKeyButton {

    /**
     * The key name for the name of the extra key if using a dict to define the extra key. {key: name, ...}
     */
    public static final String KEY_KEY_NAME = "key";

    /**
     * The key name for the macro value of the extra key if using a dict to define the extra key. {macro: value, ...}
     */
    public static final String KEY_MACRO = "macro";

    /**
     * The key name for the alternate display name of the extra key if using a dict to define the extra key. {display: name, ...}
     */
    public static final String KEY_DISPLAY_NAME = "display";

    /**
     * The key name for the nested dict to define popup extra key info if using a dict to define the extra key. {popup: {key: name, ...}, ...}
     */
    public static final String KEY_POPUP = "popup";

    /**
     * The key that will be sent to the terminal, either a control character, like defined in
     * {@link ExtraKeysConstants#PRIMARY_KEY_CODES_FOR_STRINGS} (LEFT, RIGHT, PGUP...) or some text.
     */
    private final String key;

    /**
     * If the key is a macro, i.e. a sequence of keys separated by space.
     */
    private final boolean macro;

    /**
     * The text that will be displayed on the button.
     */
    private final String display;

    /**
     * The {@link ExtraKeyButton} containing the information of the popup button (triggered by swipe up).
     */
    @Nullable
    private final ExtraKeyButton popup;

    /**
     * Initialize a {@link ExtraKeyButton}.
     *
     * @param config The {@link JSONObject} containing the info to create the {@link ExtraKeyButton}.
     * @param extraKeyDisplayMap The {@link ExtraKeysConstants.ExtraKeyDisplayMap} that defines the
     *                           display text mapping for the keys if a custom value is not defined
     *                           by {@link #KEY_DISPLAY_NAME}.
     * @param extraKeyAliasMap The {@link ExtraKeysConstants.ExtraKeyDisplayMap} that defines the
     *                           aliases for the actual key names.
     */
    public ExtraKeyButton(@NonNull JSONObject config, @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap, @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        this(config, null, extraKeyDisplayMap, extraKeyAliasMap);
    }

    /**
     * Initialize a {@link ExtraKeyButton}.
     *
     * @param config The {@link JSONObject} containing the info to create the {@link ExtraKeyButton}.
     * @param popup The {@link ExtraKeyButton} optional {@link #popup} button.
     * @param extraKeyDisplayMap The {@link ExtraKeysConstants.ExtraKeyDisplayMap} that defines the
     *                           display text mapping for the keys if a custom value is not defined
     *                           by {@link #KEY_DISPLAY_NAME}.
     * @param extraKeyAliasMap The {@link ExtraKeysConstants.ExtraKeyDisplayMap} that defines the
     *                           aliases for the actual key names.
     */
    public ExtraKeyButton(@NonNull JSONObject config, @Nullable ExtraKeyButton popup, @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap, @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        String keyFromConfig = getStringFromJson(config, KEY_KEY_NAME);
        String macroFromConfig = getStringFromJson(config, KEY_MACRO);
        String[] keys;
        if (keyFromConfig != null && macroFromConfig != null) {
            throw new JSONException("Both key and macro can't be set for the same key. key: \"" + keyFromConfig + "\", macro: \"" + macroFromConfig + "\"");
        } else if (keyFromConfig != null) {
            keys = new String[] { keyFromConfig };
            this.macro = false;
        } else if (macroFromConfig != null) {
            keys = macroFromConfig.split(" ");
            this.macro = true;
        } else {
            throw new JSONException("All keys have to specify either key or macro");
        }
        for (int i = 0; i < keys.length; i++) {
            keys[i] = replaceAlias(extraKeyAliasMap, keys[i]);
        }
        this.key = TextUtils.join(" ", keys);
        this.display = resolveDisplay(keys, getStringFromJson(config, KEY_DISPLAY_NAME), extraKeyDisplayMap);
        this.popup = popup;
    }

    /**
     * What a key cap reads: an explicit {@code display} if there is one, otherwise every key name
     * put through {@code extraKeyDisplayMap} — which is what turns {@code KEYBOARD} into {@code ⌨}.
     *
     * <p>Public and static because the row editor has to arrive at the same text as the live row.
     * It used to fall back to the raw key name and so showed caps reading {@code KEYBOARD}, or
     * {@code tool:workspace.picker}, for keys the toolbar itself drew as a single glyph.
     *
     * @param keySpec the key name, or a macro's key names joined by spaces.
     */
    @NonNull
    public static String resolveDisplay(@NonNull String keySpec, @Nullable String display,
                                        @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap) {
        return resolveDisplay(keySpec.split(" "), display, extraKeyDisplayMap);
    }

    @NonNull
    private static String resolveDisplay(@NonNull String[] keys, @Nullable String display,
                                         @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap) {
        if (display != null) return display;
        return Arrays.stream(keys).map(key -> extraKeyDisplayMap.get(key, key)).collect(Collectors.joining(" "));
    }

    public String getStringFromJson(@NonNull JSONObject config, @NonNull String key) {
        try {
            return config.getString(key);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Get {@link #key}.
     */
    public String getKey() {
        return key;
    }

    /**
     * Check whether a {@link #macro} is defined or not.
     */
    public boolean isMacro() {
        return macro;
    }

    /**
     * Get {@link #display}.
     */
    public String getDisplay() {
        return display;
    }

    /**
     * Get {@link #popup}.
     */
    @Nullable
    public ExtraKeyButton getPopup() {
        return popup;
    }

    /**
     * Replace the alias with its actual key name if found in extraKeyAliasMap.
     */
    public static String replaceAlias(@NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap, String key) {
        return extraKeyAliasMap.get(key, key);
    }
}
