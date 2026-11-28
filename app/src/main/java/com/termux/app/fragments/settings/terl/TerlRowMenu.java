package com.termux.app.fragments.settings.terl;

import android.content.Context;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

/**
 * Every setting row's menu, raised by holding the row: Copy as config (its terl.conf line) and,
 * while the setting is off its default, Reset. The menu is wired as rows come on screen, by the
 * registry id each row is keyed by; any other row is left as it is.
 */
final class TerlRowMenu {

    private TerlRowMenu() {}

    /** Marks a row view this menu wired, so a recycled view given to another row is unwired. */
    private static final int TAG = R.id.terl_settings_row_menu;

    /** Keys a search hit's row on an edit-mode control: the prefix, then the look id. */
    static final String HIT_KEY_PREFIX = "terl_edit_mode_hit:";

    private static final int ITEM_COPY = 1;
    private static final int ITEM_RESET = 2;

    /** Wires the menu on {@code fragment}'s list; {@code afterReset} runs after a reset. */
    static void attach(@NonNull PreferenceFragmentCompat fragment, @NonNull Runnable afterReset) {
        RecyclerView list = fragment.getListView();
        // Wired once per list: the root's search and a page's own wiring may both ask.
        if (list == null || list.getTag(TAG) != null) return;
        list.setTag(TAG, Boolean.TRUE);
        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                String id = settingId(list, view);
                if (id == null) {
                    if (view.getTag(TAG) != null) {
                        view.setOnLongClickListener(null);
                        view.setLongClickable(false);
                        view.setTag(TAG, null);
                    }
                    return;
                }
                view.setTag(TAG, id);
                view.setOnLongClickListener(row -> show(row, id, afterReset));
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {}
        });
    }

    @Nullable
    private static String settingId(@NonNull RecyclerView list, @NonNull View view) {
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        if (!(adapter instanceof PreferenceGroupAdapter)) return null;
        int position = list.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) return null;
        Preference row = ((PreferenceGroupAdapter) adapter).getItem(position);
        String key = row == null ? null : row.getKey();
        if (key != null && key.startsWith(HIT_KEY_PREFIX)) {
            String id = key.substring(HIT_KEY_PREFIX.length());
            return com.termux.app.editmode.EditModeControls.cardOf(id) != null ? id : null;
        }
        return TerlSettingsTree.setting(key) != null ? key : null;
    }

    /** Raises the menu for {@code id} on {@code anchor}; true when it had anything to offer. */
    static boolean show(@NonNull View anchor, @NonNull String id, @NonNull Runnable afterReset) {
        Context context = anchor.getContext();
        boolean copy = TerlSettings.configLine(context, id) != null;
        boolean reset = TerlChanged.isChanged(TerlSettings.registry(context), id);
        if (!copy && !reset) return false;
        PopupMenu menu = new PopupMenu(context, anchor);
        if (copy) menu.getMenu().add(Menu.NONE, ITEM_COPY, Menu.NONE, R.string.terl_settings_copy_as_config);
        if (reset) menu.getMenu().add(Menu.NONE, ITEM_RESET, Menu.NONE, R.string.terl_settings_reset);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == ITEM_COPY) {
                TerlSettings.copyAsConfig(context, id);
            } else if (item.getItemId() == ITEM_RESET && TerlSettings.reset(context, id)) {
                afterReset.run();
            }
            return true;
        });
        menu.show();
        return true;
    }
}
