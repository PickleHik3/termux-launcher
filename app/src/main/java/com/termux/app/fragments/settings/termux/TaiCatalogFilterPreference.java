package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.R;
import com.termux.ai.TaiModelSpec;

/**
 * The catalogue's backend / state filter: the theme's stock segmented button (a single-selection
 * {@link MaterialButtonToggleGroup}), one button per filter value.
 */
public final class TaiCatalogFilterPreference extends Preference {

    public interface OnFilterSelectedListener {
        void onFilterSelected(@NonNull String value);
    }

    private String selectedValue = "all";
    private boolean includeAllOption = true;
    @Nullable private OnFilterSelectedListener listener;

    public TaiCatalogFilterPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_tai_catalog_filters);
        setIconSpaceReserved(false);
        setSelectable(false);
    }

    public TaiCatalogFilterPreference(@NonNull Context context) {
        this(context, null);
    }

    public void setSelectedValue(@NonNull String value) {
        selectedValue = value;
        notifyChanged();
    }

    public void setIncludeAllOption(boolean includeAllOption) {
        this.includeAllOption = includeAllOption;
        if (!includeAllOption && ("all".equals(selectedValue) || "installed".equals(selectedValue) || "usable".equals(selectedValue))) {
            selectedValue = TaiModelSpec.BACKEND_LITERT_LM;
        }
        notifyChanged();
    }

    public void setOnFilterSelectedListener(@Nullable OnFilterSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View groupView = holder.findViewById(R.id.tai_backend_segment);
        if (!(groupView instanceof MaterialButtonToggleGroup)) return;
        MaterialButtonToggleGroup group = (MaterialButtonToggleGroup) groupView;
        group.clearOnButtonCheckedListeners();
        int[] ids = {R.id.tai_catalog_filter_all, R.id.tai_catalog_filter_litert, R.id.tai_catalog_filter_mnn,
            R.id.tai_catalog_filter_installed, R.id.tai_catalog_filter_usable};
        String[] values = {"all", TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.BACKEND_MNN_LLM, "installed", "usable"};
        boolean[] extra = {true, false, false, true, true};
        for (int i = 0; i < ids.length; i++) {
            View button = group.findViewById(ids[i]);
            if (button != null && extra[i]) button.setVisibility(includeAllOption ? View.VISIBLE : View.GONE);
        }
        // An unknown value (or one hidden with the extra options) falls back to LiteRT, as before.
        int checked = R.id.tai_catalog_filter_litert;
        for (int i = 0; i < ids.length; i++) {
            if (values[i].equals(selectedValue) && (!extra[i] || includeAllOption)) checked = ids[i];
        }
        group.check(checked);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] != checkedId || values[i].equals(selectedValue)) continue;
                selectedValue = values[i];
                if (listener != null) listener.onFilterSelected(values[i]);
            }
        });
    }
}
