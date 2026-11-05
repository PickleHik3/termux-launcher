package com.termux.app.assistant;

import android.content.Context;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

/**
 * The Assistant page's content (ADR 0022). For now an empty container wearing the page's ground:
 * the chat's views are added to it by the page's own code, so the wall and {@code TermuxActivity}
 * never need to know what is inside.
 */
public class AssistantPageView extends FrameLayout {

    public AssistantPageView(@NonNull Context context) {
        super(context);
        setBackgroundColor(MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurface, 0xFF000000));
    }
}
