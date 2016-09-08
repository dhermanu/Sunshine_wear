package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by dean on 9/6/16.
 */
public class SunshineWatchService extends Service
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    private static final String KEY_ID = "WEATHER_ID";
    private static final String KEY_MAX_TEMP = "MAX_TEMP";
    private static final String KEY_MIN_TEMP = "MIN_TEMP";
    private static final String PATH_WEATHER = "/weather";
    private static final String ACTION_UPDATE_WEATHER_WATCHFACE = "UPDATE_WEATHER";

    private GoogleApiClient mGoogleApiClient;


    @Override
    public void onConnected(Bundle bundle) {
        String locationQuery = Utility.getPreferredLocation(this);

        Uri weatherUri = WeatherContract.WeatherEntry
                .buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

        Cursor cursor = getContentResolver().query(
                        weatherUri,
                        new String[]{WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                        WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                        WeatherContract.WeatherEntry.COLUMN_MIN_TEMP},
                        null, null, null);
        if(cursor.moveToFirst()){
            int id = cursor.getInt
                    (cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID));
            String high = Utility.formatTemperature
                    (this, cursor.getDouble(cursor.getColumnIndex
                            (WeatherContract.WeatherEntry.COLUMN_MAX_TEMP)));
            String low = Utility.formatTemperature
                    (this, cursor
                            .getDouble(cursor.getColumnIndex
                                    (WeatherContract.WeatherEntry.COLUMN_MAX_TEMP)));

            Log.d("DATA", high);
            Log.d("DATA", low);
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WEATHER);
            putDataMapRequest.getDataMap().putInt(KEY_ID, id);
            putDataMapRequest.getDataMap().putString(KEY_MAX_TEMP, high);
            putDataMapRequest.getDataMap().putString(KEY_MIN_TEMP, low);

            PendingResult<DataApi.DataItemResult> pendingResult =
                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataMapRequest.asPutDataRequest());

            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    if (dataItemResult.getStatus().isSuccess()) {
                        Log.d("AdapterWear", "DataItem stored Successfully");
                    } else {
                        Log.d("AdapterWear", "DataItem not stored");
                    }
                }
            });

        }
        cursor.close();


    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.v("TEST", "EVER GO HERE");
        if(intent != null &&intent.getAction() != null
                &&  intent.getAction().equals(ACTION_UPDATE_WEATHER_WATCHFACE)){
            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mGoogleApiClient.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}