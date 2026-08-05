package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * Card-style row for a TAI model: name + status pill, role tag (summary),
 * monospace size/accelerator/memory meta line, and a download progress bar.
 */
public final class TaiModelPreference extends Preference {

    /** Backend tone controls pill color: LiteRT uses primaryContainer, MNN uses tertiaryContainer. */
    public enum BackendTone { LITERT, MNN, NEUTRAL }

    private boolean showProgress;
    private boolean indeterminate;
    private int progress;
    private CharSequence pillText = "";
    private boolean pillAccent;
    private BackendTone backendTone = BackendTone.NEUTRAL;
    private CharSequence metaLine = "";
    private CharSequence primaryActionText = "";
    private boolean primaryActionEnabled = true;
    private boolean primaryActionDestructive;
    private boolean recommended;
    @Nullable private Typeface titleTypeface;
    private View.OnClickListener primaryActionClickListener;
    private CharSequence tuneActionText = "";
    private View.OnClickListener tuneActionClickListener;
    /** The currently bound progress bar, so per-tick updates can skip the rebind entirely. */
    @Nullable private ProgressBar boundProgressBar;

    public TaiModelPreference(@NonNull Context context) {
        super(context);
        setLayoutResource(R.layout.preference_tai_model);
        setIconSpaceReserved(false);
    }

    // Every setter below only calls notifyChanged() when the visible state actually changed.
    // The catalogs refresh all their rows on every download progress tick, and a no-op rebind is
    // not free: Nothing OS (Android 16) flashes a ghost insertion cursor over the last glyph of
    // any button whose text is re-bound on screen.

    public void setDownloadProgress(boolean showProgress, boolean indeterminate, int progress) {
        progress = Math.max(0, Math.min(10000, progress));
        if (this.showProgress == showProgress && this.indeterminate == indeterminate
            && this.progress == progress) return;
        boolean visibilityChanged = this.showProgress != showProgress;
        this.showProgress = showProgress;
        this.indeterminate = indeterminate;
        this.progress = progress;
        // A pure value tick is applied straight to the bound bar: a rebind per megabyte makes
        // the whole list repaint, which flickers (and ghost-cursors on Nothing OS). The tag
        // check guards against the holder having been recycled to another row.
        if (!visibilityChanged && boundProgressBar != null && boundProgressBar.getTag() == this) {
            boundProgressBar.setIndeterminate(indeterminate);
            boundProgressBar.setProgress(progress);
            return;
        }
        notifyChanged();
    }

    public void setPill(@Nullable CharSequence text, boolean accent) {
        CharSequence value = text == null ? "" : text;
        if (TextUtils.equals(this.pillText, value) && this.pillAccent == accent) return;
        this.pillText = value;
        this.pillAccent = accent;
        notifyChanged();
    }

    public void setBackendTone(@NonNull BackendTone tone) {
        if (this.backendTone == tone) return;
        this.backendTone = tone;
        notifyChanged();
    }

    public void setMetaLine(@Nullable CharSequence metaLine) {
        CharSequence value = metaLine == null ? "" : metaLine;
        if (TextUtils.equals(this.metaLine, value)) return;
        this.metaLine = value;
        notifyChanged();
    }

    public void setPrimaryAction(@Nullable CharSequence text, boolean enabled,
                                  @Nullable View.OnClickListener listener) {
        setPrimaryAction(text, enabled, false, listener);
    }

    public void setPrimaryAction(@Nullable CharSequence text, boolean enabled,
                                  boolean destructive, @Nullable View.OnClickListener listener) {
        CharSequence value = text == null ? "" : text;
        // The listener is stored unconditionally but never forces a rebind: the bound view holds
        // a stable trampoline that reads this field at click time.
        this.primaryActionClickListener = listener;
        if (TextUtils.equals(this.primaryActionText, value) && this.primaryActionEnabled == enabled
            && this.primaryActionDestructive == destructive) return;
        this.primaryActionText = value;
        this.primaryActionEnabled = enabled;
        this.primaryActionDestructive = destructive;
        notifyChanged();
    }

    public void setTuneAction(@Nullable CharSequence text, @Nullable View.OnClickListener listener) {
        CharSequence value = text == null ? "" : text;
        this.tuneActionClickListener = listener;
        if (TextUtils.equals(this.tuneActionText, value)) return;
        this.tuneActionText = value;
        notifyChanged();
    }

    /** Shows a small star before the model name for recommended models. */
    public void setRecommended(boolean recommended) {
        if (this.recommended == recommended) return;
        this.recommended = recommended;
        notifyChanged();
    }

    /**
     * Renders the title in the given typeface — the font picker uses the family's own installed
     * regular face so each row previews itself. Null restores the layout's default face.
     */
    public void setTitleTypeface(@Nullable Typeface typeface) {
        if (this.titleTypeface == typeface) return;
        this.titleTypeface = typeface;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title != null) {
            // Holders recycle, so the default face must be restored explicitly when unset.
            title.setTypeface(titleTypeface != null
                ? titleTypeface : Typeface.create("sans-serif-medium", Typeface.NORMAL));
            if (recommended) {
                title.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_star_16, 0, 0, 0);
                title.setCompoundDrawablePadding(dp(5));
                title.setCompoundDrawableTintList(ColorStateList.valueOf(
                    resolveAttrColor(com.termux.shared.R.attr.termuxColorPrimary)));
            } else {
                title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            }
        }

        View view = holder.findViewById(R.id.tai_download_progress);
        if (view instanceof ProgressBar) {
            ProgressBar progressBar = (ProgressBar) view;
            progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            progressBar.setIndeterminate(indeterminate);
            progressBar.setProgress(progress);
            progressBar.setTag(this);
            boundProgressBar = progressBar;
        }

        TextView pill = (TextView) holder.findViewById(R.id.tai_model_pill);
        if (pill != null) {
            if (pillText.length() == 0) {
                pill.setVisibility(View.GONE);
            } else {
                pill.setVisibility(View.VISIBLE);
                if (!pillText.toString().contentEquals(pill.getText())) pill.setText(pillText);
                int pillTextColor;
                int pillBgColor;
                if (pillAccent) {
                    int pillAttr = backendTone == BackendTone.MNN
                        ? com.termux.shared.R.attr.termuxColorTertiaryContainer
                        : com.termux.shared.R.attr.termuxColorPrimaryContainer;
                    int pillOnAttr = backendTone == BackendTone.MNN
                        ? com.termux.shared.R.attr.termuxColorOnTertiaryContainer
                        : com.termux.shared.R.attr.termuxColorOnPrimaryContainer;
                    pillTextColor = resolveAttrColor(pillOnAttr);
                    pillBgColor = resolveAttrColor(pillAttr);
                } else {
                    pillTextColor = resolveAttrColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant);
                    pillBgColor = resolveAttrColor(com.termux.shared.R.attr.termuxColorSurfacePanel);
                }
                pill.setTextColor(pillTextColor);
                pill.setBackgroundTintList(ColorStateList.valueOf(pillBgColor));
            }
        }

        TextView meta = (TextView) holder.findViewById(R.id.tai_model_meta);
        if (meta != null) {
            if (metaLine.length() == 0) {
                meta.setVisibility(View.GONE);
            } else {
                meta.setVisibility(View.VISIBLE);
                meta.setText(metaLine);
            }
        }

        ImageButton tuneAction = (ImageButton) holder.findViewById(R.id.tai_model_tune_action);
        boolean showTune = bindTuneButton(tuneAction, tuneActionText);
        Button primaryAction = (Button) holder.findViewById(R.id.tai_model_primary_action);
        boolean showPrimary = bindActionButton(primaryAction, primaryActionText, primaryActionEnabled);
        if (primaryAction != null && showPrimary) {
            tintPrimaryAction(primaryAction);
        }
        LinearLayout actions = (LinearLayout) holder.findViewById(R.id.tai_model_actions);
        if (actions != null) actions.setVisibility(showTune || showPrimary ? View.VISIBLE : View.GONE);
    }

    private boolean bindActionButton(@Nullable Button button, @NonNull CharSequence text,
                                     boolean enabled) {
        if (button == null) return false;
        if (text.length() == 0) {
            button.setVisibility(View.GONE);
            button.setOnClickListener(null);
            return false;
        }
        button.setVisibility(View.VISIBLE);
        // Only touch the text when it actually changed — see the setter comment above.
        if (!text.toString().contentEquals(button.getText())) button.setText(text);
        button.setEnabled(enabled);
        // Stable trampoline: the current listener is read at click time, so swapping listeners
        // (they capture per-refresh state) never requires re-binding the row.
        button.setOnClickListener(view -> {
            View.OnClickListener current = primaryActionClickListener;
            if (current != null) current.onClick(view);
        });
        return true;
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private void tintPrimaryAction(@NonNull Button button) {
        int backgroundAttr;
        int textAttr;
        if (!button.isEnabled()) {
            backgroundAttr = com.termux.shared.R.attr.termuxColorSurfacePanelHigh;
            textAttr = com.termux.shared.R.attr.termuxColorOnSurfaceVariant;
        } else if (primaryActionDestructive) {
            backgroundAttr = com.termux.shared.R.attr.termuxColorErrorContainer;
            textAttr = com.termux.shared.R.attr.termuxColorOnErrorContainer;
        } else {
            backgroundAttr = com.termux.shared.R.attr.termuxColorPrimaryContainer;
            textAttr = com.termux.shared.R.attr.termuxColorOnPrimaryContainer;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(resolveAttrColor(backgroundAttr)));
        button.setTextColor(resolveAttrColor(textAttr));
    }

    private boolean bindTuneButton(@Nullable ImageButton button, @NonNull CharSequence text) {
        if (button == null) return false;
        if (text.length() == 0) {
            button.setVisibility(View.GONE);
            button.setOnClickListener(null);
            return false;
        }
        button.setVisibility(View.VISIBLE);
        button.setEnabled(true);
        button.setImageTintList(ColorStateList.valueOf(
            resolveAttrColor(com.termux.shared.R.attr.termuxColorOnSurface)));
        button.setBackgroundTintList(ColorStateList.valueOf(
            resolveAttrColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh)));
        button.setOnClickListener(view -> {
            View.OnClickListener current = tuneActionClickListener;
            if (current != null) current.onClick(view);
        });
        return true;
    }

    private int resolveAttrColor(int attr) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant);
    }
}
