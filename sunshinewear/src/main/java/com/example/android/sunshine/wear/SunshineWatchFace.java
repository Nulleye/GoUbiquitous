/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.android.sunshine.common.SunshineWearContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {

    public static final String  TAG = "sunshine-wear";

    private static Typeface NORMAL_TYPEFACE;
    private static Typeface BOLD_TYPEFACE;
    private static Typeface NORMAL_TYPEFACE_COND;
    private static Typeface BOLD_TYPEFACE_COND;


    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {

        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;


        public EngineHandler(SunshineWatchFace.Engine reference) {
            Log.d(TAG, "create EngineHandler()");
            mWeakReference = new WeakReference<>(reference);
        }


        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        return; //Don't log this message!
                }
            }
            Log.d(TAG, "handleMessage: " + msg);
        }

    } //EngineHandler


    private class Engine extends CanvasWatchFaceService.Engine implements
            DataApi.DataListener, CapabilityApi.CapabilityListener,
//            MessageApi.MessageListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        private int TIMEOUT_MS = 30000;

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        boolean mRegisteredReceivers = false;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "onReceive: timezone change");
                refreshDateTimeData(intent.getStringExtra("time-zone"), true);
            }

        };

        final BroadcastReceiver mTimeChangeReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "onReceive: time change");
                refreshDateTimeData(null, true);
            }

        };

        final BroadcastReceiver mLocaleChangeReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "onReceive: locale change");
                refreshDateTimeData(null, true);
            }

        };

        final GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

//        int mTapCount;

        Calendar mCalendar;
        SimpleDateFormat mDateFormatDay;
        DateFormat mDateFormat;

        int mWeatherIconId = SunshineWearContract.WEATHER_ICON_NOID;
        String mMaximumTemperature = null;
        String mMinimumTemperature = null;

        boolean mAmbient;
        boolean mLowBitAmbient;

        float mYOffset;
        float mMargin;
        float mSeparatorWidth;

        Paint mBackgroundPaint;
        Paint mHoursPaint;
        Paint mMinutesPaint;
        Paint mSecondsPaint;
        Paint mDatePaint;
        Paint mSeparatorPaint;
        Paint mWeatherIconPaint;
        Bitmap mWeatherIconImage;
//        Bitmap mWeatherIconAmbientImage;
        Paint mMaximumTemperaturePaint;
        Paint mMinimumTemperaturePaint;
        Paint mWeatherInfoPaint;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            Log.d(TAG, "onCreate()");

            //Load custom typefaces
            try {
                NORMAL_TYPEFACE = Typeface.createFromAsset(SunshineWatchFace.this.getAssets(), "fonts/FuturaStdLight.otf");
                BOLD_TYPEFACE = Typeface.createFromAsset(SunshineWatchFace.this.getAssets(), "fonts/FuturaStdBook.otf");
                NORMAL_TYPEFACE_COND = Typeface.createFromAsset(SunshineWatchFace.this.getAssets(), "fonts/FuturaStdCondensedLight.otf");
                BOLD_TYPEFACE_COND = Typeface.createFromAsset(SunshineWatchFace.this.getAssets(), "fonts/FuturaStdCondensed.otf");
            } catch(Exception e) {
                Log.d(TAG, e.getMessage());
                NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
                BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
                NORMAL_TYPEFACE_COND = NORMAL_TYPEFACE;
                BOLD_TYPEFACE_COND = BOLD_TYPEFACE;
            }

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            final Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimensionPixelSize(R.dimen.digital_y_offset);
            mMargin = resources.getDimensionPixelSize(R.dimen.margin);
            mSeparatorWidth = resources.getDimensionPixelSize(R.dimen.separator_width);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mHoursPaint = createTextPaint(resources.getColor(R.color.digital_text), BOLD_TYPEFACE_COND);
            mMinutesPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE_COND);
            mSecondsPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE_COND);

            mDatePaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);
            mDatePaint.setTextAlign(Paint.Align.CENTER);

            mSeparatorPaint = new Paint();
            mSeparatorPaint.setColor(resources.getColor(R.color.digital_text));

            mWeatherIconPaint = new Paint();
            mMaximumTemperaturePaint = createTextPaint(resources.getColor(R.color.digital_text), BOLD_TYPEFACE);
            mMinimumTemperaturePaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);
            mWeatherInfoPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);
            mWeatherInfoPaint.setTextAlign(Paint.Align.CENTER);

            refreshDateTimeData(null, false);
//            connectGoogleApiClient(true);
        }


        private void refreshDateTimeData(final String timeZone, final boolean invalidate) {
            Log.d(TAG, "refreshDateTimeData: " + timeZone + " " + invalidate);
            mCalendar = (timeZone != null)?
                    Calendar.getInstance(TimeZone.getTimeZone(timeZone), Locale.getDefault()) :
                    Calendar.getInstance(TimeZone.getDefault());  //Calendar.getInstance(Locale.getDefault());
            mDateFormatDay = new SimpleDateFormat("EEE ");
            mDateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
            if (invalidate) invalidate();
        }


        @Override
        public void onDestroy() {
            Log.d(TAG,"onDestroy()");
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }


        private Paint createTextPaint(int textColor, Typeface typeFace) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeFace);
            paint.setAntiAlias(true);
            return paint;
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.d(TAG,"onVisibilityChanged: " + visible);
            super.onVisibilityChanged(visible);

            if (visible) {
                // Update time zone in case it changed while we weren't visible.
                refreshDateTimeData(null, false);
                registerReceivers();
                connectGoogleApiClient(true); //Will register listeners once connected
            } else {
                unregisterReceivers();
                unregisterListeners();
                connectGoogleApiClient(false);
            }
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }


        //Send message to request weather data
        public void requestUpdateWeather() {

            connectGoogleApiClient(true);

            Log.d(TAG, "requestUpdateWeather: connected=" +
                    mGoogleApiClient.isConnected() + " hasWearableAPI=" +
                    mGoogleApiClient.hasConnectedApi(Wearable.API));

            DataMap dataMap = new DataMap();
            dataMap.putInt(SunshineWearContract.WEATHER_ICON_ID, mWeatherIconId);
            dataMap.putString(SunshineWearContract.WEATHER_MAXIMUM_TEMPERATURE, mMaximumTemperature);
            dataMap.putString(SunshineWearContract.WEATHER_MINIMUM_TEMPERATURE, mMinimumTemperature);
            Wearable.MessageApi.sendMessage(mGoogleApiClient, "", SunshineWearContract.WEATHER_UPDATE, dataMap.toByteArray())
                    .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {

                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                            Log.d(TAG, "sendMessage.setResultCallback - onResult(): " + sendMessageResult.getStatus());
                        }

                    });

        }


        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            Log.d(TAG, "onApplyWindowInsets: " + insets);
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            float textSize = resources.getDimensionPixelSize(isRound?
                    R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            mHoursPaint.setTextSize(textSize);
            mMinutesPaint.setTextSize(textSize);
            mSecondsPaint.setTextSize(resources.getDimensionPixelSize(isRound?
                    R.dimen.digital_text_seconds_size_round : R.dimen.digital_text_seconds_size));
            mDatePaint.setTextSize(resources.getDimensionPixelSize(isRound?
                    R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size));
            textSize = resources.getDimensionPixelSize(isRound?
                    R.dimen.digital_temperature_text_size_round : R.dimen.digital_temperature_text_size);
            mMaximumTemperaturePaint.setTextSize(textSize);
            mMinimumTemperaturePaint.setTextSize(textSize);
            mWeatherInfoPaint.setTextSize(resources.getDimensionPixelSize(isRound?
                    R.dimen.digital_info_text_size_round : R.dimen.digital_info_text_size));
        }


        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }


        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }


        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                Boolean setAlias = null;
                if (inAmbientMode) {
                    if (mLowBitAmbient == mBackgroundPaint.isAntiAlias()) setAlias = !mLowBitAmbient;
                } else if (!mBackgroundPaint.isAntiAlias()) setAlias = true;
                if (setAlias != null) {
                    mBackgroundPaint.setAntiAlias(setAlias);
                    mHoursPaint.setAntiAlias(setAlias);
                    mMinutesPaint.setAntiAlias(setAlias);
                    mSecondsPaint.setAntiAlias(setAlias);
                    mDatePaint.setAntiAlias(setAlias);
                    mSeparatorPaint.setAntiAlias(setAlias);
                    mWeatherIconPaint.setAntiAlias(setAlias);
                    mMaximumTemperaturePaint.setAntiAlias(setAlias);
                    mMinimumTemperaturePaint.setAntiAlias(setAlias);
                    mWeatherInfoPaint.setAntiAlias(setAlias);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }


//        /**
//         * Captures tap event (and tap type) and toggles the background color if the user finishes
//         * a tap.
//         */
//        @Override
//        public void onTapCommand(int tapType, int x, int y, long eventTime) {
//            Resources resources = SunshineWatchFace.this.getResources();
//            switch (tapType) {
//                case TAP_TYPE_TOUCH:
//                    // The user has started touching the screen.
//                    break;
//                case TAP_TYPE_TOUCH_CANCEL:
//                    // The user has started a different gesture or otherwise cancelled the tap.
//                    break;
//                case TAP_TYPE_TAP:
//                    // The user has completed the tap gesture.
//                    mTapCount++;
//                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
//                            R.color.background : R.color.background2));
//                    break;
//            }
//            invalidate();
//        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            //Log.d(TAG, "onDraw: " + bounds);

            // Draw the background.
            if (isInAmbientMode()) canvas.drawColor(Color.BLACK);
            else canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            final Date now = new Date();
            mCalendar.setTime(now);

            final float midX = bounds.width() / 2;
            float currentY = mYOffset;

            //Log.d(TAG, "onDraw: midX=" + midX + " mYOffset=" + mYOffset);

            //Draw time
            final String hours = String.format("%tH", mCalendar);
            final float hoursSize = mHoursPaint.measureText(hours);
            final String minutes = String.format(":%tM", mCalendar);
            final float minutesSize = mMinutesPaint.measureText(minutes);
            final String seconds;
            final float secondsSize;
            if (!isInAmbientMode()) {
                seconds = String.format(":%tS", mCalendar);
                secondsSize = mSecondsPaint.measureText(seconds);
            } else {
                seconds = null;
                secondsSize = 0;
            }
            float currentX = midX - ((hoursSize + minutesSize + secondsSize) / 2);
            canvas.drawText(hours, currentX, currentY, mHoursPaint);
            currentX = currentX + hoursSize;
            canvas.drawText(minutes, currentX, currentY, mMinutesPaint);
            if (seconds != null) {
                currentX = currentX + minutesSize;
                canvas.drawText(seconds, currentX, currentY, mSecondsPaint);
            }

            //Draw date
            final String date = mDateFormatDay.format(now) + mDateFormat.format(now);
            currentY = currentY + measureHeight(mDatePaint, date) + mMargin;
            //Log.d(TAG, "onDraw: currentY=" + currentY);
            canvas.drawText(date, midX , currentY, mDatePaint);

            //Draw separator
            currentY = currentY + mMargin;
            //Log.d(TAG, "onDraw: currentY=" + currentY);
            canvas.drawLine(midX - (mSeparatorWidth / 2), currentY, midX + (mSeparatorWidth / 2), currentY, mSeparatorPaint);

            //Draw weather icon && temperatures
            currentY = currentY + mSeparatorPaint.getStrokeWidth() + mMargin;
            if ((mMaximumTemperature != null) && (mMinimumTemperature != null)) {
                final float maxTempSize = mMaximumTemperaturePaint.measureText(mMaximumTemperature);
                final float minTempSize = mMinimumTemperaturePaint.measureText(mMinimumTemperature);
                final float weatherIconSize;
                if (mWeatherIconImage != null) weatherIconSize = mWeatherIconImage.getWidth();
                else weatherIconSize = 0;
                final float separationSize = mMargin;
                final float maxH = Math.max(measureHeight(mMaximumTemperaturePaint, mMaximumTemperature),
                        measureHeight(mMaximumTemperaturePaint, mMaximumTemperature));
                currentY = currentY + maxH;
                //Log.d(TAG, "onDraw: currentY=" + currentY);
                if (weatherIconSize != 0) {
                    //Log.d(TAG, "onDraw: h=" + mWeatherIconImage.getHeight() + " w=" + mWeatherIconImage.getWidth());
                    currentX = midX - ((weatherIconSize + maxTempSize + minTempSize + (2*separationSize)) / 2);
                    final int imgH = mWeatherIconImage.getHeight();
                    canvas.drawBitmap(mWeatherIconImage, currentX, currentY - (imgH + ((maxH-imgH)/2)), mWeatherIconPaint);
                    currentX += weatherIconSize + separationSize;
                    canvas.drawText(mMaximumTemperature, currentX, currentY, mMaximumTemperaturePaint);
                    currentX += maxTempSize + separationSize;
                    canvas.drawText(mMinimumTemperature, currentX, currentY, mMinimumTemperaturePaint);
                } else {
                    currentX = midX - ((maxTempSize + (2*separationSize) + maxTempSize) / 2);
                    canvas.drawText(mMaximumTemperature, currentX, currentY, mMaximumTemperaturePaint);
                    currentX += maxTempSize + (2*separationSize);
                    canvas.drawText(mMinimumTemperature, currentX, currentY, mMinimumTemperaturePaint);
                }
            } else {
                final String str = getString(R.string.no_weather);
                currentY = currentY + measureHeight(mWeatherInfoPaint, str);
                canvas.drawText(str, midX , currentY, mWeatherInfoPaint);
            }
        }


        public int measureHeight(final Paint paint, final String text) {
            Rect result = new Rect();
            paint.getTextBounds(text, 0, text.length(), result);
            return result.height();
        }


        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            Log.d(TAG, "updateTimer()");
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }


        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }


        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        private void connectGoogleApiClient(boolean connect) {
            if (mGoogleApiClient.isConnected() != connect) {
                if (connect) mGoogleApiClient.connect();
                else mGoogleApiClient.disconnect();
            }
        }


        private void registerReceivers() {
            Log.d(TAG, "registerReceivers()");
            if (!mRegisteredReceivers) {
                mRegisteredReceivers = true;
                IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
                filter = new IntentFilter(Intent.ACTION_TIME_CHANGED);
                SunshineWatchFace.this.registerReceiver(mTimeChangeReceiver, filter);
                filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
                SunshineWatchFace.this.registerReceiver(mLocaleChangeReceiver, filter);
            }
        }


        private void unregisterReceivers() {
            Log.d(TAG, "unregisterReceivers()");
            if (mRegisteredReceivers) {
                mRegisteredReceivers = false;
                SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
                SunshineWatchFace.this.unregisterReceiver(mTimeChangeReceiver);
                SunshineWatchFace.this.unregisterReceiver(mLocaleChangeReceiver);
            }
        }


        private void registerListeners() {
            Log.d(TAG, "registerListeners()");
            //Wearable.MessageApi.addListener(mGoogleApiClient, this);
            Wearable.DataApi.addListener(mGoogleApiClient, this);
            Wearable.CapabilityApi.addCapabilityListener(mGoogleApiClient,
                    this, SunshineWearContract.WEATHER_CAPABILITY);
        }


        private void unregisterListeners() {
            Log.d(TAG, "unregisterListeners()");
            //Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.CapabilityApi.removeCapabilityListener(mGoogleApiClient,
                    this, SunshineWearContract.WEATHER_CAPABILITY);
        }


        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "GoogleAPI: connected " + bundle);
            registerListeners();
            requestUpdateWeather();
        }


        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "GoogleAPI: suspended " + i);
        }


        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(TAG, "GoogleAPI: connection failed " + connectionResult);
        }


        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged: " + dataEvents);

            //Find latest data
            long latest = 0;
            DataMap latestDataMap = null;
            for (DataEvent event : dataEvents)
                if ((event.getType() == DataEvent.TYPE_CHANGED) &&
                        (SunshineWearContract.WEATHER_UPDATE.equals(event.getDataItem().getUri().getPath()))) {
                    DataItem dataItem = event.getDataItem();
                    Log.d(TAG, "onDataChanged: update data " + dataItem.getUri());
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                    DataMap dataMap = dataMapItem.getDataMap();
                    long time = dataMap.getLong(SunshineWearContract.WEATHER_TIME, 0);
                    if (latest < time) {
                        latest = time;
                        latestDataMap = dataMap;
                    }
                }

            //Get latest data
            if ((latest > 0) && (latestDataMap != null)) {
                mMaximumTemperature = latestDataMap.getString(SunshineWearContract.WEATHER_MAXIMUM_TEMPERATURE, null);
                mMinimumTemperature = latestDataMap.getString(SunshineWearContract.WEATHER_MINIMUM_TEMPERATURE, null);
                mWeatherIconId = latestDataMap.getInt(SunshineWearContract.WEATHER_ICON_ID, SunshineWearContract.WEATHER_ICON_NOID);
                Asset profileAsset = latestDataMap.getAsset(SunshineWearContract.WEATHER_ICON);
                if (profileAsset != null) {
                    Wearable.DataApi.getFdForAsset(mGoogleApiClient,
                            profileAsset).setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {

                        @Override
                        public void onResult(DataApi.GetFdForAssetResult getFdForAssetResult) {
                            Bitmap receivedBitmap = BitmapFactory.decodeStream(getFdForAssetResult.getInputStream());
                            //This is a xxdpi bitmap, so we check if we need to rescale to "my" density
                            int myDpi = Resources.getSystem().getDisplayMetrics().densityDpi;
                            if (SunshineWearContract.bitmapNeedRescale(myDpi))
                                mWeatherIconImage = SunshineWearContract.scaleDown(receivedBitmap,
                                    SunshineWearContract.maxDensityPxToDensityPx(myDpi, receivedBitmap.getWidth()), true);
                            else mWeatherIconImage = receivedBitmap;
                            Log.d(TAG, "onResult: myDpi="+ myDpi + " mWeatherIconImage=" + mWeatherIconImage.getWidth() +
                                    " receivedBitmap=" + receivedBitmap.getWidth());
                            invalidate();
                        }

                    });
                    //mWeatherIconImage = loadBitmapFromAsset(profileAsset);
                } else {
                    mWeatherIconImage = null;
                    invalidate();
                }
            } //if
        }


        public Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result =
                    mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mGoogleApiClient, asset).await().getInputStream();
            mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }


//        @Override
//        public void onMessageReceived(MessageEvent messageEvent) {
//            Log.d(TAG, "onMessageReceived: " + messageEvent.getPath());
//            if (SunshineWearContract.WEATHER_UPDATE.equalsIgnoreCase(messageEvent.getPath())) {
//                DataMap dataMap = DataMap.fromByteArray(messageEvent.getData());
//                mMaximumTemperature = dataMap.getString(SunshineWearContract.WEATHER_MAXIMUM_TEMPERATURE);
//                mMinimumTemperature = dataMap.getString(SunshineWearContract.WEATHER_MINIMUM_TEMPERATURE);
//                invalidate();
//            }
//        }


        @Override
        public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
            Log.d(TAG, "onCapabilityChanged: " + capabilityInfo);
            requestUpdateWeather();
        }

    } //Engine

}
