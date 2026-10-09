package com.termux.app.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.termux.shared.termux.TermuxConstants;

/**
 * Basic embedded browser for viewing help pages.
 */
public final class HelpActivity extends AppCompatActivity {

    WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final RelativeLayout progressLayout = new RelativeLayout(this);
        RelativeLayout.LayoutParams lParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        com.google.android.material.progressindicator.CircularProgressIndicator progressBar =
            new com.google.android.material.progressindicator.CircularProgressIndicator(this);
        progressBar.setIndeterminate(true);
        progressBar.setLayoutParams(lParams);
        progressLayout.addView(progressBar);
        mWebView = new WebView(this);
        WebSettings settings = mWebView.getSettings();
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        setContentView(progressLayout);
        mWebView.clearCache(true);
        mWebView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (url.toString().equals(TermuxConstants.TERMUX_WIKI_URL) || url.toString().startsWith(TermuxConstants.TERMUX_WIKI_URL + "/")) {
                    // Inline help.
                    setContentView(progressLayout);
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException e) {
                    // Android TV does not have a system browser.
                    setContentView(progressLayout);
                    return false;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                setContentView(mWebView);
            }
        });
        mWebView.loadUrl(TermuxConstants.TERMUX_WIKI_URL);
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
