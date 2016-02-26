package material.sahitya.traveljarassigment.travelLibrary;

import android.app.Fragment;
import android.support.annotation.NonNull;

import material.sahitya.traveljarassigment.travelLibrary.internal.RecordingBaseActivity;
import material.sahitya.traveljarassigment.travelLibrary.internal.RecordingFragment1;


public class RecordActivity2Recording extends RecordingBaseActivity {

    @Override
    @NonNull
    public Fragment getFragment() {
        return RecordingFragment1.newInstance();
    }
}