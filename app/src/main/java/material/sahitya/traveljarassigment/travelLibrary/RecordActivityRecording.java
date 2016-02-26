package material.sahitya.traveljarassigment.travelLibrary;

import android.app.Fragment;
import android.support.annotation.NonNull;

import material.sahitya.traveljarassigment.travelLibrary.internal.RecordingBaseActivity;
import material.sahitya.traveljarassigment.travelLibrary.internal.PreviewFragment;


public class RecordActivityRecording extends RecordingBaseActivity {

    @Override
    @NonNull
    public Fragment getFragment() {
        return PreviewFragment.newInstance();
    }
}