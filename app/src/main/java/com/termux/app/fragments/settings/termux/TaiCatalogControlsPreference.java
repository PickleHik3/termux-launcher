package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;
import com.termux.ai.TaiModelSpec;

/**
 * Catalog controls row: a backend dropdown pill (Both / LiteRT / MNN) on the left, an install-status
 * dropdown pill (All / Installed / Not installed) on the right, and an inline pill search field that
 * filters on the keyboard's search action (no dialog popup).
 */
public final class TaiCatalogControlsPreference extends Preference {

    public static final String BACKEND_ALL = "all";
    public static final String INSTALL_ALL = "all";
    public static final String INSTALL_INSTALLED = "installed";
    public static final String INSTALL_NOT_INSTALLED = "not_installed";

    public interface OnControlsListener {
        void onBackendSelected(@NonNull String backend);
        void onInstallSelected(@NonNull String install);
        void onSortSelected(@NonNull String sort);
        void onSearchSubmitted(@NonNull String query);
    }

    private String backendValue = BACKEND_ALL;
    private String installValue = INSTALL_ALL;
    private String sortValue = "downloaded";
    private String searchText = "";
    @Nullable private OnControlsListener listener;

    public TaiCatalogControlsPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_tai_catalog_controls);
        setIconSpaceReserved(false);
        setSelectable(false);
    }

    public TaiCatalogControlsPreference(@NonNull Context context) {
        this(context, null);
    }

    public void setOnControlsListener(@Nullable OnControlsListener listener) {
        this.listener = listener;
    }

    public void setValues(String backend, String install, String sort, String search) {
        backendValue = backend;
        installValue = install;
        sortValue = sort;
        searchText = search;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView backend = (TextView) holder.findViewById(R.id.tai_catalog_backend_pill);
        TextView install = (TextView) holder.findViewById(R.id.tai_catalog_install_pill);
        EditText search = (EditText) holder.findViewById(R.id.tai_catalog_search_input);

        TextView sort = (TextView) holder.findViewById(R.id.tai_catalog_sort_pill);
        if (sort != null) {
            sort.setText(sortLabel(sortValue));
            sort.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(sort.getContext(), sort);
                String[] values = {"downloaded", "name", "size"};
                for (int i = 0; i < values.length; i++) menu.getMenu().add(0, i, i, sortLabel(values[i]));
                menu.setOnMenuItemClickListener(item -> {
                    sortValue = values[item.getItemId()];
                    sort.setText(sortLabel(sortValue));
                    if (listener != null) listener.onSortSelected(sortValue);
                    return true;
                });
                menu.show();
            });
        }
        if (backend != null) {
            backend.setText(backendLabel(backendValue));
            backend.setOnClickListener(v -> showBackendMenu(backend));
        }
        if (install != null) {
            install.setText(installLabel(installValue));
            install.setOnClickListener(v -> showInstallMenu(install));
        }
        if (search != null) {
            if (!search.getText().toString().equals(searchText)) search.setText(searchText);
            search.setOnEditorActionListener((view, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    searchText = view.getText().toString().trim();
                    if (listener != null) listener.onSearchSubmitted(searchText);
                    return true;
                }
                return false;
            });
        }
    }

    private void showBackendMenu(@NonNull TextView anchor) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(0, 0, 0, backendLabel(BACKEND_ALL));
        menu.getMenu().add(0, 1, 1, backendLabel(TaiModelSpec.BACKEND_LITERT_LM));
        menu.getMenu().add(0, 2, 2, backendLabel(TaiModelSpec.BACKEND_MNN_LLM));
        menu.setOnMenuItemClickListener(item -> {
            backendValue = item.getItemId() == 1 ? TaiModelSpec.BACKEND_LITERT_LM
                : item.getItemId() == 2 ? TaiModelSpec.BACKEND_MNN_LLM : BACKEND_ALL;
            anchor.setText(backendLabel(backendValue));
            if (listener != null) listener.onBackendSelected(backendValue);
            return true;
        });
        menu.show();
    }

    private void showInstallMenu(@NonNull TextView anchor) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(0, 0, 0, installLabel(INSTALL_ALL));
        menu.getMenu().add(0, 1, 1, installLabel(INSTALL_INSTALLED));
        menu.getMenu().add(0, 2, 2, installLabel(INSTALL_NOT_INSTALLED));
        menu.setOnMenuItemClickListener(item -> {
            installValue = item.getItemId() == 1 ? INSTALL_INSTALLED
                : item.getItemId() == 2 ? INSTALL_NOT_INSTALLED : INSTALL_ALL;
            anchor.setText(installLabel(installValue));
            if (listener != null) listener.onInstallSelected(installValue);
            return true;
        });
        menu.show();
    }

    private String sortLabel(String value) {
        return getContext().getString("name".equals(value) ? R.string.termux_ai_sort_name
            : "size".equals(value) ? R.string.termux_ai_sort_size : R.string.termux_ai_sort_downloaded);
    }

    @NonNull
    private String backendLabel(@NonNull String value) {
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(value)) return getContext().getString(R.string.termux_ai_backend_label_litert);
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(value)) return getContext().getString(R.string.termux_ai_backend_label_mnn);
        return getContext().getString(R.string.termux_ai_catalog_backend_both);
    }

    @NonNull
    private String installLabel(@NonNull String value) {
        if (INSTALL_INSTALLED.equals(value)) return getContext().getString(R.string.termux_ai_catalog_filter_installed);
        if (INSTALL_NOT_INSTALLED.equals(value)) return getContext().getString(R.string.termux_ai_catalog_filter_not_installed);
        return getContext().getString(R.string.termux_ai_catalog_install_all);
    }
}
