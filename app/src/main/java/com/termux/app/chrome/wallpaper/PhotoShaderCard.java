package com.termux.app.chrome.wallpaper;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * "Make one from a photo": a bottom sheet over the Shader page with three steps. Copy puts the
 * instructions for an AI tool on the clipboard (the button then reads Copied until the sheet
 * closes); Paste shader closes the sheet and hands over to the host, which opens the editor on a new
 * shader holding the clipboard. The footnote links to the guide page.
 */
final class PhotoShaderCard {

    /** The user guide, on the same docs base the help panel links to. */
    static final String GUIDE_URL =
        com.termux.app.help.HelpPanelView.DOCS_BASE + "Photo_Shader_Wallpapers.md";

    /** What the card asks of its host; on the main thread. */
    interface Listener {
        /** Paste shader was tapped; the card has closed. */
        void onPasteShader();
    }

    private final Context mContext;
    private final BottomSheetDialog mDialog;
    private final MaterialButton mCopy;
    private boolean mCopied;

    private PhotoShaderCard(@NonNull Context context, @NonNull Listener listener) {
        mContext = context;
        mDialog = new BottomSheetDialog(context);
        View content = LayoutInflater.from(mDialog.getContext()).inflate(R.layout.photo_shader_card, null, false);
        mCopy = content.findViewById(R.id.photo_card_copy);
        mCopy.setOnClickListener(v -> copyInstructions());
        content.findViewById(R.id.photo_card_paste).setOnClickListener(v -> {
            mDialog.dismiss();
            listener.onPasteShader();
        });
        bindFootnote(content.findViewById(R.id.photo_card_footnote));
        mDialog.setContentView(content);
        mDialog.getBehavior().setSkipCollapsed(true);
        mDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    /** Shows the card; the caller keeps the result to {@link #dismiss} it. */
    @NonNull
    static PhotoShaderCard show(@NonNull Context context, @NonNull Listener listener) {
        PhotoShaderCard card = new PhotoShaderCard(context, listener);
        card.mDialog.show();
        return card;
    }

    void dismiss() {
        if (mDialog.isShowing()) mDialog.dismiss();
    }

    private void copyInstructions() {
        String text = readInstructions(mContext);
        ClipboardManager cm = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        if (text == null || cm == null) return;
        try {
            cm.setPrimaryClip(ClipData.newPlainText(mContext.getString(R.string.photo_shader_title), text));
        } catch (RuntimeException e) {
            return;
        }
        if (!mCopied) {
            mCopied = true;
            mCopy.setText(R.string.photo_shader_copied);
        }
    }

    /** The AI instructions (the raw resource, English), or null when it cannot be read. */
    @Nullable
    static String readInstructions(@NonNull Context context) {
        try (InputStream in = context.getResources().openRawResource(R.raw.photo_shader_instructions)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** "Then choose the same photo in the editor." followed by the link "About depth". */
    private void bindFootnote(@NonNull TextView view) {
        String note = mContext.getString(R.string.photo_shader_footnote);
        String link = mContext.getString(R.string.photo_shader_about_depth);
        SpannableStringBuilder b = new SpannableStringBuilder(note).append(' ');
        int start = b.length();
        b.append(link);
        b.setSpan(new ClickableSpan() {
            @Override public void onClick(@NonNull View widget) {
                openGuide();
            }
        }, start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(b);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setLinkTextColor(MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary));
    }

    private void openGuide() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // No browser to hand it to; nothing else sensible to do.
        }
    }

    // --- tests ---

    @NonNull
    View copyButton() {
        return mCopy;
    }
}
