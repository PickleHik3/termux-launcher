package com.termux.app.assistant;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.termux.R;

import java.util.Map;

/**
 * The Model sheet (sheet 03): the two chat models with one radio for the launcher's default
 * assistant model, each row's state, and the one action that fits it. It watches
 * {@link AssistantModelStatus} while it is open and keeps no state of its own besides the failures
 * the page remembers from the actions that failed.
 */
final class AssistantModelSheet implements AssistantModelStatus.Observer {

    /** What the sheet asks of the page. */
    interface Host {
        @NonNull Map<String, AssistantModelPresenter.Failure> failures();

        void choose(@NonNull String modelId);

        void download(@NonNull String modelId);

        void cancelDownload(@NonNull String modelId);

        void unload();

        void loadAgain(@NonNull String modelId);

        void freeUpSpace();

        void dismissed();
    }

    private final class RowViews {
        final String modelId;
        final LinearLayout root;
        final MaterialRadioButton radio;
        final TextView name;
        final TextView desc;
        final TextView state;
        final LinearProgressIndicator progress;
        final MaterialButton action;
        @Nullable Runnable onAction;

        RowViews(Context context, String modelId, int nameRes, int descRes) {
            this.modelId = modelId;
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setMinimumHeight(AssistantUi.dp(context, 88));
            root.setPadding(0, AssistantUi.dp(context, 8), 0, AssistantUi.dp(context, 8));
            radio = new MaterialRadioButton(context);
            radio.setMinWidth(AssistantUi.dp(context, 48));
            radio.setMinHeight(AssistantUi.dp(context, 48));
            root.addView(radio, new LinearLayout.LayoutParams(AssistantUi.dp(context, 48), AssistantUi.dp(context, 48)));
            LinearLayout text = new LinearLayout(context);
            text.setOrientation(LinearLayout.VERTICAL);
            name = AssistantUi.text(context);
            name.setText(nameRes);
            desc = AssistantUi.text(context);
            desc.setText(descRes);
            state = AssistantUi.text(context);
            progress = new LinearProgressIndicator(context);
            progress.setIndeterminate(false);
            progress.setMax(100);
            progress.setTrackThickness(AssistantUi.dp(context, 4));
            progress.setTrackCornerRadius(AssistantUi.dp(context, 2));
            text.addView(name);
            text.addView(desc);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.topMargin = AssistantUi.dp(context, 4);
            text.addView(state, sp);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            pp.topMargin = AssistantUi.dp(context, 8);
            text.addView(progress, pp);
            root.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            action = new MaterialButton(context);
            action.setMinHeight(AssistantUi.dp(context, 40));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            ap.setMarginStart(AssistantUi.dp(context, 8));
            root.addView(action, ap);
            action.setOnClickListener(v -> {
                if (onAction != null) onAction.run();
            });
            radio.setOnClickListener(v -> host.choose(modelId));
        }
    }

    private final Context context;
    private final AssistantModelStatus status;
    private final Host host;
    private final BottomSheetDialog dialog;
    private final RowViews e2b;
    private final RowViews e4b;
    private AssistantUi.Palette palette;
    @Nullable private AssistantModelStatus.Snapshot last;

    AssistantModelSheet(@NonNull Context context, @NonNull AssistantModelStatus status,
                        @NonNull AssistantUi.Palette palette, @NonNull Host host) {
        this.context = context;
        this.status = status;
        this.palette = palette;
        this.host = host;
        dialog = new BottomSheetDialog(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int side = AssistantUi.dp(context, 16);
        content.setPadding(side, AssistantUi.dp(context, 8), side, AssistantUi.dp(context, 28));
        TextView title = AssistantUi.text(context);
        title.setText(R.string.assistant_model_sheet_title);
        AssistantUi.type(title, 22f, 28f, false, palette.onSurface);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMargins(AssistantUi.dp(context, 8), AssistantUi.dp(context, 8), AssistantUi.dp(context, 8),
            AssistantUi.dp(context, 8));
        content.addView(title, tp);
        e2b = new RowViews(context, AssistantModelStatus.MODEL_E2B, R.string.assistant_model_e2b,
            R.string.assistant_model_e2b_desc);
        e4b = new RowViews(context, AssistantModelStatus.MODEL_E4B, R.string.assistant_model_e4b,
            R.string.assistant_model_e4b_desc);
        content.addView(e2b.root);
        View divider = new View(context);
        divider.setBackgroundColor(palette.outlineVariant);
        content.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        content.addView(e4b.root);
        TextView storage = AssistantUi.text(context);
        storage.setText(R.string.assistant_model_note_storage);
        AssistantUi.bodySmall(storage, palette.onSurfaceVariant);
        LinearLayout.LayoutParams n1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        n1.setMargins(AssistantUi.dp(context, 8), AssistantUi.dp(context, 12), AssistantUi.dp(context, 8), 0);
        content.addView(storage, n1);
        TextView shared = AssistantUi.text(context);
        shared.setText(R.string.assistant_model_note_shared);
        AssistantUi.bodySmall(shared, palette.onSurfaceVariant);
        LinearLayout.LayoutParams n2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        n2.setMargins(AssistantUi.dp(context, 8), AssistantUi.dp(context, 4), AssistantUi.dp(context, 8), 0);
        content.addView(shared, n2);
        dialog.setContentView(content);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.setOnDismissListener(d -> {
            status.removeObserver(this);
            host.dismissed();
        });
    }

    void show() {
        status.addObserver(this);
        dialog.show();
    }

    void dismiss() {
        dialog.dismiss();
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    /** The failures changed (an action failed or cleared): redraw from the last state. */
    void refresh() {
        if (last != null) onModelStatus(last);
    }

    @Override
    public void onModelStatus(@NonNull AssistantModelStatus.Snapshot snapshot) {
        last = snapshot;
        String selected = snapshot.defaultModel.modelId;
        bind(e2b, snapshot.gemmaE2b.state, AssistantModelStatus.sameModel(selected, e2b.modelId), snapshot);
        bind(e4b, snapshot.gemmaE4b.state, AssistantModelStatus.sameModel(selected, e4b.modelId), snapshot);
    }

    private void bind(@NonNull RowViews row, @NonNull AssistantModelStatus.State state, boolean selected,
                      @NonNull AssistantModelStatus.Snapshot snapshot) {
        AssistantModelPresenter.Failure failure = host.failures().get(row.modelId);
        if (failure == null) failure = AssistantModelPresenter.Failure.NONE;
        if (state.kind == AssistantModelStatus.Kind.DOWNLOADING || state.kind == AssistantModelStatus.Kind.READY) {
            failure = AssistantModelPresenter.Failure.NONE;
        }
        AssistantUi.label(row.name, palette.onSurface);
        AssistantUi.bodySmall(row.desc, palette.onSurfaceVariant);
        boolean canSelect = AssistantModelPresenter.selectable(state);
        row.radio.setChecked(selected && canSelect);
        row.radio.setEnabled(canSelect);
        row.radio.setButtonTintList(new ColorStateList(
            new int[][] {{-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}},
            new int[] {AssistantUi.disabledInk(palette), palette.primary, palette.onSurfaceVariant}));
        // The state line, with the failure in error ink.
        AssistantUi.meta(row.state, palette.onSurfaceVariant);
        CharSequence stateText;
        boolean error = false;
        switch (failure) {
            case DOWNLOAD_FAILED: stateText = context.getString(R.string.assistant_model_failed); error = true; break;
            case NO_SPACE: stateText = context.getString(R.string.assistant_model_no_space); error = true; break;
            case NO_MEMORY: stateText = context.getString(R.string.assistant_model_no_memory); error = true; break;
            default: {
                int res = AssistantModelPresenter.rowState(state, selected);
                stateText = state.kind == AssistantModelStatus.Kind.DOWNLOADING && state.percent >= 0
                    ? context.getString(res, state.percent) : context.getString(res);
            }
        }
        if (error) row.state.setTextColor(palette.error);
        row.state.setText(stateText);
        boolean showProgress = state.kind == AssistantModelStatus.Kind.DOWNLOADING && state.percent >= 0;
        AssistantUi.setVisible(row.progress, showProgress);
        if (showProgress) {
            row.progress.setIndicatorColor(palette.primary);
            row.progress.setTrackColor(ColorUtils.setAlphaComponent(palette.primary, 61));
            row.progress.setProgressCompat(state.percent, false);
        }
        row.root.setContentDescription(context.getString(R.string.assistant_model_row_description,
            context.getString(AssistantModelPresenter.fullName(row.modelId)),
            context.getString(row.modelId.equals(AssistantModelStatus.MODEL_E4B)
                ? R.string.assistant_model_e4b_desc : R.string.assistant_model_e2b_desc), stateText));
        // The one action that fits.
        row.onAction = null;
        int label = 0;
        int style = 0; // 0 tonal, 1 outlined, 2 text
        switch (failure) {
            case DOWNLOAD_FAILED:
                label = R.string.assistant_try_again;
                row.onAction = () -> host.download(row.modelId);
                break;
            case NO_SPACE:
                label = R.string.assistant_free_up_space;
                row.onAction = host::freeUpSpace;
                break;
            case NO_MEMORY:
                label = R.string.assistant_try_again;
                row.onAction = () -> host.loadAgain(row.modelId);
                break;
            default:
                switch (state.kind) {
                    case NOT_DOWNLOADED:
                    case NONE:
                        label = R.string.assistant_download;
                        row.onAction = () -> host.download(row.modelId);
                        break;
                    case DOWNLOADING:
                        label = R.string.assistant_cancel;
                        style = 2;
                        row.onAction = () -> host.cancelDownload(row.modelId);
                        break;
                    case READY:
                        label = R.string.assistant_model_unload;
                        style = 1;
                        row.onAction = host::unload;
                        break;
                    case DOWNLOADED:
                        if (!selected && failureElsewhere(snapshot)) {
                            label = R.string.assistant_model_use_this;
                            style = 2;
                            row.onAction = () -> host.choose(row.modelId);
                        }
                        break;
                    default:
                        break;
                }
        }
        AssistantUi.setVisible(row.action, label != 0);
        if (label == 0) return;
        row.action.setText(label);
        if (style == 0) {
            row.action.setBackgroundTintList(ColorStateList.valueOf(palette.secondaryContainer));
            row.action.setTextColor(palette.onSecondaryContainer);
            row.action.setStrokeWidth(0);
        } else if (style == 1) {
            row.action.setBackgroundTintList(ColorStateList.valueOf(0));
            row.action.setTextColor(palette.onSurface);
            row.action.setStrokeColor(ColorStateList.valueOf(palette.outlineVariant));
            row.action.setStrokeWidth(AssistantUi.dp(context, 1));
        } else {
            row.action.setBackgroundTintList(ColorStateList.valueOf(0));
            row.action.setTextColor(palette.primary);
            row.action.setStrokeWidth(0);
        }
    }

    /** "Use this one" is offered on the other downloaded row only after a memory error. */
    private boolean failureElsewhere(@NonNull AssistantModelStatus.Snapshot snapshot) {
        return host.failures().containsValue(AssistantModelPresenter.Failure.NO_MEMORY);
    }
}
