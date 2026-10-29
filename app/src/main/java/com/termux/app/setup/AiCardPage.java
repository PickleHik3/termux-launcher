package com.termux.app.setup;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.ai.TaiDownloadHub;
import com.termux.ai.TaiTierPolicy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The AI card's page: a row per capability with its model and size, ticked by tier, a Wi-Fi only
 * switch, the storage line, "Download selected" with progress, and Done when every ticked model
 * is present. Steps: 0 "Choose capabilities", 1 "Download selected models".
 */
final class AiCardPage implements SetupCardPage {

    private Context mContext;
    private Host mHost;
    private LinearLayout mRoot;
    private TaiTierPolicy.Env mEnv;
    private AiModelRows.Sizes mSizes;
    private List<AiModelRows.Row> mRows = new ArrayList<>();
    private final Set<String> mTicked = new LinkedHashSet<>();
    private boolean mWifiOnly = true;
    private boolean mNeedWifiNote;
    /** The model ids queued by Download; null until then. */
    @Nullable private List<String> mQueued;
    private boolean mFinished;
    @Nullable private TaiDownloadHub.Listener mListener;

    @NonNull
    @Override
    public View createView(@NonNull Context context, @NonNull SetupCards.Card card,
                           @NonNull Host host) {
        mContext = context;
        mHost = host;
        mFinished = false;
        mNeedWifiNote = false;
        mQueued = null;
        mEnv = TaiTierPolicy.Env.forDevice(context);
        mSizes = AiModelDownloads.catalogSizes();
        mWifiOnly = AiModelDownloads.wifiOnly(context);
        mRows = AiModelRows.rows(mEnv, AiModelDownloads.installedIds(context), mSizes);
        mTicked.clear();
        mTicked.addAll(AiModelRows.initialTicks(mRows));
        mRoot = new LinearLayout(context);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        render();
        return mRoot;
    }

    @Override
    public void onHidden() {
        detach();
    }

    private void detach() {
        if (mListener != null && mContext != null) {
            TaiDownloadHub.get(mContext).removeListener(mListener);
        }
        mListener = null;
    }

    private void attach() {
        if (mListener != null) return;
        mListener = this::onSnapshots;
        // addListener posts the current snapshot, so a re-shown page catches up.
        TaiDownloadHub.get(mContext).addListener(mListener);
    }

    private void onSnapshots(@NonNull List<TaiDownloadHub.Snapshot> snapshots) {
        if (mQueued == null || mFinished) return;
        AiModelDownloads.Progress progress = AiModelDownloads.progress(snapshots, mQueued,
            AiModelDownloads.installedIds(mContext), mSizes);
        if (progress.allInstalled) {
            mFinished = true;
            detach();
            mHost.completeCard(SetupCards.AI);
            return;
        }
        render();
    }

    // ------------------------------------------------------------------------------------ views

    private void render() {
        Context context = mContext;
        mRoot.removeAllViews();
        TextView title = SetupUi.text(context, 22f, true, SetupUi.onSurface(mRoot));
        title.setText(R.string.tai_welcome_title);
        mRoot.addView(title, SetupUi.matchWrap(0));

        if (mRows.isEmpty()) {
            TextView none = SetupUi.text(context, 14f, false, SetupUi.onSurfaceVariant(mRoot));
            none.setText(R.string.setup_ai_no_models);
            mRoot.addView(none, SetupUi.matchWrap(SetupUi.dp(context, 8)));
            MaterialButton done = button(R.string.setup_ai_done, true);
            done.setOnClickListener(v -> mHost.completeCard(SetupCards.AI));
            mRoot.addView(done, SetupUi.matchWrap(SetupUi.dp(context, 20)));
            return;
        }

        TextView tier = SetupUi.text(context, 14f, false, SetupUi.onSurfaceVariant(mRoot));
        tier.setText(tierLine());
        mRoot.addView(tier, SetupUi.matchWrap(SetupUi.dp(context, 4)));

        boolean downloading = mQueued != null;
        List<TaiDownloadHub.Snapshot> snapshots = downloading
            ? TaiDownloadHub.get(context).snapshot() : new ArrayList<>();
        Set<String> installed = downloading
            ? AiModelDownloads.installedIds(context) : new LinkedHashSet<>();
        for (AiModelRows.Row row : mRows) {
            mRoot.addView(rowView(row, downloading, snapshots, installed),
                SetupUi.matchWrap(SetupUi.dp(context, 14)));
        }

        long free = context.getFilesDir().getUsableSpace();
        TextView footer = SetupUi.text(context, 13.5f, false, SetupUi.onSurfaceVariant(mRoot));
        AiModelRows.Footer foot = AiModelRows.footer(mRows, mTicked, free);
        if (downloading) {
            AiModelDownloads.Progress p =
                AiModelDownloads.progress(snapshots, mQueued, installed, mSizes);
            footer.setText(context.getString(R.string.setup_ai_footer_progress,
                AiModelRows.formatSize(p.doneBytes), AiModelRows.formatSize(p.totalBytes)));
        } else {
            footer.setText(context.getString(R.string.tai_welcome_footer, foot.selectedText,
                foot.freeText));
        }
        mRoot.addView(footer, SetupUi.matchWrap(SetupUi.dp(context, 16)));
        if (!downloading && foot.disabledReasonRes != 0) {
            TextView reason = SetupUi.text(context, 13f, false, SetupUi.onSurfaceVariant(mRoot));
            reason.setText(foot.disabledReasonRes);
            mRoot.addView(reason, SetupUi.matchWrap(SetupUi.dp(context, 2)));
        }

        if (!downloading) {
            SetupUi.toggleRow(mRoot, R.string.tai_welcome_wifi_only, 0, mWifiOnly, 12,
                (button, checked) -> {
                    mWifiOnly = checked;
                    AiModelDownloads.setWifiOnly(mContext, checked);
                });
            if (mNeedWifiNote) {
                TextView note = SetupUi.text(context, 13f, false, SetupUi.accent(mRoot));
                note.setText(R.string.setup_ai_need_wifi);
                mRoot.addView(note, SetupUi.matchWrap(SetupUi.dp(context, 6)));
            }
            MaterialButton download = button(R.string.tai_welcome_download, true);
            download.setEnabled(foot.downloadEnabled);
            download.setOnClickListener(v -> onDownload());
            mRoot.addView(download, SetupUi.matchWrap(SetupUi.dp(context, 16)));
            MaterialButton later = button(R.string.tai_welcome_later, false);
            later.setOnClickListener(v -> mHost.back());
            mRoot.addView(later, SetupUi.matchWrap(SetupUi.dp(context, 6)));
        } else {
            MaterialButton later = button(R.string.tai_welcome_later, false);
            later.setOnClickListener(v -> mHost.back());
            mRoot.addView(later, SetupUi.matchWrap(SetupUi.dp(context, 16)));
        }
    }

    private void onDownload() {
        List<String> order = AiModelRows.downloadOrder(mRows, mTicked, mSizes);
        AiModelDownloads.Start start = AiModelDownloads.start(mContext, order, mWifiOnly);
        if (start == AiModelDownloads.Start.NEEDS_WIFI) {
            mNeedWifiNote = true;
            render();
            return;
        }
        if (start == AiModelDownloads.Start.NOTHING) return;
        mNeedWifiNote = false;
        mQueued = order;
        mHost.completeStep(SetupCards.AI, 0);
        attach();
        render();
    }

    @NonNull
    private String tierLine() {
        String chip = AiModelRows.chipName(
            Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER : null,
            Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : null, Build.HARDWARE);
        AiModelRows.Header h = AiModelRows.header(mEnv, chip, Build.VERSION.RELEASE);
        return h.chip.isEmpty()
            ? mContext.getString(R.string.tai_welcome_header_no_chip, h.tierNumber, h.ramGb,
                h.androidVersion)
            : mContext.getString(R.string.tai_welcome_header, h.tierNumber, h.ramGb, h.chip,
                h.androidVersion);
    }

    @NonNull
    private MaterialButton button(int textRes, boolean primary) {
        MaterialButton button = primary ? new MaterialButton(mContext)
            : new MaterialButton(mContext, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(textRes);
        button.setAllCaps(false);
        return button;
    }

    @NonNull
    private View rowView(@NonNull AiModelRows.Row row, boolean downloading,
                         @NonNull List<TaiDownloadHub.Snapshot> snapshots,
                         @NonNull Set<String> installedNow) {
        Context context = mContext;
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        ImageView glyph = new ImageView(context);
        glyph.setImageResource(row.glyphRes);
        glyph.setColorFilter(SetupUi.onSurfaceVariant(mRoot));
        int g = SetupUi.dp(context, 24);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(g, g);
        gp.rightMargin = SetupUi.dp(context, 12);
        line.addView(glyph, gp);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = SetupUi.text(context, 16f, true, SetupUi.onSurface(mRoot));
        name.setText(row.titleRes);
        words.addView(name, SetupUi.matchWrap(0));
        TextView model = SetupUi.text(context, 13f, false, SetupUi.onSurfaceVariant(mRoot));
        model.setText(row.modelNames);
        words.addView(model, SetupUi.matchWrap(SetupUi.dp(context, 1)));
        TextView about = SetupUi.text(context, 13f, false, SetupUi.onSurfaceVariant(mRoot));
        about.setText(row.lineRes);
        words.addView(about, SetupUi.matchWrap(SetupUi.dp(context, 1)));

        boolean ticked = mTicked.contains(row.id);
        boolean rowInstalled = row.installed;
        if (downloading && ticked && !row.installed) {
            AiModelDownloads.Progress p = AiModelDownloads.progress(snapshots, row.missingIds,
                installedNow, mSizes);
            rowInstalled = p.allInstalled;
            if (!rowInstalled) {
                ProgressBar bar = new ProgressBar(context, null,
                    android.R.attr.progressBarStyleHorizontal);
                bar.setMax(1000);
                bar.setProgress(Math.round(p.fraction() * 1000f));
                LinearLayout.LayoutParams bp = SetupUi.matchWrap(SetupUi.dp(context, 6));
                bp.height = SetupUi.dp(context, 4);
                words.addView(bar, bp);
                TextView of = SetupUi.text(context, 12f, false, SetupUi.onSurfaceVariant(mRoot));
                of.setText(context.getString(R.string.setup_ai_row_progress,
                    AiModelRows.formatSize(p.doneBytes), AiModelRows.formatSize(p.totalBytes)));
                words.addView(of, SetupUi.matchWrap(SetupUi.dp(context, 2)));
            }
        }
        line.addView(words,
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout side = new LinearLayout(context);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (rowInstalled) {
            TextView ok = SetupUi.text(context, 13f, true, SetupUi.accent(mRoot));
            ok.setText(R.string.tai_welcome_installed);
            side.addView(ok);
        } else {
            TextView size = SetupUi.text(context, 13f, false, SetupUi.onSurfaceVariant(mRoot));
            size.setText(row.sizeText);
            side.addView(size);
            if (!downloading) {
                CheckBox box = new CheckBox(context);
                box.setChecked(ticked);
                box.setContentDescription(context.getString(row.titleRes));
                box.setOnCheckedChangeListener((b, checked) -> {
                    if (checked) mTicked.add(row.id); else mTicked.remove(row.id);
                    render();
                });
                side.addView(box);
                line.setOnClickListener(v -> box.toggle());
            }
        }
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = SetupUi.dp(context, 8);
        line.addView(side, sp);
        return line;
    }
}
