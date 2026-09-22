package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * One finished desktop-notification request, assembled by {@link KittyNotifications} from the
 * {@code OSC 99} escapes a program sent.
 *
 * <p>A program can spread a single notification over several escapes — a title chunk, then body
 * chunks, then a final chunk that says it is done — so nothing here is known until the request
 * closes. What the terminal hands its client is this: one whole notification, already decoded.
 */
public final class KittyNotification {

    /** Show it whatever the terminal is doing. */
    public static final String OCCASION_ALWAYS = "always";

    /** Show it only when this window is not the focused one. */
    public static final String OCCASION_UNFOCUSED = "unfocused";

    /** Show it only when the terminal is not on screen at all. */
    public static final String OCCASION_INVISIBLE = "invisible";

    /** The program did not say how urgent it is. */
    public static final int URGENCY_UNSET = -1;

    public static final int URGENCY_LOW = 0;

    public static final int URGENCY_NORMAL = 1;

    public static final int URGENCY_CRITICAL = 2;

    /** Let the system decide how long it stays up. */
    public static final int TIMEOUT_DEFAULT = -1;

    /** It stays up until something dismisses it. */
    public static final int TIMEOUT_NEVER = 0;

    @NonNull private final String mId;

    @NonNull private final String mTitle;

    @NonNull private final String mBody;

    private final int mUrgency;

    @NonNull private final String mOccasion;

    private final boolean mFocusOnActivate;

    private final boolean mReportOnActivate;

    private final boolean mReportOnClose;

    private final int mTimeoutMillis;

    @Nullable private final String mApplicationName;

    @NonNull private final List<String> mIconNames;

    @NonNull private final List<String> mTypes;

    @Nullable private final String mSound;

    @NonNull private final List<String> mButtons;

    KittyNotification(@NonNull String id, @NonNull String title, @NonNull String body, int urgency,
                      @NonNull String occasion, boolean focusOnActivate, boolean reportOnActivate,
                      boolean reportOnClose, int timeoutMillis, @Nullable String applicationName,
                      @NonNull List<String> iconNames, @NonNull List<String> types,
                      @Nullable String sound, @NonNull List<String> buttons) {
        mId = id;
        mTitle = title;
        mBody = body;
        mUrgency = urgency;
        mOccasion = occasion;
        mFocusOnActivate = focusOnActivate;
        mReportOnActivate = reportOnActivate;
        mReportOnClose = reportOnClose;
        mTimeoutMillis = timeoutMillis;
        mApplicationName = applicationName;
        mIconNames = Collections.unmodifiableList(iconNames);
        mTypes = Collections.unmodifiableList(types);
        mSound = sound;
        mButtons = Collections.unmodifiableList(buttons);
    }

    /**
     * The name the program gave this notification, empty when it gave none. It is the handle for
     * replacing, closing and reporting on it.
     */
    @NonNull
    public String getId() {
        return mId;
    }

    @NonNull
    public String getTitle() {
        return mTitle;
    }

    @NonNull
    public String getBody() {
        return mBody;
    }

    /** {@link #URGENCY_LOW}, {@link #URGENCY_NORMAL}, {@link #URGENCY_CRITICAL} or unset. */
    public int getUrgency() {
        return mUrgency;
    }

    /** One of the {@code OCCASION_} constants: when the program wants it to be shown at all. */
    @NonNull
    public String getOccasion() {
        return mOccasion;
    }

    /** Whether tapping it should bring the terminal that sent it to the front. */
    public boolean isFocusOnActivate() {
        return mFocusOnActivate;
    }

    /** Whether the program asked to be told when the user taps it. */
    public boolean isReportOnActivate() {
        return mReportOnActivate;
    }

    /** Whether the program asked to be told when it goes away. */
    public boolean isReportOnClose() {
        return mReportOnClose;
    }

    /**
     * How long it should stay up, in milliseconds: {@link #TIMEOUT_DEFAULT} to let the system
     * choose, {@link #TIMEOUT_NEVER} to leave it until it is dismissed.
     */
    public int getTimeoutMillis() {
        return mTimeoutMillis;
    }

    /** The program's own name for itself, when it gave one. */
    @Nullable
    public String getApplicationName() {
        return mApplicationName;
    }

    /** Icon names the program suggested. The terminal is free to ignore them. */
    @NonNull
    public List<String> getIconNames() {
        return mIconNames;
    }

    /** Free-form categories the program tagged it with, for filtering. */
    @NonNull
    public List<String> getTypes() {
        return mTypes;
    }

    /** The sound the program asked for, when it asked for one. */
    @Nullable
    public String getSound() {
        return mSound;
    }

    /** Button labels the program offered. The terminal is free to ignore them. */
    @NonNull
    public List<String> getButtons() {
        return mButtons;
    }

    /** Whether there is anything at all to show. */
    public boolean isEmpty() {
        return mTitle.isEmpty() && mBody.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return "KittyNotification{id='" + mId + "', title='" + mTitle + "', body='" + mBody
            + "', urgency=" + mUrgency + ", occasion=" + mOccasion + "}";
    }
}
