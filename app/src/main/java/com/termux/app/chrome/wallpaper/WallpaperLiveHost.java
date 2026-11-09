package com.termux.app.chrome.wallpaper;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.chrome.scene.LiveWallpaperChoice;
import com.termux.app.chrome.scene.ShaderCheck;
import com.termux.app.chrome.scene.ShaderErrors;
import com.termux.app.chrome.scene.ShaderExamples;
import com.termux.app.chrome.scene.ShaderStore;
import com.termux.app.chrome.scene.WallpaperMotion;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Photo | Video | Shader at the top of the Wallpaper picker. Photo is the picker page as it always
 * was ({@link WallpaperPickerPage}, untouched); Video is {@link VideoPage}; Shader is the list of
 * saved shaders ({@link ShaderListPage}) and, one level down, the editor ({@link ShaderEditorPage}).
 * The host wraps the picker's root: the sheet that frames the picker gets {@link #root()} instead.
 * Below Android 10 there is no kind row and nothing of Video or Shader shows.
 *
 * <p>It opens on Video or Shader while one is in use ({@link LiveSceneLogic#startKind}). The
 * editor is a sub-page: the sheet's back arrow and the system back go to the list first
 * ({@link #consumeBack}). A shader is written (and so starts) only by Apply, after it compiles
 * and draws one frame; a video only once it is imported. What Apply and Back to photo do to the
 * phone's wallpaper is {@link LiveWallpaperApplier}; terminal-app mode and Also set as phone
 * wallpaper are the picker's own ({@link WallpaperPickerPage#launcherOnly}, the same remembered
 * setting).</p>
 */
public final class WallpaperLiveHost {

    private static final String LOG_TAG = "WallpaperLiveHost";
    /** The hint line of the video in use, kept at import time. */
    private static final String KEY_VIDEO_HINT = "wallpaper_video_hint";
    /** An imported shader file longer than this is not read (a shader is a few KB). */
    private static final long MAX_IMPORT_BYTES = 1L << 20;
    private static final String[] SHADER_MIME_TYPES = {
        "text/*", "application/octet-stream", "application/x-glsl", "text/x-glsl"
    };

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WallpaperLiveHost");
        t.setDaemon(true);
        return t;
    });

    /** What the host tells the activity. */
    public interface Listener {
        /** The phone's Home and Lock changed: the launcher takes up the new picture. */
        void onSystemWallpaperChanged();
    }

    /** Copies a picked video or GIF into the app and describes it. Called off the main thread. */
    public interface VideoSource {
        /** The no-op source until the real import is wired in. */
        @NonNull
        VideoSource UNAVAILABLE = (context, uri) -> new Result(false, null, null, "Video arrives in the next build");

        @NonNull
        Result importFile(@NonNull Context context, @NonNull Uri uri);

        /** How an import ended. */
        final class Result {
            public final boolean ok;
            /** The stored file's name (for {@code WallpaperMotion.video}); set when {@link #ok}. */
            @Nullable public final String name;
            /** One line about the file: codec, size, frame rate, length, hardware or not. */
            @Nullable public final String hint;
            /** What went wrong, one line; set when not {@link #ok}. */
            @Nullable public final String error;

            public Result(boolean ok, @Nullable String name, @Nullable String hint, @Nullable String error) {
                this.ok = ok;
                this.name = name;
                this.hint = hint;
                this.error = error;
            }
        }
    }

    private enum Page { LIST, EDITOR, VIDEO }

    private final Context mContext;
    private final WallpaperPickerPage mPicker;
    private final Listener mListener;
    private final boolean mSupported;
    private final boolean mHomeApp;
    private final LinearLayout mRoot;
    @Nullable private final ShaderListPage mList;
    @Nullable private final ShaderEditorPage mEditor;
    @Nullable private final VideoPage mVideo;
    private final FrameLayout mLive;
    private final View mPhotoRoot;
    @Nullable private MaterialButtonToggleGroup mKindGroup;
    private LiveSceneLogic.Kind mKind = LiveSceneLogic.Kind.PHOTO;
    @Nullable private Page mPage;
    private final LivePageSwap mSwap = new LivePageSwap();
    /** The list's scroll when the editor opened over it; put back when the list returns. */
    private int mListScrollY;
    private boolean mReleased;
    /** Apply or Back to photo changed what the Photo page read: it reads again when shown. */
    private boolean mPhotoStale;
    /** The editor's shader is on disk (applied once): leaving it keeps the folder. */
    private boolean mEditorSaved;
    @NonNull private VideoSource mVideoSource = VideoSource.UNAVAILABLE;
    @Nullable private Supplier<String> mHeldReason;

    @Nullable private ActivityResultLauncher<String[]> mImportLauncher;
    @Nullable private ActivityResultLauncher<PickVisualMediaRequest> mTextureLauncher;
    @Nullable private ActivityResultLauncher<PickVisualMediaRequest> mVideoLauncher;
    @Nullable private String mTextureTarget;

    public WallpaperLiveHost(@NonNull Context context, @NonNull WallpaperPickerPage picker,
                             @NonNull Listener listener) {
        mContext = context;
        mPicker = picker;
        mListener = listener;
        mSupported = LiveWallpaperChoice.isSupported();
        mHomeApp = !picker.launcherOnly();
        mPhotoRoot = picker.root();

        mRoot = new LinearLayout(context);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        FrameLayout content = new FrameLayout(context);
        mLive = new FrameLayout(context);
        mLive.setVisibility(View.GONE);

        if (mSupported) {
            mRoot.addView(buildKindRow(context), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            mList = new ShaderListPage(context, new ShaderListPage.Listener() {
                @Override public void onOpen(@NonNull String id) {
                    openShader(id);
                }

                @Override public void onOpenExample(@NonNull String slug) {
                    openExample(slug);
                }

                @Override public void onNew() {
                    openShader(null);
                }

                @Override public void onBackToPhoto() {
                    backToPhoto();
                }
            });
            mEditor = new ShaderEditorPage(context, mHomeApp, new ShaderEditorPage.Actions() {
                @Override public void onApply(@NonNull ShaderEditorPage.Draft draft) {
                    applyShader(draft);
                }

                @Override public void onBackToPhoto() {
                    backToPhoto();
                }

                @Override public void onAlsoPhone(boolean on) {
                    // The Photo page's own checkbox remembers it; one setting for both pages.
                    if (!mReleased) mPicker.alsoPhone().setChecked(on);
                }

                @Override public void onImport() {
                    ActivityResultLauncher<String[]> launcher = mImportLauncher;
                    if (launcher == null) toast(R.string.shader_import_unavailable);
                    else launcher.launch(SHADER_MIME_TYPES);
                }

                @Override public void onPickTexture(@NonNull String id) {
                    ActivityResultLauncher<PickVisualMediaRequest> launcher = mTextureLauncher;
                    if (launcher == null) {
                        toast(R.string.shader_import_unavailable);
                        return;
                    }
                    mTextureTarget = id;
                    launcher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
                }

                @Override public void onDelete(@NonNull String id) {
                    deleteShader(id);
                }
            });
            mVideo = new VideoPage(context, new VideoPage.Actions() {
                @Override public void onChoose() {
                    ActivityResultLauncher<PickVisualMediaRequest> launcher = mVideoLauncher;
                    if (launcher == null) {
                        toast(R.string.shader_import_unavailable);
                        return;
                    }
                    launcher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE).build());
                }

                @Override public void onBackToPhoto() {
                    backToPhoto();
                }

                @Override public void onRate(int hz) {
                    SharedPreferences prefs = prefs();
                    if (prefs != null) ShaderStore.writeVideoRate(prefs, hz);
                }
            });
            for (View page : new View[] {mList.root(), mEditor.root(), mVideo.root()}) {
                page.setVisibility(View.GONE);
                mLive.addView(page, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            registerLaunchers();
        } else {
            mList = null;
            mEditor = null;
            mVideo = null;
        }

        content.addView(mPhotoRoot, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(mLive, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRoot.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (mSupported) showKind(LiveSceneLogic.startKind(readStored(), true));
    }

    /** What the sheet frames: the picker, with Photo | Video | Shader above it where they are offered. */
    @NonNull
    public View root() {
        return mRoot;
    }

    /** The picker page under the host. */
    @NonNull
    public WallpaperPickerPage picker() {
        return mPicker;
    }

    /** Where Choose video or GIF hands the picked file; until set, it says video is not ready. */
    public void setVideoSource(@NonNull VideoSource source) {
        mVideoSource = source;
    }

    /** The running shader's hold reason (the scene controller's), read when the editor opens. */
    public void setHeldReasonSource(@Nullable Supplier<String> source) {
        mHeldReason = source;
    }

    // --- the surface's page ---

    /** Back from the surface: the editor goes to the list first. True when it was spent here. */
    public boolean consumeBack() {
        if (mReleased || mPage != Page.EDITOR) return false;
        showList();
        return true;
    }

    public void release() {
        mReleased = true;
        mSwap.cancel();
        if (mPage == Page.EDITOR) discardEditor();
        if (mList != null) mList.release();
        if (mImportLauncher != null) mImportLauncher.unregister();
        if (mTextureLauncher != null) mTextureLauncher.unregister();
        if (mVideoLauncher != null) mVideoLauncher.unregister();
        mImportLauncher = null;
        mTextureLauncher = null;
        mVideoLauncher = null;
        if (mList != null) mLive.setVisibility(View.GONE);
    }

    // --- Photo | Video | Shader ---

    @NonNull
    private View buildKindRow(@NonNull Context context) {
        View row = LayoutInflater.from(context).inflate(R.layout.preference_segmented_pill, null, false);
        // The settings row's title and summary header is not wanted: the segments are the row.
        ((ViewGroup) row).getChildAt(0).setVisibility(View.GONE);
        MaterialButtonToggleGroup group = row.findViewById(R.id.segmented_pill_track);
        MaterialButton photo = row.findViewById(R.id.segmented_pill_default);
        MaterialButton video = row.findViewById(R.id.segmented_pill_capsule);
        MaterialButton shader = row.findViewById(R.id.segmented_pill_third);
        photo.setText(R.string.wallpaper_kind_photo);
        video.setText(R.string.wallpaper_kind_video);
        shader.setText(R.string.wallpaper_kind_shader);
        // The segment style already tints icons with the same selector as the label, so the
        // glyphs follow checked / unchecked and the theme. 18dp + 4dp is the style's own size:
        // at font scale 1.3 "Shader" (about 57dp) plus 24dp padding fits a ~110dp segment.
        float density = context.getResources().getDisplayMetrics().density;
        setKindGlyph(photo, R.drawable.ic_wallpaper_kind_photo, density);
        setKindGlyph(video, R.drawable.ic_wallpaper_kind_video, density);
        setKindGlyph(shader, R.drawable.ic_wallpaper_kind_shader, density);
        shader.setVisibility(View.VISIBLE);
        ((ViewGroup.MarginLayoutParams) group.getLayoutParams()).topMargin = 0;
        group.setContentDescription(context.getString(R.string.wallpaper_kind_label));
        mKindGroup = group;
        final int photoId = photo.getId();
        final int videoId = video.getId();
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked || mReleased) return;
            showKind(checkedId == photoId ? LiveSceneLogic.Kind.PHOTO
                : checkedId == videoId ? LiveSceneLogic.Kind.VIDEO : LiveSceneLogic.Kind.SHADER);
        });
        return row;
    }

    private static void setKindGlyph(@NonNull MaterialButton button, int drawableRes, float density) {
        button.setIconResource(drawableRes);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconSize(Math.round(18 * density));
        button.setIconPadding(Math.round(4 * density));
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    /**
     * Whether the Video or Shader side is up. The Photo picker is then GONE and never sizes its
     * centred card, so whoever waits on the picker's readiness must not wait while this is true.
     */
    public boolean showingLive() {
        return mKind != LiveSceneLogic.Kind.PHOTO;
    }

    /**
     * How far the shader list is scrolled: where it stands, or stood when the editor now open
     * over it opened. A relaunch carries it ({@link #restoreSide}).
     */
    public int gridScrollY() {
        if (mList == null) return 0;
        return mPage == Page.EDITOR ? mListScrollY : mList.scrollY();
    }

    /**
     * The side a relaunch carried: Video or Shader ({@code live}; the stored kind, Shader when
     * none) with the list scrolled to {@code gridScrollY} once laid out, or Photo. Where they are
     * not offered, nothing.
     */
    public void restoreSide(boolean live, int gridScrollY) {
        if (mReleased || !mSupported) return;
        LiveSceneLogic.Kind kind = LiveSceneLogic.Kind.PHOTO;
        if (live) {
            LiveSceneLogic.Kind stored = LiveSceneLogic.kindOf(readStored());
            kind = stored == LiveSceneLogic.Kind.VIDEO ? stored : LiveSceneLogic.Kind.SHADER;
        }
        if (kind != mKind) showKind(kind);
        if (kind == LiveSceneLogic.Kind.SHADER && mList != null) {
            mListScrollY = gridScrollY;
            mList.restoreScrollYWhenLaidOut(gridScrollY);
        }
    }

    private void showKind(@NonNull LiveSceneLogic.Kind kind) {
        if (mPage == Page.EDITOR) discardEditor();
        mSwap.cancel();
        mKind = kind;
        MaterialButtonToggleGroup group = mKindGroup;
        if (group != null) {
            int id = kind == LiveSceneLogic.Kind.PHOTO ? R.id.segmented_pill_default
                : kind == LiveSceneLogic.Kind.VIDEO ? R.id.segmented_pill_capsule : R.id.segmented_pill_third;
            if (group.getCheckedButtonId() != id) group.check(id);
        }
        boolean photo = kind == LiveSceneLogic.Kind.PHOTO;
        if (photo && mPhotoStale) {
            mPhotoStale = false;
            mPicker.refreshStored();
        }
        mPhotoRoot.setVisibility(photo ? View.VISIBLE : View.GONE);
        mLive.setVisibility(photo ? View.GONE : View.VISIBLE);
        if (photo) {
            hidePages();
            mPage = null;
        } else if (kind == LiveSceneLogic.Kind.VIDEO) {
            showVideo();
        } else {
            mPage = null;
            showList();
        }
    }

    private void hidePages() {
        if (mList == null || mEditor == null || mVideo == null) return;
        mList.root().setVisibility(View.GONE);
        mEditor.root().setVisibility(View.GONE);
        mVideo.root().setVisibility(View.GONE);
    }

    @Nullable
    private View pageView(@Nullable Page page) {
        if (page == null || mList == null || mEditor == null || mVideo == null) return null;
        switch (page) {
            case EDITOR: return mEditor.root();
            case VIDEO: return mVideo.root();
            default: return mList.root();
        }
    }

    /** Makes {@code next} the page: a fade from the one up, or a cut. */
    private void goTo(@NonNull Page next, boolean fade) {
        View in = pageView(next);
        View out = pageView(mPage);
        if (in == null) return;
        if (fade && out != null && out != in && out.getVisibility() == View.VISIBLE) {
            mSwap.swap(out, in, ReducedMotion.isEnabled(mContext));
        } else {
            mSwap.cancel();
            hidePages();
            in.setVisibility(View.VISIBLE);
            in.setAlpha(1f);
        }
        mPage = next;
    }

    private void showVideo() {
        if (mVideo == null) return;
        SharedPreferences prefs = prefs();
        WallpaperMotion stored = readStored();
        mVideo.show(stored, prefs == null ? 30 : ShaderStore.readVideoRate(prefs));
        mVideo.setHint(stored.kind == WallpaperMotion.Kind.VIDEO && stored.id != null
            ? videoStatus(stored.id, prefs == null ? null : prefs.getString(KEY_VIDEO_HINT, null)) : null);
        goTo(Page.VIDEO, false);
    }

    private void showList() {
        if (mList == null || mEditor == null) return;
        boolean fromEditor = mPage == Page.EDITOR;
        if (fromEditor) discardEditor();
        mList.bind(readStored());
        if (fromEditor) mList.restoreScrollY(mListScrollY);
        goTo(Page.LIST, fromEditor);
    }

    private void openShader(@Nullable String id) {
        if (mList == null || mEditor == null || mReleased) return;
        ShaderStore.Meta meta = null;
        String source = null;
        if (id != null) {
            meta = ShaderStore.readMeta(mContext, id);
            source = ShaderStore.readText(ShaderStore.sourceFile(mContext, id));
        }
        mEditor.show(id, meta, source, LiveSceneLogic.showsBackToPhoto(readStored()),
            mPicker.alsoPhone().isChecked(), heldReason());
        mEditorSaved = id != null;
        mListScrollY = mList.scrollY();
        goTo(Page.EDITOR, true);
    }

    /** A bundled example in the editor; nothing is saved until Apply, which saves a copy. */
    private void openExample(@NonNull String slug) {
        if (mList == null || mEditor == null || mReleased) return;
        ShaderExamples.Example example = ShaderExamples.find(mContext, slug);
        if (example == null || example.source == null) return;
        mEditor.showExample(example.meta(), example.source, LiveSceneLogic.showsBackToPhoto(readStored()),
            mPicker.alsoPhone().isChecked(), heldReason());
        mEditorSaved = false;
        mListScrollY = mList.scrollY();
        goTo(Page.EDITOR, true);
    }

    /** The editor goes without applying: a new shader's folder, or a texture picked and not saved, is dropped. */
    private void discardEditor() {
        if (mEditor == null || mEditorSaved) return;
        final String id = mEditor.id();
        final String unsaved = mEditor.unsavedTexture();
        final Context app = mContext.getApplicationContext();
        if (mEditor.isNew()) IO.execute(() -> ShaderStore.delete(app, id));
        else if (unsaved != null) IO.execute(() -> ShaderStore.removeTexture(app, id, unsaved));
    }

    @Nullable
    private String heldReason() {
        Supplier<String> s = mHeldReason;
        return s == null ? null : s.get();
    }

    /** The surface shows the page (again); the thumbnails follow their own visibility. */
    public void onShown() {
        if (mReleased || mKind != LiveSceneLogic.Kind.SHADER || mPage != Page.LIST) return;
        if (mList != null) mList.bind(readStored());
    }

    // --- Apply, Back to photo ---

    private void applyShader(@NonNull ShaderEditorPage.Draft draft) {
        if (mEditor == null || mReleased) return;
        mEditor.setBusy(true);
        final Context app = mContext.getApplicationContext();
        IO.execute(() -> {
            List<ShaderErrors.Error> errors = ShaderCheck.run(draft.source);
            if (ShaderEditorLogic.applyDecision(errors) == ShaderEditorLogic.Decision.STAY) {
                mRoot.post(() -> {
                    if (mReleased || mEditor == null) return;
                    mEditor.showErrors(errors);
                    mEditor.setBusy(false);
                });
                return;
            }
            // An example is never edited in place: Apply saves a copy (draft.id is a fresh id),
            // its name made different from every saved shader's.
            final ShaderEditorPage.Draft toSave = ShaderEditorLogic.savesAsCopy(draft.isExample, draft.isNew)
                && draft.isExample ? withUniqueName(app, draft) : draft;
            boolean saved = saveShader(app, toSave);
            mRoot.post(() -> {
                if (mReleased || mEditor == null) return;
                if (!saved) {
                    mEditor.setBusy(false);
                    toast(R.string.wallpaper_picker_apply_failed);
                    return;
                }
                mEditorSaved = true;
                mEditor.showErrors(null);
                useMotion(WallpaperMotion.shader(draft.id));
            });
        });
    }

    @NonNull
    private static ShaderEditorPage.Draft withUniqueName(@NonNull Context app, @NonNull ShaderEditorPage.Draft d) {
        List<String> names = new java.util.ArrayList<>();
        for (ShaderStore.Entry e : ShaderStore.list(app)) names.add(e.name);
        String unique = ShaderEditorLogic.uniqueName(d.name, names);
        return unique.equals(d.name) ? d : d.withName(unique);
    }

    private static boolean saveShader(@NonNull Context app, @NonNull ShaderEditorPage.Draft d) {
        try {
            ShaderStore.writeSource(app, d.id, d.source);
            ShaderStore.writeMeta(app, d.id, new ShaderStore.Meta(d.name, d.quality, d.rateHz, d.texture));
            if (d.savedTexture != null && !d.savedTexture.equals(d.texture)) {
                ShaderStore.removeTexture(app, d.id, d.savedTexture);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Saving the shader failed", e);
            return false;
        }
    }

    /** Starts {@code motion} and puts its still on Home and Lock where that applies. */
    private void useMotion(@NonNull WallpaperMotion motion) {
        if (mReleased) return;
        setBusy(true);
        boolean also = mPicker.alsoPhone().isChecked();
        LiveWallpaperApplier.useMotion(mContext, motion, mHomeApp, also, result -> {
            if (mReleased) return;
            setBusy(false);
            switch (result) {
                case SET:
                    mListener.onSystemWallpaperChanged();
                    break;
                case STILL_FAILED:
                    toast(R.string.live_still_failed);
                    break;
                case FAILED:
                    toast(R.string.wallpaper_picker_apply_failed);
                    break;
                case IN_APP_ONLY:
                default:
                    break;
            }
            mPhotoStale = true;
            if (mEditor != null) mEditor.setBackToPhotoVisible(true);
            if (mVideo != null) mVideo.setBackToPhotoVisible(true);
            // A shader that is on goes back to the list, where "In use" confirms it; a message stays on its page.
            if (!LiveSceneLogic.staysOnScenePage(result) && mPage == Page.EDITOR) showList();
        });
    }

    private void backToPhoto() {
        if (mReleased) return;
        setBusy(true);
        LiveWallpaperApplier.backToPhoto(mContext, result -> {
            if (mReleased) return;
            setBusy(false);
            mPhotoStale = true;
            if (result == LiveWallpaperApplier.Result.SET) mListener.onSystemWallpaperChanged();
            else if (result == LiveWallpaperApplier.Result.FAILED) toast(R.string.wallpaper_picker_apply_failed);
            // Back on the list (or the video page), with nothing in use.
            if (mPage == Page.EDITOR) showList();
            else if (mPage == Page.VIDEO) showVideo();
            else if (mList != null) mList.bind(readStored());
        });
    }

    private void deleteShader(@NonNull String id) {
        if (mReleased) return;
        final Context app = mContext.getApplicationContext();
        mEditorSaved = true;
        if (LiveSceneLogic.inUse(readStored(), id)) {
            // The running shader goes: the photo is back first, so nothing draws a deleted shader.
            setBusy(true);
            LiveWallpaperApplier.backToPhoto(mContext, result -> {
                if (mReleased) return;
                setBusy(false);
                mPhotoStale = true;
                if (result == LiveWallpaperApplier.Result.SET) mListener.onSystemWallpaperChanged();
                deleteThenShowList(app, id);
            });
        } else {
            deleteThenShowList(app, id);
        }
    }

    /** The folder is gone before the list reads again, so the deleted shader does not come back as a tile. */
    private void deleteThenShowList(@NonNull Context app, @NonNull String id) {
        IO.execute(() -> {
            ShaderStore.delete(app, id);
            mRoot.post(() -> {
                if (!mReleased) showList();
            });
        });
    }

    private void setBusy(boolean busy) {
        if (mEditor != null) mEditor.setBusy(busy);
        if (mVideo != null) mVideo.setBusy(busy);
    }

    // --- the pickers' results ---

    private void registerLaunchers() {
        ComponentActivity activity = activityOf(mContext);
        if (activity == null) return;
        String prefix = "tl_wallpaper_live_" + System.identityHashCode(this) + "_";
        mImportLauncher = activity.getActivityResultRegistry().register(prefix + "import",
            new ActivityResultContracts.OpenDocument(), this::onShaderFilePicked);
        mTextureLauncher = activity.getActivityResultRegistry().register(prefix + "texture",
            new ActivityResultContracts.PickVisualMedia(), this::onTexturePicked);
        mVideoLauncher = activity.getActivityResultRegistry().register(prefix + "video",
            new ActivityResultContracts.PickVisualMedia(), this::onVideoPicked);
    }

    @Nullable
    private static ComponentActivity activityOf(@NonNull Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof ComponentActivity) return (ComponentActivity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private void onShaderFilePicked(@Nullable Uri uri) {
        if (uri == null || mReleased || mEditor == null) return;
        final Context app = mContext.getApplicationContext();
        IO.execute(() -> {
            String text = readText(app, uri);
            mRoot.post(() -> {
                if (mReleased || mEditor == null) return;
                if (text == null) toast(R.string.shader_import_failed);
                else mEditor.setSource(text);
            });
        });
    }

    @Nullable
    private static String readText(@NonNull Context app, @NonNull Uri uri) {
        try (InputStream in = app.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_IMPORT_BYTES) return null;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the shader file failed", e);
            return null;
        }
    }

    private void onTexturePicked(@Nullable Uri uri) {
        final String id = mTextureTarget;
        mTextureTarget = null;
        if (uri == null || id == null || mReleased || mEditor == null) return;
        final Context app = mContext.getApplicationContext();
        IO.execute(() -> {
            String stored = null;
            try {
                stored = ShaderStore.importTexture(app, id, uri, displayName(app, uri), null);
            } catch (IOException | RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Copying the texture failed", e);
            }
            final String name = stored;
            mRoot.post(() -> {
                if (mReleased || mEditor == null || !id.equals(mEditor.id())) return;
                if (name == null) toast(R.string.shader_texture_failed);
                else mEditor.setTexture(name);
            });
        });
    }

    @Nullable
    private static String displayName(@NonNull Context app, @NonNull Uri uri) {
        ContentResolver cr = app.getContentResolver();
        try (Cursor c = cr.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignored) {
            // No name: the texture is stored under a plain one.
        }
        return null;
    }

    private void onVideoPicked(@Nullable Uri uri) {
        if (uri == null || mReleased || mVideo == null) return;
        mVideo.setBusy(true);
        mVideo.setHint(null);
        final Context app = mContext.getApplicationContext();
        final VideoSource source = mVideoSource;
        IO.execute(() -> {
            VideoSource.Result result;
            try {
                result = source.importFile(app, uri);
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Importing the video failed", e);
                result = new VideoSource.Result(false, null, null, null);
            }
            final VideoSource.Result done = result;
            mRoot.post(() -> {
                if (mReleased || mVideo == null) return;
                if (!done.ok || done.name == null) {
                    mVideo.setBusy(false);
                    mVideo.setHint(done.error != null ? done.error : mContext.getString(R.string.video_failed));
                    return;
                }
                SharedPreferences prefs = prefs();
                if (prefs != null) prefs.edit().putString(KEY_VIDEO_HINT, done.hint == null ? "" : done.hint).apply();
                mVideo.setHint(videoStatus(done.name, done.hint));
                useMotion(WallpaperMotion.video(done.name));
            });
        });
    }

    /** "In use: name" and, under it, the import's one-line hint. */
    @NonNull
    private String videoStatus(@NonNull String name, @Nullable String hint) {
        String line = mContext.getString(R.string.video_in_use, name);
        return hint == null || hint.isEmpty() ? line : line + "\n" + hint;
    }

    private void toast(@StringRes int res) {
        Toast.makeText(mContext, res, Toast.LENGTH_LONG).show();
    }

    /** The app's preferences store, or null when it cannot be built. */
    @Nullable
    private SharedPreferences prefs() {
        try {
            TermuxAppSharedPreferences p = TermuxAppSharedPreferences.build(mContext.getApplicationContext(), false);
            if (p != null) return p.getSharedPreferences();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the wallpaper preferences failed", e);
        }
        return null;
    }

    @NonNull
    private WallpaperMotion readStored() {
        SharedPreferences prefs = prefs();
        return prefs == null ? WallpaperMotion.photo() : ShaderStore.readMotion(prefs);
    }

    // --- tests ---

    @NonNull
    LiveSceneLogic.Kind kind() {
        return mKind;
    }

    @Nullable
    ShaderListPage list() {
        return mList;
    }

    @Nullable
    ShaderEditorPage editor() {
        return mEditor;
    }

    @Nullable
    VideoPage video() {
        return mVideo;
    }

    boolean editorShown() {
        return mPage == Page.EDITOR;
    }
}
