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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.notice.AppNotice;
import com.termux.app.settings.TermuxPropertiesFile;
import com.termux.app.terminal.io.ExtraKeyActionLabels;
import com.termux.app.terminal.io.ExtraKeyActionPicker;
import com.termux.app.terminal.io.ExtraKeyDetailSheet;
import com.termux.app.terminal.io.ExtraKeysLayoutModel;
import com.termux.app.terminal.io.ExtraKeysPresets;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.font.NerdFontSpans;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The extra-keys editor as a Settings page.
 *
 * <p>The page shows the row the way the terminal will draw it — the real {@link ExtraKeysView},
 * fed the draft — and edits it three ways: one-tap chips for the keys people most often want
 * ({@code CTRL} first; issue #22), a list of the page's keys that reorders by drag and opens a
 * per-key sheet on tap, and whole-row presets for anyone who would rather start from the classic
 * Termux row. Both pages of the toolbar are drafts here; nothing reaches {@code termux.properties}
 * until Save, and leaving with unsaved edits asks first.
 *
 * <p>Reached from Keyboard settings, from the {@code extrakeys.edit} action and from the old
 * edit-row deep link, all of which now land in Settings rather than in a sheet over the terminal.
 */
@Keep
public class ExtraKeysEditorFragment extends Fragment {

    /** Height of one row of the live toolbar, before the user's {@code terminal-toolbar-height}. */
    private static final float ROW_HEIGHT_DP = 37.5f;

    private final List<ExtraKeysLayoutModel> pages = new ArrayList<>();
    private int currentPage;
    private boolean dirty;

    @NonNull
    private ExtraKeysConstants.ExtraKeyDisplayMap displayMap =
        ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY;
    private String extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
    private boolean allCaps = true;
    private float rowHeightPx;

    private int colorText;
    private int colorSubtle;
    private int colorOutline;
    private int colorCap;
    private int colorError;

    private ExtraKeysView previewRow;
    private TextView previewEmpty;
    /** The matrix behind the current preview, for mapping a tap back to a key. */
    @Nullable private ExtraKeyButton[][] previewMatrix;
    private ChipGroup pageChips;
    private TextView pageHint;
    private KeysAdapter adapter;
    private ItemTouchHelper touchHelper;
    private ChipGroup presetChips;
    private MaterialButton saveButton;
    private MaterialButton discardButton;

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
        colorOutline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutlineVariant, 0x33FFFFFF);
        colorCap = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF2A3140);
        colorError = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorError, 0xFFF2B8B5);
        loadDraft(context);

        LinearLayout root = column(context);
        NestedScrollView scroller = new NestedScrollView(context);
        scroller.setFillViewport(true);
        LinearLayout content = column(context);
        content.setPadding(dp(16), dp(4), dp(16), dp(16));
        scroller.addView(content);
        root.addView(scroller, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(previewCard(context));
        content.addView(pagesRow(context));

        content.addView(sectionHeader(context, getString(R.string.settings_extra_keys_add_header)));
        content.addView(quickAddChips(context));

        content.addView(keysHeader(context));
        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setNestedScrollingEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new KeysAdapter();
        list.setAdapter(adapter);
        touchHelper = new ItemTouchHelper(new DragCallback());
        touchHelper.attachToRecyclerView(list);
        content.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(sectionHeader(context,
            getString(R.string.settings_extra_keys_presets_header)));
        presetChips = new ChipGroup(context);
        content.addView(presetChips);

        root.addView(saveBar(context));

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
            backCallback);
        refreshAll();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.settings_edit_extra_keys_title);
    }

    // ---------------------------------------------------------------- draft

    private void loadDraft(@NonNull Context context) {
        pages.clear();
        Properties properties = TermuxPropertiesFile.load(context);
        extraKeysStyle = properties.getProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE);
        displayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
        String caps = properties.getProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS);
        allCaps = caps == null || !"false".equals(caps.trim().toLowerCase(Locale.ROOT));
        float scale = TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR;
        try {
            String stored = properties.getProperty(
                TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR);
            if (stored != null) scale = Math.max(0.5f, Math.min(3f, Float.parseFloat(stored.trim())));
        } catch (NumberFormatException ignored) {
            // The row falls back to its default height for a malformed value; so does the preview.
        }
        rowHeightPx = ROW_HEIGHT_DP * scale * context.getResources().getDisplayMetrics().density;

        for (int page = 0; page < TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS.length; page++) {
            String value = properties.getProperty(TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS[page]);
            if (value == null) value = TermuxTerminalExtraKeys.PAGE_DEFAULT_VALUES[page];
            pages.add(ExtraKeysLayoutModel.parse(value));
        }
        if (pages.isEmpty()) pages.add(ExtraKeysLayoutModel.empty());
        currentPage = Math.min(currentPage, pages.size() - 1);
    }

    @NonNull
    private ExtraKeysLayoutModel page() {
        return pages.get(currentPage);
    }

    private void markDirty() {
        dirty = true;
        backCallback.setEnabled(true);
        updateSaveBar();
    }

    private void refreshAll() {
        refreshPreview();
        refreshPageChips();
        refreshPresets();
        adapter.rebuild();
        updateSaveBar();
    }

    private void save() {
        for (int page = 0; page < pages.size()
            && page < TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS.length; page++) {
            ExtraKeysLayoutModel model = pages.get(page);
            model.pruneEmptyRows();
            TermuxPropertiesFile.write(TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS[page],
                model.serialize());
        }
        dirty = false;
        backCallback.setEnabled(false);
        Context context = requireContext();
        // The terminal rebuilds its toolbar pages when it next comes to the front.
        TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
        AppNotice.show(context, R.string.settings_extra_keys_saved);
        refreshAll();
    }

    private void confirmDiscard(@NonNull Runnable onDiscard) {
        if (!dirty) {
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

    // -------------------------------------------------------------- preview

    /**
     * The row as the terminal draws it, inside a card standing in for the dock. Touch goes to an
     * overlay rather than to the keys: a tap here opens the key, it never sends it.
     */
    @SuppressLint("ClickableViewAccessibility")
    private View previewCard(@NonNull Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(20));
        card.setCardElevation(0f);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.DKGRAY));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(8);
        card.setLayoutParams(cardParams);

        FrameLayout frame = new FrameLayout(context);
        frame.setPadding(dp(8), dp(10), dp(8), dp(10));
        frame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        previewRow = new ExtraKeysView(context, null);
        previewRow.setButtonTextColor(colorText);
        frame.addView(previewRow, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        previewEmpty = new TextView(context);
        previewEmpty.setText(R.string.settings_extra_keys_preview_empty);
        previewEmpty.setTextColor(colorSubtle);
        previewEmpty.setGravity(Gravity.CENTER);
        previewEmpty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        previewEmpty.setVisibility(View.GONE);
        frame.addView(previewEmpty, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        View overlay = new View(context);
        overlay.setOnTouchListener(new TapOverlay());
        frame.addView(overlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card.addView(frame);
        return card;
    }

    private void refreshPreview() {
        ExtraKeysLayoutModel model = page();
        previewMatrix = null;
        if (model.isEmpty()) {
            previewRow.setVisibility(View.GONE);
            previewEmpty.setVisibility(View.VISIBLE);
            return;
        }
        try {
            ExtraKeysInfo info = new ExtraKeysInfo(model.serialize(), extraKeysStyle,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            previewMatrix = info.getMatrix();
            previewRow.setButtonTextAllCaps(allCaps);
            previewRow.reload(info, rowHeightPx);
            // The row's buttons fill their grid rows by weight, so the grid itself carries the
            // height: one toolbar row per row of keys, as the terminal sizes it.
            ViewGroup.LayoutParams params = previewRow.getLayoutParams();
            params.height = Math.round(rowHeightPx * previewMatrix.length);
            previewRow.setLayoutParams(params);
            previewRow.setVisibility(View.VISIBLE);
            previewEmpty.setVisibility(View.GONE);
        } catch (JSONException e) {
            previewRow.setVisibility(View.GONE);
            previewEmpty.setVisibility(View.VISIBLE);
        }
    }

    /** Turns a tap on the preview into the key under it, then opens that key. */
    private final class TapOverlay implements View.OnTouchListener {
        private float downX;
        private float downY;
        private long downAt;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    downAt = event.getEventTime();
                    return true;
                case MotionEvent.ACTION_UP:
                    int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                    boolean moved = Math.abs(event.getX() - downX) > slop
                        || Math.abs(event.getY() - downY) > slop;
                    boolean quick = event.getEventTime() - downAt
                        < ViewConfiguration.getLongPressTimeout();
                    if (!moved && quick) {
                        v.performClick();
                        openKeyAt(event.getX() + v.getLeft() - previewRow.getLeft(),
                            event.getY() + v.getTop() - previewRow.getTop());
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    private void openKeyAt(float x, float y) {
        ExtraKeyButton[][] matrix = previewMatrix;
        if (matrix == null || matrix.length == 0 || previewRow.getHeight() == 0) return;
        float rowHeight = previewRow.getHeight() / (float) matrix.length;
        int row = Math.max(0, Math.min(matrix.length - 1, (int) (y / rowHeight)));
        int firstChild = 0;
        for (int r = 0; r < row; r++) firstChild += matrix[r].length;
        int column = -1;
        float nearest = Float.MAX_VALUE;
        for (int c = 0; c < matrix[row].length; c++) {
            View child = previewRow.getChildAt(firstChild + c);
            if (child == null) continue;
            if (x >= child.getLeft() && x <= child.getRight()) {
                column = c;
                break;
            }
            float distance = Math.abs(x - (child.getLeft() + child.getRight()) / 2f);
            if (distance < nearest) {
                nearest = distance;
                column = c;
            }
        }
        if (column < 0) return;
        // The matrix skips empty rows, so its row index counts only rows that hold keys.
        ExtraKeysLayoutModel model = page();
        int modelRow = -1;
        for (int r = 0, seen = -1; r < model.rowCount(); r++) {
            if (model.row(r).isEmpty()) continue;
            if (++seen == row) {
                modelRow = r;
                break;
            }
        }
        if (modelRow < 0 || column >= model.row(modelRow).size()) return;
        openKeySheet(model.row(modelRow).get(column));
    }

    // ---------------------------------------------------------------- pages

    private View pagesRow(@NonNull Context context) {
        LinearLayout block = column(context);
        pageChips = new ChipGroup(context);
        pageChips.setSingleLine(true);
        pageChips.setSingleSelection(true);
        pageChips.setSelectionRequired(true);
        for (int page = 0; page < pages.size(); page++) {
            final int index = page;
            Chip chip = new Chip(context);
            chip.setText(getString(R.string.settings_extra_keys_page, page + 1));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setId(View.generateViewId());
            chip.setOnClickListener(v -> {
                if (currentPage == index) return;
                currentPage = index;
                refreshAll();
            });
            pageChips.addView(chip);
        }
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.topMargin = dp(10);
        block.addView(pageChips, chipParams);

        pageHint = new TextView(context);
        pageHint.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        pageHint.setTextColor(colorSubtle);
        pageHint.setPadding(dp(4), dp(2), dp(4), 0);
        block.addView(pageHint);
        return block;
    }

    private void refreshPageChips() {
        for (int i = 0; i < pageChips.getChildCount(); i++) {
            View child = pageChips.getChildAt(i);
            if (child instanceof Chip) ((Chip) child).setChecked(i == currentPage);
        }
        if (page().isEmpty()) {
            pageHint.setText(currentPage == 0
                ? R.string.settings_extra_keys_page_hint_first_empty
                : R.string.settings_extra_keys_page_hint_empty);
        } else {
            boolean otherPageHasKeys = false;
            for (int i = 0; i < pages.size(); i++) {
                if (i != currentPage && !pages.get(i).isEmpty()) otherPageHasKeys = true;
            }
            pageHint.setText(otherPageHasKeys
                ? R.string.settings_extra_keys_page_hint_swipe
                : R.string.settings_extra_keys_page_hint_tap);
        }
    }

    // ------------------------------------------------------------ quick add

    /** The keys most rows want, one tap each, plus the door to everything else. */
    private View quickAddChips(@NonNull Context context) {
        ChipGroup chips = new ChipGroup(context);
        chips.setChipSpacingVertical(dp(4));
        Chip more = new Chip(context);
        more.setText(R.string.settings_extra_keys_add_more);
        more.setChipIconResource(R.drawable.ic_symbol_search);
        more.setChipIconTint(android.content.res.ColorStateList.valueOf(colorText));
        more.setChipIconVisible(true);
        more.setOnClickListener(v -> ExtraKeyActionPicker.show(context, this::addKey));
        chips.addView(more);
        for (ExtraKeysPresets.QuickKey quick : ExtraKeysPresets.quickKeys()) {
            Chip chip = new Chip(context);
            chip.setText(quick.label);
            if (!quick.label.equals(quick.key)) chip.setContentDescription(quick.key);
            chip.setOnClickListener(v -> addKey(new ExtraKeysLayoutModel.Key(quick.key)));
            chips.addView(chip);
        }
        return chips;
    }

    /** Appends to the page's last row, making the first row when the page has none. */
    private void addKey(@NonNull ExtraKeysLayoutModel.Key key) {
        ExtraKeysLayoutModel model = page();
        if (model.rowCount() == 0) model.addRow();
        model.row(model.rowCount() - 1).add(key);
        markDirty();
        refreshAll();
    }

    private void openKeySheet(@NonNull ExtraKeysLayoutModel.Key key) {
        new ExtraKeyDetailSheet(requireContext(), key, displayMap, new ExtraKeyDetailSheet.Host() {
            @Override
            public void onKeyChanged() {
                markDirty();
                refreshPreview();
                adapter.rebuild();
            }

            @Override
            public void onRemoveKey() {
                for (List<ExtraKeysLayoutModel.Key> row : page().rows()) {
                    if (row.remove(key)) break;
                }
                markDirty();
                refreshAll();
            }
        }).show();
    }

    // ----------------------------------------------------------------- list

    private View keysHeader(@NonNull Context context) {
        LinearLayout header = row(context);
        TextView title = sectionHeader(context, getString(R.string.settings_extra_keys_keys_header));
        header.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton addRow = new MaterialButton(context, null,
            com.google.android.material.R.attr.borderlessButtonStyle);
        addRow.setText(R.string.settings_extra_keys_add_row);
        addRow.setIconResource(R.drawable.ic_symbol_add);
        addRow.setOnClickListener(v -> {
            page().addRow();
            markDirty();
            refreshAll();
        });
        header.addView(addRow);
        return header;
    }

    private void removeRow(int rowIndex) {
        ExtraKeysLayoutModel model = page();
        if (rowIndex < 0 || rowIndex >= model.rowCount()) return;
        Runnable remove = () -> {
            model.removeRow(rowIndex);
            markDirty();
            refreshAll();
        };
        if (model.row(rowIndex).isEmpty()) {
            remove.run();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_extra_keys_remove_row_title, rowIndex + 1))
            .setMessage(R.string.settings_extra_keys_remove_row_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_extra_keys_remove, (d, w) -> remove.run())
            .show();
    }

    private static final int TYPE_ROW_HEADER = 0;
    private static final int TYPE_KEY = 1;
    private static final int TYPE_EMPTY_ROW = 2;

    /** One line of the list: a row heading, a key, or the placeholder of a row with no keys. */
    private static final class Item {
        final int type;
        final int row;
        @Nullable final ExtraKeysLayoutModel.Key key;

        Item(int type, int row, @Nullable ExtraKeysLayoutModel.Key key) {
            this.type = type;
            this.row = row;
            this.key = key;
        }
    }

    private final class KeysAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<Item> items = new ArrayList<>();

        /** Flattens the current page: every row is a heading followed by its keys. */
        @SuppressWarnings("NotifyDataSetChanged")
        void rebuild() {
            items.clear();
            ExtraKeysLayoutModel model = page();
            for (int r = 0; r < model.rowCount(); r++) {
                items.add(new Item(TYPE_ROW_HEADER, r, null));
                List<ExtraKeysLayoutModel.Key> keys = model.row(r);
                if (keys.isEmpty()) items.add(new Item(TYPE_EMPTY_ROW, r, null));
                for (ExtraKeysLayoutModel.Key key : keys) items.add(new Item(TYPE_KEY, r, key));
            }
            notifyDataSetChanged();
        }

        /**
         * Writes the list order back into the page after a drag. Headings decide which row a key
         * now belongs to, so a key dragged past a heading changed row.
         */
        void commitOrder() {
            List<List<ExtraKeysLayoutModel.Key>> rows = new ArrayList<>();
            for (Item item : items) {
                if (item.type == TYPE_ROW_HEADER) {
                    rows.add(new ArrayList<>());
                } else if (item.type == TYPE_KEY && item.key != null) {
                    if (rows.isEmpty()) rows.add(new ArrayList<>());
                    rows.get(rows.size() - 1).add(item.key);
                }
            }
            page().replaceRows(rows);
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            View view;
            if (viewType == TYPE_ROW_HEADER) view = new RowHeaderView(context);
            else if (viewType == TYPE_KEY) view = new KeyItemView(context);
            else view = emptyRowView(context);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (viewType != TYPE_ROW_HEADER) params.bottomMargin = dp(6);
            view.setLayoutParams(params);
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            if (holder.itemView instanceof RowHeaderView) {
                ((RowHeaderView) holder.itemView).bind(item.row);
            } else if (holder.itemView instanceof KeyItemView && item.key != null) {
                ((KeyItemView) holder.itemView).bind(item.key, holder);
            }
        }
    }

    /** Drag between any two list positions below the first heading; the drop rewrites the page. */
    private final class DragCallback extends ItemTouchHelper.Callback {
        private boolean moved;

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
            boolean key = viewHolder.itemView instanceof KeyItemView;
            return makeMovementFlags(key ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public boolean canDropOver(@NonNull RecyclerView recyclerView,
                                   @NonNull RecyclerView.ViewHolder current,
                                   @NonNull RecyclerView.ViewHolder target) {
            // Position 0 is the first row's heading; nothing sits above it.
            return target.getAdapterPosition() > 0;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            int from = viewHolder.getAdapterPosition();
            int to = target.getAdapterPosition();
            if (from < 0 || to <= 0) return false;
            Item item = adapter.items.remove(from);
            adapter.items.add(to, item);
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
            adapter.commitOrder();
            markDirty();
            refreshAll();
        }
    }

    /** "Row N" with its remove control. */
    private final class RowHeaderView extends LinearLayout {
        private final TextView label;
        private final ImageView removeView;
        private int row;

        RowHeaderView(@NonNull Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(4), dp(10), 0, dp(2));
            label = new TextView(context);
            label.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            label.setTextColor(colorSubtle);
            addView(label, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ImageView remove = new ImageView(context);
            remove.setImageResource(R.drawable.ic_symbol_close);
            remove.setColorFilter(colorSubtle);
            remove.setPadding(dp(12), dp(12), dp(12), dp(12));
            remove.setBackgroundResource(android.R.drawable.list_selector_background);
            remove.setOnClickListener(v -> removeRow(row));
            addView(remove, new LayoutParams(dp(48), dp(48)));
            removeView = remove;
        }

        void bind(int row) {
            this.row = row;
            label.setText(getString(R.string.settings_extra_keys_row, row + 1));
            removeView.setContentDescription(getString(R.string.settings_extra_keys_remove_row_title,
                row + 1));
        }
    }

    /** One key: its cap, what it does, what a swipe up does, and a drag handle. */
    private final class KeyItemView extends LinearLayout {
        private final TextView cap;
        private final TextView title;
        private final TextView subtitle;
        private final ImageView handle;

        KeyItemView(@NonNull Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(60));
            setPadding(dp(8), dp(6), 0, dp(6));
            GradientDrawable background = new GradientDrawable();
            background.setColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurfaceContainer, Color.TRANSPARENT));
            background.setCornerRadius(dp(16));
            setBackground(background);

            cap = new TextView(context);
            cap.setGravity(Gravity.CENTER);
            cap.setTextColor(colorText);
            cap.setSingleLine(true);
            cap.setEllipsize(TextUtils.TruncateAt.END);
            cap.setPadding(dp(4), 0, dp(4), 0);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(cap, 9, 16, 1,
                TypedValue.COMPLEX_UNIT_SP);
            GradientDrawable capBackground = new GradientDrawable();
            capBackground.setColor(colorCap);
            capBackground.setCornerRadius(dp(10));
            cap.setBackground(capBackground);
            addView(cap, new LayoutParams(dp(52), dp(38)));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            LayoutParams textParams = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textParams.setMarginStart(dp(14));
            title = new TextView(context);
            title.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            title.setTextColor(colorText);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);
            subtitle = new TextView(context);
            subtitle.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            subtitle.setTextColor(colorSubtle);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(subtitle);
            addView(texts, textParams);

            handle = new ImageView(context);
            handle.setImageResource(R.drawable.ic_drag_indicator_24);
            handle.setColorFilter(colorSubtle);
            handle.setContentDescription(getString(R.string.settings_extra_keys_drag_handle));
            handle.setPadding(dp(12), dp(12), dp(12), dp(12));
            addView(handle, new LayoutParams(dp(48), dp(48)));
        }

        @SuppressLint("ClickableViewAccessibility")
        void bind(@NonNull ExtraKeysLayoutModel.Key key, @NonNull RecyclerView.ViewHolder holder) {
            Context context = getContext();
            cap.setText(NerdFontSpans.span(context, ExtraKeyActionLabels.capText(key, displayMap)));
            title.setText(ExtraKeyActionLabels.title(context, key));
            if (key.popup != null) {
                subtitle.setText(getString(R.string.settings_extra_keys_swipe_up_summary,
                    ExtraKeyActionLabels.title(context, key.popup)));
                subtitle.setVisibility(VISIBLE);
            } else {
                subtitle.setVisibility(GONE);
            }
            setOnClickListener(v -> openKeySheet(key));
            handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder);
                    return true;
                }
                return false;
            });
        }
    }

    private View emptyRowView(@NonNull Context context) {
        TextView empty = new TextView(context);
        empty.setText(R.string.settings_extra_keys_empty_row);
        empty.setTextColor(colorSubtle);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        empty.setGravity(Gravity.CENTER);
        empty.setMinimumHeight(dp(52));
        empty.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable dashed = new GradientDrawable();
        dashed.setColor(Color.TRANSPARENT);
        dashed.setStroke(Math.max(1, dp(1)), colorOutline, dp(6), dp(4));
        dashed.setCornerRadius(dp(16));
        empty.setBackground(dashed);
        return empty;
    }

    // -------------------------------------------------------------- presets

    private void refreshPresets() {
        presetChips.removeAllViews();
        Context context = requireContext();
        for (ExtraKeysPresets.Preset preset : ExtraKeysPresets.presetsForPage(currentPage)) {
            Chip chip = new Chip(context);
            chip.setText(preset.titleRes);
            chip.setOnClickListener(v -> applyPreset(preset));
            presetChips.addView(chip);
        }
    }

    private void applyPreset(@NonNull ExtraKeysPresets.Preset preset) {
        Runnable apply = () -> {
            pages.set(currentPage, preset.model());
            markDirty();
            refreshAll();
        };
        if (page().isEmpty()) {
            apply.run();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(preset.titleRes)
            .setMessage(getString(R.string.settings_extra_keys_preset_replace_message,
                currentPage + 1))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_extra_keys_preset_replace, (d, w) -> apply.run())
            .show();
    }

    // ------------------------------------------------------------- save bar

    private View saveBar(@NonNull Context context) {
        LinearLayout bar = row(context);
        bar.setPadding(dp(16), dp(10), dp(16), dp(12));
        bar.setBackgroundColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurface, Color.BLACK));
        discardButton = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        discardButton.setText(R.string.settings_extra_keys_discard);
        discardButton.setOnClickListener(v -> confirmDiscard(() -> {
            loadDraft(context);
            dirty = false;
            backCallback.setEnabled(false);
            refreshAll();
        }));
        bar.addView(discardButton);
        View spacer = new View(context);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
        saveButton = new MaterialButton(context);
        saveButton.setText(R.string.settings_extra_keys_save);
        saveButton.setIconResource(R.drawable.ic_symbol_check);
        saveButton.setOnClickListener(v -> save());
        bar.addView(saveButton);
        return bar;
    }

    private void updateSaveBar() {
        if (saveButton == null) return;
        saveButton.setEnabled(dirty);
        discardButton.setEnabled(dirty);
    }

    // -------------------------------------------------------------- helpers

    private TextView sectionHeader(@NonNull Context context, @NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        view.setTextColor(colorText);
        view.setPadding(dp(4), dp(20), dp(4), dp(6));
        return view;
    }

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
