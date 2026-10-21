package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.app.terminal.inappkeyboard.KeyboardLayoutFiles;
import com.termux.app.terminal.inappkeyboard.LauncherKeyboardLayouts;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One keyboard layout's XML, edited as text inside Settings.
 *
 * <p>Opened from the Layouts screen for a shipped layout (its edit file if there is one, else its
 * shipped source), for one of the user's own, or — with {@link #ARG_NEW} — for a new one seeded
 * from the launcher layout. The text is checked as it is typed, off the main thread, with the
 * same parser the keyboard loads layouts with, so Save is only ever offered for a layout that
 * will load. Saving writes the layout's file (see {@link KeyboardLayoutFiles}); the keyboard
 * picks it up when the user leaves Settings, as it does the ring.
 *
 * <p>The XML field fills the screen and scrolls itself in both directions rather than wrapping:
 * a key line holds up to nine attributes, and wrapped XML loses the indentation that makes the
 * rows readable.
 */
@Keep
public class KeyboardLayoutEditorFragment extends Fragment {

    /** The layout id to edit. */
    public static final String ARG_LAYOUT_ID = "keyboard_layout_id";
    /** True for a new user layout, which has no id until it is first saved. */
    public static final String ARG_NEW = "keyboard_layout_new";

    private static final String STATE_DRAFT_TEXT = "draft_text";
    private static final String STATE_DRAFT_NAME = "draft_name";
    /** Past this a draft is not parcelled on rotation; the saved file is reloaded instead. */
    private static final int MAX_STATE_CHARS = 64 * 1024;
    private static final long VALIDATION_DELAY_MS = 300;

    /** Null while a new layout is still unsaved. */
    @Nullable private String mLayoutId;
    private boolean mUserLayout;
    private boolean mLoaded;
    private boolean mSaving;
    private String mOriginalText = "";
    private String mOriginalName = "";
    /** Lowercased names no other layout may share with a user layout. */
    private final Set<String> mTakenNames = new HashSet<>();

    @Nullable private String mXmlError;
    private boolean mValidated;
    private int mValidationGeneration;
    /** True while text is set programmatically, so the watchers do not count it as an edit. */
    private boolean mSettingText;

    private TextInputLayout mNameLayout;
    private TextInputEditText mNameInput;
    private TextInputLayout mXmlLayout;
    private TextInputEditText mXmlInput;
    private MaterialButton mSaveButton;
    private MaterialButton mDiscardButton;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mValidate = this::validate;
    private int mPreviousSoftInputMode = -1;

    private final OnBackPressedCallback mBackCallback = new OnBackPressedCallback(false) {
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
        Bundle args = getArguments();
        boolean isNew = args != null && args.getBoolean(ARG_NEW, false);
        mLayoutId = args == null ? null : args.getString(ARG_LAYOUT_ID);
        if (!isNew && !isEditable(context.getResources(), mLayoutId)) {
            // Restored from a saved Back stack, which keeps no arguments, or named by something
            // that is not a layout: there is nothing to edit, so the screen gets out of the way.
            mMainHandler.post(() -> {
                if (isAdded()) getParentFragmentManager().popBackStack();
            });
            return new View(context);
        }
        if (isNew) mLayoutId = null;
        mUserLayout = isNew || LauncherKeyboardLayouts.isUserLayout(mLayoutId);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), 0);
        root.addView(content, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(buildNameField(context));
        content.addView(buildXmlField(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(saveBar(context));

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
            mBackCallback);
        setInputsEnabled(false);
        load(context, savedInstanceState);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(mLayoutId == null
            ? R.string.settings_keyboard_layout_editor_new_title
            : R.string.settings_keyboard_layout_editor_title);
        // The XML field fills the screen; the system keyboard has to shrink it, not cover it.
        WindowManager.LayoutParams attributes = requireActivity().getWindow().getAttributes();
        mPreviousSoftInputMode = attributes.softInputMode;
        requireActivity().getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mPreviousSoftInputMode != -1)
            requireActivity().getWindow().setSoftInputMode(mPreviousSoftInputMode);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mLoaded || mXmlInput == null) return;
        String text = textOf(mXmlInput);
        if (text.length() <= MAX_STATE_CHARS) outState.putString(STATE_DRAFT_TEXT, text);
        outState.putString(STATE_DRAFT_NAME, textOf(mNameInput));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mMainHandler.removeCallbacks(mValidate);
        mValidationGeneration++;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    /** A shipped layout, the launcher layout, or a user layout id: anything else is refused. */
    private static boolean isEditable(@NonNull Resources resources, @Nullable String id) {
        if (LauncherKeyboardLayouts.LAYOUT_MAIN.equals(id)) return true;
        if (LauncherKeyboardLayouts.isUserLayout(id)) return true;
        LauncherKeyboardLayouts.Layout layout = LauncherKeyboardLayouts.find(resources, id);
        return layout != null && layout.xmlResId != 0
            && KeyboardLayoutFiles.isSafeId(id);
    }

    // ---------------------------------------------------------------- loading

    private void load(@NonNull Context context, @Nullable Bundle savedInstanceState) {
        Context appContext = context.getApplicationContext();
        Resources resources = context.getResources();
        String layoutId = mLayoutId;
        boolean user = mUserLayout;
        String draftText = savedInstanceState == null ? null
            : savedInstanceState.getString(STATE_DRAFT_TEXT);
        String draftName = savedInstanceState == null ? null
            : savedInstanceState.getString(STATE_DRAFT_NAME);
        mExecutor.execute(() -> {
            File home = KeyboardLayoutFiles.defaultHome();
            String text = null;
            String name;
            String error = null;
            Set<String> taken = new HashSet<>();
            try {
                if (layoutId == null) {
                    text = KeyboardLayoutFiles.mainSource(appContext, home);
                } else if (user || isFileBacked(home, layoutId)) {
                    text = KeyboardLayoutFiles.read(KeyboardLayoutFiles.overrideFile(home, layoutId));
                } else if (LauncherKeyboardLayouts.LAYOUT_MAIN.equals(layoutId)) {
                    text = KeyboardLayoutFiles.mainSource(appContext, home);
                } else {
                    LauncherKeyboardLayouts.Layout layout =
                        LauncherKeyboardLayouts.find(resources, layoutId);
                    if (layout == null) throw new IllegalStateException(layoutId);
                    text = KeyboardLayoutFiles.bundledSource(resources, layout.xmlResId);
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            if (user) {
                name = layoutId == null || text == null ? ""
                    : KeyboardLayoutFiles.nameAttribute(text);
                if (name == null || name.trim().isEmpty())
                    name = layoutId == null ? "" : LauncherKeyboardLayouts.fallbackUserLabel(layoutId);
                for (KeyboardLayoutFiles.UserLayout other : KeyboardLayoutFiles.userLayouts(home)) {
                    if (!other.id.equals(layoutId)) taken.add(other.name.toLowerCase(Locale.ROOT));
                }
                for (LauncherKeyboardLayouts.Layout shipped : LauncherKeyboardLayouts.catalog(resources))
                    taken.add(shipped.label.toLowerCase(Locale.ROOT));
            } else {
                name = LauncherKeyboardLayouts.labelFor(resources, layoutId);
            }
            final String loadedText = text;
            final String loadedName = name.trim();
            final String loadError = error;
            mMainHandler.post(() -> {
                if (!isAdded() || mXmlInput == null) return;
                if (loadedText == null) {
                    mXmlLayout.setError(getString(
                        R.string.settings_keyboard_layout_editor_load_error, loadError));
                    mNameInput.setText(loadedName);
                    return;
                }
                mTakenNames.clear();
                mTakenNames.addAll(taken);
                mOriginalText = loadedText;
                mOriginalName = loadedName;
                mSettingText = true;
                mNameInput.setText(draftName != null && mUserLayout ? draftName : loadedName);
                mXmlInput.setText(draftText != null ? draftText : loadedText);
                mSettingText = false;
                mLoaded = true;
                setInputsEnabled(true);
                validate();
            });
        });
    }

    private static boolean isFileBacked(@NonNull File home, @NonNull String id) {
        File file = KeyboardLayoutFiles.overrideFile(home, id);
        return file != null && file.isFile();
    }

    // ------------------------------------------------------------- validation

    private void scheduleValidation() {
        mValidated = false;
        mValidationGeneration++;
        mMainHandler.removeCallbacks(mValidate);
        mMainHandler.postDelayed(mValidate, VALIDATION_DELAY_MS);
        mXmlLayout.setError(null);
        mXmlLayout.setHelperText(getString(R.string.settings_keyboard_layout_editor_checking));
        updateState();
    }

    private void validate() {
        if (mXmlInput == null || !mLoaded) return;
        mMainHandler.removeCallbacks(mValidate);
        final int generation = ++mValidationGeneration;
        final String text = textOf(mXmlInput);
        mExecutor.execute(() -> {
            String error = KeyboardLayoutFiles.validationError(text);
            mMainHandler.post(() -> {
                if (generation != mValidationGeneration || mXmlLayout == null) return;
                mValidated = true;
                mXmlError = error;
                showValidation();
                updateState();
            });
        });
    }

    private void showValidation() {
        if (mXmlError != null) {
            mXmlLayout.setError(mXmlError);
        } else {
            mXmlLayout.setError(null);
            mXmlLayout.setHelperText(mLayoutId == null ? null
                : getString(R.string.settings_keyboard_layout_editor_valid,
                    KeyboardLayoutFiles.displayPath(mLayoutId)));
        }
    }

    /** Why the name cannot be saved, or null. Shipped layouts keep their own names. */
    @Nullable
    private String nameError() {
        if (!mUserLayout) return null;
        String name = textOf(mNameInput).trim();
        if (name.isEmpty()) return getString(R.string.settings_keyboard_layout_editor_name_required);
        if (mTakenNames.contains(name.toLowerCase(Locale.ROOT)))
            return getString(R.string.settings_keyboard_layout_editor_name_taken);
        return null;
    }

    private boolean hasChanges() {
        if (!mLoaded) return false;
        return !textOf(mXmlInput).equals(mOriginalText)
            || (mUserLayout && !textOf(mNameInput).trim().equals(mOriginalName));
    }

    private void updateState() {
        if (mSaveButton == null) return;
        boolean changed = hasChanges();
        String nameError = nameError();
        // An empty name is a prompt, not a mistake; a duplicate one is.
        boolean nameEmpty = mUserLayout && textOf(mNameInput).trim().isEmpty();
        mNameLayout.setError(nameError != null && !nameEmpty ? nameError : null);
        if (mUserLayout)
            mNameLayout.setHelperText(nameEmpty ? nameError : null);
        mSaveButton.setEnabled(changed && !mSaving && mValidated && mXmlError == null
            && nameError == null);
        mDiscardButton.setEnabled(changed && !mSaving);
        mBackCallback.setEnabled(changed);
    }

    // ------------------------------------------------------------------ saving

    private void save() {
        if (mSaving || !mLoaded) return;
        String name = textOf(mNameInput).trim();
        String text = textOf(mXmlInput);
        if (mUserLayout) text = KeyboardLayoutFiles.withNameAttribute(text, name);
        final String toWrite = text;
        final String existingId = mLayoutId;
        Context appContext = requireContext().getApplicationContext();
        mSaving = true;
        updateState();
        mExecutor.execute(() -> {
            File home = KeyboardLayoutFiles.defaultHome();
            String id = existingId;
            String error = null;
            try {
                if (id == null) {
                    Set<String> taken = new HashSet<>();
                    for (KeyboardLayoutFiles.UserLayout other : KeyboardLayoutFiles.userLayouts(home))
                        taken.add(other.id);
                    id = KeyboardLayoutFiles.newUserId(name, taken);
                }
                File file = KeyboardLayoutFiles.overrideFile(home, id);
                if (file == null) throw new IllegalStateException(id);
                KeyboardLayoutFiles.writeAtomically(file, toWrite);
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String savedId = id;
            final String saveError = error;
            mMainHandler.post(() -> {
                mSaving = false;
                if (saveError != null) {
                    AppNotice.show(appContext, appContext.getString(
                        R.string.settings_keyboard_layout_editor_save_error, saveError), true);
                    if (isAdded()) updateState();
                    return;
                }
                if (LauncherKeyboardLayouts.isUserLayout(savedId))
                    LauncherKeyboardLayouts.rememberUserLabel(savedId, name);
                AppNotice.show(appContext, R.string.settings_keyboard_layout_editor_saved);
                if (!isAdded() || mXmlInput == null) return;
                onSaved(savedId, toWrite, name);
            });
        });
    }

    private void onSaved(@NonNull String id, @NonNull String text, @NonNull String name) {
        if (mLayoutId == null) {
            mLayoutId = id;
            // A recreated screen reopens the file just written, not another new layout.
            Bundle args = getArguments() == null ? new Bundle() : getArguments();
            args.remove(ARG_NEW);
            args.putString(ARG_LAYOUT_ID, id);
            requireActivity().setTitle(R.string.settings_keyboard_layout_editor_title);
        }
        mOriginalText = text;
        mOriginalName = name;
        if (!textOf(mXmlInput).equals(text)) {
            // The name went into the XML on the way out; show what was written.
            int selection = Math.min(mXmlInput.getSelectionStart(), text.length());
            mSettingText = true;
            mXmlInput.setText(text);
            mXmlInput.setSelection(Math.max(0, selection));
            mSettingText = false;
        }
        showValidation();
        updateState();
    }

    private void confirmDiscard(@NonNull Runnable onDiscard) {
        if (!hasChanges()) {
            onDiscard.run();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_keyboard_layout_editor_discard_title)
            .setMessage(R.string.settings_keyboard_layout_editor_discard_message)
            .setNegativeButton(R.string.settings_keyboard_layout_editor_keep_editing, null)
            .setPositiveButton(R.string.settings_keyboard_layout_editor_discard,
                (dialog, which) -> onDiscard.run())
            .show();
    }

    // ------------------------------------------------------------------- views

    @NonNull
    private View buildNameField(@NonNull Context context) {
        mNameLayout = new TextInputLayout(context);
        mNameLayout.setHint(getString(R.string.settings_keyboard_layout_editor_name));
        mNameInput = new TextInputEditText(context);
        mNameInput.setSingleLine(true);
        mNameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        mNameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        mNameInput.addTextChangedListener(new AfterTextChanged(() -> {
            if (!mSettingText) updateState();
        }));
        mNameLayout.addView(mNameInput, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        mNameLayout.setLayoutParams(params);
        return mNameLayout;
    }

    @NonNull
    private View buildXmlField(@NonNull Context context) {
        mXmlLayout = new TextInputLayout(context);
        mXmlLayout.setHint(getString(R.string.settings_keyboard_layout_editor_xml));
        mXmlInput = new TextInputEditText(context);
        mXmlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mXmlInput.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI
            | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        mXmlInput.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        mXmlInput.setTypeface(Typeface.MONOSPACE);
        mXmlInput.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        mXmlInput.setGravity(Gravity.TOP | Gravity.START);
        mXmlInput.setHorizontallyScrolling(true);
        mXmlInput.setVerticalScrollBarEnabled(true);
        mXmlInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        mXmlInput.addTextChangedListener(new AfterTextChanged(() -> {
            if (!mSettingText && mLoaded) scheduleValidation();
        }));
        mXmlLayout.addView(mXmlInput);
        // TextInputLayout hands the field's frame the field's own layout params; make the frame
        // take the space left above the helper line and the field fill the frame, so the field
        // is the screen's scroller rather than growing with its text.
        mXmlInput.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (mXmlInput.getParent() instanceof View) {
            ((View) mXmlInput.getParent()).setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return mXmlLayout;
    }

    /** Discard and Save, the way the extra-keys editor lays them out. */
    @NonNull
    private View saveBar(@NonNull Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(16), dp(12));
        bar.setBackgroundColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurface, Color.BLACK));
        mDiscardButton = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        mDiscardButton.setText(R.string.settings_keyboard_layout_editor_discard);
        mDiscardButton.setOnClickListener(v -> confirmDiscard(this::revert));
        bar.addView(mDiscardButton);
        bar.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1f));
        mSaveButton = new MaterialButton(context);
        mSaveButton.setText(R.string.settings_keyboard_layout_editor_save);
        mSaveButton.setIconResource(R.drawable.ic_symbol_check);
        mSaveButton.setOnClickListener(v -> save());
        bar.addView(mSaveButton);
        return bar;
    }

    /** Back to what was last loaded or saved. */
    private void revert() {
        mSettingText = true;
        mNameInput.setText(mOriginalName);
        mXmlInput.setText(mOriginalText);
        mSettingText = false;
        validate();
        updateState();
    }

    private void setInputsEnabled(boolean enabled) {
        // A shipped layout's name is shown, never edited: it is the catalogue's.
        mNameInput.setEnabled(enabled && mUserLayout);
        mXmlInput.setEnabled(enabled);
        if (!enabled) {
            mSaveButton.setEnabled(false);
            mDiscardButton.setEnabled(false);
        }
    }

    @NonNull
    private static String textOf(@Nullable TextInputEditText input) {
        Editable text = input == null ? null : input.getText();
        return text == null ? "" : text.toString();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** A watcher that only cares that the text changed. */
    private static final class AfterTextChanged implements TextWatcher {
        private final Runnable mOnChange;

        AfterTextChanged(@NonNull Runnable onChange) {
            mOnChange = onChange;
        }

        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

        @Override public void afterTextChanged(Editable editable) {
            mOnChange.run();
        }
    }
}
