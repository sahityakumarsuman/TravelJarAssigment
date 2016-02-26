package material.sahitya.traveljarassigment.travelLibrary.internal;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


import java.io.File;

import material.sahitya.traveljarassigment.R;
import material.sahitya.traveljarassigment.travelLibrary.Camera;
import material.sahitya.traveljarassigment.travelLibrary.util.CameraUtil;
import material.sahitya.traveljarassigment.travelLibrary.util.Degrees;

import static android.app.Activity.RESULT_CANCELED;
import static material.sahitya.traveljarassigment.travelLibrary.internal.RecordingBaseActivity.CAMERA_POSITION_BACK;



abstract class RecordingFragment extends Fragment implements CameraInterface, View.OnClickListener {

    protected ImageButton mButtonVideo;
    protected ImageButton mButtonFacing;
    protected TextView mRecordDuration;

    private boolean mIsRecording;
    protected String mOutputUri;
    protected RecodingInterface mInterface;
    protected Handler mPositionHandler;
    protected MediaRecorder mMediaRecorder;

    protected static void LOG(Object context, String message) {
        Log.d(context instanceof Class<?> ? ((Class<?>) context).getSimpleName() :
                context.getClass().getSimpleName(), message);
    }

    private final Runnable mPositionUpdater = new Runnable() {
        @Override
        public void run() {
            if (mInterface == null || mRecordDuration == null) return;
            final long mRecordStart = mInterface.getRecordingStart();
            final long mRecordEnd = mInterface.getRecordingEnd();
            if (mRecordStart == -1 && mRecordEnd == -1) return;
            final long now = System.currentTimeMillis();
            if (mRecordEnd != -1) {
                if (now >= mRecordEnd) {
                    stopRecordingVideo(true);
                } else {
                    final long diff = mRecordEnd - now;
                    mRecordDuration.setText(String.format("-%s", CameraUtil.getDurationString(diff)));
                }
            } else {
                mRecordDuration.setText(CameraUtil.getDurationString(now - mRecordStart));
            }
            if (mPositionHandler != null)
                mPositionHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.videocapture_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mButtonVideo = (ImageButton) view.findViewById(R.id.video);
        mButtonFacing = (ImageButton) view.findViewById(R.id.facing);
        if (CameraUtil.isArcWelder())
            mButtonFacing.setVisibility(View.GONE);
        mRecordDuration = (TextView) view.findViewById(R.id.recordDuration);
        mButtonFacing.setImageResource(mInterface.getCurrentCameraPosition() == CAMERA_POSITION_BACK ?
                R.drawable.aa_camera_switch_button : R.drawable.aa_camera_switch_button);
        if (mMediaRecorder != null && mIsRecording) {
            mButtonVideo.setImageResource(R.drawable.aa_camera_feed_button_chat_notification);
        } else {
            mButtonVideo.setImageResource(R.drawable.capture);
            mInterface.setDidRecord(false);
        }

        mButtonVideo.setOnClickListener(this);
        mButtonFacing.setOnClickListener(this);

        final int primaryColor = getArguments().getInt(Key.PRIMARY_COLOR);
        view.findViewById(R.id.controlsFrame).setBackgroundColor(CameraUtil.darkenColor(primaryColor));

        if (savedInstanceState != null)
            mOutputUri = savedInstanceState.getString("output_uri");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mButtonVideo = null;
        mButtonFacing = null;
        mRecordDuration = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mInterface != null && mInterface.hasLengthLimit()) {
            if (mInterface.countdownImmediately() || mInterface.getRecordingStart() > -1) {
                if (mInterface.getRecordingStart() == -1)
                    mInterface.setRecordingStart(System.currentTimeMillis());
                startCounter();
            } else {
                mRecordDuration.setText(String.format("-%s", CameraUtil.getDurationString(mInterface.getLengthLimit())));
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public final void onAttach(Activity activity) {
        super.onAttach(activity);
        mInterface = (RecodingInterface) activity;
    }

    @NonNull
    protected final File getOutputMediaFile() {
        return CameraUtil.makeTempFile(getActivity(), getArguments().getString(Key.SAVE_DIR), ".mp4");
    }

    public abstract void openCamera();

    public abstract void closeCamera();

    @Override
    public void onPause() {
        super.onPause();
        closeCamera();
        releaseRecorder();
        stopCounter();
    }

    @Override
    public final void onDetach() {
        super.onDetach();
        mInterface = null;
    }

    public final void startCounter() {
        if (mPositionHandler == null)
            mPositionHandler = new Handler();
        else mPositionHandler.removeCallbacks(mPositionUpdater);
        mPositionHandler.post(mPositionUpdater);
    }

    @RecordingBaseActivity.CameraPosition
    public final int getCurrentCameraPosition() {
        if (mInterface == null) return RecordingBaseActivity.CAMERA_POSITION_UNKNOWN;
        return mInterface.getCurrentCameraPosition();
    }

    public final int getCurrentCameraId() {
        if (mInterface.getCurrentCameraPosition() == RecordingBaseActivity.CAMERA_POSITION_BACK)
            return (Integer) mInterface.getBackCamera();
        else return (Integer) mInterface.getFrontCamera();
    }

    public final void stopCounter() {
        if (mPositionHandler != null) {
            mPositionHandler.removeCallbacks(mPositionUpdater);
            mPositionHandler = null;
        }
    }

    public final void releaseRecorder() {
        if (mMediaRecorder != null) {
            if (mIsRecording) {
                try {
                    mMediaRecorder.stop();
                } catch (Throwable t) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(mOutputUri).delete();
                    t.printStackTrace();
                }
                mIsRecording = false;
            }
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    public boolean startRecordingVideo() {
        if (mInterface != null && mInterface.hasLengthLimit() && !mInterface.countdownImmediately()) {
            if (mInterface.getRecordingStart() == -1)
                mInterface.setRecordingStart(System.currentTimeMillis());
            startCounter();
        }

        final int orientation = Degrees.getActivityOrientation(getActivity());
        getActivity().setRequestedOrientation(orientation);
        mInterface.setDidRecord(true);
        return true;
    }

    public void stopRecordingVideo(boolean reachedZero) {
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    public final void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("output_uri", mOutputUri);
    }

    @Override
    public final String getOutputUri() {
        return mOutputUri;
    }

    protected final void throwError(Exception e) {
        Activity act = getActivity();
        if (act != null) {
            act.setResult(RESULT_CANCELED, new Intent().putExtra(Camera.ERROR_EXTRA, e));
            act.finish();
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.facing) {
            mInterface.toggleCameraPosition();
            mButtonFacing.setImageResource(mInterface.getCurrentCameraPosition() == RecordingBaseActivity.CAMERA_POSITION_BACK ?
                    R.drawable.aa_camera_switch_button : R.drawable.aa_camera_switch_button);
            closeCamera();
            openCamera();
        } else if (view.getId() == R.id.video) {
            if (mIsRecording) {
                stopRecordingVideo(false);
                mIsRecording = false;
            } else {
                if (getArguments().getBoolean(Key.SHOW_PORTRAIT_WARNING, true) &&
                        Degrees.isPortrait(getActivity())) {

                    Toast.makeText(getActivity(), R.string.portrait_warning, Toast.LENGTH_SHORT).show();
                } else {
                    mIsRecording = startRecordingVideo();
                }
            }
        }
    }
}