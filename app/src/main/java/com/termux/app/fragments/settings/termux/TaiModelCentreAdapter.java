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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
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
        /** The speed pill was tapped: open that model's benchmark result. */
        void onModelBenchmark(@NonNull ModelRow row);
        /** "Add token" under a catalogue row's note: open the token dialog for that model. */
        void onAddToken(@NonNull ModelRow row);
        /** "Add token" on a failed download: open the token dialog, then retry that download. */
        void onAddToken(@NonNull TaiDownloadHub.Snapshot snapshot);
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
        /** The row's inference backend ("LiteRT", "MNN"), quieter than pillPrimary/pillSecondary. */
        @NonNull String pillBackend = "";
        /** The chat model's best recent writing speed ("21 tok/s"), as quiet as pillBackend; empty with no benchmark result. */
        @NonNull String pillSpeed = "";
        boolean installable;
        boolean installing;
        @NonNull String note = "";
        boolean noteIsError;
        /** The note is Hugging Face asking for a token: offer "Add token" under it. */
        boolean tokenAction;
        /** A speech-output (voice) model: its own menu and install path, the wave icon like speech. */
        boolean voiceOutput;
        /** A text-to-image model: the picture icon, and a menu with Delete only. */
        boolean image;
        /** A wallpaper vision graph: the picture icon, and a menu with "Use for depth maps" and Delete only. */
        boolean vision;
        /** Brought into view from a deep link: ringed for a moment so the eye finds it. */
        boolean highlighted;

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
                + pillBackend + '|' + pillSpeed + '|' + installable + '|' + installing + '|' + note + '|' + noteIsError + '|' + tokenAction + '|' + highlighted;
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

    /** The list position of the model row for {@code modelId}, or -1 when this list has none. */
    int positionOfModel(@NonNull String modelId) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.type == TYPE_MODEL && item.key.equals(modelId)) return i;
        }
        return -1;
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

    /** A Material colour role (colorPrimary, colorOnSurfaceVariant, ...) in the view's theme. */
    static int role(@NonNull Context context, int attr) {
        return MaterialColors.getColor(context, attr, 0xFF808080);
    }

    /**
     * A status chip in one of the tones. The container roles (primary, tertiary, error, and the
     * neutral surface) come from colour-state resources that name the theme's colour roles, so
     * light and dark both hold. A plain TextView pill (a layout outside the Model centre still
     * on the old pill) gets the same roles as tints.
     */
    static void tonePill(@NonNull TextView pill, @NonNull TaiModelCentreRows.Tone tone) {
        Context context = pill.getContext();
        int background;
        int text;
        switch (tone) {
            case ACCENT:
                background = R.color.tai_chip_bg_accent;
                text = R.color.tai_chip_fg_accent;
                break;
            case WARN:
                background = R.color.tai_chip_bg_warn;
                text = R.color.tai_chip_fg_warn;
                break;
            case ERROR:
                background = R.color.tai_chip_bg_error;
                text = R.color.tai_chip_fg_error;
                break;
            default:
                background = R.color.tai_chip_bg_neutral;
                text = R.color.tai_chip_fg_neutral;
                break;
        }
        if (pill instanceof Chip) {
            ((Chip) pill).setChipBackgroundColorResource(background);
            pill.setTextColor(AppCompatResources.getColorStateList(context, text));
        } else {
            pill.setBackgroundTintList(AppCompatResources.getColorStateList(context, background));
            pill.setTextColor(AppCompatResources.getColorStateList(context, text));
        }
    }

    /** The loud action style (Install, Add, Retry, the banner's follow-up); a stock button carries it itself. */
    static void goPill(@NonNull TextView pill) {
        if (pill instanceof MaterialButton) return;
        pill.setBackgroundTintList(ColorStateList.valueOf(role(pill.getContext(), androidx.appcompat.R.attr.colorPrimary)));
        pill.setTextColor(role(pill.getContext(), com.google.android.material.R.attr.colorOnPrimary));
    }

    /** The quiet action style (File, Start now, Change window); a stock button carries it itself. */
    static void ghostPill(@NonNull TextView pill) {
        if (pill instanceof MaterialButton) return;
        pill.setBackgroundTintList(ColorStateList.valueOf(role(pill.getContext(), com.google.android.material.R.attr.colorSurfaceContainerHighest)));
        pill.setTextColor(role(pill.getContext(), com.google.android.material.R.attr.colorOnSurface));
    }

    /** The quietest chip of all: a model's backend name. */
    static void backendPill(@NonNull TextView pill) {
        tonePill(pill, TaiModelCentreRows.Tone.NEUTRAL);
    }

    /** Sets a stock icon button's glyph. */
    static void setIcon(@NonNull MaterialButton button, int drawable) {
        button.setIcon(AppCompatResources.getDrawable(button.getContext(), drawable));
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
        final MaterialButton more;
        final TextView error;

        LinkHolder(@NonNull View view) {
            super(view);
            input = view.findViewById(R.id.tai_centre_link_input);
            file = view.findViewById(R.id.tai_centre_link_file);
            add = view.findViewById(R.id.tai_centre_link_add);
            more = view.findViewById(R.id.tai_centre_link_more);
            error = view.findViewById(R.id.tai_centre_link_error);
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

    private static boolean retryNeedsToken(@NonNull DownloadRow row) {
        return row.state.actions.contains(TaiModelCentreRows.Action.RETRY)
            && TaiModelCentreRows.needsToken(row.snapshot.error);
    }

    private final class DownloadHolder extends RecyclerView.ViewHolder {
        final ImageView kind;
        final TextView title;
        final TextView subtitle;
        final TextView pill;
        final TextView textAction;
        final MaterialButton toggle;
        final MaterialButton cancel;
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
            // Listeners read the row at tap time, so a rebind never has to swap them.
            toggle.setOnClickListener(v -> {
                if (row != null && boundToggle != null) callbacks.onDownloadAction(row.snapshot, boundToggle, v);
            });
            cancel.setOnClickListener(v -> {
                if (row != null) callbacks.onDownloadAction(row.snapshot, TaiModelCentreRows.Action.CANCEL, v);
            });
            textAction.setOnClickListener(v -> {
                if (row == null) return;
                if (retryNeedsToken(row)) {
                    callbacks.onAddToken(row.snapshot);
                    return;
                }
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
            kind.setImageResource(next.speech ? R.drawable.ic_tai_wave
                : TaiModelCatalog.visionEntries().containsKey(next.snapshot.modelId) ? R.drawable.ic_tai_image
                : R.drawable.ic_tai_chat);
            setText(title, next.title);
            setText(subtitle, next.subtitle);
            setText(pill, state.pill);
            if (!state.pill.isEmpty()) tonePill(pill, state.tone);

            boolean startNow = state.actions.contains(TaiModelCentreRows.Action.START_NOW);
            boolean retry = state.actions.contains(TaiModelCentreRows.Action.RETRY);
            // A download Hugging Face refused for want of a token swaps Retry for Add token.
            setText(textAction, startNow ? context.getString(R.string.tai_centre_action_start_now)
                : retry ? context.getString(retryNeedsToken(next) ? R.string.tai_centre_action_add_token
                    : R.string.tai_centre_action_retry) : "");

            TaiModelCentreRows.Action toggleAction = state.actions.contains(TaiModelCentreRows.Action.PAUSE)
                ? TaiModelCentreRows.Action.PAUSE
                : state.actions.contains(TaiModelCentreRows.Action.RESUME) ? TaiModelCentreRows.Action.RESUME : null;
            toggle.setVisibility(toggleAction == null ? View.GONE : View.VISIBLE);
            if (toggleAction != null) {
                boolean pause = toggleAction == TaiModelCentreRows.Action.PAUSE;
                setIcon(toggle, pause ? R.drawable.ic_tai_pause : R.drawable.ic_tai_play);
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
                case PAUSED: indicator = com.google.android.material.R.attr.colorTertiary; break;
                case WAITING: indicator = com.google.android.material.R.attr.colorOnSurfaceVariant; break;
                default: indicator = androidx.appcompat.R.attr.colorPrimary; break;
            }
            // Track and thickness are the theme's; only the phase picks the indicator's role.
            bar.setIndicatorColor(role(context, indicator));
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
        final MaterialButton dismiss;
        @Nullable Banner banner;

        BannerHolder(@NonNull View view) {
            super(view);
            strip = view.findViewById(R.id.tai_centre_banner);
            icon = view.findViewById(R.id.tai_centre_banner_icon);
            text = view.findViewById(R.id.tai_centre_banner_text);
            action = view.findViewById(R.id.tai_centre_banner_action);
            dismiss = view.findViewById(R.id.tai_centre_banner_dismiss);
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
        final TextView pillSpeed;
        final TextView pillBackend;
        final TextView pillPrimary;
        final TextView pillSecondary;
        final TextView install;
        final CircularProgressIndicator ring;
        final MaterialButton more;
        final TextView note;
        final TextView noteAction;
        @Nullable ModelRow row;

        ModelHolder(@NonNull View view) {
            super(view);
            core = view.findViewById(R.id.tai_centre_shell);
            kind = view.findViewById(R.id.tai_centre_kind_icon);
            title = view.findViewById(R.id.tai_centre_title);
            subtitle = view.findViewById(R.id.tai_centre_subtitle);
            pillSpeed = view.findViewById(R.id.tai_centre_pill_speed);
            pillBackend = view.findViewById(R.id.tai_centre_pill_backend);
            pillPrimary = view.findViewById(R.id.tai_centre_pill_primary);
            pillSecondary = view.findViewById(R.id.tai_centre_pill_secondary);
            install = view.findViewById(R.id.tai_centre_install);
            ring = view.findViewById(R.id.tai_centre_install_ring);
            more = view.findViewById(R.id.tai_centre_more);
            note = view.findViewById(R.id.tai_centre_note);
            noteAction = view.findViewById(R.id.tai_centre_note_action);
            noteAction.setOnClickListener(v -> {
                if (row != null) callbacks.onAddToken(row);
            });
            tonePill(pillSecondary, TaiModelCentreRows.Tone.NEUTRAL);
            backendPill(pillBackend);
            backendPill(pillSpeed);
            pillSpeed.setOnClickListener(v -> {
                if (row != null) callbacks.onModelBenchmark(row);
            });
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
            kind.setImageResource(next.image || next.vision ? R.drawable.ic_tai_image
                : next.speech ? R.drawable.ic_tai_wave : R.drawable.ic_tai_chat);
            setText(title, next.title);
            setText(subtitle, next.subtitle);
            setText(pillSpeed, next.pillSpeed);
            setText(pillBackend, next.pillBackend);
            setText(pillPrimary, next.pillPrimary);
            if (!next.pillPrimary.isEmpty()) tonePill(pillPrimary, next.tonePrimary);
            setText(pillSecondary, next.pillSecondary);
            install.setVisibility(next.installable && !next.installing ? View.VISIBLE : View.GONE);
            install.setContentDescription(context.getString(R.string.tai_centre_action_install) + " " + next.title);
            ring.setVisibility(next.installing ? View.VISIBLE : View.GONE);
            more.setVisibility(next.installed != null ? View.VISIBLE : View.GONE);
            more.setContentDescription(context.getString(R.string.tai_centre_action_more, next.title));
            if (core instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) core;
                card.setStrokeColor(role(context, androidx.appcompat.R.attr.colorPrimary));
                card.setStrokeWidth(next.highlighted ? Math.round(2 * context.getResources().getDisplayMetrics().density) : 0);
            }
            setText(note, next.note);
            note.setTextColor(role(context, next.noteIsError ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
            setText(noteAction, next.tokenAction ? context.getString(R.string.tai_centre_action_add_token) : "");
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
