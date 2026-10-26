package com.termux.app.fragments.settings.termux;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.MenuProvider;
import androidx.core.widget.NestedScrollView;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.notice.AppNotice;
import com.termux.app.settings.TermuxPropertiesFile;
import com.termux.app.terminal.io.ExtraKeyActionLabels;
import com.termux.app.terminal.io.ExtraKeyActionPicker;
import com.termux.app.terminal.io.ExtraKeyDetailSheet;
import com.termux.app.terminal.io.ExtraKeyOptionsSheets;
import com.termux.app.terminal.io.ExtraKeysDraft;
import com.termux.app.terminal.io.ExtraKeysLayoutModel;
import com.termux.app.terminal.io.ExtraKeysPresets;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.font.NerdFontSpans;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The extra-keys editor as a Settings page: pages, the rows on a page, the keys in a row.
 *
 * <p>Each row is a card of key caps that scrolls sideways, with a dashed + pinned at its end. A
 * tap on a cap opens the Edit key sheet, the + opens the Choose a key sheet, a row's ⋮ opens its
 * options, and a row reorders by dragging its handle. Everything edits an {@link ExtraKeysDraft};
 * nothing reaches {@code termux.properties} until Save changes, Cancel puts the draft away, and
 * leaving with unsaved edits asks first. Removals offer Undo.
 *
 * <p>Reached from Keyboard settings, from the {@code extrakeys.edit} action and from the old
 * edit-row deep link, all of which now land in Settings rather than in a sheet over the terminal.
 */
@Keep
public class ExtraKeysEditorFragment extends Fragment
    implements com.termux.app.fragments.settings.SettingsUnsavedChanges {

    @Override
    public boolean hasUnsavedChanges() {
        return backCallback.isEnabled();
    }

    private static final int KEY_CAP_DP = 48;
    private static final int GAP_DP = 8;

    private ExtraKeysDraft draft = ExtraKeysDraft.of(new ArrayList<>());
    /** The draft as loaded or last saved, to tell whether anything is left to save. */
    private String savedFingerprint = "";
    private int currentPage;

    @NonNull
    private ExtraKeysConstants.ExtraKeyDisplayMap displayMap =
        ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY;

    private int colorText;
    private int colorSubtle;
    private int colorCap;

    private View root;
    private ChipGroup pageChips;
    private MaterialButton pageAddButton;
    private ImageView pageMenuButton;
    private TextView countLabel;
    private RowAdapter adapter;
    private ItemTouchHelper touchHelper;
    private View saveBar;
    private MaterialButton saveButton;

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            confirmDiscard(() -> {
                setEnabled(false);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            });
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        colorText = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        colorSubtle = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY);
        colorCap = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF2A3140);
        loadDraft(context);

        LinearLayout column = column(context);
        NestedScrollView scroller = new NestedScrollView(context);
        scroller.setFillViewport(true);
        LinearLayout content = column(context);
        content.setPadding(dp(16), dp(4), dp(16), dp(16));
        scroller.addView(content);
        column.addView(scroller, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(pagesBar(context));
        content.addView(hintRow(context));

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setNestedScrollingEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        touchHelper = new ItemTouchHelper(new DragCallback());
        touchHelper.attachToRecyclerView(list);
        content.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(addRowButton(context));

        saveBar = saveBar(context);
        column.addView(saveBar);
        root = column;

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
            backCallback);
        requireActivity().addMenuProvider(new ResetMenu(), getViewLifecycleOwner());
        refreshAll();
        return column;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.settings_edit_extra_keys_title);
    }

    // ---------------------------------------------------------------- draft

    private void loadDraft(@NonNull Context context) {
        Properties properties = TermuxPropertiesFile.load(context);
        String style = properties.getProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE);
        displayMap = ExtraKeysInfo.getCharDisplayMapForStyle(style);

        List<ExtraKeysLayoutModel> loaded = new ArrayList<>();
        for (int page = 0; page < TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS.length; page++) {
            String value = properties.getProperty(TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS[page]);
            if (value == null) value = TermuxTerminalExtraKeys.PAGE_DEFAULT_VALUES[page];
            loaded.add(ExtraKeysLayoutModel.parse(value));
        }
        draft = ExtraKeysDraft.of(loaded);
        savedFingerprint = draft.fingerprint();
        currentPage = Math.min(currentPage, draft.pageCount() - 1);
    }

    private boolean isDirty() {
        return !draft.fingerprint().equals(savedFingerprint);
    }

    /** After any edit: the save bar and the Back guard follow whether the draft differs. */
    private void edited() {
        backCallback.setEnabled(isDirty());
        updateSaveBar();
    }

    private void refreshAll() {
        refreshPages();
        refreshRows();
        updateSaveBar();
    }

    private void refreshRows() {
        ExtraKeysLayoutModel page = draft.page(currentPage);
        // An empty page shows one empty row, so its + is the way in. Empty rows are never saved.
        if (page.rowCount() == 0) page.addRow();
        adapter.notifyDataSetChanged();
        int rows = page.rowCount();
        countLabel.setText(getResources().getQuantityString(
            R.plurals.settings_extra_keys_row_count, rows, rows));
    }

    private void save() {
        for (int page = 0; page < draft.pageCount(); page++) draft.page(page).pruneEmptyRows();
        for (int page = 0; page < TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS.length; page++) {
            TermuxPropertiesFile.write(TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS[page],
                draft.valueFor(page));
        }
        savedFingerprint = draft.fingerprint();
        backCallback.setEnabled(false);
        Context context = requireContext();
        // The terminal rebuilds its toolbar pages when it next comes to the front.
        TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
        AppNotice.show(context, R.string.settings_extra_keys_saved);
        refreshAll();
    }

    private void confirmDiscard(@NonNull Runnable onDiscard) {
        if (!isDirty()) {
            onDiscard.run();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_extra_keys_discard_title)
            .setMessage(R.string.settings_extra_keys_discard_message)
            .setNegativeButton(R.string.settings_extra_keys_keep_editing, null)
            .setPositiveButton(R.string.settings_extra_keys_discard, (d, w) -> onDiscard.run())
            .show();
    }

    /** Puts the draft away and leaves the page, as Back does once the reader has confirmed. */
    private void cancel() {
        confirmDiscard(() -> {
            backCallback.setEnabled(false);
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        });
    }

    /** Takes back the last removal: the draft returns to how it was, on the page it was on. */
    private void offerUndo(@StringRes int message, @Nullable Object arg,
                           @NonNull ExtraKeysDraft.Snapshot before, int pageBefore) {
        String text = arg == null ? getString(message) : getString(message, arg);
        Snackbar snackbar = Snackbar.make(root, text, Snackbar.LENGTH_LONG);
        snackbar.setAnchorView(saveBar);
        snackbar.setAction(R.string.settings_extra_keys_undo, v -> {
            draft.restore(before);
            currentPage = Math.max(0, Math.min(pageBefore, draft.pageCount() - 1));
            edited();
            refreshAll();
        });
        snackbar.show();
    }

    // ---------------------------------------------------------------- pages

    private View pagesBar(@NonNull Context context) {
        LinearLayout block = column(context);

        LinearLayout line = row(context);
        pageChips = new ChipGroup(context);
        pageChips.setSingleLine(true);
        pageChips.setSingleSelection(true);
        pageChips.setSelectionRequired(true);
        line.addView(pageChips, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        pageMenuButton = new ImageView(context);
        pageMenuButton.setImageResource(R.drawable.ic_editor_more_vert);
        pageMenuButton.setColorFilter(colorText);
        pageMenuButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        pageMenuButton.setBackgroundResource(android.R.drawable.list_selector_background);
        pageMenuButton.setContentDescription(getString(R.string.settings_extra_keys_page_options_cd));
        pageMenuButton.setOnClickListener(v -> showPageOptions());
        line.addView(pageMenuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        pageAddButton = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonOutlinedStyle);
        pageAddButton.setIconResource(R.drawable.ic_symbol_add);
        pageAddButton.setContentDescription(getString(R.string.settings_extra_keys_add_page_cd));
        pageAddButton.setOnClickListener(v -> {
            int added = draft.addPage();
            if (added < 0) return;
            currentPage = added;
            edited();
            refreshAll();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        addParams.setMarginStart(dp(4));
        line.addView(pageAddButton, addParams);
        block.addView(line);
        return block;
    }

    /** One chip per page; the + goes away once both pages exist. */
    private void refreshPages() {
        Context context = requireContext();
        pageChips.removeAllViews();
        for (int page = 0; page < draft.pageCount(); page++) {
            final int index = page;
            Chip chip = new Chip(context);
            chip.setText(getString(R.string.settings_extra_keys_page, page + 1));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setId(View.generateViewId());
            chip.setChecked(page == currentPage);
            chip.setOnClickListener(v -> {
                if (currentPage == index) return;
                currentPage = index;
                refreshAll();
            });
            pageChips.addView(chip);
        }
        pageAddButton.setVisibility(draft.canAddPage() ? View.VISIBLE : View.GONE);
    }

    private void showPageOptions() {
        final int page = currentPage;
        ExtraKeyOptionsSheets.showPage(requireContext(), page + 1,
            draft.canMovePage(page, -1), draft.canMovePage(page, 1), draft.canRemovePage(page),
            new ExtraKeyOptionsSheets.PageActions() {
                @Override
                public void moveLeft() {
                    movePage(page, -1);
                }

                @Override
                public void moveRight() {
                    movePage(page, 1);
                }

                @Override
                public void remove() {
                    ExtraKeysDraft.Snapshot before = draft.snapshot();
                    currentPage = draft.removePage(page);
                    edited();
                    refreshAll();
                    offerUndo(R.string.settings_extra_keys_page_removed, page + 1, before, page);
                }
            });
    }

    private void movePage(int page, int delta) {
        if (!draft.movePage(page, delta)) return;
        currentPage = page + delta;
        edited();
        refreshAll();
    }

    // ---------------------------------------------------------------- reset

    /** Reset in the app bar; its presets drop down from the action itself. */
    private final class ResetMenu implements MenuProvider {
        private final int itemId = View.generateViewId();

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
            menu.add(Menu.NONE, itemId, Menu.NONE, R.string.settings_extra_keys_reset)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem item) {
            if (item.getItemId() != itemId) return false;
            View anchor = requireActivity().findViewById(itemId);
            showResetMenu(anchor != null ? anchor : root);
            return true;
        }
    }

    /** Presets for the page in view, under Reset. */
    private void showResetMenu(@NonNull View anchor) {
        Context context = requireContext();
        // The row the user had before they took this release's row, when they took it: the one
        // preset that is theirs alone, so it leads the list and stays after it is used.
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        String previous = preferences == null ? null : preferences.getPreviousExtraKeys(currentPage);
        List<ExtraKeysPresets.Preset> presets =
            ExtraKeysPresets.presetsForPage(currentPage, previous);
        android.widget.PopupMenu menu = new android.widget.PopupMenu(context, anchor);
        for (int i = 0; i < presets.size(); i++) {
            menu.getMenu().add(0, i, i, presets.get(i).titleRes);
        }
        menu.setOnMenuItemClickListener(item -> {
            applyPreset(presets.get(item.getItemId()));
            return true;
        });
        menu.show();
    }

    private void applyPreset(@NonNull ExtraKeysPresets.Preset preset) {
        final int page = currentPage;
        Runnable apply = () -> {
            ExtraKeysDraft.Snapshot before = draft.snapshot();
            draft.setPage(page, preset.model());
            edited();
            refreshAll();
            offerUndo(R.string.settings_extra_keys_page_reset, page + 1, before, page);
        };
        if (draft.page(page).isEmpty()) {
            apply.run();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(preset.titleRes)
            .setMessage(getString(R.string.settings_extra_keys_preset_replace_message, page + 1))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_extra_keys_preset_replace, (d, w) -> apply.run())
            .show();
    }

    // ----------------------------------------------------------------- rows

    private View hintRow(@NonNull Context context) {
        LinearLayout line = row(context);
        line.setPadding(dp(4), dp(8), dp(4), dp(8));
        TextView hint = new TextView(context);
        hint.setText(R.string.settings_extra_keys_hint);
        hint.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        hint.setTextColor(colorSubtle);
        hint.setSingleLine(true);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        countLabel = new TextView(context);
        countLabel.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
        countLabel.setTextColor(colorSubtle);
        countLabel.setSingleLine(true);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.setMarginStart(dp(8));
        line.addView(countLabel, countParams);
        return line;
    }

    private View addRowButton(@NonNull Context context) {
        MaterialButton add = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        add.setText(R.string.settings_extra_keys_add_row);
        add.setIconResource(R.drawable.ic_symbol_add);
        add.setMinHeight(dp(48));
        add.setOnClickListener(v -> {
            draft.addRow(currentPage);
            edited();
            refreshRows();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        add.setLayoutParams(params);
        return add;
    }

    private void showRowOptions(int row) {
        final int page = currentPage;
        ExtraKeyOptionsSheets.showRow(requireContext(), row + 1,
            draft.canMoveRow(page, row, -1), draft.canMoveRow(page, row, 1),
            new ExtraKeyOptionsSheets.RowActions() {
                @Override
                public void moveUp() {
                    moveRow(page, row, -1);
                }

                @Override
                public void moveDown() {
                    moveRow(page, row, 1);
                }

                @Override
                public void delete() {
                    ExtraKeysDraft.Snapshot before = draft.snapshot();
                    draft.removeRow(page, row);
                    edited();
                    refreshRows();
                    offerUndo(R.string.settings_extra_keys_row_deleted, row + 1, before, page);
                }
            });
    }

    private void moveRow(int page, int row, int delta) {
        if (!draft.moveRow(page, row, delta)) return;
        edited();
        refreshRows();
    }

    private void addKeyTo(int row) {
        ExtraKeyActionPicker.show(requireContext(),
            getString(R.string.settings_extra_keys_picker_adding_to, row + 1),
            R.string.settings_extra_keys_picker_confirm_add, picked -> {
                draft.addKey(currentPage, row, picked);
                edited();
                refreshRows();
            }, null);
    }

    private void openKeySheet(@NonNull ExtraKeysLayoutModel.Key key) {
        final int page = currentPage;
        new ExtraKeyDetailSheet(requireContext(), key, displayMap, new ExtraKeyDetailSheet.Host() {
            @Override
            public void onKeyChanged() {
                edited();
                refreshRows();
            }

            @Override
            public void onRemoveKey() {
                ExtraKeysDraft.Snapshot before = draft.snapshot();
                draft.removeKey(page, key);
                edited();
                refreshRows();
                offerUndo(R.string.settings_extra_keys_key_removed, null, before, page);
            }

            @Override
            public boolean canMoveKey(int delta) {
                return draft.canMoveKey(page, key, delta);
            }

            @Override
            public void onMoveKey(int delta) {
                if (!draft.moveKey(page, key, delta)) return;
                edited();
                refreshRows();
            }
        }).show();
    }

    /** One card per row of the page. */
    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final RowCardView card;

            Holder(@NonNull RowCardView card) {
                super(card);
                this.card = card;
            }
        }

        @Override
        public int getItemCount() {
            return draft.page(currentPage).rowCount();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            RowCardView card = new RowCardView(parent.getContext());
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(GAP_DP);
            card.setLayoutParams(params);
            return new Holder(card);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.card.bind(position, holder);
        }
    }

    /** Drag a row by its handle; the draft follows the card as it passes the others. */
    private final class DragCallback extends ItemTouchHelper.Callback {
        private boolean moved;

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (from < 0 || to < 0 || from == to) return false;
            int step = to > from ? 1 : -1;
            for (int row = from; row != to; row += step) draft.moveRow(currentPage, row, step);
            adapter.notifyItemMoved(from, to);
            moved = true;
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder.itemView.setAlpha(0.9f);
                viewHolder.itemView.setElevation(dp(6));
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setAlpha(1f);
            viewHolder.itemView.setElevation(0f);
            if (!moved) return;
            moved = false;
            edited();
            // The cards were swapped in place; rebind so every "Row N" matches its place.
            recyclerView.post(ExtraKeysEditorFragment.this::refreshRows);
        }
    }

    /** A row: handle, "Row N", ⋮, then the caps scrolling sideways beside a pinned dashed +. */
    private final class RowCardView extends MaterialCardView {
        private final ImageView handle;
        private final TextView title;
        private final ImageView more;
        private final LinearLayout caps;
        private final TextView add;
        private int row;

        RowCardView(@NonNull Context context) {
            super(context, null, com.google.android.material.R.attr.materialCardViewOutlinedStyle);
            LinearLayout body = column(context);
            body.setPadding(dp(8), dp(4), dp(8), dp(12));
            addView(body);

            LinearLayout header = row(context);
            handle = new ImageView(context);
            handle.setImageResource(R.drawable.ic_drag_grip_24);
            handle.setColorFilter(colorSubtle);
            handle.setContentDescription(getString(R.string.settings_extra_keys_drag_handle));
            handle.setPadding(dp(12), dp(12), dp(12), dp(12));
            header.addView(handle, new LinearLayout.LayoutParams(dp(48), dp(48)));
            title = new TextView(context);
            title.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            title.setTextColor(colorText);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            more = new ImageView(context);
            more.setImageResource(R.drawable.ic_editor_more_vert);
            more.setColorFilter(colorText);
            more.setPadding(dp(12), dp(12), dp(12), dp(12));
            more.setBackgroundResource(android.R.drawable.list_selector_background);
            more.setContentDescription(getString(R.string.settings_extra_keys_row_options_cd));
            more.setOnClickListener(v -> showRowOptions(row));
            header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
            body.addView(header);

            LinearLayout line = row(context);
            line.setPadding(dp(4), 0, dp(4), 0);
            HorizontalScrollView scroll = new HorizontalScrollView(context);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            caps = new LinearLayout(context);
            caps.setOrientation(LinearLayout.HORIZONTAL);
            scroll.addView(caps, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            line.addView(scroll, new LinearLayout.LayoutParams(0, dp(KEY_CAP_DP), 1f));

            add = new TextView(context);
            add.setText("+");
            add.setGravity(Gravity.CENTER);
            add.setTextColor(colorText);
            add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
            add.setContentDescription(getString(R.string.settings_extra_keys_add_key_cd));
            GradientDrawable dashed = new GradientDrawable();
            dashed.setColor(Color.TRANSPARENT);
            dashed.setStroke(Math.max(1, dp(1)), colorSubtle, dp(4), dp(3));
            dashed.setCornerRadius(dp(12));
            add.setBackground(dashed);
            add.setOnClickListener(v -> addKeyTo(row));
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                dp(KEY_CAP_DP), dp(KEY_CAP_DP));
            addParams.setMarginStart(dp(GAP_DP));
            line.addView(add, addParams);
            body.addView(line);
        }

        @SuppressLint("ClickableViewAccessibility")
        void bind(int rowIndex, @NonNull RecyclerView.ViewHolder holder) {
            this.row = rowIndex;
            Context context = getContext();
            title.setText(getString(R.string.settings_extra_keys_row, rowIndex + 1));
            handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder);
                    return true;
                }
                return false;
            });
            caps.removeAllViews();
            List<ExtraKeysLayoutModel.Key> keys = draft.page(currentPage).row(rowIndex);
            for (int i = 0; i < keys.size(); i++) {
                ExtraKeysLayoutModel.Key key = keys.get(i);
                TextView cap = capView(context, key);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(KEY_CAP_DP), dp(KEY_CAP_DP));
                if (i > 0) params.setMarginStart(dp(GAP_DP));
                caps.addView(cap, params);
            }
        }
    }

    private TextView capView(@NonNull Context context, @NonNull ExtraKeysLayoutModel.Key key) {
        TextView cap = new TextView(context);
        cap.setGravity(Gravity.CENTER);
        cap.setTextColor(colorText);
        cap.setSingleLine(true);
        cap.setEllipsize(TextUtils.TruncateAt.END);
        cap.setPadding(dp(3), 0, dp(3), 0);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(cap, 8, 16, 1,
            TypedValue.COMPLEX_UNIT_SP);
        cap.setText(NerdFontSpans.span(context, ExtraKeyActionLabels.capText(key, displayMap)));
        cap.setContentDescription(ExtraKeyActionLabels.title(context, key));
        GradientDrawable background = new GradientDrawable();
        background.setColor(colorCap);
        background.setCornerRadius(dp(12));
        cap.setBackground(background);
        cap.setOnClickListener(v -> openKeySheet(key));
        return cap;
    }

    // ------------------------------------------------------------- save bar

    private View saveBar(@NonNull Context context) {
        LinearLayout bar = row(context);
        bar.setPadding(dp(16), dp(10), dp(16), dp(12));
        bar.setBackgroundColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurface, Color.BLACK));
        MaterialButton cancelButton = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancelButton.setText(android.R.string.cancel);
        cancelButton.setMinHeight(dp(48));
        cancelButton.setOnClickListener(v -> cancel());
        bar.addView(cancelButton);
        View spacer = new View(context);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
        saveButton = new MaterialButton(context);
        saveButton.setText(R.string.settings_extra_keys_save_changes);
        saveButton.setMinHeight(dp(48));
        saveButton.setSingleLine(true);
        saveButton.setEllipsize(TextUtils.TruncateAt.END);
        saveButton.setOnClickListener(v -> save());
        bar.addView(saveButton);
        return bar;
    }

    private void updateSaveBar() {
        if (saveButton == null) return;
        saveButton.setEnabled(isDirty());
    }

    // -------------------------------------------------------------- helpers

    private static LinearLayout column(@NonNull Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private static LinearLayout row(@NonNull Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
