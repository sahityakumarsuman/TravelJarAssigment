package material.sahitya.traveljarassigment.travelLibrary.internal;

import android.support.annotation.Nullable;


public interface RecodingInterface {

    void onRetry(@Nullable String outputUri);

    void onShowPreview(@Nullable String outputUri, boolean countdownIsAtZero);

    void setRecordingStart(long start);

    void setRecordingEnd(long end);

    long getRecordingStart();

    long getRecordingEnd();

    boolean hasLengthLimit();

    boolean countdownImmediately();

    long getLengthLimit();

    void setCameraPosition(int position);

    void toggleCameraPosition();

    Object getCurrentCameraId();

    @RecordingBaseActivity.CameraPosition
    int getCurrentCameraPosition();

    void setFrontCamera(Object id);

    void setBackCamera(Object id);

    Object getFrontCamera();

    Object getBackCamera();

    void useVideo(String uri);

    boolean shouldAutoSubmit();

    boolean allowRetry();

    void setDidRecord(boolean didRecord);

    boolean didRecord();

    boolean restartTimerOnRetry();

    boolean continueTimerInPlayback();

    int videoBitRate();

    int videoFrameRate();

    int videoPreferredHeight();

    float videoPreferredAspect();
}
