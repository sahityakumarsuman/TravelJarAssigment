package material.sahitya.traveljarassigment.travelLibrary.internal;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;



import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import material.sahitya.traveljarassigment.R;
import material.sahitya.traveljarassigment.travelLibrary.util.CameraUtil;
import material.sahitya.traveljarassigment.travelLibrary.util.Degrees;

import static material.sahitya.traveljarassigment.travelLibrary.internal.RecordingBaseActivity.CAMERA_POSITION_BACK;
import static material.sahitya.traveljarassigment.travelLibrary.internal.RecordingBaseActivity.CAMERA_POSITION_FRONT;
import static material.sahitya.traveljarassigment.travelLibrary.internal.RecordingBaseActivity.CAMERA_POSITION_UNKNOWN;


@SuppressWarnings("deprecation")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class PreviewFragment extends RecordingFragment implements View.OnClickListener {

    CameraPreview mPreviewView;
    RelativeLayout mPreviewFrame;

    private Camera.Size mVideoSize;
    private Camera mCamera;
    private Point mWindowSize;
    private int mDisplayOrientation;

    public static PreviewFragment newInstance() {
        PreviewFragment fragment = new PreviewFragment();
        fragment.setRetainInstance(true);
        return fragment;
    }

    private static Camera.Size chooseVideoSize(RecodingInterface ci, List<Camera.Size> choices) {
        Camera.Size backupSize = null;
        for (Camera.Size size : choices) {
            if (size.height <= ci.videoPreferredHeight()) {
                if (size.width == size.height * ci.videoPreferredAspect())
                    return size;
                if (ci.videoPreferredHeight() >= size.height)
                    backupSize = size;
            }
        }
        if (backupSize != null) return backupSize;
        LOG(PreviewFragment.class, "Couldn't find any suitable video size");
        return choices.get(choices.size() - 1);
    }

    private static Camera.Size chooseOptimalSize(List<Camera.Size> choices, int width, int height, Camera.Size aspectRatio) {

        List<Camera.Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.width;
        int h = aspectRatio.height;
        for (Camera.Size option : choices) {
            if (option.height == width * h / w &&
                    option.width >= width && option.height >= height) {
                bigEnough.add(option);
            }
        }


        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            LOG(PreviewFragment.class, "Couldn't find any suitable preview size");
            return choices.get(0);
        }
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPreviewFrame = (RelativeLayout) view.findViewById(R.id.rootFrame);
        mPreviewFrame.setOnClickListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPreviewFrame = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        openCamera();
    }

    @Override
    public void onPause() {
        if (mCamera != null) mCamera.lock();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.rootFrame) {
            if (mCamera == null) return;
            mCamera.cancelAutoFocus();
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    if (!success)
                        Toast.makeText(getActivity(), "Unable to auto-focus!", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            super.onClick(view);
        }
    }

    @Override
    public void openCamera() {
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) return;
        try {
            final int mBackCameraId = mInterface.getBackCamera() != null ? (Integer) mInterface.getBackCamera() : -1;
            final int mFrontCameraId = mInterface.getFrontCamera() != null ? (Integer) mInterface.getFrontCamera() : -1;
            if (mBackCameraId == -1 || mFrontCameraId == -1) {
                int numberOfCameras = Camera.getNumberOfCameras();
                if (numberOfCameras == 0) {
                    throwError(new Exception("No cameras are available on this device."));
                    return;
                }

                for (int i = 0; i < numberOfCameras; i++) {

                    if (mFrontCameraId != -1 && mBackCameraId != -1) break;
                    Camera.CameraInfo info = new Camera.CameraInfo();
                    Camera.getCameraInfo(i, info);
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && mFrontCameraId == -1) {
                        mInterface.setFrontCamera(i);
                    } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK && mBackCameraId == -1) {
                        mInterface.setBackCamera(i);
                    }
                }
            }
            if (getCurrentCameraPosition() == CAMERA_POSITION_UNKNOWN) {
                if (getArguments().getBoolean(Key.DEFAULT_TO_FRONT_FACING, false)) {

                    if (mInterface.getFrontCamera() != null && (Integer) mInterface.getFrontCamera() != -1) {
                        mButtonFacing.setImageResource(R.drawable.aa_camera_switch_button);
                        mInterface.setCameraPosition(CAMERA_POSITION_FRONT);
                    } else {
                        mButtonFacing.setImageResource(R.drawable.aa_camera_switch_button);
                        if (mInterface.getBackCamera() != null && (Integer) mInterface.getBackCamera() != -1)
                            mInterface.setCameraPosition(CAMERA_POSITION_BACK);
                        else mInterface.setCameraPosition(CAMERA_POSITION_UNKNOWN);
                    }
                } else {

                    if (mInterface.getBackCamera() != null && (Integer) mInterface.getBackCamera() != -1) {
                        mButtonFacing.setImageResource(R.drawable.aa_camera_switch_button);
                        mInterface.setCameraPosition(CAMERA_POSITION_BACK);
                    } else {
                        mButtonFacing.setImageResource(R.drawable.aa_camera_switch_button);
                        if (mInterface.getFrontCamera() != null && (Integer) mInterface.getFrontCamera() != -1)
                            mInterface.setCameraPosition(CAMERA_POSITION_FRONT);
                        else mInterface.setCameraPosition(CAMERA_POSITION_UNKNOWN);
                    }
                }
            }

            if (mWindowSize == null)
                mWindowSize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(mWindowSize);
            final int toOpen = getCurrentCameraId();
            mCamera = Camera.open(toOpen == -1 ? 0 : toOpen);
            Camera.Parameters parameters = mCamera.getParameters();
            List<Camera.Size> videoSizes = parameters.getSupportedVideoSizes();
            if (videoSizes == null || videoSizes.size() == 0)
                videoSizes = parameters.getSupportedPreviewSizes();
            mVideoSize = chooseVideoSize((RecordingBaseActivity) activity, videoSizes);
            Camera.Size previewSize = chooseOptimalSize(parameters.getSupportedPreviewSizes(),
                    mWindowSize.x, mWindowSize.y, mVideoSize);
            parameters.setPreviewSize(previewSize.width, previewSize.height);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                parameters.setRecordingHint(true);
            setCameraDisplayOrientation(parameters);
            mCamera.setParameters(parameters);
            createPreview();
            mMediaRecorder = new MediaRecorder();
        } catch (IllegalStateException e) {
            throwError(new Exception("Cannot access the camera.", e));
        } catch (RuntimeException e2) {
            throwError(new Exception("Cannot access the camera, you may need to restart your device.", e2));
        }
    }


    private void setCameraDisplayOrientation(Camera.Parameters parameters) {
        Camera.CameraInfo info =
                new Camera.CameraInfo();
        Camera.getCameraInfo(getCurrentCameraId(), info);
        final int deviceOrientation = Degrees.getDisplayRotation(getActivity());
        mDisplayOrientation = Degrees.getDisplayOrientation(
                info.orientation, deviceOrientation, info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        Log.d("PreviewFragment", String.format("Orientations: Sensor = %d˚, Device = %d˚, Display = %d˚",
                info.orientation, deviceOrientation, mDisplayOrientation));

        int previewOrientation;
        if (CameraUtil.isArcWelder()) {
            previewOrientation = 0;
        } else {
            previewOrientation = mDisplayOrientation;
            if (Degrees.isPortrait(deviceOrientation) && getCurrentCameraPosition() == CAMERA_POSITION_FRONT)
                previewOrientation = Degrees.mirror(mDisplayOrientation);
        }
        parameters.setRotation(previewOrientation);
        mCamera.setDisplayOrientation(previewOrientation);
    }

    private void createPreview() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (mWindowSize == null)
            mWindowSize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(mWindowSize);
        mPreviewView = new CameraPreview(getActivity(), mCamera);
        if (mPreviewFrame.getChildCount() > 0 && mPreviewFrame.getChildAt(0) instanceof CameraPreview)
            mPreviewFrame.removeViewAt(0);
        mPreviewFrame.addView(mPreviewView, 0);
        mPreviewView.setAspectRatio(mWindowSize.x, mWindowSize.y);
    }

    @Override
    public void closeCamera() {
        try {
            if (mCamera != null) {
                try {
                    mCamera.lock();
                } catch (Throwable ignored) {
                }
                mCamera.release();
                mCamera = null;
            }
        } catch (IllegalStateException e) {
            throwError(new Exception("Illegal state while trying to close camera.", e));
        }
    }

    private boolean prepareMediaRecorder() {
        return prepareMediaRecorder(CamcorderProfile.QUALITY_480P);
    }

    private boolean prepareMediaRecorder(int forceQuality) {
        try {
            final Activity activity = getActivity();
            if (null == activity) return false;
            final RecodingInterface captureInterface = (RecodingInterface) activity;

            setCameraDisplayOrientation(mCamera.getParameters());
            mMediaRecorder = new MediaRecorder();
            mCamera.stopPreview();
            mCamera.unlock();
            mMediaRecorder.setCamera(mCamera);

            Log.d("Camera1Fragment", String.format(
                    "Bit rate: %d, Frame rate: %d, Resolution: %s",
                    captureInterface.videoBitRate(), captureInterface.videoFrameRate(),
                    String.format(Locale.getDefault(), "%dx%d", mVideoSize.width, mVideoSize.height)));

            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
            mMediaRecorder.setProfile(CamcorderProfile.get(getCurrentCameraId(), forceQuality));
            mMediaRecorder.setVideoSize(mVideoSize.width, mVideoSize.height);
            if (captureInterface.videoFrameRate() > 0)
                mMediaRecorder.setVideoFrameRate(captureInterface.videoFrameRate());
            if (captureInterface.videoBitRate() > 0)
                mMediaRecorder.setVideoEncodingBitRate(captureInterface.videoBitRate());

            Uri uri = Uri.fromFile(getOutputMediaFile());
            mOutputUri = uri.toString();
            mMediaRecorder.setOutputFile(uri.getPath());

            mMediaRecorder.setOrientationHint(mDisplayOrientation);
            mMediaRecorder.setPreviewDisplay(mPreviewView.getHolder().getSurface());

            try {
                mMediaRecorder.prepare();
                return true;
            } catch (Throwable e) {
                throwError(new Exception("Failed to prepare the media recorder: " + e.getMessage(), e));
                return false;
            }
        } catch (Throwable t) {
            try {
                mCamera.lock();
            } catch (IllegalStateException e) {
                throwError(new Exception("Failed to re-lock camera: " + e.getMessage(), e));
                return false;
            }
            if (forceQuality == CamcorderProfile.QUALITY_480P)
                return prepareMediaRecorder(CamcorderProfile.QUALITY_720P);
            else if (forceQuality == CamcorderProfile.QUALITY_720P)
                return prepareMediaRecorder(CamcorderProfile.QUALITY_LOW);
            else if (forceQuality == CamcorderProfile.QUALITY_LOW) {
                return prepareMediaRecorder(CamcorderProfile.QUALITY_1080P);
            }
            throwError(new Exception("Failed to begin recording: " + t.getMessage(), t));
            return false;
        }
    }

    @Override
    public boolean startRecordingVideo() {
        super.startRecordingVideo();
        if (prepareMediaRecorder()) {
            try {

                mButtonVideo.setImageResource(R.drawable.aa_camera_feed_button_chat_notification);
                if (!CameraUtil.isArcWelder())
                    mButtonFacing.setVisibility(View.GONE);


                if (!mInterface.hasLengthLimit()) {
                    mInterface.setRecordingStart(System.currentTimeMillis());
                    startCounter();
                }


                mMediaRecorder.start();

                mButtonVideo.setEnabled(false);
                mButtonVideo.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mButtonVideo.setEnabled(true);
                    }
                }, 200);

                return true;
            } catch (Throwable t) {
                t.printStackTrace();
                mInterface.setRecordingStart(-1);
                stopRecordingVideo(false);
                throwError(new Exception("Failed to start recording: " + t.getMessage(), t));
            }
        }
        return false;
    }

    @Override
    public void stopRecordingVideo(final boolean reachedZero) {
        super.stopRecordingVideo(reachedZero);

        if (mInterface.hasLengthLimit() && mInterface.shouldAutoSubmit() &&
                (mInterface.getRecordingStart() < 0 || mMediaRecorder == null)) {
            stopCounter();
            if (mCamera != null) {
                try {
                    mCamera.lock();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            releaseRecorder();
            closeCamera();
            mButtonFacing.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mInterface.onShowPreview(mOutputUri, reachedZero);
                }
            }, 100);
            return;
        }

        if (mCamera != null)
            mCamera.lock();
        releaseRecorder();
        closeCamera();

        if (!mInterface.didRecord())
            mOutputUri = null;

        mButtonVideo.setImageResource(R.drawable.capture);
        if (!CameraUtil.isArcWelder())
            mButtonFacing.setVisibility(View.VISIBLE);
        if (mInterface.getRecordingStart() > -1 && getActivity() != null)
            mInterface.onShowPreview(mOutputUri, reachedZero);

        stopCounter();
    }

    static class CompareSizesByArea implements Comparator<Camera.Size> {
        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return Long.signum((long) lhs.width * lhs.height -
                    (long) rhs.width * rhs.height);
        }
    }
}