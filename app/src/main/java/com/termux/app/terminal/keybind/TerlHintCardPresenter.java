package com.termux.app.terminal.keybind;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.leader.Leader;
import com.termux.app.terminal.TerminalKeyBindingResolver;

import java.util.EnumSet;
import java.util.List;

/**
 * When the Terl key's hint card is up (ADR 0042; replaces v1's hint strip and ? table under Terl).
 * Holding the leader slides it out: the Terl key held on the launcher keyboard (at once: the
 * keyboard's hold has already waited), the leader held on a hardware keyboard (Ctrl+Alt with the
 * Terl key as leader) or a held Super, both after {@link #HOLD_DELAY_MS} so a quick chord never
 * flashes it. A chord fired while it shows takes it down, and it stays down until every leader is
 * let go; letting go takes it down too. A quick tap of Super is not a hold: it keeps its meaning
 * (the bar's search), which {@link #setSuperHeld} reports to the caller.
 */
public final class TerlHintCardPresenter {

    /** How long a hardware leader or Super is held before the card shows. */
    public static final long HOLD_DELAY_MS = 400L;

    /** What is holding the card up; the header names it. */
    public enum Source { TERL_KEY, HARDWARE, SUPER }

    /** The card on screen. */
    public interface Surface {
        /** Shows (or redraws) the card under {@code leaderName}. */
        void show(@NonNull String leaderName, @NonNull List<TerlHintCard.Section> sections);

        void hide();

        boolean isShowing();
    }

    @NonNull private final Surface mSurface;
    @NonNull private final KeybindHintPresenter.Scheduler mScheduler;
    @NonNull private final KeybindHintPresenter.Hints mHints;
    @NonNull private final KeybindHintModel.Labels mLabels;
    private final EnumSet<Source> mHeld = EnumSet.noneOf(Source.class);
    /** A chord ran under this hold: the card stays down until every leader is let go. */
    private boolean mSpent;
    /** The card showed during the current Super hold, so its release is no tap. */
    private boolean mShownForSuper;
    @Nullable private Source mPendingSource;

    private final Runnable mShowLater = () -> {
        Source source = mPendingSource;
        mPendingSource = null;
        if (source != null && mHeld.contains(source)) show(source);
    };

    public TerlHintCardPresenter(@NonNull Surface surface,
                                 @NonNull KeybindHintPresenter.Scheduler scheduler,
                                 @NonNull KeybindHintPresenter.Hints hints,
                                 @NonNull KeybindHintModel.Labels labels) {
        mSurface = surface;
        mScheduler = scheduler;
        mHints = hints;
        mLabels = labels;
    }

    /** The Terl key held past the keyboard's hold ({@code true}) or let go. */
    public void setTerlKeyHeld(boolean held) {
        if (held) {
            mHeld.add(Source.TERL_KEY);
            show(Source.TERL_KEY);
        } else {
            release(Source.TERL_KEY);
        }
    }

    /** A hardware keyboard holds the leader (or a prefix leader waits for its key), or not. */
    public void setHardwareLeaderHeld(boolean held) {
        if (held == mHeld.contains(Source.HARDWARE)) return;
        if (held) {
            mHeld.add(Source.HARDWARE);
            showAfterHold(Source.HARDWARE);
        } else {
            release(Source.HARDWARE);
        }
    }

    /**
     * Super pressed ({@code true}) or let go. On the release, answers whether the card showed
     * while it was held: then the release is no tap and the bar's search stays shut.
     */
    public boolean setSuperHeld(boolean held) {
        if (held) {
            if (mHeld.add(Source.SUPER)) {
                mShownForSuper = false;
                showAfterHold(Source.SUPER);
            }
            return false;
        }
        boolean showed = mShownForSuper;
        mShownForSuper = false;
        release(Source.SUPER);
        return showed;
    }

    /** Whether Super is held now (its keys fire leader chords while it is). */
    public boolean isSuperHeld() {
        return mHeld.contains(Source.SUPER);
    }

    /** A chord ran: the card goes at once and stays down for the rest of this hold. */
    public void onConsumed() {
        mSpent = !mHeld.isEmpty();
        cancelPending();
        if (mSurface.isShowing()) mSurface.hide();
    }

    /** {@code ?} under the leader: the card shows, or goes when it is up. */
    public void toggle() {
        if (mSurface.isShowing()) {
            mSurface.hide();
            return;
        }
        mSpent = false;
        show(mHeld.contains(Source.SUPER) ? Source.SUPER
            : mHeld.contains(Source.HARDWARE) ? Source.HARDWARE : Source.TERL_KEY);
    }

    public boolean isVisible() {
        return mSurface.isShowing();
    }

    /** Takes the card down and forgets every hold (the window lost focus, the desktop went). */
    public void reset() {
        mHeld.clear();
        mSpent = false;
        mShownForSuper = false;
        cancelPending();
        if (mSurface.isShowing()) mSurface.hide();
    }

    private void showAfterHold(@NonNull Source source) {
        cancelPending();
        mPendingSource = source;
        mScheduler.postDelayed(mShowLater, HOLD_DELAY_MS);
    }

    private void cancelPending() {
        mPendingSource = null;
        mScheduler.remove(mShowLater);
    }

    private void release(@NonNull Source source) {
        mHeld.remove(source);
        if (mPendingSource == source) cancelPending();
        if (!mHeld.isEmpty()) return;
        mSpent = false;
        cancelPending();
        if (mSurface.isShowing()) mSurface.hide();
    }

    private void show(@NonNull Source source) {
        if (mSpent) return;
        List<TerlHintCard.Section> sections = TerlHintCard.build(
            mHints.hintsForPrefix(TerminalKeyBindingResolver.LEADER_HINT_PREFIX),
            mHints.hintsForPrefix(TerminalKeyBindingResolver.LEADER_HINT_PREFIX + "shift+"),
            mLabels);
        if (sections.isEmpty()) return;
        if (source == Source.SUPER) mShownForSuper = true;
        mSurface.show(leaderName(source, mHints.leader()), sections);
    }

    /**
     * The card's header: the key being held. The Terl key is "Terl"; a hardware keyboard types the
     * leader as Ctrl+Alt (the Terl key's stand-in there) or as the Custom chord; Super is "Super".
     */
    @NonNull
    public static String leaderName(@NonNull Source source, @NonNull Leader leader) {
        switch (source) {
            case TERL_KEY: return "Terl";
            case SUPER: return "Super";
            default:
                if (leader.chord == null) return "Ctrl+Alt";
                return KeybindHintModel.prefixLabel(leader.chord.isPrefix()
                    ? leader.chord.spec() + ">" : leader.chord.modifierPrefix());
        }
    }
}
