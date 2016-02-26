package material.sahitya.traveljarassigment.travelLibrary.internal;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import material.sahitya.traveljarassigment.R;
import material.sahitya.traveljarassigment.travelLibrary.util.CameraUtil;
import material.sahitya.traveljarassigment.travelLibrary.util.Degrees;

import static material.sahitya.traveljarassigment.travelLibrary.internal.RecordingBaseActivity.CAMERA_POSITION_BACK;
import static material.sahitya.traveljarassigment.travelLibrary.internal.RecordingBaseActivity.CAMERA_POSITION_FRONT;
import static material.sahitya.traveljarassigment.travelLibrary.internal.RecordingBaseActivity.CAMERA_POSITION_UNKNOWN;



@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class RecordingFragment1 extends RecordingFragment implements View.OnClickListener {

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    private AutoFitScreen mTextureView;

    private Size mPreviewSize;
    private Size mVideoSize;
    @Degrees.DegreeUnits
    private int mDisplayOrientation;
    private CaptureRequest.Builder mPreviewBuilder;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;

            String errorMsg = "Unknown camera error";
            switch (error) {
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    errorMsg = "Camera is already in use.";
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    errorMsg = "Max number of cameras are open, close previous cameras first.";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    errorMsg = "Camera is disabled, e.g. due to device policies.";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    errorMsg = "Camera device has encountered a fatal error, please try again.";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    errorMsg = "Camera service has encountered a fatal error, please try again.";
                    break;
            }
            throwError(new Exception(errorMsg));
        }
    };

    public static RecordingFragment1 newInstance() {
        RecordingFragment1 fragment = new RecordingFragment1();
        fragment.setRetainInstance(true);
        return fragment;
    }


    private static Size chooseVideoSize(RecodingInterface ci, Size[] choices) {
        Size backupSize = null;
        for (Size size : choices) {
            if (size.getHeight() <= ci.videoPreferredHeight()) {
                if (size.getWidth() == size.getHeight() * ci.videoPreferredAspect())
                    return size;
                if (ci.videoPreferredHeight() >= size.getHeight())
                    backupSize = size;
            }
        }
        if (backupSize != null) return backupSize;
        return choices[choices.length - 1];
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTextureView = (AutoFitScreen) view.findViewById(R.id.texture);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mTextureView = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        stopCounter();
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void openCamera() {
        final int width = mTextureView.getWidth();
        final int height = mTextureView.getHeight();
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) return;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throwError(new Exception("Time out waiting to lock camera opening."));
                return;
            }
            if (mInterface.getFrontCamera() == null || mInterface.getBackCamera() == null) {
                for (String cameraId : manager.getCameraIdList()) {
                    if (cameraId == null) continue;
                    if (mInterface.getFrontCamera() != null && mInterface.getBackCamera() != null)
                        break;
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                    int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT)
                        mInterface.setFrontCamera(cameraId);
                    else if (facing == CameraCharacteristics.LENS_FACING_BACK)
                        mInterface.setBackCamera(cameraId);
                }
            }
            if (mInterface.getCurrentCameraPosition() == CAMERA_POSITION_UNKNOWN) {
                if (getArguments().getBoolean(Key.DEFAULT_TO_FRONT_FACING, false)) {
                    if (mInterface.getFrontCamera() != null) {
                        mButtonFacing.setImageResource(R.drawable.aa_camera_switch_button);
                        mInterface.setCameraPosition(CAMERA_POSITION_FRONT);
                    } else {
                        mButtonFacing.setImageResource(R.drawable.aa_camera_switch_button);
                        if (mInterface.getBackCamera() != null)
                            mInterface.setCameraPosition(CAMERA_POSITION_BACK);
                        else mInterface.setCameraPosition(CAMERA_POSITION_UNKNOWN);
                    }
                } else {
                    if (mInterface.getBackCamera() != null) {
                        mButtonFacing.setImageResource(R.drawable.aa_camera_switch_button);
                        mInterface.setCameraPosition(CAMERA_POSITION_BACK);
                    } else {
                        mButtonFacing.setImageResource(R.drawable.aa_camera_switch_button);
                        if (mInterface.getFrontCamera() != null)
                            mInterface.setCameraPosition(CAMERA_POSITION_FRONT);
                        else mInterface.setCameraPosition(CAMERA_POSITION_UNKNOWN);
                    }
                }
            }

            CameraCharacteristics characteristics = manager.getCameraCharacteristics((String) mInterface.getCurrentCameraId());
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            mVideoSize = chooseVideoSize((RecodingInterface) activity, map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);

            @Degrees.DegreeUnits
            final int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            @Degrees.DegreeUnits
            int deviceRotation = Degrees.getDisplayRotation(getActivity());
            mDisplayOrientation = Degrees.getDisplayOrientation(
                    sensorOrientation, deviceRotation, getCurrentCameraPosition() == CAMERA_POSITION_FRONT);
            Log.d("RecordingFragment1", String.format("Orientations: Sensor = %d˚, Device = %d˚, Display = %d˚",
                    sensorOrientation, deviceRotation, mDisplayOrientation));

            int orientation = VideoStreamView.getScreenOrientation(activity);
            if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                    orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            // noinspection ResourceType
            manager.openCamera((String) mInterface.getCurrentCameraId(), mStateCallback, null);
        } catch (CameraAccessException e) {
            throwError(new Exception("Cannot access the camera.", e));
        } catch (NullPointerException e) {

        } catch (InterruptedException e) {
            throwError(new Exception("Interrupted while trying to lock camera opening.", e));
        }
    }

    @Override
    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throwError(new Exception("Interrupted while trying to lock camera opening.", e));
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize)
            return;
        try {
            if (!setUpMediaRecorder()) return;
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    throwError(new Exception("Camera configuration failed"));
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private boolean setUpMediaRecorder() {
        final Activity activity = getActivity();
        if (null == activity) return false;
        final RecodingInterface captureInterface = (RecodingInterface) activity;
        if (mMediaRecorder == null)
            mMediaRecorder = new MediaRecorder();
        boolean canUseAudio = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            canUseAudio = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (canUseAudio) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        } else {
            Toast.makeText(getActivity(), R.string.no_audio_access, Toast.LENGTH_LONG).show();
        }

        Log.d("RecordingFragment1", String.format(
                "Bit rate: %d, Frame rate: %d, Resolution: %s",
                captureInterface.videoBitRate(), captureInterface.videoFrameRate(),
                String.format(Locale.getDefault(), "%dx%d", mVideoSize.getWidth(), mVideoSize.getHeight())));

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (captureInterface.videoBitRate() > 0)
            mMediaRecorder.setVideoEncodingBitRate(captureInterface.videoBitRate());
        if (captureInterface.videoFrameRate() > 0)
            mMediaRecorder.setVideoFrameRate(captureInterface.videoFrameRate());
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (canUseAudio)
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        Uri uri = Uri.fromFile(getOutputMediaFile());
        mOutputUri = uri.toString();
        mMediaRecorder.setOutputFile(uri.getPath());
        mMediaRecorder.setOrientationHint(mDisplayOrientation);

        try {
            mMediaRecorder.prepare();
            return true;
        } catch (Throwable e) {
            throwError(new Exception("Failed to prepare the media recorder: " + e.getMessage(), e));
            return false;
        }
    }

    @Override
    public boolean startRecordingVideo() {
        super.startRecordingVideo();
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
        return false;
    }

    @Override
    public void stopRecordingVideo(boolean reachedZero) {
        super.stopRecordingVideo(reachedZero);

        if (mInterface.hasLengthLimit() && mInterface.shouldAutoSubmit() &&
                (mInterface.getRecordingStart() < 0 || mMediaRecorder == null)) {
            stopCounter();
            releaseRecorder();
            mInterface.onShowPreview(mOutputUri, reachedZero);
            return;
        }

        if (!mInterface.didRecord())
            mOutputUri = null;

        releaseRecorder();
        mButtonVideo.setImageResource(R.drawable.capture);
        if (!CameraUtil.isArcWelder())
            mButtonFacing.setVisibility(View.VISIBLE);
        if (mInterface.getRecordingStart() > -1 && getActivity() != null)
            mInterface.onShowPreview(mOutputUri, reachedZero);

        stopCounter();
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }


}