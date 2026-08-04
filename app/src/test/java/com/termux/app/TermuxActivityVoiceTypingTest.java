package com.termux.app;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.speech.RecognizerIntent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TermuxActivityVoiceTypingTest {

    @Test
    public void normalIntentUsesFreeFormPlatformRecognition() {
        Intent intent = TermuxActivity.createVoiceTypingIntent(RuntimeEnvironment.getApplication(), false);
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.getAction());
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL));
    }

    @Test
    public void chooserWrapsTheSameRecognitionIntent() {
        Intent chooser = TermuxActivity.createVoiceTypingIntent(RuntimeEnvironment.getApplication(), true);
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        Intent target = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, target.getAction());
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            target.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL));
    }
}
