package com.termux.app.fragments.settings.termux;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.material.M3;
import com.termux.app.notice.AppNotice;
import com.termux.app.terminal.inappkeyboard.KeyboardLayoutFiles;
import com.termux.app.terminal.PaneMotionOverlayView;
import com.termux.app.terminal.inappkeyboard.LauncherKeyboardLayouts;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Picks the layouts the in-app keyboard hot-swaps between, and the order it walks them in.
 *
 * <p>Two lists, because they answer two different questions. The cycle at the top is short,
 * ordered and editable in place — it is what a bound key steps through. The catalogue below is
 * every layout the app ships, searchable, because ninety-one entries is not a list anyone
 * scrolls.
 *
 * <p>Every edit persists immediately. The live keyboard re-reads the ring in
 * {@code onPreferencesReloaded} when the user returns from Settings, so there is no Save button
 * to forget: leaving the screen is the apply.
 *
 * <p>This is also where layouts are edited. Above the two lists sit the user's own layouts, with
 * + to start a new one; every row opens {@link KeyboardLayoutEditorFragment}, and an edited
 * shipped layout carries an "Edited" tag and a reset. Both are files (see
 * {@link KeyboardLayoutFiles}), so the disk is read on a background thread whenever the screen
 * comes back, which also picks up anything changed from a terminal in between.
 */
@Keep
public class KeyboardLayoutsFragment extends Fragment {

    private TermuxAppSharedPreferences mPreferences;
    private final List<String> mSelection = new ArrayList<>();
    private LinearLayout mUserList;
    private LinearLayout mCycleList;
    private LinearLayout mAvailableList;
    private String mFilter = "";

    /**
     * The row whose button strip is open, by list and layout id rather than by view: every edit
     * re-renders the lists, and the strip has to stay open on the same row as it moves.
     */
    private static final int LIST_NONE = 0;
    private static final int LIST_USER = 1;
    private static final int LIST_CYCLE = 2;
    private static final int LIST_AVAILABLE = 3;
    private int mOpenList = LIST_NONE;
    @Nullable private String mOpenId;
    /** The open row's views in the current render, so opening another row can close it. */
    @Nullable private View mOpenHeader;
    @Nullable private View mOpenStrip;
    private boolean mOpenRendered;

    /** Shipped layouts with an override file, {@code main} included. Filled off the main thread. */
    private final Set<String> mOverridden = new HashSet<>();
    /** The user's own layouts, as last read from disk. */
    private final List<KeyboardLayoutFiles.UserLayout> mUserLayouts = new ArrayList<>();
    private boolean mFilesLoaded;
    private final ExecutorService mFileExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private int mColorText;
    private int mColorSubtle;
    private int mColorAccent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        mPreferences = TermuxAppSharedPreferences.build(context);
        if (mPreferences == null)
            throw new IllegalStateException("Termux preferences unavailable");
        mColorText = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        mColorSubtle = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        mColorAccent = MaterialColors.getColor(context,
            androidx.appcompat.R.attr.colorPrimary, mColorText);
        mSelection.clear();
        mSelection.addAll(LauncherKeyboardLayouts.parseSelection(getResources(),
            mPreferences.getInAppKeyboardLayouts()));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));

        TextView instructions = new TextView(context);
        instructions.setText(R.string.settings_keyboard_layouts_instructions);
        instructions.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        instructions.setTextColor(mColorSubtle);
        root.addView(instructions);

        root.addView(userHeader(context));
        mUserList = new LinearLayout(context);
        mUserList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mUserList);

        root.addView(sectionHeader(context, getString(R.string.settings_keyboard_layouts_in_cycle)));
        mCycleList = new LinearLayout(context);
        mCycleList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mCycleList);

        root.addView(sectionHeader(context,
            getString(R.string.settings_keyboard_layouts_available)));
        root.addView(buildSearchField(context));
        mAvailableList = new LinearLayout(context);
        mAvailableList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mAvailableList);

        // The user list waits for the disk read; so does the cycle while a user layout in it
        // has no name yet (a cold start), or the page opens on "zed" and redraws as "Zed".
        renderUser();
        if (LauncherKeyboardLayouts.userLabelsKnown(mSelection)) renderCycle();
        renderAvailable();

        ScrollView scroller = new ScrollView(context);
        scroller.addView(root, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Back from the editor, or from a terminal: what is on disk may have moved.
        refreshFiles();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mFileExecutor.shutdown();
    }

    /** Re-reads which layouts are edited and which are the user's own, then re-renders. */
    private void refreshFiles() {
        File home = KeyboardLayoutFiles.defaultHome();
        mFileExecutor.execute(() -> {
            Set<String> overridden = KeyboardLayoutFiles.overriddenIds(home);
            List<KeyboardLayoutFiles.UserLayout> users = KeyboardLayoutFiles.userLayouts(home);
            for (KeyboardLayoutFiles.UserLayout layout : users)
                LauncherKeyboardLayouts.rememberUserLabel(layout.id, layout.name);
            mMainHandler.post(() -> {
                if (!isAdded() || mCycleList == null) return;
                mOverridden.clear();
                mOverridden.addAll(overridden);
                mUserLayouts.clear();
                mUserLayouts.addAll(users);
                mFilesLoaded = true;
                pruneMissingUserLayouts();
                renderUser();
                renderCycle();
                renderAvailable();
            });
        });
    }

    /**
     * A user layout whose file is gone — deleted from a terminal — leaves the ring, so the keyboard
     * does not keep a stop that only shows the fallback QWERTY.
     */
    private void pruneMissingUserLayouts() {
        Set<String> present = new HashSet<>();
        for (KeyboardLayoutFiles.UserLayout layout : mUserLayouts) present.add(layout.id);
        boolean changed = false;
        for (int i = mSelection.size() - 1; i >= 0; i--) {
            String id = mSelection.get(i);
            if (LauncherKeyboardLayouts.isUserLayout(id) && !present.contains(id)) {
                mSelection.remove(i);
                changed = true;
            }
        }
        if (!changed) return;
        if (mSelection.isEmpty()) mSelection.add(LauncherKeyboardLayouts.LAYOUT_MAIN);
        mPreferences.setInAppKeyboardLayouts(LauncherKeyboardLayouts.joinSelection(mSelection));
    }

    @NonNull
    private View buildSearchField(@NonNull Context context) {
        TextInputLayout field = new TextInputLayout(context);
        field.setHint(getString(R.string.settings_keyboard_layouts_search_hint));
        TextInputEditText input = new TextInputEditText(context);
        input.setSingleLine(true);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void afterTextChanged(Editable editable) {
                mFilter = editable == null ? "" : editable.toString().trim();
                renderAvailable();
            }
        });
        field.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    /** The ordered ring: the reorder and remove controls live on the rows themselves. */
    private void renderCycle() {
        Context context = requireContext();
        mCycleList.removeAllViews();
        beginRender(LIST_CYCLE);
        for (int i = 0; i < mSelection.size(); i++) {
            final int index = i;
            String id = mSelection.get(i);
            String label = LauncherKeyboardLayouts.labelFor(getResources(), id);
            Row row = row(context, LIST_CYCLE, id, label, meta(String.valueOf(i + 1), id));
            row.actions.addView(glyphButton(context, "↑",
                getString(R.string.settings_keyboard_layouts_move_up, label),
                index > 0, view -> move(index, -1)));
            row.actions.addView(glyphButton(context, "↓",
                getString(R.string.settings_keyboard_layouts_move_down, label),
                index < mSelection.size() - 1, view -> move(index, 1)));
            row.actions.addView(glyphButton(context, "✕",
                getString(R.string.settings_keyboard_layouts_remove, label),
                true, view -> remove(index)));
            addFileButtons(context, row.actions, id, label);
            mCycleList.addView(row.item);
        }
        endRender(LIST_CYCLE);
    }

    /** Everything the app ships, minus what is already in the ring, narrowed by the search box. */
    private void renderAvailable() {
        Context context = requireContext();
        mAvailableList.removeAllViews();
        beginRender(LIST_AVAILABLE);
        String needle = mFilter.toLowerCase(Locale.US);
        int shown = 0;
        for (LauncherKeyboardLayouts.Layout layout
            : LauncherKeyboardLayouts.catalog(getResources())) {
            if (mSelection.contains(layout.id)) continue;
            if (!needle.isEmpty()
                && !layout.label.toLowerCase(Locale.US).contains(needle)
                && !layout.id.toLowerCase(Locale.US).contains(needle)) continue;
            Row row = row(context, LIST_AVAILABLE, layout.id, layout.label,
                meta(layout.id, layout.id));
            row.actions.addView(glyphButton(context, "+",
                getString(R.string.settings_keyboard_layouts_add, layout.label),
                true, view -> add(layout.id)));
            addFileButtons(context, row.actions, layout.id, layout.label);
            mAvailableList.addView(row.item);
            shown++;
        }
        if (shown == 0 && !needle.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(getString(R.string.settings_keyboard_layouts_none_match, mFilter));
            empty.setTextColor(mColorSubtle);
            empty.setPadding(0, dp(8), 0, dp(8));
            mAvailableList.addView(empty);
        }
        endRender(LIST_AVAILABLE);
    }

    /** The user's own layouts: add to the cycle, edit, delete. */
    private void renderUser() {
        Context context = requireContext();
        mUserList.removeAllViews();
        if (!mFilesLoaded) return;
        beginRender(LIST_USER);
        if (mUserLayouts.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.settings_keyboard_layouts_yours_empty);
            empty.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            empty.setTextColor(mColorSubtle);
            empty.setPadding(0, dp(4), 0, dp(4));
            mUserList.addView(empty);
        }
        for (KeyboardLayoutFiles.UserLayout layout : mUserLayouts) {
            Row row = row(context, LIST_USER, layout.id, layout.name,
                KeyboardLayoutFiles.displayPath(layout.id));
            row.actions.addView(glyphButton(context, "+",
                getString(R.string.settings_keyboard_layouts_add, layout.name),
                !mSelection.contains(layout.id), view -> add(layout.id)));
            addFileButtons(context, row.actions, layout.id, layout.name);
            mUserList.addView(row.item);
        }
        endRender(LIST_USER);
    }

    /**
     * Edit on every row; reset on an edited shipped layout, delete on a user's own. Nothing but
     * edit until the disk has been read, so a reset is never offered for a file not yet seen.
     */
    private void addFileButtons(@NonNull Context context, @NonNull LinearLayout actions,
                                @NonNull String id, @NonNull String label) {
        actions.addView(iconButton(context, R.drawable.ic_symbol_edit,
            getString(R.string.settings_keyboard_layouts_edit, label), view -> openEditor(id)));
        if (LauncherKeyboardLayouts.isUserLayout(id)) {
            if (mFilesLoaded)
                actions.addView(iconButton(context, R.drawable.ic_symbol_delete,
                    getString(R.string.settings_keyboard_layouts_delete, label),
                    view -> confirmDelete(id, label)));
        } else if (mOverridden.contains(id)) {
            actions.addView(iconButton(context, R.drawable.ic_symbol_restart,
                getString(R.string.settings_keyboard_layouts_reset, label),
                view -> confirmReset(id, label)));
        }
    }

    /** A row's second line, with the "Edited" tag in front when the layout has an override. */
    @NonNull
    private CharSequence meta(@NonNull String text, @NonNull String id) {
        if (!mOverridden.contains(id)) return text;
        SpannableStringBuilder meta = new SpannableStringBuilder(
            getString(R.string.settings_keyboard_layouts_edited));
        meta.setSpan(new ForegroundColorSpan(mColorAccent), 0, meta.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        meta.append(" · ").append(text);
        return meta;
    }

    /** The editor for {@code id}, or for a new layout of the user's own when null. */
    private void openEditor(@Nullable String id) {
        if (!(getActivity() instanceof SettingsActivity)) return;
        Bundle args = new Bundle();
        if (id == null) args.putBoolean(KeyboardLayoutEditorFragment.ARG_NEW, true);
        else args.putString(KeyboardLayoutEditorFragment.ARG_LAYOUT_ID, id);
        ((SettingsActivity) getActivity()).openScreen(KeyboardLayoutEditorFragment.class,
            id == null ? R.string.settings_keyboard_layout_editor_new_title
                : R.string.settings_keyboard_layout_editor_title, args);
    }

    private void confirmReset(@NonNull String id, @NonNull String label) {
        String path = KeyboardLayoutFiles.displayPath(id);
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_keyboard_layouts_reset_title, label))
            .setMessage(getString(R.string.settings_keyboard_layouts_reset_message, path))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_keyboard_layouts_reset_action,
                (dialog, which) -> deleteFile(id))
            .show();
    }

    private void confirmDelete(@NonNull String id, @NonNull String label) {
        String path = KeyboardLayoutFiles.displayPath(id);
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_keyboard_layouts_delete_title, label))
            .setMessage(getString(R.string.settings_keyboard_layouts_delete_message, path))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_keyboard_layouts_delete_action,
                (dialog, which) -> deleteFile(id))
            .show();
    }

    /** Resets an edited layout or deletes a user's own; the ring loses a deleted one. */
    private void deleteFile(@NonNull String id) {
        File file = KeyboardLayoutFiles.overrideFile(KeyboardLayoutFiles.defaultHome(), id);
        Context appContext = requireContext().getApplicationContext();
        String path = KeyboardLayoutFiles.displayPath(id);
        mFileExecutor.execute(() -> {
            boolean deleted = KeyboardLayoutFiles.delete(file);
            mMainHandler.post(() -> {
                if (!deleted)
                    AppNotice.show(appContext, appContext.getString(
                        R.string.settings_keyboard_layouts_file_error, path), true);
                if (isAdded()) refreshFiles();
            });
        });
    }

    private void add(@NonNull String layoutId) {
        if (mSelection.size() >= LauncherKeyboardLayouts.MAX_SELECTION) {
            AppNotice.show(requireContext(), getString(R.string.settings_keyboard_layouts_full,
                LauncherKeyboardLayouts.MAX_SELECTION), false);
            return;
        }
        if (mSelection.contains(layoutId)) return;
        mSelection.add(layoutId);
        // A catalogue row moves up into the ring; its strip goes with it.
        if (mOpenList == LIST_AVAILABLE && layoutId.equals(mOpenId)) mOpenList = LIST_CYCLE;
        persist();
    }

    private void remove(int index) {
        if (index < 0 || index >= mSelection.size()) return;
        // The ring is what the keyboard types on; emptying it would leave nothing to render.
        if (mSelection.size() == 1) {
            AppNotice.show(requireContext(),
                getString(R.string.settings_keyboard_layouts_last), false);
            return;
        }
        String removed = mSelection.remove(index);
        // The row falls back to where it lives outside the ring; its strip goes with it, and
        // closes if the search box hides it there.
        if (mOpenList == LIST_CYCLE && removed.equals(mOpenId))
            mOpenList = LauncherKeyboardLayouts.isUserLayout(removed) ? LIST_USER : LIST_AVAILABLE;
        persist();
    }

    private void move(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= mSelection.size() || target < 0 || target >= mSelection.size())
            return;
        mSelection.add(target, mSelection.remove(index));
        persist();
    }

    private void persist() {
        mPreferences.setInAppKeyboardLayouts(LauncherKeyboardLayouts.joinSelection(mSelection));
        renderUser();
        renderCycle();
        renderAvailable();
    }

    /** A rendered row: the whole item, and the strip its buttons go in. */
    private static final class Row {
        final LinearLayout item;
        final LinearLayout actions;

        Row(@NonNull LinearLayout item, @NonNull LinearLayout actions) {
            this.item = item;
            this.actions = actions;
        }
    }

    /**
     * A row is its name and second line; tapping it slides a strip of its buttons open beneath,
     * so the name keeps the full width. The strip starts open, without animating, when this is
     * the row the user left open before the lists re-rendered.
     */
    @NonNull
    private Row row(@NonNull Context context, int list, @NonNull String id,
                    @NonNull String label, @NonNull CharSequence meta) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(0, dp(2), 0, dp(2));
        text.setMinimumHeight(dp(48));
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextColor(mColorText);
        title.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        text.addView(title);
        TextView subtitle = new TextView(context);
        subtitle.setText(meta);
        subtitle.setTextColor(mColorSubtle);
        subtitle.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        text.addView(subtitle);
        item.addView(text, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(4), dp(4), dp(4), dp(4));
        actions.setBackground(M3.surface(context,
            com.google.android.material.R.attr.shapeAppearanceCornerMedium,
            M3.surfaceContainer(context)));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.bottomMargin = dp(4);
        item.addView(actions, actionsParams);

        boolean open = list == mOpenList && id.equals(mOpenId);
        actions.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) {
            mOpenRendered = true;
            mOpenHeader = text;
            mOpenStrip = actions;
        }

        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        text.setBackgroundResource(ripple.resourceId);
        text.setClickable(true);
        text.setFocusable(true);
        text.setContentDescription(label + ", " + meta);
        text.setOnClickListener(view -> toggle(list, id, text, actions));
        setExpandedState(text, open);
        return new Row(item, actions);
    }

    /** Opens the tapped row's strip, closing whichever other one was open; or closes its own. */
    private void toggle(int list, @NonNull String id, @NonNull View header, @NonNull View strip) {
        boolean wasOpen = list == mOpenList && id.equals(mOpenId);
        if (mOpenStrip != null && mOpenHeader != null) {
            collapseStrip(mOpenStrip);
            setExpandedState(mOpenHeader, false);
        }
        mOpenList = LIST_NONE;
        mOpenId = null;
        mOpenHeader = null;
        mOpenStrip = null;
        if (wasOpen) return;
        mOpenList = list;
        mOpenId = id;
        mOpenHeader = header;
        mOpenStrip = strip;
        expandStrip(strip);
        setExpandedState(header, true);
    }

    /** Starts tracking whether {@code list} still holds the open row as it re-renders. */
    private void beginRender(int list) {
        if (list != mOpenList) return;
        mOpenRendered = false;
        mOpenHeader = null;
        mOpenStrip = null;
    }

    /** The open row left {@code list} — deleted, or hidden by the search box — so nothing is open. */
    private void endRender(int list) {
        if (list != mOpenList || mOpenRendered) return;
        mOpenList = LIST_NONE;
        mOpenId = null;
    }

    /** Expanded/collapsed for TalkBack, with the matching expand or collapse action. */
    private void setExpandedState(@NonNull View header, boolean expanded) {
        ViewCompat.setStateDescription(header, getString(expanded
            ? R.string.settings_keyboard_layouts_row_expanded
            : R.string.settings_keyboard_layouts_row_collapsed));
        ViewCompat.removeAccessibilityAction(header,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_EXPAND.getId());
        ViewCompat.removeAccessibilityAction(header,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_COLLAPSE.getId());
        ViewCompat.replaceAccessibilityAction(header, expanded
                ? AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_COLLAPSE
                : AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_EXPAND,
            getString(expanded ? R.string.settings_keyboard_layouts_row_collapse
                : R.string.settings_keyboard_layouts_row_expand),
            (view, arguments) -> view.performClick());
    }

    /** Grows the strip from nothing to its measured height, easing in from just above. */
    private void expandStrip(@NonNull View strip) {
        int from = Math.max(0, stopStrip(strip));
        strip.setVisibility(View.VISIBLE);
        View parent = (View) strip.getParent();
        if (!ValueAnimator.areAnimatorsEnabled() || parent == null || parent.getWidth() == 0) {
            settleStrip(strip, true);
            return;
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) strip.getLayoutParams();
        int width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight()
            - params.leftMargin - params.rightMargin;
        strip.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        animateStrip(strip, from, strip.getMeasuredHeight(), true);
    }

    /** Shrinks the strip back to nothing, the reverse of {@link #expandStrip}. */
    private void collapseStrip(@NonNull View strip) {
        int from = stopStrip(strip);
        if (strip.getVisibility() != View.VISIBLE) return;
        if (from < 0) from = strip.getHeight();
        if (!ValueAnimator.areAnimatorsEnabled() || from <= 0) {
            settleStrip(strip, false);
            return;
        }
        animateStrip(strip, from, 0, false);
    }

    /**
     * Cancels a running strip animation, so a quick second tap reverses from where the strip is.
     * Returns the height it stopped at, or -1 when nothing was running.
     */
    private int stopStrip(@NonNull View strip) {
        Object running = strip.getTag();
        if (!(running instanceof ValueAnimator)) return -1;
        ((ValueAnimator) running).cancel();
        strip.setTag(null);
        return Math.max(0, strip.getLayoutParams().height);
    }

    private void animateStrip(@NonNull View strip, int from, int to, boolean opening) {
        float lift = -dp(4);
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(opening ? 200 : 150);
        animator.setInterpolator(PaneMotionOverlayView.standardInterpolator());
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams params = strip.getLayoutParams();
            params.height = (int) animation.getAnimatedValue();
            strip.setLayoutParams(params);
            // Alpha rides along on linear time; the easing shapes the height only.
            float time = Math.min(1f, animation.getCurrentPlayTime()
                / (float) Math.max(1L, animation.getDuration()));
            float shown = opening ? time : 1f - time;
            strip.setAlpha(shown);
            strip.setTranslationY(lift * (1f - shown));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (mCancelled) return;
                strip.setTag(null);
                settleStrip(strip, opening);
            }
        });
        strip.setTag(animator);
        animator.start();
    }

    /** The strip at rest: wrapping its buttons when open, gone when closed. */
    private void settleStrip(@NonNull View strip, boolean open) {
        ViewGroup.LayoutParams params = strip.getLayoutParams();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        strip.setLayoutParams(params);
        strip.setAlpha(1f);
        strip.setTranslationY(0f);
        strip.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    /**
     * A single-glyph control. Disabled rather than hidden at the ends of the list, so the row
     * keeps its shape and the buttons stay under the same thumb from row to row.
     */
    @NonNull
    private MaterialButton glyphButton(@NonNull Context context, @NonNull String glyph,
                                       @NonNull String description, boolean enabled,
                                       @NonNull View.OnClickListener onClick) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(glyph);
        button.setContentDescription(description);
        button.setEnabled(enabled);
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(4));
        button.setLayoutParams(params);
        return button;
    }

    /**
     * An icon-only control at the 48dp touch target the glyph buttons keep, borderless so a row
     * of them reads lighter than the ring's own reorder controls.
     */
    @NonNull
    private MaterialButton iconButton(@NonNull Context context, int icon,
                                      @NonNull String description,
                                      @NonNull View.OnClickListener onClick) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(icon);
        button.setContentDescription(description);
        TooltipCompat.setTooltipText(button, description);
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(4));
        button.setLayoutParams(params);
        return button;
    }

    /** "Your layouts", with + at its end to start a new one. */
    @NonNull
    private View userHeader(@NonNull Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = sectionHeader(context, getString(R.string.settings_keyboard_layouts_yours));
        header.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton add = iconButton(context, R.drawable.ic_symbol_add,
            getString(R.string.settings_keyboard_layouts_new), view -> openEditor(null));
        header.addView(add);
        return header;
    }

    @NonNull
    private TextView sectionHeader(@NonNull Context context, @NonNull String text) {
        TextView header = new TextView(context);
        header.setText(text);
        header.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        header.setTextColor(mColorText);
        header.setPadding(0, dp(16), 0, dp(4));
        return header;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
