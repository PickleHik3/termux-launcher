package com.termux.app.surfaces;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * The Appearance surface's one bar, as a page frame: the shared {@code appearance_page_frame}
 * (back arrow, the Wallpaper | Look | Layout | Icon pack pill, Undo while there is
 * something to undo, Done) over a content region. Every page of the surface wears the same bar;
 * only the pill's selection and the end actions change between pages.
 *
 * <p>Three kinds: the Overview ({@link #overview}, the wallpaper page in the content region, the
 * pill on Wallpaper), the Icon pack page ({@link #icons}, the pill on Icon pack), and the
 * editor (Look and Layout, built by the surface). The editor's frame is not in this view: it is the
 * launcher's container, scaled underneath, standing in the region below the bar ({@link #BAR_DP}
 * plus the status inset is where {@link SurfaceEditorController} puts the frame's top), so the
 * editor kind is transparent and takes no touch outside the bar's own buttons.</p>
 *
 * <p>The pill's labels show on the selected segment only, so four segments and Undo fit the
 * 64dp bar at 360dp; each unselected segment keeps its glyph, and its name as its content
 * description. A tap on a segment asks the surface to go there and the pill then snaps back to the
 * page that is showing: it moves when the page does (or when the editor's mode does), never ahead of
 * it, so a question that keeps the person editing leaves the pill where it was.</p>
 */
public final class AppearanceEditorPage {

    /** The bar's height, dp: the frame stands below it. */
    static final int BAR_DP = 64;

    /** The pill's segments, one per page of the surface. */
    public enum Segment { WALLPAPER, LOOK, LAYOUT, ICON_PACK }

    /** What the bar's buttons ask of the surface. */
    interface Callbacks {
        void onBack();

        void onUndo();

        void onDone();

        /** The pill was tapped on {@code segment}. */
        void onSegment(@NonNull Segment segment);
    }

    private enum Kind { OVERVIEW, EDITOR, ICONS }

    @NonNull private final Kind mKind;
    @NonNull private final View mRoot;
    @NonNull private final View mBar;
    @NonNull private final TextView mTitle;
    @NonNull private final View mModeSlot;
    @NonNull private final MaterialButtonToggleGroup mMode;
    @NonNull private final MaterialButton mWallpaper;
    @NonNull private final MaterialButton mLook;
    @NonNull private final MaterialButton mLayout;
    @NonNull private final MaterialButton mIconPack;
    @NonNull private final CharSequence mWallpaperLabel;
    @NonNull private final CharSequence mLookLabel;
    @NonNull private final CharSequence mLayoutLabel;
    @NonNull private final CharSequence mIconPackLabel;
    /** The segment of the page that is showing; the pill returns to it after a tap. */
    @NonNull private Segment mCurrent;
    private boolean mRestating;
    /** The pill's own listener is running: the selection is applied after it returns. */
    private boolean mDispatching;
    @NonNull private final android.os.Handler mMain = new android.os.Handler(android.os.Looper.getMainLooper());
    @NonNull private final View mUndo;
    @NonNull private final View mDone;
    @Nullable private Drawable mBackground;

    /** The editor's bar: Look and Layout, transparent over the scaled launcher. */
    AppearanceEditorPage(@NonNull Context context, @NonNull Callbacks callbacks) {
        this(context, callbacks, Kind.EDITOR, null, null);
    }

    /** The Overview: {@code content} (the wallpaper page) under the bar with the pill on Wallpaper. */
    @NonNull
    public static AppearanceEditorPage overview(@NonNull Context context,
                                                @NonNull AppearanceSurfaceController.Navigator navigator,
                                                @NonNull View content) {
        return new AppearanceEditorPage(context, new Callbacks() {
            @Override public void onBack() {
                navigator.close();
            }

            @Override public void onUndo() {
            }

            @Override public void onDone() {
                navigator.close();
            }

            @Override public void onSegment(@NonNull Segment segment) {
                if (segment == Segment.ICON_PACK)
                    navigator.openIcons();
                else if (segment == Segment.LOOK)
                    navigator.openLook();
                else if (segment == Segment.LAYOUT)
                    navigator.openLayout();
            }
        }, Kind.OVERVIEW, null, content);
    }

    /** The Icon pack page: back, the pill on Icon pack (no title), Done, over {@code content}. */
    @NonNull
    public static AppearanceEditorPage icons(@NonNull Context context,
                                             @NonNull AppearanceSurfaceController.Navigator navigator,
                                             @NonNull CharSequence title, @NonNull View content) {
        return new AppearanceEditorPage(context, new Callbacks() {
            @Override public void onBack() {
                navigator.back();
            }

            @Override public void onUndo() {
            }

            @Override public void onDone() {
                navigator.close();
            }

            @Override public void onSegment(@NonNull Segment segment) {
                // Wallpaper is one page back; Look and Layout go through the Overview.
                if (segment == Segment.WALLPAPER)
                    navigator.back();
                else if (segment == Segment.LOOK)
                    navigator.openLook();
                else if (segment == Segment.LAYOUT)
                    navigator.openLayout();
            }
        }, Kind.ICONS, title, content);
    }

    private AppearanceEditorPage(@NonNull Context context, @NonNull Callbacks callbacks, @NonNull Kind kind,
                                 @Nullable CharSequence title, @Nullable View content) {
        mKind = kind;
        mRoot = LayoutInflater.from(context).inflate(R.layout.appearance_page_frame, null, false);
        mBar = mRoot.findViewById(R.id.appearance_page_bar);
        mTitle = mRoot.findViewById(R.id.appearance_page_title);
        mModeSlot = mRoot.findViewById(R.id.appearance_page_mode_slot);
        mMode = mRoot.findViewById(R.id.appearance_page_mode);
        mWallpaper = mRoot.findViewById(R.id.appearance_page_mode_wallpaper);
        mLook = mRoot.findViewById(R.id.appearance_page_mode_look);
        mLayout = mRoot.findViewById(R.id.appearance_page_mode_layout);
        mIconPack = mRoot.findViewById(R.id.appearance_page_mode_icon_pack);
        mIconPackLabel = mIconPack.getText();
        mWallpaperLabel = mWallpaper.getText();
        mLookLabel = mLook.getText();
        mLayoutLabel = mLayout.getText();
        mUndo = mRoot.findViewById(R.id.appearance_page_undo);
        mDone = mRoot.findViewById(R.id.appearance_page_done);
        mRoot.findViewById(R.id.appearance_page_back).setOnClickListener(v -> callbacks.onBack());
        mUndo.setOnClickListener(v -> callbacks.onUndo());
        mDone.setOnClickListener(v -> callbacks.onDone());
        if (title != null)
            mTitle.setText(title);
        mUndo.setVisibility(View.GONE);
        mDone.setVisibility(View.VISIBLE);
        mCurrent = kind == Kind.OVERVIEW ? Segment.WALLPAPER : kind == Kind.ICONS ? Segment.ICON_PACK : Segment.LOOK;

        ViewGroup region = mRoot.findViewById(R.id.appearance_page_content);
        {
            // The pill stands in the title's place; the title text stays for the shared frame.
            mTitle.setVisibility(View.GONE);
            mModeSlot.setVisibility(View.VISIBLE);
            mMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (mRestating || !isChecked)
                    return;
                Segment tapped = checkedId == R.id.appearance_page_mode_wallpaper ? Segment.WALLPAPER
                    : checkedId == R.id.appearance_page_mode_icon_pack ? Segment.ICON_PACK
                    : checkedId == R.id.appearance_page_mode_layout ? Segment.LAYOUT : Segment.LOOK;
                mDispatching = true;
                try {
                    callbacks.onSegment(tapped);
                } finally {
                    mDispatching = false;
                }
                // The page that is showing owns the selection: a tap that did not move it (a
                // question left unanswered, a hop under way) puts the pill back. After the group
                // has finished telling its listeners, never inside it.
                mMain.post(this::apply);
            });
        }
        if (kind == Kind.EDITOR) {
            // Transparent: the scaled launcher shows through the content region, and colorSurface
            // (the surface's scrim, in the content view) stands behind the bar and around the frame.
            mRoot.setBackground(null);
            region.setClickable(false);
            region.setFocusable(false);
            setLayoutMode(false);
        } else {
            if (content != null) {
                region.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            }
            if (kind == Kind.OVERVIEW)
                select(Segment.WALLPAPER);
            else if (kind == Kind.ICONS)
                select(Segment.ICON_PACK);
        }
    }

    @NonNull
    public View root() {
        return mRoot;
    }

    /** The content region: what slides between the Overview and the Icon pack page while the bar stays put. */
    @NonNull
    public View content() {
        return mRoot.findViewById(R.id.appearance_page_content);
    }

    @NonNull
    CharSequence title() {
        return mTitle.getText();
    }

    /** The frame's own colorSurface at {@code alpha} (1 at rest): fades with the page behind it. */
    public void setBackgroundAlpha(float alpha) {
        if (mBackground == null) {
            Drawable background = mRoot.getBackground();
            if (background == null)
                return;
            mBackground = background.mutate();
        }
        mBackground.setAlpha(Math.round(255f * Math.max(0f, Math.min(1f, alpha))));
    }

    /** The bar alone, hidden while another page's bar stands exactly over it (the editor hop). */
    public void setBarVisible(boolean visible) {
        mBar.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    boolean isBarVisible() {
        return mBar.getVisibility() == View.VISIBLE;
    }

    /** The editor's mode: the pill's Look or Layout, and the title text. */
    void setLayoutMode(boolean layout) {
        mTitle.setText(layout ? R.string.appearance_page_layout : R.string.appearance_page_look);
        select(layout ? Segment.LAYOUT : Segment.LOOK);
    }

    /** The editor is on its way out to the Overview: the pill shows Wallpaper while it goes. */
    void showWallpaper() {
        select(Segment.WALLPAPER);
    }

    /** No Overview behind the editor (a direct open): the Wallpaper segment is not offered. */
    void setWallpaperAvailable(boolean available) {
        mWallpaper.setVisibility(available ? View.VISIBLE : View.GONE);
    }

    @NonNull
    Segment selected() {
        return mCurrent;
    }

    private void select(@NonNull Segment segment) {
        mCurrent = segment;
        if (!mDispatching)
            apply();
    }

    /** Puts the pill on the current segment: the checked segment and the one label. */
    private void apply() {
        Segment segment = mCurrent;
        int id = segment == Segment.WALLPAPER ? R.id.appearance_page_mode_wallpaper
            : segment == Segment.LAYOUT ? R.id.appearance_page_mode_layout
            : segment == Segment.ICON_PACK ? R.id.appearance_page_mode_icon_pack : R.id.appearance_page_mode_look;
        mRestating = true;
        if (mMode.getCheckedButtonId() != id)
            mMode.check(id);
        label(mWallpaper, mWallpaperLabel, segment == Segment.WALLPAPER);
        label(mLook, mLookLabel, segment == Segment.LOOK);
        label(mLayout, mLayoutLabel, segment == Segment.LAYOUT);
        label(mIconPack, mIconPackLabel, segment == Segment.ICON_PACK);
        mRestating = false;
    }

    /** The selected segment shows its name; the others keep the glyph and name themselves for TalkBack. */
    private static void label(@NonNull MaterialButton button, @NonNull CharSequence name, boolean selected) {
        button.setText(selected ? name : "");
        button.setContentDescription(name);
        button.setTooltipText(name);
    }

    boolean isModeShown() {
        return mModeSlot.getVisibility() == View.VISIBLE;
    }

    boolean isTitleShown() {
        return mTitle.getVisibility() == View.VISIBLE;
    }

    void setDirty(boolean dirty) {
        int visibility = dirty ? View.VISIBLE : View.GONE;
        if (mUndo.getVisibility() != visibility)
            mUndo.setVisibility(visibility);
    }

    boolean isUndoShown() {
        return mUndo.getVisibility() == View.VISIBLE;
    }

    boolean isDoneShown() {
        return mDone.getVisibility() == View.VISIBLE;
    }
}
