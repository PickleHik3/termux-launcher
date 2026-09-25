package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiHuggingFace;
import com.termux.ai.TaiImportFit;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelImporter;
import com.termux.ai.TaiModelProfile;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.app.notice.AppNotice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Adding a model, as five short steps: where is it (a link or a file), the app works it out, one
 * card that says what it can do and whether it will run on this phone, progress that means
 * something, and a "ready" screen with Try it and Use as default. Everything the app can detect it
 * detects; the checkboxes, processor choice, format settings and internal name sit under
 * Advanced, collapsed. The fragment only launches the flow, forwards its picker results and
 * receives "models changed".
 */
final class TaiImportFlow {
    /** What the flow needs from the screen that owns it. */
    interface Host {
        /** The screen's context, or null once it has gone away. */
        @Nullable Context context();
        @NonNull ExecutorService executor();
        @NonNull Handler handler();
        void pickFile();
        void pickFolder();
        void openUrl(@NonNull String url);
        void setDefaultModel(@NonNull String modelId);
        /** A model was added or a download ended: redraw the list. */
        void modelsChanged();
    }

    enum Source { LINK, FILE, FOLDER }
    enum Processor { AUTO, CPU, GPU }

    /** What the flow does with the answer to a preview request. */
    enum PreviewOutcome { NEED_TOKEN, CHOOSE_FILE, SUMMARY, DOWNLOAD_STARTED, FAILED }

    /** Everything the flow has learned about the model being added. */
    static final class Draft {
        Source source = Source.LINK;
        /** The link as pasted, then the chosen file's own URL once the repository was read. */
        String url = "";
        /** The chosen file's name inside a repository; empty for a bare link. */
        String fileName = "";
        Uri document;
        TaiModelImporter.DocumentMetadata metadata;
        Uri folder;
        long sizeBytes = -1L;
        String displayName = "";
        /** An explicit internal name from Advanced; empty means derive it from the display name. */
        String internalName = "";
        final LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        Processor processor = Processor.AUTO;
        TaiModelProfile customProfile;

        /** The text the guesses read: link, file name and folder name together. */
        @NonNull
        String identity() {
            return (url + " " + fileName + " " + (metadata == null ? "" : metadata.displayName)).trim();
        }

        boolean embeddingOnly() {
            return capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)
                && !capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        }
    }

    private static final String TRY_PROMPT = "Say hello in five words.";
    private static final long TRY_TIMEOUT_MS = 90_000L;
    private static final long WATCH_INTERVAL_MS = 1000L;
    private static final String[] PICKER_MIME_TYPES = {"application/octet-stream", "application/json", "*/*"};

    private final Host host;
    private Draft draft = new Draft();
    private long deviceMemoryBytes = -1L;

    TaiImportFlow(@NonNull Host host) {
        this.host = host;
    }

    // ---- pure decisions, unit-tested ----

    /**
     * {@code result} with its candidate list narrowed to what a user should be offered: a bare
     * {@code .tflite} next to a {@code .task}/{@code .litertlm} build of the same repository is the
     * raw graph, which this app only imports as an embedding model — for a chat repository it is
     * a trap, so it is dropped whenever a packaged build exists. Edits {@code result} in place.
     */
    @Nullable
    static JSONObject pruneCandidates(@Nullable JSONObject result) {
        JSONArray candidates = result == null ? null : result.optJSONArray("candidates");
        if (candidates == null) return result;
        boolean packaged = false;
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate != null && !isEmbeddingFile(candidate.optString("file", ""))) packaged = true;
        }
        if (!packaged) return result;
        JSONArray kept = new JSONArray();
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate != null && !isEmbeddingFile(candidate.optString("file", ""))) kept.put(candidate);
        }
        try {
            result.put("candidates", kept);
        } catch (JSONException ignored) {
        }
        return result;
    }

    @NonNull
    static PreviewOutcome outcomeOf(@Nullable JSONObject result) {
        if (result == null) return PreviewOutcome.FAILED;
        if (result.optBoolean("ok", false)) return PreviewOutcome.DOWNLOAD_STARTED;
        String error = result.optString("error", "");
        if ("gated_model_requires_auth".equals(error)) return PreviewOutcome.NEED_TOKEN;
        if ("artifact_selection_required".equals(error)) {
            JSONArray candidates = result.optJSONArray("candidates");
            int count = candidates == null ? 0 : candidates.length();
            return count == 0 ? PreviewOutcome.FAILED : count == 1 ? PreviewOutcome.SUMMARY : PreviewOutcome.CHOOSE_FILE;
        }
        return PreviewOutcome.FAILED;
    }

    /**
     * The file to pre-select from a repository's runnable files: the largest that fits this phone
     * outright, else one of unknown size, else the smallest that would be slow, else the smallest.
     * Within the same fit, a full-precision ({@code f32}) build loses to any other: it needs about
     * four times the memory of a compact one and runs slower, for little gain on a phone.
     */
    static int preselect(@NonNull JSONArray candidates, long deviceMemoryBytes) {
        int best = -1;
        int bestRank = Integer.MAX_VALUE;
        long bestSize = -1L;
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate == null) continue;
            long size = candidate.optLong("sizeBytes", -1L);
            TaiImportFit fit = TaiImportFit.check(size, deviceMemoryBytes, isEmbeddingFile(candidate.optString("file", "")));
            int rank = (fit.verdict == TaiImportFit.Verdict.YES ? 0 : fit.verdict == TaiImportFit.Verdict.UNKNOWN ? 1
                : fit.verdict == TaiImportFit.Verdict.SLOW ? 2 : 3) * 2
                + (TaiImportNames.isFullPrecision(candidate.optString("file", "")) ? 1 : 0);
            // Among files that fit, prefer the largest (the more accurate build); otherwise the smallest.
            boolean better = rank < bestRank
                || rank == bestRank && (rank / 2 == 0 ? size > bestSize : size >= 0L && (bestSize < 0L || size < bestSize));
            if (better) {
                best = i;
                bestRank = rank;
                bestSize = size;
            }
        }
        return Math.max(0, best);
    }

    /** A failed load or answer the graphics chip had a hand in: the CPU is the retry to offer. */
    static boolean gpuFailure(@Nullable JSONObject result) {
        if (result == null) return false;
        if ("gpu".equalsIgnoreCase(result.optString("accelerator", ""))) return true;
        JSONObject preflight = result.optJSONObject("preflight");
        if (preflight != null && "gpu".equalsIgnoreCase(preflight.optString("effectiveAccelerator", ""))) return true;
        String text = (result.optString("error", "") + " " + result.optString("code", "") + " "
            + result.optString("message", "")).toLowerCase(Locale.ROOT);
        return text.contains("gpu") || text.contains("opencl");
    }

    static boolean memoryFailure(@Nullable JSONObject result) {
        if (result == null) return false;
        String error = result.optString("error", result.optString("code", ""));
        return error.startsWith("low_available_memory") || "insufficient_memory".equals(error)
            || result.optString("message", "").toLowerCase(Locale.ROOT).contains("memory");
    }

    static boolean isEmbeddingFile(@NonNull String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".tflite");
    }

    // ---- step 1: where is the model? ----

    void start() {
        Context context = host.context();
        if (context == null) return;
        draft = new Draft();
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout layout = column(context);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_model_import_dialog_title)
            .setView(dialogScroll(context, layout))
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        layout.addView(choice(context, R.string.termux_ai_import_where_link, R.string.termux_ai_import_where_link_hint, v -> {
            dialog.dismiss();
            showLink();
        }));
        layout.addView(choice(context, R.string.termux_ai_import_where_file, R.string.termux_ai_import_where_file_hint, v -> {
            dialog.dismiss();
            draft.source = Source.FILE;
            host.pickFile();
        }));
        // The MNN folder path stays reachable, but as the small print under the two real choices.
        TextView folder = choice(context, R.string.termux_ai_import_where_folder, R.string.termux_ai_import_where_folder_hint, v -> {
            dialog.dismiss();
            draft.source = Source.FOLDER;
            host.pickFolder();
        });
        folder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        folder.setPadding(Math.round(12 * density), Math.round(24 * density), Math.round(12 * density), Math.round(8 * density));
        layout.addView(folder);
        dialog.show();
    }

    @NonNull
    static String[] pickerMimeTypes() {
        return PICKER_MIME_TYPES;
    }

    // ---- step 2: the app works it out ----

    private void showLink() {
        Context context = host.context();
        if (context == null) return;
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.termux_ai_import_link_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(draft.url);
        LinearLayout layout = column(context);
        layout.addView(input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_import_link_title)
            .setView(dialogScroll(context, layout))
            .setPositiveButton(R.string.termux_ai_import_link_next, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            // The hint shows the link the way people say it, without a scheme.
            if (!url.isEmpty() && !url.contains("://")) url = "https://" + url;
            TaiModelImporter.ValidationResult validation = TaiModelImporter.validateHuggingFaceImportUrl(url);
            if (!validation.supported) {
                TaiImportMessages.Message message = TaiImportMessages.forLink(validation);
                input.setError(context.getString(message.resId, message.args));
                return;
            }
            dialog.dismiss();
            draft = new Draft();
            draft.source = Source.LINK;
            draft.url = url;
            draft.displayName = TaiImportNames.displayName(url);
            draft.capabilities.addAll(TaiImportGuess.capabilities(url));
            if (TaiHuggingFace.parse(url) != null) preview();
            else showSummary();
        }));
        dialog.show();
    }

    /** Asks the manager what the link holds without downloading anything. */
    private void preview() {
        Context context = host.context();
        if (context == null) return;
        Progress waiting = progress(context, null, context.getString(R.string.termux_ai_import_checking_link));
        boolean[] abandoned = {false};
        waiting.dialog.setOnCancelListener(d -> abandoned[0] = true);
        waiting.dialog.show();
        Context app = context.getApplicationContext();
        host.executor().execute(() -> {
            JSONObject result = null;
            try {
                result = TaiManager.getInstance(app).downloadModel(downloadRequest(true).toString());
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = pruneCandidates(result);
            host.handler().post(() -> {
                waiting.dialog.dismiss();
                if (abandoned[0] || host.context() == null) return;
                switch (outcomeOf(finalResult)) {
                    case NEED_TOKEN:
                        showGated();
                        break;
                    case CHOOSE_FILE:
                        showChooseFile(finalResult.optJSONArray("candidates"));
                        break;
                    case SUMMARY:
                        applyCandidate(finalResult.optJSONArray("candidates").optJSONObject(0));
                        showSummary();
                        break;
                    case DOWNLOAD_STARTED:
                        host.modelsChanged();
                        watchDownload();
                        break;
                    default:
                        showError(TaiImportMessages.forResult(finalResult, draft.fileName));
                        break;
                }
            });
        });
    }

    private void showGated() {
        Context context = host.context();
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_import_gated_title)
            .setMessage(R.string.termux_ai_import_gated_message)
            .setPositiveButton(R.string.termux_ai_import_gated_add_token, (d, w) -> promptToken(this::preview))
            .setNeutralButton(R.string.termux_ai_import_gated_open_page, (d, w) -> {
                host.openUrl(TaiImportNames.modelPageUrl(draft.url));
                // The page opens in the browser; when the user comes back the same question stands.
                host.handler().post(this::showGated);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showChooseFile(@Nullable JSONArray candidates) {
        Context context = host.context();
        if (context == null || candidates == null || candidates.length() == 0) return;
        int selected = preselect(candidates, deviceMemoryBytes(context));
        CharSequence[] labels = new CharSequence[candidates.length()];
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            String file = candidate == null ? "" : candidate.optString("file", "");
            long size = candidate == null ? -1L : candidate.optLong("sizeBytes", -1L);
            List<String> notes = new ArrayList<>();
            notes.add(size > 0L ? TaiImportMessages.formatBytes(size) : context.getString(R.string.termux_ai_import_size_unknown));
            TaiImportFit fit = TaiImportFit.check(size, deviceMemoryBytes(context), isEmbeddingFile(file));
            if (i == selected) notes.add(context.getString(R.string.termux_ai_import_variant_recommended));
            else if (fit.verdict == TaiImportFit.Verdict.SLOW) notes.add(context.getString(R.string.termux_ai_import_variant_slow));
            else if (fit.verdict == TaiImportFit.Verdict.TOO_BIG) notes.add(context.getString(R.string.termux_ai_import_variant_too_big));
            notes.add(file);
            int titleRes = TaiImportNames.variantHint(file);
            String title = titleRes != 0 ? capitalize(context.getString(titleRes))
                : context.getString(R.string.termux_ai_import_variant_standard);
            labels[i] = twoLines(context, title, join(notes));
        }
        int[] choice = {selected};
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_import_choose_variant_title)
            .setSingleChoiceItems(labels, selected, (d, which) -> choice[0] = which)
            .setPositiveButton(R.string.termux_ai_import_link_next, (d, w) -> {
                applyCandidate(candidates.optJSONObject(choice[0]));
                showSummary();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void applyCandidate(@Nullable JSONObject candidate) {
        if (candidate == null) return;
        draft.url = candidate.optString("url", draft.url);
        draft.fileName = candidate.optString("file", "");
        draft.sizeBytes = candidate.optLong("sizeBytes", -1L);
        draft.capabilities.clear();
        draft.capabilities.addAll(TaiImportGuess.capabilities(draft.identity()));
    }

    // ---- picker results ----

    void onFileSelected(@Nullable Uri uri) {
        Context context = host.context();
        if (uri == null || context == null) return;
        TaiModelImporter.DocumentMetadata metadata = TaiManager.getInstance(context).modelDocumentMetadata(uri);
        TaiModelImporter.ValidationResult validation =
            TaiModelImporter.validateImportFileNameForBackend(TaiModelSpec.BACKEND_LITERT_LM, metadata.displayName);
        if (!validation.supported) {
            showError(TaiImportMessages.forValidation(validation, metadata.displayName));
            return;
        }
        draft = new Draft();
        draft.source = Source.FILE;
        draft.document = uri;
        draft.metadata = metadata;
        draft.sizeBytes = metadata.sizeBytes;
        draft.displayName = TaiImportNames.displayName(metadata.displayName);
        draft.capabilities.addAll(TaiImportGuess.capabilities(metadata.displayName));
        showSummary();
    }

    void onFolderSelected(@Nullable Uri uri) {
        Context context = host.context();
        if (uri == null || context == null) return;
        draft = new Draft();
        draft.source = Source.FOLDER;
        draft.folder = uri;
        try {
            String rootId = android.provider.DocumentsContract.getTreeDocumentId(uri);
            Uri root = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, rootId);
            draft.metadata = TaiManager.getInstance(context).modelDocumentMetadata(root);
        } catch (RuntimeException e) {
            draft.metadata = null;
        }
        String folderName = draft.metadata == null ? "" : draft.metadata.displayName;
        draft.displayName = TaiImportNames.displayName(folderName);
        draft.capabilities.addAll(TaiImportGuess.capabilities(folderName + " config.json"));
        // A folder's size is not known until its config names the files; the fit is checked
        // after the copy by the load itself.
        showSummary();
    }

    // ---- step 3: the summary card ----

    private void showSummary() {
        Context context = host.context();
        if (context == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout layout = column(context);

        EditText name = new EditText(context);
        name.setSingleLine(true);
        name.setHint(R.string.termux_ai_import_name_hint);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(draft.displayName);
        layout.addView(name);

        layout.addView(label(context, R.string.termux_ai_import_can_do_label));
        TextView chips = new TextView(context);
        chips.setText(chipLine(context, draft.capabilities));
        layout.addView(chips);

        TextView size = new TextView(context);
        size.setPadding(0, Math.round(12 * density), 0, 0);
        size.setText(context.getString(R.string.termux_ai_import_size_label,
            draft.sizeBytes > 0L ? TaiImportMessages.formatBytes(draft.sizeBytes) : context.getString(R.string.termux_ai_import_size_unknown)));
        layout.addView(size);

        layout.addView(label(context, R.string.termux_ai_import_fit_label));
        TextView fitView = new TextView(context);
        TaiImportFit fit = TaiImportFit.check(draft.sizeBytes, deviceMemoryBytes(context), draft.embeddingOnly());
        fitView.setText(fitText(context, fit));
        fitView.setTypeface(Typeface.DEFAULT_BOLD);
        if (fit.verdict == TaiImportFit.Verdict.TOO_BIG) {
            fitView.setTextColor(resolveAttrColor(context, com.termux.shared.R.attr.termuxColorError));
        }
        layout.addView(fitView);

        // Advanced: what the old dialog asked up front, now collapsed and pre-filled.
        TextView advancedToggle = new TextView(context);
        advancedToggle.setText(R.string.termux_ai_import_advanced_show);
        advancedToggle.setTextColor(resolveAttrColor(context, com.termux.shared.R.attr.termuxColorPrimary));
        advancedToggle.setTypeface(Typeface.DEFAULT_BOLD);
        advancedToggle.setPadding(0, Math.round(20 * density), 0, Math.round(8 * density));
        layout.addView(advancedToggle);
        LinearLayout advanced = new LinearLayout(context);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        layout.addView(advanced);
        advancedToggle.setOnClickListener(v -> {
            boolean open = advanced.getVisibility() == View.VISIBLE;
            advanced.setVisibility(open ? View.GONE : View.VISIBLE);
            advancedToggle.setText(open ? R.string.termux_ai_import_advanced_show : R.string.termux_ai_import_advanced_hide);
        });

        boolean rawTflite = draft.metadata != null && isEmbeddingFile(draft.metadata.displayName)
            || isEmbeddingFile(draft.fileName);
        List<CheckBox> boxes = new ArrayList<>();
        String[] keys = capabilityKeys();
        int[] titles = capabilityTitles();
        for (int i = 0; i < keys.length; i++) {
            CheckBox box = new CheckBox(context);
            box.setText(titles[i]);
            box.setTag(keys[i]);
            box.setChecked(draft.capabilities.contains(keys[i]));
            if (rawTflite) {
                // A raw .tflite is only ever served by the embedding runtime.
                box.setChecked(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS.equals(keys[i]));
                box.setEnabled(false);
            }
            box.setOnCheckedChangeListener((button, checked) -> {
                captureCapabilities(boxes);
                chips.setText(chipLine(context, draft.capabilities));
            });
            boxes.add(box);
            advanced.addView(box);
        }

        advanced.addView(label(context, R.string.termux_ai_import_processor_label));
        Spinner processor = new Spinner(context);
        ArrayAdapter<CharSequence> processorAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
            new CharSequence[]{context.getString(R.string.termux_ai_import_processor_auto),
                context.getString(R.string.termux_ai_import_processor_cpu),
                context.getString(R.string.termux_ai_import_processor_gpu)});
        processorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        processor.setAdapter(processorAdapter);
        processor.setSelection(draft.processor.ordinal());
        advanced.addView(processor);

        Button profileButton = new Button(context);
        profileButton.setText(R.string.termux_ai_import_profile);
        profileButton.setOnClickListener(v -> TaiImportProfileDialog.show(context, runtimeProfile(),
            profile -> draft.customProfile = profile));
        advanced.addView(profileButton);

        EditText internalName = new EditText(context);
        internalName.setSingleLine(true);
        internalName.setHint(R.string.termux_ai_import_internal_name_hint);
        internalName.setInputType(InputType.TYPE_CLASS_TEXT);
        internalName.setText(draft.internalName);
        advanced.addView(internalName);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_import_summary_title)
            .setView(dialogScroll(context, layout))
            .setPositiveButton(R.string.termux_ai_import_add_action, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            draft.displayName = name.getText().toString().trim();
            if (draft.displayName.isEmpty()) {
                name.setError(context.getString(R.string.termux_ai_import_name_missing));
                return;
            }
            draft.internalName = internalName.getText().toString().trim();
            draft.processor = Processor.values()[processor.getSelectedItemPosition()];
            captureCapabilities(boxes);
            if (draft.capabilities.isEmpty()) {
                AppNotice.show(context, R.string.termux_ai_import_no_capability, true);
                return;
            }
            if (new TaiModelStore(context).getUserModel(modelId()) != null) {
                name.setError(context.getString(R.string.termux_ai_import_err_exists));
                return;
            }
            dialog.dismiss();
            if (fit.verdict == TaiImportFit.Verdict.TOO_BIG) confirmTooBig();
            else startAdding();
        }));
        dialog.show();
    }

    private void confirmTooBig() {
        Context context = host.context();
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_import_too_big_confirm_title)
            .setMessage(R.string.termux_ai_import_too_big_confirm_message)
            .setPositiveButton(R.string.termux_ai_import_add_anyway, (d, w) -> startAdding())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void captureCapabilities(@NonNull List<CheckBox> boxes) {
        draft.capabilities.clear();
        for (CheckBox box : boxes) {
            if (box.isChecked()) draft.capabilities.add((String) box.getTag());
        }
        if (TaiImportGuess.qwenThinking(draft.identity() + " " + modelId())) {
            draft.capabilities.add("reasoning");
            draft.capabilities.add(TaiModelSpec.CAPABILITY_LLM_THINKING);
        }
    }

    @NonNull
    private String modelId() {
        String explicit = TaiModelImporter.sanitizeModelId(draft.internalName);
        if (!explicit.isEmpty()) return explicit;
        String fromName = TaiImportNames.modelId(draft.displayName);
        if (!fromName.isEmpty()) return fromName;
        return TaiImportNames.modelId(draft.identity().isEmpty() ? "model" : draft.identity());
    }

    /** The profile the model is stored with: the processor choice on top of the known defaults. */
    @NonNull
    private TaiModelProfile runtimeProfile() {
        List<String> accelerators;
        switch (draft.processor) {
            case CPU: accelerators = Collections.singletonList("cpu"); break;
            case GPU: accelerators = Arrays.asList("gpu", "cpu"); break;
            default: accelerators = TaiImportGuess.accelerators(draft.identity()); break;
        }
        TaiModelProfile defaults = new TaiModelProfile(Collections.singletonList("cpu"),
            1024, 64, 0.95d, 1.0d, null, "edge-gallery-import-default");
        if (TaiImportGuess.qwenThinking(draft.identity())) {
            defaults = new TaiModelProfile(Arrays.asList("gpu", "cpu"), 2048, 64,
                0.95d, 1.0d, 3, TaiModelProfile.SOURCE_LITERT_COMMUNITY,
                TaiModelProfile.THINKING_ALWAYS, "<think>", "</think>");
        }
        if (draft.customProfile != null) defaults = draft.customProfile;
        return new TaiModelProfile(accelerators, defaults.defaultMaxTokens, defaults.defaultTopK,
            defaults.defaultTopP, defaults.defaultTemperature, defaults.minDeviceMemoryInGb,
            draft.customProfile == null ? "import-dialog-selection" : "user-artifact-profile",
            defaults.thinkingMode, defaults.thinkingChannelStart, defaults.thinkingChannelEnd, defaults.maxContextTokens);
    }

    @NonNull
    private JSONObject downloadRequest(boolean previewOnly) throws JSONException {
        JSONObject request = new JSONObject();
        String modelId = modelId();
        request.put("modelId", modelId);
        request.put("displayName", draft.displayName.isEmpty() ? modelId : draft.displayName);
        request.put("url", draft.url);
        request.put("previewOnly", previewOnly);
        request.put("acceptedTerms", true);
        // Backend and format are detected by the manager from the resolved file.
        JSONArray capabilities = new JSONArray();
        for (String capability : draft.capabilities) capabilities.put(capability);
        request.put("capabilities", capabilities);
        request.put("runtimeProfile", runtimeProfile().toJson());
        return request;
    }

    // ---- step 4: progress ----

    private void startAdding() {
        Context context = host.context();
        if (context == null) return;
        Context app = context.getApplicationContext();
        String modelId = modelId();
        String displayName = draft.displayName;
        Draft adding = draft;
        switch (draft.source) {
            case FILE:
            case FOLDER: {
                Progress progress = progress(context, context.getString(R.string.termux_ai_import_adding_title, displayName),
                    context.getString(R.string.termux_ai_import_progress_starting));
                boolean[] cancelled = {false};
                progress.dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                    (d, w) -> cancelled[0] = true);
                progress.dialog.setCancelable(false);
                progress.dialog.show();
                TaiModelImporter.ProgressListener listener = (copied, total) -> {
                    host.handler().post(() -> progress.update(context, copied, total));
                    return !cancelled[0];
                };
                LinkedHashSet<String> capabilities = new LinkedHashSet<>(adding.capabilities);
                TaiModelProfile profile = runtimeProfile();
                host.executor().execute(() -> {
                    JSONObject result = null;
                    try {
                        TaiModelImporter importer = new TaiModelImporter(app, new TaiModelStore(app));
                        result = adding.source == Source.FILE
                            ? importer.importDocument(adding.document, modelId, TaiModelSpec.BACKEND_LITERT_LM,
                                capabilities, profile, displayName, listener)
                            : importer.importMnnDirectory(adding.folder, modelId, capabilities, displayName, listener);
                    } catch (JSONException | RuntimeException ignored) {
                    }
                    JSONObject finalResult = pruneCandidates(result);
                    host.handler().post(() -> {
                        progress.dialog.dismiss();
                        host.modelsChanged();
                        if (host.context() == null) return;
                        if (finalResult != null && finalResult.optBoolean("ok", false)) {
                            showReady(modelId, displayName, adding.embeddingOnly());
                        } else {
                            showError(TaiImportMessages.forResult(finalResult,
                                adding.metadata == null ? adding.fileName : adding.metadata.displayName));
                        }
                    });
                });
                break;
            }
            default:
                host.executor().execute(() -> {
                    JSONObject result = null;
                    try {
                        result = TaiManager.getInstance(app).downloadModel(downloadRequest(false).toString());
                    } catch (JSONException | RuntimeException ignored) {
                    }
                    JSONObject finalResult = pruneCandidates(result);
                    host.handler().post(() -> {
                        if (host.context() == null) return;
                        switch (outcomeOf(finalResult)) {
                            case DOWNLOAD_STARTED:
                                host.modelsChanged();
                                watchDownload();
                                break;
                            case NEED_TOKEN:
                                // Public metadata, gated files: the same sign-in question, then the download again.
                                showGatedForDownload();
                                break;
                            case CHOOSE_FILE:
                            case SUMMARY:
                                applyCandidate(finalResult.optJSONArray("candidates").optJSONObject(0));
                                startAdding();
                                break;
                            default:
                                showError(TaiImportMessages.forResult(finalResult, adding.fileName));
                                break;
                        }
                    });
                });
                break;
        }
    }

    private void showGatedForDownload() {
        Context context = host.context();
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_import_gated_title)
            .setMessage(R.string.termux_ai_import_gated_message)
            .setPositiveButton(R.string.termux_ai_import_gated_add_token, (d, w) -> promptToken(this::startAdding))
            .setNeutralButton(R.string.termux_ai_import_gated_open_page, (d, w) -> {
                host.openUrl(TaiImportNames.modelPageUrl(draft.url));
                host.handler().post(this::showGatedForDownload);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Follows a download the service runs, in the same progress dialog the copies use. */
    private void watchDownload() {
        Context context = host.context();
        if (context == null) return;
        Context app = context.getApplicationContext();
        String modelId = modelId();
        String displayName = draft.displayName;
        boolean embeddingOnly = draft.embeddingOnly();
        Progress progress = progress(context, context.getString(R.string.termux_ai_import_adding_title, displayName),
            context.getString(R.string.termux_ai_import_progress_starting));
        boolean[] watching = {true};
        progress.dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel), (d, w) -> {
            watching[0] = false;
            host.executor().execute(() -> {
                try {
                    TaiManager.getInstance(app).cancelDownload(new JSONObject().put("modelId", modelId).toString());
                } catch (JSONException | RuntimeException ignored) {
                }
                host.handler().post(host::modelsChanged);
            });
        });
        // Hide keeps the download going; the model row shows its progress as before.
        progress.dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.termux_ai_dialog_hide),
            (d, w) -> watching[0] = false);
        progress.dialog.setCancelable(false);
        progress.dialog.show();
        Runnable poll = new Runnable() {
            @Override
            public void run() {
                if (!watching[0] || host.context() == null) return;
                // Read on this thread: the download record is a SharedPreferences read the
                // downloader keeps current every megabyte. Going through host.executor() queued
                // behind the page's runtime calls, and on a busy runtime the dialog sat at 0 % until
                // the file was done.
                JSONObject transfer = findDownload(new TaiModelStore(app).getDownloads(), modelId);
                String status = transfer == null ? "" : transfer.optString("status", "");
                if (TaiModelStore.STATE_INSTALLED.equals(status)) {
                    watching[0] = false;
                    progress.dialog.dismiss();
                    host.modelsChanged();
                    showReady(modelId, displayName, embeddingOnly);
                } else if (TaiModelStore.STATE_FAILED.equals(status)) {
                    watching[0] = false;
                    progress.dialog.dismiss();
                    host.modelsChanged();
                    showError(TaiImportMessages.forDownloadError(transfer.optString("error", "")));
                } else if (TaiModelStore.STATE_CANCELLED.equals(status)) {
                    watching[0] = false;
                    progress.dialog.dismiss();
                    host.modelsChanged();
                    AppNotice.show(host.context(), R.string.termux_ai_import_cancelled, false);
                } else {
                    if (TaiModelStore.STATE_VERIFYING.equals(status)) {
                        progress.text.setText(R.string.termux_ai_import_progress_verifying);
                    } else if (transfer != null) {
                        progress.update(host.context(), transfer.optLong("bytesRead", 0L), transfer.optLong("totalBytes", -1L));
                    }
                    host.handler().postDelayed(this, WATCH_INTERVAL_MS);
                }
            }
        };
        host.handler().postDelayed(poll, WATCH_INTERVAL_MS);
    }

    @Nullable
    private static JSONObject findDownload(@Nullable JSONArray downloads, @NonNull String modelId) {
        if (downloads == null) return null;
        for (int i = downloads.length() - 1; i >= 0; i--) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null && modelId.equals(item.optString("modelId", ""))) return item;
        }
        return null;
    }

    // ---- step 5: ready ----

    private void showReady(@NonNull String modelId, @NonNull String displayName, boolean embeddingOnly) {
        Context context = host.context();
        if (context == null) return;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.termux_ai_import_ready_title, displayName))
            .setMessage(embeddingOnly ? R.string.termux_ai_import_ready_embedding_message : R.string.termux_ai_import_ready_message)
            .setNegativeButton(R.string.termux_ai_import_done_action, null);
        if (!embeddingOnly) {
            builder.setPositiveButton(R.string.termux_ai_import_try_action, null)
                .setNeutralButton(R.string.termux_ai_import_use_default_action, null);
        }
        AlertDialog dialog = builder.create();
        if (!embeddingOnly) {
            dialog.setOnShowListener(d -> {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                    dialog.dismiss();
                    tryIt(modelId, displayName, null);
                });
                Button useDefault = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
                useDefault.setOnClickListener(v -> {
                    host.setDefaultModel(modelId);
                    useDefault.setText(R.string.termux_ai_model_active_action);
                    useDefault.setEnabled(false);
                });
            });
        }
        dialog.show();
    }

    /** Loads the model and asks it one short question; a plain failure names the retry. */
    private void tryIt(@NonNull String modelId, @NonNull String displayName, @Nullable String accelerator) {
        Context context = host.context();
        if (context == null) return;
        Context app = context.getApplicationContext();
        Progress progress = progress(context, null, context.getString(R.string.termux_ai_import_loading, displayName));
        progress.dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
            (d, w) -> host.executor().execute(() -> {
                try {
                    TaiManager.getInstance(app).cancelRuntime();
                } catch (JSONException | RuntimeException ignored) {
                }
            }));
        boolean[] abandoned = {false};
        progress.dialog.setOnDismissListener(d -> abandoned[0] = true);
        progress.dialog.show();
        host.executor().execute(() -> {
            JSONObject load = null;
            JSONObject chat = null;
            try {
                JSONObject request = new JSONObject().put("model", modelId);
                if (accelerator != null) request.put("accelerator", accelerator);
                load = TaiManager.getInstance(app).loadModel(request.toString());
                if (load.optBoolean("ok", false)) {
                    host.handler().post(() -> progress.text.setText(R.string.termux_ai_import_asking));
                    JSONObject body = new JSONObject().put("model", modelId).put("max_tokens", 96).put("stream", false)
                        .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", TRY_PROMPT)));
                    if (accelerator != null) body.put("accelerator", accelerator);
                    chat = TaiManager.getInstance(app).openAiChatCompletions(body.toString(), TRY_TIMEOUT_MS);
                }
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalLoad = load;
            JSONObject finalChat = chat;
            host.handler().post(() -> {
                boolean cancelled = abandoned[0];
                progress.dialog.dismiss();
                host.modelsChanged();
                if (cancelled || host.context() == null) return;
                if (finalLoad == null || !finalLoad.optBoolean("ok", false)) {
                    showTryFailure(modelId, displayName, accelerator, finalLoad);
                    return;
                }
                if (finalChat == null || finalChat.has("error") && !finalChat.optBoolean("ok", true)) {
                    showTryFailure(modelId, displayName, accelerator, finalChat);
                    return;
                }
                showReply(modelId, displayName, replyText(finalChat));
            });
        });
    }

    @NonNull
    static String replyText(@NonNull JSONObject completion) {
        JSONArray choices = completion.optJSONArray("choices");
        JSONObject first = choices == null ? null : choices.optJSONObject(0);
        JSONObject message = first == null ? null : first.optJSONObject("message");
        String content = message == null || message.isNull("content") ? "" : message.optString("content", "");
        return content.trim();
    }

    private void showReply(@NonNull String modelId, @NonNull String displayName, @NonNull String reply) {
        Context context = host.context();
        if (context == null) return;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.termux_ai_import_reply_title, displayName))
            .setMessage(reply.isEmpty() ? context.getString(R.string.termux_ai_import_reply_empty) : reply)
            .setNegativeButton(R.string.termux_ai_import_done_action, null);
        if (reply.isEmpty()) {
            builder.setNeutralButton(R.string.termux_ai_import_retry_action, (d, w) -> tryIt(modelId, displayName, null));
        }
        boolean isDefault = modelId.equals(new TaiSettings(context).getDefaultAssistantModel());
        if (!isDefault) {
            builder.setPositiveButton(R.string.termux_ai_import_use_default_action, (d, w) -> host.setDefaultModel(modelId));
        }
        builder.show();
    }

    private void showTryFailure(@NonNull String modelId, @NonNull String displayName,
                                @Nullable String requestedAccelerator, @Nullable JSONObject result) {
        Context context = host.context();
        if (context == null) return;
        boolean offerCpu = !"cpu".equals(requestedAccelerator) && gpuFailure(result);
        int messageRes = offerCpu ? R.string.termux_ai_import_try_failed_gpu
            : memoryFailure(result) ? R.string.termux_ai_import_try_failed_memory
            : R.string.termux_ai_import_try_failed;
        String detail = result == null ? null : result.optString("message", "");
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_import_try_failed_title)
            .setMessage(messageRes)
            .setPositiveButton(offerCpu ? R.string.termux_ai_import_retry_cpu_action : R.string.termux_ai_import_retry_action,
                (d, w) -> tryIt(modelId, displayName, offerCpu ? "cpu" : requestedAccelerator))
            .setNegativeButton(R.string.termux_ai_import_done_action, null);
        if (detail != null && !detail.trim().isEmpty()) {
            builder.setNeutralButton(R.string.termux_ai_import_show_details, (d, w) -> showDetails(detail));
        }
        builder.show();
    }

    // ---- errors and the token prompt ----

    private void showError(@NonNull TaiImportMessages.Message message) {
        Context context = host.context();
        if (context == null) return;
        if (message.resId == R.string.termux_ai_import_cancelled) {
            AppNotice.show(context, R.string.termux_ai_import_cancelled, false);
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_import_err_title)
            .setMessage(context.getString(message.resId, message.args))
            .setPositiveButton(android.R.string.ok, null);
        if (message.detail != null) {
            builder.setNeutralButton(R.string.termux_ai_import_show_details, (d, w) -> showDetails(message.detail));
        }
        builder.show();
    }

    private void showDetails(@NonNull String detail) {
        Context context = host.context();
        if (context == null) return;
        TextView text = new TextView(context);
        text.setText(detail);
        text.setTextIsSelectable(true);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout layout = column(context);
        layout.addView(text);
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_import_details_title)
            .setView(dialogScroll(context, layout))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    /** Asks for a Hugging Face token, saves it, then runs {@code onSaved}. */
    private void promptToken(@NonNull Runnable onSaved) {
        Context context = host.context();
        if (context == null) return;
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.termux_ai_huggingface_token_title);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(new TaiSettings(context).getHuggingFaceToken());
        input.setSelectAllOnFocus(true);
        LinearLayout layout = column(context);
        TextView message = new TextView(context);
        message.setText(R.string.termux_ai_huggingface_token_dialog_message);
        layout.addView(message);
        layout.addView(input);
        layout.addView(hint(context, R.string.termux_ai_huggingface_token_permissions_hint));
        layout.addView(hint(context, R.string.termux_ai_huggingface_token_gated_hint));
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_huggingface_token_title)
            .setView(dialogScroll(context, layout))
            .setPositiveButton(R.string.termux_ai_dialog_save, (d, w) -> {
                new TaiSettings(context).setHuggingFaceToken(input.getText().toString().trim());
                onSaved.run();
            })
            .setNeutralButton(R.string.termux_ai_huggingface_token_get_action,
                (d, w) -> host.openUrl("https://huggingface.co/settings/tokens"))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ---- view helpers ----

    /** A progress dialog: a line of text over a bar, with whatever buttons the step adds before showing it. */
    private static final class Progress {
        final AlertDialog dialog;
        final ProgressBar bar;
        final TextView text;

        Progress(AlertDialog dialog, ProgressBar bar, TextView text) {
            this.dialog = dialog;
            this.bar = bar;
            this.text = text;
        }

        void update(@Nullable Context context, long done, long total) {
            if (context == null) return;
            if (total > 0L) {
                bar.setIndeterminate(false);
                bar.setProgress((int) Math.min(10000L, done * 10000L / total));
                text.setText(context.getString(R.string.termux_ai_import_progress,
                    TaiImportMessages.formatBytes(done), TaiImportMessages.formatBytes(total),
                    String.format(Locale.US, "%d%%", Math.min(100L, done * 100L / total))));
            } else {
                bar.setIndeterminate(true);
                text.setText(context.getString(R.string.termux_ai_import_progress_unknown_total, TaiImportMessages.formatBytes(done)));
            }
        }
    }

    /** Builds, but does not show, a progress dialog; the step adds its buttons first. */
    @NonNull
    private static Progress progress(@NonNull Context context, @Nullable String title, @NonNull String text) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout layout = column(context);
        TextView status = new TextView(context);
        status.setText(text);
        status.setPadding(0, 0, 0, Math.round(12 * density));
        layout.addView(status);
        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(10000);
        bar.setIndeterminate(true);
        layout.addView(bar);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context).setView(layout);
        if (title != null) builder.setTitle(title);
        return new Progress(builder.create(), bar, status);
    }

    private long deviceMemoryBytes(@NonNull Context context) {
        if (deviceMemoryBytes < 0L) deviceMemoryBytes = TaiDeviceCapabilities.detect(context).memoryBytes;
        return deviceMemoryBytes;
    }

    @NonNull
    private static String fitText(@NonNull Context context, @NonNull TaiImportFit fit) {
        switch (fit.verdict) {
            case YES: return context.getString(R.string.termux_ai_import_fit_yes);
            case SLOW: return context.getString(R.string.termux_ai_import_fit_slow, fit.neededGb, fit.deviceGb);
            case TOO_BIG: return context.getString(R.string.termux_ai_import_fit_too_big, fit.neededGb, fit.deviceGb);
            default: return context.getString(R.string.termux_ai_import_fit_unknown);
        }
    }

    @NonNull
    private static String[] capabilityKeys() {
        return new String[]{TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS,
            TaiModelSpec.CAPABILITY_IMAGE_INPUT, TaiModelSpec.CAPABILITY_AUDIO_INPUT, TaiModelSpec.CAPABILITY_TOOL_USE,
            TaiModelSpec.CAPABILITY_CODE, "reasoning", "multilingual"};
    }

    @NonNull
    private static int[] capabilityTitles() {
        return new int[]{R.string.termux_ai_import_cap_chat, R.string.termux_ai_import_cap_embeddings,
            R.string.termux_ai_import_cap_image, R.string.termux_ai_import_cap_audio, R.string.termux_ai_import_cap_tools,
            R.string.termux_ai_import_cap_code, R.string.termux_ai_import_cap_reasoning, R.string.termux_ai_import_cap_multilingual};
    }

    /** "Chat · Understands images": the guessed capabilities in plain words. */
    @NonNull
    static String chipLine(@NonNull Context context, @NonNull java.util.Set<String> capabilities) {
        String[] keys = capabilityKeys();
        int[] titles = capabilityTitles();
        List<String> chips = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            if (capabilities.contains(keys[i])) chips.add(context.getString(titles[i]));
        }
        return join(chips);
    }

    @NonNull
    private static String join(@NonNull List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (out.length() > 0) out.append(" · ");
            out.append(part);
        }
        return out.toString();
    }

    @NonNull
    private static LinearLayout column(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(24 * density);
        layout.setPadding(padH, Math.round(8 * density), padH, 0);
        return layout;
    }

    @NonNull
    private static ScrollView dialogScroll(@NonNull Context context, @NonNull View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        return scroll;
    }

    /** One of step 1's two big choices: a title with one line of help under it. */
    @NonNull
    private static TextView choice(@NonNull Context context, int titleRes, int hintRes, @NonNull View.OnClickListener listener) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView view = new TextView(context);
        view.setText(twoLines(context, context.getString(titleRes), context.getString(hintRes)));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setPadding(Math.round(12 * density), Math.round(14 * density), Math.round(12 * density), Math.round(14 * density));
        TypedValue background = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
        view.setBackgroundResource(background.resourceId);
        view.setClickable(true);
        view.setOnClickListener(listener);
        return view;
    }

    @NonNull
    private static TextView label(@NonNull Context context, int textRes) {
        TextView label = new TextView(context);
        label.setText(textRes);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        float density = context.getResources().getDisplayMetrics().density;
        label.setPadding(0, Math.round(12 * density), 0, Math.round(4 * density));
        return label;
    }

    @NonNull
    private static TextView hint(@NonNull Context context, int textRes) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView hint = new TextView(context);
        hint.setText(textRes);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(resolveAttrColor(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        hint.setPadding(0, Math.round(10 * density), 0, 0);
        return hint;
    }

    @NonNull
    private static String capitalize(@NonNull String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** A label with a smaller, dimmer second line saying what the choice means. */
    @NonNull
    private static CharSequence twoLines(@NonNull Context context, @NonNull String main, @NonNull String hint) {
        SpannableStringBuilder text = new SpannableStringBuilder(main).append('\n');
        int start = text.length();
        text.append(hint);
        text.setSpan(new RelativeSizeSpan(0.85f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, value, true)) {
            text.setSpan(new ForegroundColorSpan(value.data), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private static int resolveAttrColor(@NonNull Context context, int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) return value.data;
        return 0xFF000000;
    }
}
