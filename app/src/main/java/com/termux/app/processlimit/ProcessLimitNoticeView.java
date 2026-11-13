package com.termux.app.processlimit;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.processlimit.ProcessLimitPolicy.Action;
import com.termux.app.processlimit.ProcessLimitPolicy.Card;
import com.termux.privileged.PrivilegedAvailabilitySignal;
import com.termux.privileged.PrivilegedBackendManager;

import java.util.List;

/**
 * Draws {@link ProcessLimitNotice}'s card wherever it is placed, and is gone while there is none.
 * It listens while attached, so every place shows the same card at the same moment.
 */
public final class ProcessLimitNoticeView extends FrameLayout
    implements ProcessLimitNotice.Listener, PrivilegedAvailabilitySignal.Listener {

    /** What the place holding the card hears. */
    public interface Host {
        /** The card's height may have changed (it came, went, or changed its text). */
        default void onNoticeResized() { }
        /** A button opened another screen; a sheet puts itself away. */
        default void onNoticeLeaving() { }
    }

    @NonNull private final TextView title;
    @NonNull private final TextView body;
    @NonNull private final Button action1;
    @NonNull private final Button action2;
    @NonNull private final Button primary;
    @Nullable private Host host;
    @Nullable private Card shown;

    public ProcessLimitNoticeView(@NonNull Context context) {
        this(context, null);
    }

    public ProcessLimitNoticeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.process_limit_notice, this, true);
        title = findViewById(R.id.process_limit_title);
        body = findViewById(R.id.process_limit_body);
        action1 = findViewById(R.id.process_limit_action_1);
        action2 = findViewById(R.id.process_limit_action_2);
        primary = findViewById(R.id.process_limit_action_primary);
        setVisibility(GONE);
    }

    public void setHost(@Nullable Host host) {
        this.host = host;
    }

    /** Reads the limit again when a notice stands, so one fixed elsewhere goes. */
    public void refreshIfShown() {
        ProcessLimitNotice notice = ProcessLimitNotice.get(getContext());
        if (notice.isPending()) notice.refresh();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ProcessLimitNotice.get(getContext()).addListener(this);
        PrivilegedBackendManager.getInstance().addAvailabilityListener(this);
        render();
    }

    @Override
    protected void onDetachedFromWindow() {
        ProcessLimitNotice.get(getContext()).removeListener(this);
        PrivilegedBackendManager.getInstance().removeAvailabilityListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onProcessLimitChanged() {
        render();
    }

    @Override
    public void onPrivilegedAvailabilityChanged(boolean available) {
        render();
    }

    private void render() {
        Card card = ProcessLimitNotice.get(getContext()).card();
        boolean privileged = PrivilegedBackendManager.getInstance().isPrivilegedAvailableForUi();
        List<Action> actions = ProcessLimitPolicy.actions(card, privileged);
        boolean visible = card != Card.HIDDEN;
        boolean changed = card != shown || (visible != (getVisibility() == VISIBLE));
        shown = card;
        if (visible) {
            setText(title, card == Card.FIXED ? R.string.process_limit_fixed_title : R.string.process_limit_title);
            setText(body, bodyFor(card));
            int n = actions.size();
            bind(action1, n >= 2 ? actions.get(0) : null);
            bind(action2, n >= 3 ? actions.get(1) : null);
            bind(primary, n >= 1 ? actions.get(n - 1) : null);
        }
        int visibility = visible ? VISIBLE : GONE;
        if (getVisibility() != visibility) setVisibility(visibility);
        if (changed && host != null) host.onNoticeResized();
    }

    @StringRes
    private static int bodyFor(@NonNull Card card) {
        switch (card) {
            case WORKING: return R.string.process_limit_working_body;
            case FIXED: return R.string.process_limit_fixed_body;
            case FAILED: return R.string.process_limit_failed_body;
            default: return R.string.process_limit_body;
        }
    }

    private void setText(@NonNull TextView view, @StringRes int text) {
        String value = getContext().getString(text);
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    private void bind(@NonNull Button button, @Nullable Action action) {
        int visibility = action == null ? GONE : VISIBLE;
        if (button.getVisibility() != visibility) button.setVisibility(visibility);
        if (action == null) {
            button.setOnClickListener(null);
            return;
        }
        setText(button, labelFor(action));
        button.setOnClickListener(view -> run(action));
    }

    @StringRes
    private static int labelFor(@NonNull Action action) {
        switch (action) {
            case FIX: return R.string.process_limit_action_fix;
            case HOW_TO_FIX: return R.string.process_limit_action_how_to_fix;
            case DIAGNOSTICS: return R.string.process_limit_action_diagnostics;
            case UNDO: return R.string.process_limit_action_undo;
            case DONE: return R.string.process_limit_action_done;
            default: return R.string.process_limit_action_dismiss;
        }
    }

    private void run(@NonNull Action action) {
        ProcessLimitNotice notice = ProcessLimitNotice.get(getContext());
        switch (action) {
            case FIX: notice.fix(); break;
            case UNDO: notice.revert(null); break;
            case DONE: notice.finishFix(); break;
            case DISMISS: notice.dismiss(); break;
            case HOW_TO_FIX: open(ProcessLimitNotice.guideIntent(getContext())); break;
            case DIAGNOSTICS: open(ProcessLimitNotice.diagnosticsIntent(getContext())); break;
        }
    }

    private void open(@NonNull Intent intent) {
        if (host != null) host.onNoticeLeaving();
        try {
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Both are this app's own screens; nothing else to do if one is gone.
        }
    }
}
