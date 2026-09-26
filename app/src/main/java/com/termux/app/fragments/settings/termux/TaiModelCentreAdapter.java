package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.ai.TaiDownloadHub;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Model centre's list: link bar, Downloads, follow-up banners, the segmented control, the
 * segment's rows and the simultaneous-downloads setting, as one RecyclerView. The fragment
 * rebuilds the whole item list on every hub push and hands it to {@link #submit}; DiffUtil keeps
 * every row whose content is unchanged bound as it is, and a row whose content changed is rebound
 * in place (change animations are off), so a 5 Hz progress tick moves one bar and nothing else.
 */
final class TaiModelCentreAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    static final int TYPE_LINK = 0;
    static final int TYPE_SECTION = 1;
    static final int TYPE_DOWNLOAD = 2;
    static final int TYPE_BANNER = 3;
    static final int TYPE_SEGMENTS = 4;
    static final int TYPE_MODEL = 5;
    static final int TYPE_EMPTY = 6;
    static final int TYPE_SETTING = 7;

    /** What a tap on the list asks the fragment to do. */
    interface Callbacks {
        void onLinkAdd(@NonNull String text, @NonNull View source);
        void onLinkFile();
        void onLinkMore(@NonNull View anchor);
        void onLinkTextChanged(@NonNull String text);
        void onSegmentSelected(int index);
        void onDownloadAction(@NonNull TaiDownloadHub.Snapshot snapshot, @NonNull TaiModelCentreRows.Action action,
                              @NonNull View source);
        void onInstall(@NonNull ModelRow row, @NonNull View source);
        void onModelMenu(@NonNull ModelRow row, @NonNull View anchor);
        void onBannerAction(@NonNull Banner banner);
        void onBannerDismiss(@NonNull Banner banner);
        void onParallelSelected(int parallel);
    }

    /** One entry of the list. {@link #signature} is what DiffUtil compares for "same content". */
    static final class Item {
        final int type;
        @NonNull final String key;
        @NonNull final String signature;
        @Nullable final Object data;
        /** Slide in on first bind (a download that just started, a banner that just appeared). */
        final boolean arrive;

        Item(int type, @NonNull String key, @NonNull String signature, @Nullable Object data, boolean arrive) {
            this.type = type;
            this.key = key;
            this.signature = signature;
            this.data = data;
            this.arrive = arrive;
        }
    }

    static final class LinkBar {
        @NonNull final String text;
        @NonNull final String error;

        LinkBar(@NonNull String text, @NonNull String error) {
            this.text = text;
            this.error = error;
        }
    }

    static final class Section {
        @NonNull final String title;
        @NonNull final String end;
        @NonNull final String sub;

        Section(@NonNull String title, @NonNull String end, @NonNull String sub) {
            this.title = title;
            this.end = end;
            this.sub = sub;
        }
    }

    static final class DownloadRow {
        @NonNull final TaiDownloadHub.Snapshot snapshot;
        @NonNull final TaiModelCentreRows.State state;
        @NonNull final String title;
        @NonNull final String subtitle;
        final boolean speech;

        DownloadRow(@NonNull TaiDownloadHub.Snapshot snapshot, @NonNull TaiModelCentreRows.State state,
                    @NonNull String title, @NonNull String subtitle, boolean speech) {
            this.snapshot = snapshot;
            this.state = state;
            this.title = title;
            this.subtitle = subtitle;
            this.speech = speech;
        }
    }

    /** An installed model or a catalogue entry, as one card row. */
    static final class ModelRow {
        @NonNull final String modelId;
        final boolean speech;
        @Nullable final TaiModelSpec installed;
        @Nullable final TaiModelCatalog.CatalogEntry entry;
        @NonNull String title = "";
        @NonNull String subtitle = "";
        @NonNull String pillPrimary = "";
        @NonNull TaiModelCentreRows.Tone tonePrimary = TaiModelCentreRows.Tone.ACCENT;
        @NonNull String pillSecondary = "";
        boolean installable;
        boolean installing;
        @NonNull String note = "";
        boolean noteIsError;

        ModelRow(@NonNull String modelId, boolean speech, @Nullable TaiModelSpec installed,
                 @Nullable TaiModelCatalog.CatalogEntry entry) {
            this.modelId = modelId;
            this.speech = speech;
            this.installed = installed;
            this.entry = entry;
        }

        @NonNull
        String signature() {
            return title + '|' + subtitle + '|' + pillPrimary + '|' + tonePrimary + '|' + pillSecondary + '|'
                + installable + '|' + installing + '|' + note + '|' + noteIsError;
        }
    }

    static final class Banner {
        @NonNull final String modelId;
        final boolean speech;
        @NonNull final String text;
        /** Empty when there is nothing left to do (the model is already the default, or in use). */
        @NonNull final String action;

        Banner(@NonNull String modelId, boolean speech, @NonNull String text, @NonNull String action) {
            this.modelId = modelId;
            this.speech = speech;
            this.text = text;
            this.action = action;
        }
    }

    static final class Empty {
        @NonNull final String title;
        @NonNull final String summary;

        Empty(@NonNull String title, @NonNull String summary) {
            this.title = title;
            this.summary = summary;
        }
    }

    static final class Segments {
        final int selected;
        @NonNull final CharSequence[] labels;

        Segments(int selected, @NonNull CharSequence[] labels) {
            this.selected = selected;
            this.labels = labels;
        }
    }

    private final Callbacks callbacks;
    private List<Item> items = new ArrayList<>();
    /** Keys that already played their arrival, so a rebind of the same row never replays it. */
    private final Set<String> arrived = new HashSet<>();

    TaiModelCentreAdapter(@NonNull Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    void submit(@NonNull List<Item> next) {
        List<Item> previous = items;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previous.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                Item a = previous.get(oldPosition);
                Item b = next.get(newPosition);
                return a.type == b.type && a.key.equals(b.key);
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return previous.get(oldPosition).signature.equals(next.get(newPosition).signature);
            }
        }, true);
        items = next;
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_LINK: return new LinkHolder(inflater.inflate(R.layout.item_tai_centre_link_bar, parent, false));
            case TYPE_SECTION: return new SectionHolder(inflater.inflate(R.layout.item_tai_centre_section, parent, false));
            case TYPE_DOWNLOAD: return new DownloadHolder(inflater.inflate(R.layout.item_tai_centre_download, parent, false));
            case TYPE_BANNER: return new BannerHolder(inflater.inflate(R.layout.item_tai_centre_banner, parent, false));
            case TYPE_SEGMENTS: return new SegmentsHolder(inflater.inflate(R.layout.item_tai_centre_segments, parent, false));
            case TYPE_MODEL: return new ModelHolder(inflater.inflate(R.layout.item_tai_centre_model, parent, false));
            case TYPE_SETTING: return new SettingHolder(inflater.inflate(R.layout.item_tai_centre_setting, parent, false));
            default: return new EmptyHolder(inflater.inflate(R.layout.item_tai_centre_empty, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = items.get(position);
        if (holder instanceof LinkHolder) ((LinkHolder) holder).bind((LinkBar) item.data);
        else if (holder instanceof SectionHolder) ((SectionHolder) holder).bind((Section) item.data);
        else if (holder instanceof DownloadHolder) ((DownloadHolder) holder).bind((DownloadRow) item.data);
        else if (holder instanceof BannerHolder) ((BannerHolder) holder).bind((Banner) item.data);
        else if (holder instanceof SegmentsHolder) ((SegmentsHolder) holder).bind((Segments) item.data);
        else if (holder instanceof ModelHolder) ((ModelHolder) holder).bind((ModelRow) item.data);
        else if (holder instanceof SettingHolder) ((SettingHolder) holder).bind((Integer) item.data);
        else if (holder instanceof EmptyHolder) ((EmptyHolder) holder).bind((Empty) item.data);
        if (item.arrive && arrived.add(item.type + ":" + item.key)) {
            View target = holder.itemView.findViewById(item.type == TYPE_BANNER ? R.id.tai_centre_banner : R.id.tai_centre_shell);
            // The inner card moves, not the item view: the RecyclerView's own add animation runs
            // on the item view, and two animators on one view cancel each other.
            if (target != null) TaiMotion.arrive(target);
        }
    }

    /** Lets a row that left and came back (a download cancelled and retried) arrive again. */
    void forgetArrival(int type, @NonNull String key) {
        arrived.remove(type + ":" + key);
    }

    // ---- colours ----

    static int color(@NonNull Context context, int attr) {
        TypedValue value = new TypedValue();
        return context.getTheme().resolveAttribute(attr, value, true) ? value.data : 0xFF808080;
    }

    /** A pill in one of the tones, on the theme's container colours so light and dark both hold. */
    static void tonePill(@NonNull TextView pill, @NonNull TaiModelCentreRows.Tone tone) {
        Context context = pill.getContext();
        int background;
        int text;
        switch (tone) {
            case ACCENT:
                background = com.termux.shared.R.attr.termuxColorPrimaryContainer;
                text = com.termux.shared.R.attr.termuxColorOnPrimaryContainer;
                break;
            case WARN:
                background = com.termux.shared.R.attr.termuxColorTertiaryContainer;
                text = com.termux.shared.R.attr.termuxColorOnTertiaryContainer;
                break;
            case ERROR:
                background = com.termux.shared.R.attr.termuxColorErrorContainer;
                text = com.termux.shared.R.attr.termuxColorOnErrorContainer;
                break;
            default:
                background = com.termux.shared.R.attr.termuxColorSurfacePanel;
                text = com.termux.shared.R.attr.termuxColorOnSurfaceVariant;
                break;
        }
        pill.setBackgroundTintList(ColorStateList.valueOf(color(context, background)));
        pill.setTextColor(color(context, text));
    }

    /** The one loud pill style: Install, Add, Start now, Retry, the banner's follow-up. */
    static void goPill(@NonNull TextView pill) {
        Context context = pill.getContext();
        pill.setBackgroundTintList(ColorStateList.valueOf(color(context, com.termux.shared.R.attr.termuxColorPrimary)));
        pill.setTextColor(color(context, com.termux.shared.R.attr.termuxColorOnPrimary));
    }

    /** The quiet pill style: File, and the Start now pill of a waiting row. */
    static void ghostPill(@NonNull TextView pill) {
        Context context = pill.getContext();
        pill.setBackgroundTintList(ColorStateList.valueOf(color(context, com.termux.shared.R.attr.termuxColorSurfacePanelHighest)));
        pill.setTextColor(color(context, com.termux.shared.R.attr.termuxColorOnSurface));
    }

    static void roundButton(@NonNull ImageButton button) {
        Context context = button.getContext();
        button.setBackgroundTintList(ColorStateList.valueOf(color(context, com.termux.shared.R.attr.termuxColorSurfacePanelHighest)));
        button.setImageTintList(ColorStateList.valueOf(color(context, com.termux.shared.R.attr.termuxColorOnSurface)));
    }

    private static void setText(@NonNull TextView view, @NonNull CharSequence text) {
        // Rebinding the same text is not free (a relayout, and on Nothing OS a ghost cursor over
        // the last glyph of a button label); skip it when nothing changed.
        if (!text.toString().contentEquals(view.getText())) view.setText(text);
        view.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    // ---- holders ----

    private final class LinkHolder extends RecyclerView.ViewHolder {
        final EditText input;
        final TextView file;
        final TextView add;
        final ImageButton more;
        final TextView error;

        LinkHolder(@NonNull View view) {
            super(view);
            input = view.findViewById(R.id.tai_centre_link_input);
            file = view.findViewById(R.id.tai_centre_link_file);
            add = view.findViewById(R.id.tai_centre_link_add);
            more = view.findViewById(R.id.tai_centre_link_more);
            error = view.findViewById(R.id.tai_centre_link_error);
            ghostPill(file);
            goPill(add);
            roundButton(more);
            file.setOnClickListener(v -> callbacks.onLinkFile());
            add.setOnClickListener(v -> callbacks.onLinkAdd(input.getText().toString(), v));
            more.setOnClickListener(callbacks::onLinkMore);
            input.setOnEditorActionListener((v, actionId, event) -> {
                boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP;
                if (go) callbacks.onLinkAdd(input.getText().toString(), add);
                return go;
            });
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    callbacks.onLinkTextChanged(s.toString());
                }
            });
        }

        void bind(@NonNull LinkBar bar) {
            // The field is the truth while someone types; only a cleared link (after Add) resets it.
            if (!bar.text.contentEquals(input.getText())) input.setText(bar.text);
            setText(error, bar.error);
        }
    }

    private static final class SectionHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView end;
        final TextView sub;

        SectionHolder(@NonNull View view) {
            super(view);
            title = view.findViewById(R.id.tai_centre_section_title);
            end = view.findViewById(R.id.tai_centre_section_end);
            sub = view.findViewById(R.id.tai_centre_section_sub);
        }

        void bind(@NonNull Section section) {
            setText(title, section.title);
            setText(end, section.end);
            setText(sub, section.sub);
        }
    }

    private final class DownloadHolder extends RecyclerView.ViewHolder {
        final ImageView kind;
        final TextView title;
        final TextView subtitle;
        final TextView pill;
        final TextView textAction;
        final ImageButton toggle;
        final ImageButton cancel;
        final LinearProgressIndicator bar;
        final TextView metaStart;
        final TextView metaEnd;
        @Nullable DownloadRow row;
        @Nullable String boundId;
        @Nullable TaiModelCentreRows.Action boundToggle;
        @Nullable TaiModelCentreRows.Bar boundBar;

        DownloadHolder(@NonNull View view) {
            super(view);
            kind = view.findViewById(R.id.tai_centre_kind_icon);
            title = view.findViewById(R.id.tai_centre_title);
            subtitle = view.findViewById(R.id.tai_centre_subtitle);
            pill = view.findViewById(R.id.tai_centre_state_pill);
            textAction = view.findViewById(R.id.tai_centre_text_action);
            toggle = view.findViewById(R.id.tai_centre_toggle);
            cancel = view.findViewById(R.id.tai_centre_cancel);
            bar = view.findViewById(R.id.tai_centre_bar);
            metaStart = view.findViewById(R.id.tai_centre_meta_start);
            metaEnd = view.findViewById(R.id.tai_centre_meta_end);
            roundButton(toggle);
            roundButton(cancel);
            kind.setImageTintList(ColorStateList.valueOf(color(view.getContext(), com.termux.shared.R.attr.termuxColorOnSurface)));
            // Listeners read the row at tap time, so a rebind never has to swap them.
            toggle.setOnClickListener(v -> {
                if (row != null && boundToggle != null) callbacks.onDownloadAction(row.snapshot, boundToggle, v);
            });
            cancel.setOnClickListener(v -> {
                if (row != null) callbacks.onDownloadAction(row.snapshot, TaiModelCentreRows.Action.CANCEL, v);
            });
            textAction.setOnClickListener(v -> {
                if (row == null) return;
                TaiModelCentreRows.Action action = row.state.actions.contains(TaiModelCentreRows.Action.RETRY)
                    ? TaiModelCentreRows.Action.RETRY : TaiModelCentreRows.Action.START_NOW;
                callbacks.onDownloadAction(row.snapshot, action, v);
            });
        }

        void bind(@NonNull DownloadRow next) {
            Context context = itemView.getContext();
            boolean sameRow = next.snapshot.id.equals(boundId);
            row = next;
            boundId = next.snapshot.id;
            TaiModelCentreRows.State state = next.state;
            kind.setImageResource(next.speech ? R.drawable.ic_tai_wave : R.drawable.ic_tai_chat);
            setText(title, next.title);
            setText(subtitle, next.subtitle);
            setText(pill, state.pill);
            if (!state.pill.isEmpty()) tonePill(pill, state.tone);

            boolean startNow = state.actions.contains(TaiModelCentreRows.Action.START_NOW);
            boolean retry = state.actions.contains(TaiModelCentreRows.Action.RETRY);
            setText(textAction, startNow ? context.getString(R.string.tai_centre_action_start_now)
                : retry ? context.getString(R.string.tai_centre_action_retry) : "");
            if (retry) goPill(textAction);
            else if (startNow) ghostPill(textAction);

            TaiModelCentreRows.Action toggleAction = state.actions.contains(TaiModelCentreRows.Action.PAUSE)
                ? TaiModelCentreRows.Action.PAUSE
                : state.actions.contains(TaiModelCentreRows.Action.RESUME) ? TaiModelCentreRows.Action.RESUME : null;
            toggle.setVisibility(toggleAction == null ? View.GONE : View.VISIBLE);
            if (toggleAction != null) {
                boolean pause = toggleAction == TaiModelCentreRows.Action.PAUSE;
                toggle.setImageResource(pause ? R.drawable.ic_tai_pause : R.drawable.ic_tai_play);
                toggle.setContentDescription(context.getString(pause ? R.string.tai_centre_action_pause
                    : R.string.tai_centre_action_resume) + " " + next.title);
                // Pause and play morph into each other on the same row; a recycled holder just shows it.
                if (sameRow && boundToggle != null && boundToggle != toggleAction) TaiMotion.morph(toggle);
            }
            boundToggle = toggleAction;
            cancel.setVisibility(state.actions.contains(TaiModelCentreRows.Action.CANCEL) ? View.VISIBLE : View.GONE);
            cancel.setContentDescription(context.getString(R.string.tai_centre_action_cancel) + " " + next.title);

            bindBar(context, state, sameRow);
            setText(metaStart, state.metaStart);
            setText(metaEnd, state.metaEnd);
        }

        private void bindBar(@NonNull Context context, @NonNull TaiModelCentreRows.State state, boolean sameRow) {
            if (state.bar == TaiModelCentreRows.Bar.NONE) {
                bar.setVisibility(View.GONE);
                boundBar = state.bar;
                return;
            }
            int indicator;
            switch (state.phase) {
                case PAUSED: indicator = com.termux.shared.R.attr.termuxColorOnTertiaryContainer; break;
                case WAITING: indicator = com.termux.shared.R.attr.termuxColorOnSurfaceVariant; break;
                default: indicator = com.termux.shared.R.attr.termuxColorPrimary; break;
            }
            bar.setIndicatorColor(color(context, indicator));
            bar.setTrackColor(color(context, com.termux.shared.R.attr.termuxColorSurfacePanel));
            boolean indeterminate = state.bar == TaiModelCentreRows.Bar.INDETERMINATE;
            if (bar.isIndeterminate() != indeterminate) {
                // Switching mode while shown restarts the drawable mid-frame; hide it for the swap.
                bar.setVisibility(View.INVISIBLE);
                bar.setIndeterminate(indeterminate);
            }
            bar.setVisibility(View.VISIBLE);
            if (!indeterminate) {
                // Only a forward move on the same row eases; anything else (a recycled holder, a
                // retry that starts lower) jumps, so the bar never runs backwards.
                boolean animate = sameRow && boundBar == TaiModelCentreRows.Bar.DETERMINATE
                    && state.progress >= bar.getProgress() && !TaiMotion.reduced(context);
                bar.setProgressCompat(state.progress, animate);
            }
            boundBar = state.bar;
        }
    }

    private final class BannerHolder extends RecyclerView.ViewHolder {
        final View strip;
        final ImageView icon;
        final TextView text;
        final TextView action;
        final ImageButton dismiss;
        @Nullable Banner banner;

        BannerHolder(@NonNull View view) {
            super(view);
            strip = view.findViewById(R.id.tai_centre_banner);
            icon = view.findViewById(R.id.tai_centre_banner_icon);
            text = view.findViewById(R.id.tai_centre_banner_text);
            action = view.findViewById(R.id.tai_centre_banner_action);
            dismiss = view.findViewById(R.id.tai_centre_banner_dismiss);
            Context context = view.getContext();
            int on = color(context, com.termux.shared.R.attr.termuxColorOnPrimaryContainer);
            strip.setBackgroundTintList(ColorStateList.valueOf(color(context, com.termux.shared.R.attr.termuxColorPrimaryContainer)));
            icon.setImageTintList(ColorStateList.valueOf(on));
            dismiss.setImageTintList(ColorStateList.valueOf(on));
            text.setTextColor(on);
            goPill(action);
            action.setOnClickListener(v -> {
                if (banner != null) callbacks.onBannerAction(banner);
            });
            dismiss.setOnClickListener(v -> {
                if (banner != null) callbacks.onBannerDismiss(banner);
            });
        }

        void bind(@NonNull Banner next) {
            banner = next;
            setText(text, next.text);
            setText(action, next.action);
        }
    }

    private final class SegmentsHolder extends RecyclerView.ViewHolder {
        final TaiSegmentedTabs tabs;

        SegmentsHolder(@NonNull View view) {
            super(view);
            tabs = view.findViewById(R.id.tai_centre_segments);
            tabs.setOnSegmentSelectedListener(callbacks::onSegmentSelected);
        }

        void bind(@NonNull Segments segments) {
            tabs.setLabels(segments.labels);
            // The tap already slid the thumb; a rebind for the same choice leaves it where it is.
            tabs.select(segments.selected, tabs.selectedIndex() >= 0);
        }
    }

    private final class ModelHolder extends RecyclerView.ViewHolder {
        final View core;
        final ImageView kind;
        final TextView title;
        final TextView subtitle;
        final TextView pillPrimary;
        final TextView pillSecondary;
        final TextView install;
        final CircularProgressIndicator ring;
        final ImageButton more;
        final TextView note;
        @Nullable ModelRow row;

        ModelHolder(@NonNull View view) {
            super(view);
            core = view.findViewById(R.id.tai_centre_core);
            kind = view.findViewById(R.id.tai_centre_kind_icon);
            title = view.findViewById(R.id.tai_centre_title);
            subtitle = view.findViewById(R.id.tai_centre_subtitle);
            pillPrimary = view.findViewById(R.id.tai_centre_pill_primary);
            pillSecondary = view.findViewById(R.id.tai_centre_pill_secondary);
            install = view.findViewById(R.id.tai_centre_install);
            ring = view.findViewById(R.id.tai_centre_install_ring);
            more = view.findViewById(R.id.tai_centre_more);
            note = view.findViewById(R.id.tai_centre_note);
            Context context = view.getContext();
            kind.setImageTintList(ColorStateList.valueOf(color(context, com.termux.shared.R.attr.termuxColorOnSurface)));
            ring.setIndicatorColor(color(context, com.termux.shared.R.attr.termuxColorPrimary));
            goPill(install);
            roundButton(more);
            tonePill(pillSecondary, TaiModelCentreRows.Tone.NEUTRAL);
            install.setOnClickListener(v -> {
                if (row != null) callbacks.onInstall(row, v);
            });
            more.setOnClickListener(v -> {
                if (row != null) callbacks.onModelMenu(row, v);
            });
            core.setOnClickListener(v -> {
                if (row == null) return;
                if (row.installed != null) callbacks.onModelMenu(row, more);
                else if (row.installable && !row.installing) callbacks.onInstall(row, install);
            });
        }

        void bind(@NonNull ModelRow next) {
            Context context = itemView.getContext();
            row = next;
            kind.setImageResource(next.speech ? R.drawable.ic_tai_wave : R.drawable.ic_tai_chat);
            setText(title, next.title);
            setText(subtitle, next.subtitle);
            setText(pillPrimary, next.pillPrimary);
            if (!next.pillPrimary.isEmpty()) tonePill(pillPrimary, next.tonePrimary);
            setText(pillSecondary, next.pillSecondary);
            install.setVisibility(next.installable && !next.installing ? View.VISIBLE : View.GONE);
            install.setContentDescription(context.getString(R.string.tai_centre_action_install) + " " + next.title);
            ring.setVisibility(next.installing ? View.VISIBLE : View.GONE);
            more.setVisibility(next.installed != null ? View.VISIBLE : View.GONE);
            more.setContentDescription(context.getString(R.string.tai_centre_action_more, next.title));
            setText(note, next.note);
            note.setTextColor(color(context, next.noteIsError ? com.termux.shared.R.attr.termuxColorError
                : com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        }
    }

    private final class SettingHolder extends RecyclerView.ViewHolder {
        final TaiSegmentedTabs tabs;

        SettingHolder(@NonNull View view) {
            super(view);
            tabs = view.findViewById(R.id.tai_centre_parallel);
            tabs.setLabels("1", "2", "3");
            tabs.setOnSegmentSelectedListener(index -> callbacks.onParallelSelected(index + 1));
        }

        void bind(@NonNull Integer parallel) {
            tabs.select(Math.max(0, Math.min(2, parallel - 1)), tabs.selectedIndex() >= 0);
        }
    }

    private static final class EmptyHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView summary;

        EmptyHolder(@NonNull View view) {
            super(view);
            title = view.findViewById(R.id.tai_centre_empty_title);
            summary = view.findViewById(R.id.tai_centre_empty_summary);
        }

        void bind(@NonNull Empty empty) {
            setText(title, empty.title);
            setText(summary, empty.summary);
        }
    }
}
