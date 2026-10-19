package com.termux.app.fragments.settings.termux;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.termux.R;
import com.termux.app.AnimationSpeed;
import com.termux.app.ReducedMotion;
import com.termux.app.launcher.home.HomePageTransition;
import com.termux.app.launcher.home.HomeSettings;

/**
 * Settings › Home › Page transition: a miniature of two Home pages that turns with the effect
 * picked below it, and Apply. The miniature plays once when the screen opens and once per pick
 * (a tap on it plays it again, the other way), then rests: nothing draws while nobody asks. It
 * runs the same {@link HomePageTransition} curve the Home pages do, at General › Animation speed,
 * and under reduce motion shows the page it lands on at once.
 */
@Keep
public class HomePageTransitionFragment extends Fragment {

    /** One half of the preview — the page going, or the page coming — at Normal speed. */
    static final long HALF_MS = 420L;

    private HomePageTransition chosen = HomePageTransition.DEFAULT;
    private FrameLayout stage;
    private MiniPage first;
    private MiniPage second;
    @Nullable private ValueAnimator play;
    private boolean forward = true;
    private final HomePageTransition.Transform transform = new HomePageTransition.Transform();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        chosen = HomeSettings.pageTransition(context);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        NestedScrollView scroller = new NestedScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(16));
        scroller.addView(content);
        root.addView(scroller, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(stage(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));

        TextView hint = new TextView(context);
        hint.setText(R.string.settings_home_page_transition_hint);
        hint.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        hint.setPadding(0, dp(8), 0, dp(8));
        content.addView(hint);

        RadioGroup choices = new RadioGroup(context);
        for (HomePageTransition effect : HomePageTransition.values()) {
            MaterialRadioButton button = new MaterialRadioButton(context);
            button.setId(View.generateViewId());
            button.setText(titleOf(effect));
            button.setMinHeight(dp(48));
            button.setTag(effect);
            choices.addView(button, new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (effect == chosen) button.setChecked(true);
        }
        choices.setOnCheckedChangeListener((group, id) -> {
            View picked = group.findViewById(id);
            if (picked == null || !(picked.getTag() instanceof HomePageTransition)) return;
            chosen = (HomePageTransition) picked.getTag();
            forward = true;
            playPreview();
        });
        content.addView(choices);

        MaterialButton apply = new MaterialButton(context);
        apply.setText(R.string.settings_home_page_transition_apply);
        apply.setOnClickListener(v -> {
            HomeSettings.setPageTransition(requireContext(), chosen);
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        });
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        applyParams.setMargins(dp(16), dp(8), dp(16), dp(16));
        root.addView(apply, applyParams);

        stage.post(this::playPreview);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.settings_home_page_transition_title);
    }

    @Override
    public void onDestroyView() {
        if (play != null) play.cancel();
        play = null;
        super.onDestroyView();
    }

    @NonNull
    private View stage(@NonNull Context context) {
        stage = new FrameLayout(context);
        int frame = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF2A3140);
        stage.setBackgroundColor(frame);
        stage.setClipChildren(true);
        stage.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });
        stage.setClipToOutline(true);
        stage.setContentDescription(getString(R.string.settings_home_page_transition_preview));
        stage.setOnClickListener(v -> {
            forward = !forward;
            playPreview();
        });
        int ink = MaterialColors.getColor(context,
            androidx.appcompat.R.attr.colorPrimary, Color.WHITE);
        int tile = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        first = new MiniPage(context, 1, ink, tile, 0);
        second = new MiniPage(context, 2, ink, tile, 1);
        float camera = 8000f * getResources().getDisplayMetrics().density;
        first.setCameraDistance(camera);
        second.setCameraDistance(camera);
        stage.addView(second, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stage.addView(first, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        place(0f);
        return stage;
    }

    /**
     * One run of the miniature: the page on show leaves with the effect, then the other comes
     * in, as Home turns one page at a time. {@code forward} turns from page 1 to page 2.
     */
    private void playPreview() {
        if (stage == null || getContext() == null) return;
        if (play != null) play.cancel();
        float speed = AnimationSpeed.multiplier(requireContext());
        if (speed <= 0f || ReducedMotion.isEnabled(requireContext())) {
            place(2f);
            return;
        }
        android.animation.TimeInterpolator settle = HomePageTransition.settleInterpolator();
        ValueAnimator run = ValueAnimator.ofFloat(0f, 2f);
        run.setDuration(Math.round(2 * HALF_MS * (double) speed));
        // Each half on the curve the Home page settles on.
        run.setInterpolator(input -> {
            float half = input < 0.5f ? input * 2f : input * 2f - 1f;
            float eased = settle.getInterpolation(half);
            return input < 0.5f ? eased / 2f : 0.5f + eased / 2f;
        });
        run.addUpdateListener(animation -> place((float) animation.getAnimatedValue()));
        run.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (play == animation) play = null;
            }
        });
        play = run;
        run.start();
    }

    /**
     * The miniature at {@code t} of a run, 0 to 2: up to 1 the page on show leaves, from 1 the
     * other comes in from the side it was heading for.
     */
    private void place(float t) {
        if (stage == null) return;
        MiniPage leaving = forward ? first : second;
        MiniPage coming = forward ? second : first;
        float width = stage.getWidth();
        // Forward, the page leaves to the left (negative) and the next comes from the right.
        float sign = forward ? -1f : 1f;
        if (t < 1f) {
            show(leaving, sign * t, width);
            coming.setVisibility(View.INVISIBLE);
        } else {
            leaving.setVisibility(View.INVISIBLE);
            show(coming, -sign * (2f - t), width);
        }
    }

    private void show(@NonNull View page, float position, float width) {
        chosen.transform(position, width, true, transform);
        page.setVisibility(View.VISIBLE);
        page.setTranslationX(transform.translationX);
        page.setScaleX(transform.scale);
        page.setScaleY(transform.scale);
        page.setAlpha(transform.alpha);
        page.setRotationY(transform.rotationY);
        page.setPivotX(transform.pivotXFraction * width);
        page.setPivotY(page.getHeight() / 2f);
    }

    @StringRes
    static int titleOf(@NonNull HomePageTransition effect) {
        switch (effect) {
            case DEPTH: return R.string.settings_home_page_transition_depth;
            case FADE: return R.string.settings_home_page_transition_fade;
            case CAROUSEL: return R.string.settings_home_page_transition_carousel;
            case STACK: return R.string.settings_home_page_transition_stack;
            case SLIDE:
            default: return R.string.settings_home_page_transition_slide;
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** A Home page in miniature: a few app tiles and a widget block, drawn once per size. */
    private static final class MiniPage extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int ink;
        private final int tile;
        /** Which arrangement this page shows, so the two pages read as different pages. */
        private final int arrangement;

        MiniPage(@NonNull Context context, int number, int ink, int tile, int arrangement) {
            super(context);
            this.ink = ink;
            this.tile = tile;
            this.arrangement = arrangement;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setTag(number);
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            float density = getResources().getDisplayMetrics().density;
            float pad = 20f * density;
            float w = getWidth() - 2 * pad;
            float h = getHeight() - 2 * pad;
            float radius = 10f * density;
            // A widget block across the top on page 1, at the bottom on page 2.
            float blockTop = arrangement == 0 ? pad : pad + h * 0.62f;
            rect.set(pad, blockTop, pad + w, blockTop + h * 0.32f);
            paint.setColor(ink);
            paint.setAlpha(0x88);
            canvas.drawRoundRect(rect, radius, radius, paint);
            // Two rows of four app tiles in the rest of the page.
            paint.setColor(tile);
            paint.setAlpha(0x99);
            float tilesTop = arrangement == 0 ? pad + h * 0.42f : pad;
            float cell = w / 4f;
            float side = Math.min(cell, h * 0.26f) * 0.62f;
            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < 4; column++) {
                    float cx = pad + cell * column + cell / 2f;
                    float cy = tilesTop + row * (h * 0.29f) + side / 2f + 4f * density;
                    rect.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f);
                    canvas.drawRoundRect(rect, side * 0.3f, side * 0.3f, paint);
                }
            }
        }
    }
}
