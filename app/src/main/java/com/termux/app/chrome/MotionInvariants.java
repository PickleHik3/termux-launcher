package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The motion invariants, checked from the events {@link MotionLog} records. One crossing at a
 * time: {@code crossing-begin} opens it, {@code presize} marks it, {@code wall-page-settled}
 * starts a {@link #WINDOW_MS} window in which the landing must stay quiet. Pure: no Android
 * types and no timers; the caller feeds {@link #tick} when no event arrives.
 *
 * <ul>
 * <li>{@code one-resize-per-crossing}: a crossing that pre-sized sees no {@code pty-resize}
 *     within {@link #WINDOW_MS} after its settle.</li>
 * <li>{@code no-still-after-live}: a page that went live during the slide (a
 *     {@code still-dropped}, no {@code still-kept} after it) logs no {@code still-kept} within
 *     {@link #QUIET_MS} after the settle.</li>
 * <li>{@code no-frost-at-landing}: no {@code reflow-frost-start} within {@link #QUIET_MS} after
 *     the settle.</li>
 * </ul>
 */
public final class MotionInvariants {

    public static final long WINDOW_MS = 1000;
    public static final long QUIET_MS = 500;

    /** A broken invariant: its name and the key=value detail for the log line. */
    public static final class Violation {
        @NonNull public final String name;
        @NonNull public final String kv;

        Violation(@NonNull String name, @NonNull String kv) {
            this.name = name;
            this.kv = kv;
        }

        @Override
        public String toString() {
            return name + (kv.isEmpty() ? "" : " " + kv);
        }
    }

    private boolean mOpen;
    private boolean mPresized;
    private long mSettledAt = -1;
    private final Set<String> mLivePages = new HashSet<>();
    private boolean mResizeReported;

    /** Feeds one event; returns the violations it completes (usually none). */
    @NonNull
    public List<Violation> onEvent(long uptimeMs, @NonNull String event, @Nullable String kv) {
        List<Violation> out = new ArrayList<>(0);
        tick(uptimeMs);
        switch (event) {
            case "crossing-begin":
                mOpen = true;
                mPresized = false;
                mSettledAt = -1;
                mResizeReported = false;
                mLivePages.clear();
                break;
            case "presize":
                if (mOpen && mSettledAt < 0) mPresized = true;
                break;
            case "wall-page-settled":
                if (mOpen && mSettledAt < 0) mSettledAt = uptimeMs;
                break;
            case "still-dropped":
                if (mOpen && mSettledAt < 0) {
                    String page = value(kv, "page");
                    if (page != null) mLivePages.add(page);
                }
                break;
            case "still-kept": {
                String page = value(kv, "page");
                if (page == null || !mOpen) break;
                if (mSettledAt < 0) {
                    mLivePages.remove(page);
                } else if (uptimeMs - mSettledAt <= QUIET_MS && mLivePages.remove(page)) {
                    out.add(new Violation("no-still-after-live",
                        "page=" + page + " afterSettleMs=" + (uptimeMs - mSettledAt)));
                }
                break;
            }
            case "pty-resize":
                if (mOpen && mSettledAt >= 0 && mPresized && !mResizeReported
                        && uptimeMs - mSettledAt <= WINDOW_MS) {
                    mResizeReported = true;
                    out.add(new Violation("one-resize-per-crossing",
                        "afterSettleMs=" + (uptimeMs - mSettledAt)
                            + (kv == null || kv.isEmpty() ? "" : " " + kv)));
                }
                break;
            case "reflow-frost-start":
                if (mOpen && mSettledAt >= 0 && uptimeMs - mSettledAt <= QUIET_MS) {
                    out.add(new Violation("no-frost-at-landing",
                        "afterSettleMs=" + (uptimeMs - mSettledAt)));
                }
                break;
            default:
                break;
        }
        return out;
    }

    /** Closes the crossing once its window has passed; call from a delayed post after a settle. */
    public void tick(long uptimeMs) {
        if (mOpen && mSettledAt >= 0 && uptimeMs - mSettledAt > WINDOW_MS) mOpen = false;
    }

    @Nullable
    private static String value(@Nullable String kv, @NonNull String key) {
        if (kv == null) return null;
        String prefix = key + "=";
        int at = kv.startsWith(prefix) ? 0 : kv.indexOf(" " + prefix) + 1;
        if (at == 0 && !kv.startsWith(prefix)) return null;
        int start = at + prefix.length();
        int end = kv.indexOf(' ', start);
        return end < 0 ? kv.substring(start) : kv.substring(start, end);
    }
}
