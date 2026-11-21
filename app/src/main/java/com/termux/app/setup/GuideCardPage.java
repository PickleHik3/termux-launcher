package com.termux.app.setup;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.settings.UseAsSetting;
import com.termux.app.wall.UseAs;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.List;
import java.util.Set;

/**
 * The "Learn the basics" card: the chapters the answers keep, each a looping demo with one
 * caption. The card is done when every listed chapter has been marked Watched.
 */
final class GuideCardPage implements SetupCardPage {

    private Context mContext;
    private Host mHost;
    private LinearLayout mRoot;
    private List<GuideChapters.Chapter> mChapters;
    @Nullable private GestureDemoView mDemo;

    @NonNull
    @Override
    public View createView(@NonNull Context context, @NonNull SetupCards.Card card,
                           @NonNull Host host) {
        mContext = context;
        mHost = host;
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        boolean home = UseAsSetting.read(context) == UseAs.HOME;
        boolean multiplexer = prefs == null || !prefs.isCompatibilityModeEnabled();
        SharedPreferences store = prefs == null ? null : prefs.getSharedPreferences();
        boolean desktop = store != null
            && store.getBoolean(SetupState.KEY_WANTS_DESKTOP_APPS, false);
        mChapters = GuideChapters.forFacts(home, multiplexer, desktop);
        mRoot = new LinearLayout(context);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        showList();
        return mRoot;
    }

    @Override
    public void onHidden() {
        stopDemo();
    }

    private void stopDemo() {
        if (mDemo != null) mDemo.stop();
        mDemo = null;
    }

    @Nullable
    private SharedPreferences store() {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(mContext);
        return prefs == null ? null : prefs.getSharedPreferences();
    }

    private void showList() {
        stopDemo();
        mRoot.removeAllViews();
        SetupUi.header(mRoot, R.string.setup_guide_title, R.string.setup_guide_description);
        Set<String> watched = GuideChapters.watched(store());
        for (GuideChapters.Chapter chapter : mChapters) {
            mRoot.addView(row(chapter, watched.contains(chapter.id)),
                SetupUi.matchWrap(SetupUi.dp(mContext, 8)));
        }
        MaterialButton back = button(R.string.setup_guide_back, false);
        back.setOnClickListener(v -> mHost.back());
        mRoot.addView(back, SetupUi.matchWrap(SetupUi.dp(mContext, 16)));
    }

    @NonNull
    private View row(@NonNull GuideChapters.Chapter chapter, boolean watched) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = SetupUi.dp(mContext, 12);
        row.setPadding(pad, pad, pad, pad);
        SetupUi.dressChoice(row, watched);
        TextView title = SetupUi.text(mContext, 16f, false, SetupUi.onSurface(mRoot));
        title.setText(chapter.title);
        row.addView(title, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (watched) {
            TextView tick = SetupUi.text(mContext, 16f, true, SetupUi.accent(mRoot));
            tick.setText("✓");
            tick.setContentDescription(mContext.getString(R.string.setup_guide_watched_mark));
            row.addView(tick);
        }
        TextView chevron = SetupUi.text(mContext, 20f, false, SetupUi.onSurfaceVariant(mRoot));
        chevron.setText("›");
        chevron.setPadding(SetupUi.dp(mContext, 12), 0, 0, 0);
        row.addView(chevron);
        row.setOnClickListener(v -> showChapter(chapter));
        return row;
    }

    private void showChapter(@NonNull GuideChapters.Chapter chapter) {
        stopDemo();
        mRoot.removeAllViews();
        TextView title = SetupUi.text(mContext, 22f, true, SetupUi.onSurface(mRoot));
        title.setText(chapter.title);
        mRoot.addView(title, SetupUi.matchWrap(0));
        GestureDemoView demo = new GestureDemoView(mContext);
        demo.setScene(GuideScenes.forChapter(chapter.id, mContext.getResources()));
        mRoot.addView(demo, SetupUi.matchWrap(SetupUi.dp(mContext, 12)));
        TextView caption = SetupUi.text(mContext, 15f, false, SetupUi.onSurfaceVariant(mRoot));
        caption.setText(chapter.caption);
        mRoot.addView(caption, SetupUi.matchWrap(SetupUi.dp(mContext, 12)));
        MaterialButton watched = button(R.string.setup_guide_watched, true);
        watched.setOnClickListener(v -> onWatched(chapter));
        mRoot.addView(watched, SetupUi.matchWrap(SetupUi.dp(mContext, 16)));
        MaterialButton back = button(R.string.setup_guide_back, false);
        back.setOnClickListener(v -> showList());
        mRoot.addView(back, SetupUi.matchWrap(SetupUi.dp(mContext, 6)));
        mDemo = demo;
        demo.start();
    }

    private void onWatched(@NonNull GuideChapters.Chapter chapter) {
        stopDemo();
        SharedPreferences store = store();
        GuideChapters.markWatched(store, chapter.id);
        Set<String> watched = GuideChapters.watched(store);
        // markWatched applies asynchronously; the id is certainly watched now.
        watched.add(chapter.id);
        if (GuideChapters.allWatched(mChapters, watched)) {
            mHost.completeStep(SetupCards.GUIDE, 0);
            mHost.completeCard(SetupCards.GUIDE);
        } else {
            showList();
        }
    }

    @NonNull
    private MaterialButton button(int textRes, boolean primary) {
        MaterialButton button = primary ? new MaterialButton(mContext)
            : new MaterialButton(mContext, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(textRes);
        button.setAllCaps(false);
        return button;
    }
}
