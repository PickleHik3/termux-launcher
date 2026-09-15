package com.termux.app.tour;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/** The run's five values, in the app's own store, so Settings can offer a replay later. */
public final class TourPreferences implements TourController.Prefs {

    @NonNull private final TermuxAppSharedPreferences mPreferences;

    public TourPreferences(@NonNull TermuxAppSharedPreferences preferences) {
        mPreferences = preferences;
    }

    @Override
    public int getTourCompletedVersion() {
        return mPreferences.getFirstBootTourCompletedVersion();
    }

    @Override
    public void setTourCompletedVersion(int version) {
        mPreferences.setFirstBootTourCompletedVersion(version);
    }

    @Override
    public int getTourRunVersion() {
        return mPreferences.getFirstBootTourRunVersion();
    }

    @Override
    public void setTourRunVersion(int version) {
        mPreferences.setFirstBootTourRunVersion(version);
    }

    @Override
    public int getTourStepIndex() {
        return mPreferences.getFirstBootTourStep();
    }

    @Override
    public void setTourStepIndex(int index) {
        mPreferences.setFirstBootTourStep(index);
    }

    @Override
    public int getTourStepStage() {
        return mPreferences.getFirstBootTourStepStage();
    }

    @Override
    public void setTourStepStage(int stage) {
        mPreferences.setFirstBootTourStepStage(stage);
    }

    @Override
    public boolean getTourSkipped() {
        return mPreferences.getFirstBootTourSkipped();
    }

    @Override
    public void setTourSkipped(boolean skipped) {
        mPreferences.setFirstBootTourSkipped(skipped);
    }
}
