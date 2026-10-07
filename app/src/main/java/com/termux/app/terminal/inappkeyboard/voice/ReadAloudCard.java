package com.termux.app.terminal.inappkeyboard.voice;

import android.app.Activity;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiReadAloud;
import com.termux.ai.TaiSettings;
import com.termux.app.notice.AppNotice;

/**
 * Read aloud from the terminal's selection toolbar, with something to see: the dictation card's
 * shell ({@link VoiceListeningIndicator#showReading}) holding the selected text, the sentence being
 * heard marked as {@link TaiReadAloud} sends it, with Pause, the voice and Stop.
 *
 * <p>The card is its own {@link VoiceListeningIndicator}, so a dictation card that is up stays as
 * it is; both remember the same place, so a reading card opens where the dictation card was left
 * (and drags its place along with it), and lies on top of a dictation card that is up there.
 *
 * <p>Stop, the × and a swipe stop the reading and close the card at once. A reading stopped from
 * anywhere else (the toolbar's "Stop reading", {@code tai speak --stop}, dawn) closes it too, as
 * does a failure, which goes to {@link AppNotice}. A reading heard through says "Done" for a
 * moment and closes on its own; a touch in that moment holds it a little longer.
 *
 * <p>Pause holds the voice mid-word ({@link TaiReadAloud#pause}). A voice picked on the card is
 * saved as the default ({@link TaiSettings#setTtsVoice}) and heard from the next sentence, since
 * the reading reads the setting at the start of each one.
 */
public final class ReadAloudCard {

    /** How long "Done" stays before the card closes itself. */
    private static final long DONE_CLOSE_MS = 1_600L;

    private final Activity activity;
    private final View anchor;
    private final VoiceListeningIndicator indicator;
    /** Bumped by every start and close, so a reading that ends late does not touch a newer card. */
    private int session;
    private boolean done;
    private final Runnable closeAfterDone = this::dismiss;

    public ReadAloudCard(@NonNull Activity activity, @NonNull View anchor,
                         @NonNull VoiceListeningIndicator.PositionMemory memory) {
        this.activity = activity;
        this.anchor = anchor;
        this.indicator = new VoiceListeningIndicator(activity, anchor, memory);
    }

    /** Reads {@code text} with the card up; a reading already under way, here or elsewhere, is stopped first. */
    @MainThread
    public void start(@NonNull String text) {
        int mine = ++session;
        done = false;
        anchor.removeCallbacks(closeAfterDone);
        indicator.showReading(callbacks(mine), text, new TaiSettings(activity).getTtsVoice());
        if (!indicator.isShowing()) {
            // No window to put it in; read anyway, as before the card.
            session++;
            TaiReadAloud.speak(activity, text, null, null, error -> {
                if (error != null) AppNotice.show(activity, error, true);
            });
            return;
        }
        TaiReadAloud.speak(activity, text, null, null, new TaiReadAloud.Listener() {
            private boolean completed;

            @Override
            public void onSentence(int index, int count, int start, int end) {
                if (mine == session) indicator.setReadingSentence(index, count, start, end);
            }

            @Override
            public void onSounding() {
                if (mine == session) indicator.setReadingSounding();
            }

            @Override
            public void onCompleted() {
                completed = true;
                if (mine != session) return;
                done = true;
                indicator.showReadingDone();
                anchor.postDelayed(closeAfterDone, DONE_CLOSE_MS);
            }

            @Override
            public void onFinished(@Nullable String error) {
                if (error != null) AppNotice.show(activity, error, true);
                if (mine != session) return;
                // Heard through, the card closes after "Done"; stopped or failed, it goes now.
                if (error != null || !completed) dismiss();
            }
        });
    }

    /** Stops the reading if one is under way and closes the card; no-op when it is down. */
    @MainThread
    public void close() {
        if (TaiReadAloud.isSpeaking()) TaiReadAloud.stop(activity);
        dismiss();
    }

    public boolean isShowing() {
        return indicator.isShowing();
    }

    /** The card goes, the reading (if any) left alone. */
    private void dismiss() {
        session++;
        done = false;
        anchor.removeCallbacks(closeAfterDone);
        indicator.hide();
    }

    @NonNull
    private VoiceListeningIndicator.ReadingCallbacks callbacks(int mine) {
        return new VoiceListeningIndicator.ReadingCallbacks() {
            @Override
            public void onPause() {
                if (mine == session && TaiReadAloud.pause()) indicator.setReadingPaused(true);
            }

            @Override
            public void onResume() {
                if (mine == session && TaiReadAloud.resume()) indicator.setReadingPaused(false);
            }

            @Override
            public void onClose() {
                if (mine == session) close();
            }

            @Override
            public void onVoice(@NonNull String voice) {
                new TaiSettings(activity).setTtsVoice(voice);
            }

            @Override
            public void onTouched() {
                // "Done" is showing and the user reached for the card: give them a moment more.
                if (mine != session || !done) return;
                anchor.removeCallbacks(closeAfterDone);
                anchor.postDelayed(closeAfterDone, DONE_CLOSE_MS);
            }
        };
    }
}
