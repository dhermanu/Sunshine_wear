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

/**
 * Created by dean on 8/31/16.
 */

package com.example.dean.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
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

/*reference: android watch face digital sample cod*/
public class SunshineWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "SunshineWatchFaceService";

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    static final int MSG_UPDATE_TIME = 0;



    private static final String KEY_ID = "WEATHER_ID";
    private static final String KEY_MAX_TEMP = "MAX_TEMP";
    private static final String KEY_MIN_TEMP = "MIN_TEMP";
    private static final String PATH_WEATHER = "/weather";
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        /** Handler to update the time periodically in interactive mode. */
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        /**
         * Handles time zone and locale changes.
         */
        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };

        /**
         * Unregistering an unregistered receiver throws an exception. Keep track of the
         * registration state to prevent that.
         */
        boolean mRegisteredReceiver = false;

        Paint mBackgroundPaint;
        Paint mBackgroundAmbientPaint;
        Paint mDatePaint;
        Paint mHourPaint;
        Paint mMaxTempPaint;
        Paint mMinTempPaint;

        String mMaxTemp;
        String mMinTemp;
        Bitmap mWeatherImage;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;

        float mXOffset;
        float mYOffset;
        float mDateXOffset;
        float mTempXOffset;
        float mTempYOffset;
        float mLineHeight;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            mBackgroundAmbientPaint = new Paint();
            mBackgroundAmbientPaint.setColor(resources.getColor(R.color.analog_background));
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_date));
            mMaxTempPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mMinTempPaint = createTextPaint(resources.getColor(R.color.digital_date));
            mHourPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("E, dd MMM yyyy", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            mDateFormat = DateFormat.getDateFormat(SunshineWatchFaceService.this);
            mDateFormat.setCalendar(mCalendar);
        }

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {

            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.date_text_size_round : R.dimen.date_text_size);

            float tempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);

            mDateXOffset = resources.getDimension(isRound
                    ? R.dimen.date_x_offset_round : R.dimen.date_x_offset);

            mTempXOffset = resources.getDimension(R.dimen.temp_x_offset);
            mTempYOffset = resources.getDimension(R.dimen.temp_y_offset);


            mDatePaint.setTextSize(dateTextSize);
            mHourPaint.setTextSize(textSize);

            mMaxTempPaint.setTextSize(tempTextSize);
            mMinTempPaint.setTextSize(tempTextSize);


        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

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


            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mDatePaint.setAntiAlias(antiAlias);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            String text = String.format("%02d:%02d", mCalendar.get(Calendar.HOUR),mCalendar.get(Calendar.MINUTE));
            canvas.drawText(text, mXOffset, mYOffset, mHourPaint);

            if (getPeekCardPosition().isEmpty()) {

                // Day of week
                canvas.drawText(
                        mDayOfWeekFormat.format(mDate),
                        mDateXOffset, mYOffset + mLineHeight, mDatePaint);

                canvas.drawLine(bounds.centerX() - 30, bounds.centerY()+30, bounds.centerX()+30, bounds.centerY()+30, mDatePaint);


                if(mMaxTemp != null){
                    canvas.drawText(mMaxTemp, mTempXOffset*2 + 20, mTempYOffset*2 + 10, mMaxTempPaint);
                }

                else{
                    canvas.drawText("00", mTempXOffset*2 + 20, mTempYOffset*2 + 10, mMaxTempPaint);
                }

                if(mMinTemp != null){
                    canvas.drawText(mMinTemp, mTempXOffset*3 + 10, mTempYOffset*2 + 10, mMinTempPaint);
                }

                else{
                    canvas.drawText("00", mTempXOffset*3 + 10, mTempYOffset*2 + 10, mMinTempPaint);
                }

                if(mWeatherImage != null){
                    mWeatherImage = Bitmap.createScaledBitmap(mWeatherImage, 75, 75, true);
                    canvas.drawBitmap(mWeatherImage, mTempXOffset-10, mTempYOffset+75, mMaxTempPaint);
                }

                else{
                    mWeatherImage = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
                    mWeatherImage = Bitmap.createScaledBitmap(mWeatherImage, 75, 75, true);
                    canvas.drawBitmap(mWeatherImage, mTempXOffset, mTempYOffset, mMaxTempPaint);
                }
            }


        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
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



        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if(dataItem.getUri().getPath().equals(PATH_WEATHER)){
                        assignDataChanged(dataItem);
                    }
                }
                invalidate();
            }
            dataEvents.release();
        }

        public void assignDataChanged(DataItem dataItem){
            DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();

            if(dataMap.containsKey(KEY_MAX_TEMP)){
                mMaxTemp = dataMap.getString(KEY_MIN_TEMP);
            }

            if(dataMap.containsKey(KEY_MIN_TEMP)){
                mMinTemp = dataMap.getString(KEY_MIN_TEMP);
            }

            if(dataMap.containsKey(KEY_ID)){
                int id = dataMap.getInt(KEY_ID);
                if(id != 0){
                    mWeatherImage = BitmapFactory
                            .decodeResource(getResources(),SunshineWatchFaceUtil
                                    .getImageResource(id));
                }

                else{
                    mWeatherImage = BitmapFactory
                            .decodeResource(getResources(), R.mipmap.ic_launcher);
                }
            }

        }


        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {

            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(new ResultCallback<DataItemBuffer>() {
                @Override
                public void onResult(DataItemBuffer dataItems) {
                    if (dataItems.getStatus().isSuccess()) {
                        Log.d("WatchService", "Data received");
                        for (DataItem item : dataItems) {
;
                            if (PATH_WEATHER.equals(item.getUri().getPath())) {

                                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                                mMinTemp = SunshineWatchFaceUtil
                                        .formatTemperature(dataMap.getString(KEY_MIN_TEMP));
                                if(mMinTemp != null)

                                mMaxTemp = SunshineWatchFaceUtil
                                        .formatTemperature(dataMap.getString(KEY_MAX_TEMP));
                                if(mMaxTemp != null)

                                if (dataMap.containsKey(KEY_ID)) {
                                    String mWeatherId = dataMap.getString(KEY_ID);
                                    mWeatherImage = BitmapFactory
                                            .decodeResource(getResources(),SunshineWatchFaceUtil
                                                    .getImageResource(Float.valueOf(mWeatherId)));
                                }
                            }
                        }
                        invalidate();
                        dataItems.release();
                    } else {
                        Log.d("WearService", "Data not received");
                    }


                }
            });
        }

        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            Log.d("Connection", "Connection suspended");

        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            Log.d("Connection", "Connection failed");

        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }


}
