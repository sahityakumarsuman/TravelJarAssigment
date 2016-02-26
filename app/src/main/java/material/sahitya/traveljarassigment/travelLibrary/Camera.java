package material.sahitya.traveljarassigment.travelLibrary;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;



import java.io.File;

import material.sahitya.traveljarassigment.travelLibrary.internal.Key;
import material.sahitya.traveljarassigment.travelLibrary.util.CameraUtil;

/**
 * @author Aidan Follestad (afollestad)
 */
public class Camera {

    public static final String ERROR_EXTRA = "mcam_error";
    public static final String STATUS_EXTRA = "mcam_status";

    public static final int STATUS_RECORDED = 1;
    public static final int STATUS_RETRY = 2;

    private Activity mContext;
    private long mLengthLimit = -1;
    private boolean mAllowRetry = true;
    private boolean mAutoSubmit = false;
    private String mSaveDir;
    private int mPrimaryColor;
    private boolean mShowPortraitWarning = true;
    private boolean mDefaultToFrontFacing = false;
    private boolean mCountdownImmediately = false;
    private boolean mRetryExists = false;
    private boolean mRestartTimerOnRetry = false;
    private boolean mContinueTimerInPlayback = true;
    private boolean mForceCamera1 = false;

    private int mVideoBitRate = -1;
    private int mVideoFrameRate = -1;
    private int mVideoPreferredHeight = -1;
    private float mVideoPreferredAspect = -1f;

    public Camera(@NonNull Activity context) {
        mContext = context;
       // mPrimaryColor = DialogUtils.resolveColor(context, R.attr.colorPrimary);
    }

    public Camera countdownMillis(long lengthLimitMs) {
        mLengthLimit = lengthLimitMs;
        return this;
    }

    /**
     * @deprecated use {@link #countdownMillis(long)} instead.
     */
    @Deprecated
    public Camera lengthLimitMillis(long lengthLimitMs) {
        mLengthLimit = lengthLimitMs;
        return this;
    }

    public Camera countdownSeconds(float lengthLimitSec) {
        return countdownMillis((int) (lengthLimitSec * 1000f));
    }

    /**
     * @deprecated use {@link #countdownSeconds(float)} instead.
     */
    @Deprecated
    public Camera lengthLimitSeconds(float lengthLimitSec) {
        return countdownMillis((int) (lengthLimitSec * 1000f));
    }

    public Camera countdownMinutes(float lengthLimitMin) {
        return countdownMillis((int) (lengthLimitMin * 1000f * 60f));
    }

    /**
     * @deprecated use {@link #countdownMinutes(float)} instead.
     */
    @Deprecated
    public Camera lengthLimitMinutes(float lengthLimitMin) {
        return countdownMillis((int) (lengthLimitMin * 1000f * 60f));
    }

    public Camera allowRetry(boolean allowRetry) {
        mAllowRetry = allowRetry;
        return this;
    }

    public Camera autoSubmit(boolean autoSubmit) {
        mAutoSubmit = autoSubmit;
        return this;
    }

    public Camera countdownImmediately(boolean immediately) {
        mCountdownImmediately = immediately;
        return this;
    }

    public Camera saveDir(@Nullable File dir) {
        if (dir == null) return saveDir((String) null);
        return saveDir(dir.getAbsolutePath());
    }

    public Camera saveDir(@Nullable String dir) {
        mSaveDir = dir;
        return this;
    }

    public Camera primaryColor(@ColorInt int color) {
        mPrimaryColor = color;
        return this;
    }

    public Camera primaryColorRes(@ColorRes int colorRes) {
        return primaryColor(ContextCompat.getColor(mContext, colorRes));
    }

//    public Camera primaryColorAttr(@AttrRes int colorAttr) {
//        return primaryColor(DialogUtils.resolveColor(mContext, colorAttr));
//    }

    public Camera showPortraitWarning(boolean show) {
        mShowPortraitWarning = show;
        return this;
    }

    public Camera defaultToFrontFacing(boolean frontFacing) {
        mDefaultToFrontFacing = frontFacing;
        return this;
    }

    public Camera retryExits(boolean exits) {
        mRetryExists = exits;
        return this;
    }

    public Camera restartTimerOnRetry(boolean restart) {
        mRestartTimerOnRetry = restart;
        return this;
    }

    public Camera continueTimerInPlayback(boolean continueTimer) {
        mContinueTimerInPlayback = continueTimer;
        return this;
    }

    public Camera forceCamera1() {
        mForceCamera1 = true;
        return this;
    }

    public Camera videoBitRate(@IntRange(from = 1, to = Integer.MAX_VALUE) int rate) {
        mVideoBitRate = rate;
        return this;
    }

    public Camera videoFrameRate(@IntRange(from = 1, to = Integer.MAX_VALUE) int rate) {
        mVideoFrameRate = rate;
        return this;
    }

    public Camera videoPreferredHeight(@IntRange(from = 1, to = Integer.MAX_VALUE) int height) {
        mVideoPreferredHeight = height;
        return this;
    }

    public Camera videoPreferredAspect(@FloatRange(from = 0.1, to = Float.MAX_VALUE) float ratio) {
        mVideoPreferredAspect = ratio;
        return this;
    }

    public Intent getIntent() {
        final Class<?> cls = !mForceCamera1 && CameraUtil.hasCamera2(mContext) ?
                RecordActivity2Recording.class : RecordActivityRecording.class;
        Intent intent = new Intent(mContext, cls)
                .putExtra(Key.LENGTH_LIMIT, mLengthLimit)
                .putExtra(Key.ALLOW_RETRY, mAllowRetry)
                .putExtra(Key.AUTO_SUBMIT, mAutoSubmit)
                .putExtra(Key.SAVE_DIR, mSaveDir)
                .putExtra(Key.PRIMARY_COLOR, mPrimaryColor)
                .putExtra(Key.SHOW_PORTRAIT_WARNING, mShowPortraitWarning)
                .putExtra(Key.DEFAULT_TO_FRONT_FACING, mDefaultToFrontFacing)
                .putExtra(Key.COUNTDOWN_IMMEDIATELY, mCountdownImmediately)
                .putExtra(Key.RETRY_EXITS, mRetryExists)
                .putExtra(Key.RESTART_TIMER_ON_RETRY, mRestartTimerOnRetry)
                .putExtra(Key.CONTINUE_TIMER_IN_PLAYBACK, mContinueTimerInPlayback);

        if (mVideoBitRate > 0)
            intent.putExtra(Key.VIDEO_BIT_RATE, mVideoBitRate);
        if (mVideoFrameRate > 0)
            intent.putExtra(Key.VIDEO_FRAME_RATE, mVideoFrameRate);
        if (mVideoPreferredHeight > 0)
            intent.putExtra(Key.VIDEO_PREFERRED_HEIGHT, mVideoPreferredHeight);
        if (mVideoPreferredAspect > 0f)
            intent.putExtra(Key.VIDEO_PREFERRED_ASPECT, mVideoPreferredAspect);
        return intent;
    }

    public void start(int requestCode) {
        mContext.startActivityForResult(getIntent(), requestCode);
    }
}