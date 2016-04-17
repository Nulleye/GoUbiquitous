package com.example.android.sunshine.app.sync;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.SunshineApplication;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.common.SunshineWearContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

/**
 * Created by cristian on 11/4/16.
 */
public class SunshineWearListener extends WearableListenerService {

    public static final String TAG = SunshineWearListener.class.getSimpleName();


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);
        super.onMessageReceived(messageEvent);

        if (SunshineWearContract.WEATHER_UPDATE.equalsIgnoreCase(messageEvent.getPath())) {
            final DataMap dataMap = DataMap.fromByteArray(messageEvent.getData());
            final int weatherId = dataMap.getInt(SunshineWearContract.WEATHER_ICON_ID, SunshineWearContract.WEATHER_ICON_NOID);
            final String maxTemp = dataMap.getString(SunshineWearContract.WEATHER_MAXIMUM_TEMPERATURE, null);
            final String minTemp = dataMap.getString(SunshineWearContract.WEATHER_MINIMUM_TEMPERATURE, null);
            if ((maxTemp != null) && (minTemp != null) && (weatherId != SunshineWearContract.WEATHER_ICON_NOID))
                SunshineSyncAdapter.notifyWearDevices(weatherId, maxTemp, minTemp);
            else SunshineSyncAdapter.notifyWearDevices();
        }

    }


    public static void updateWearDevices(final int weatherId, final String maxTemp, final String minTemp) {
        Log.d(TAG, "updateWearDevices: " + weatherId + " " + maxTemp + " " + minTemp);

        final Context context = SunshineApplication.getContext();
        //Connect Google API
        final GoogleApiClient googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {

                    @Override
                    public void onConnected(Bundle bundle) {
                        Log.v(TAG, "GoogleAPI: connected " + bundle);
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.v(TAG, "GoogleAPI: suspended " + i);
                    }

                }).addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {

                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.v(TAG, "GoogleAPI: connection failed " + result);
                    }

                }).build();
        googleApiClient.blockingConnect(60, TimeUnit.SECONDS);
        //googleApiClient.connect();

        if (googleApiClient.hasConnectedApi(Wearable.API)) {

            //Build weather update
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(SunshineWearContract.WEATHER_UPDATE);
            DataMap dataMap = dataMapRequest.getDataMap();
            dataMap.putLong(SunshineWearContract.WEATHER_TIME, System.currentTimeMillis());
            dataMap.putString(SunshineWearContract.WEATHER_MAXIMUM_TEMPERATURE, maxTemp);
            dataMap.putString(SunshineWearContract.WEATHER_MINIMUM_TEMPERATURE, minTemp);
            dataMap.putInt(SunshineWearContract.WEATHER_ICON_ID, weatherId);
            //Pass the scaled xxhdpi version of the icon, as we don't know the screen density of the wearable
            //The wearable will know that this is an xxdpi image so it will need to rescale to its own density
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inDensity = SunshineWearContract.MAX_DENSITY;
            Bitmap resizedIcon = SunshineWearContract.scaleDown(
                    BitmapFactory.decodeResource(context.getResources(), weatherId, opts),
                    SunshineWearContract.dpToMaxDensityPx(SunshineWearContract.WEATHER_ICON_SIZE), true);
            dataMap.putAsset(SunshineWearContract.WEATHER_ICON, Utility.createAssetFromBitmap(resizedIcon));

            //Put weather update
            dataMapRequest.setUrgent();
            PendingResult<DataApi.DataItemResult> pendingResult =
                    Wearable.DataApi.putDataItem(googleApiClient, dataMapRequest.asPutDataRequest());
            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {

                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    Log.d(TAG, "putDataItem.setResultCallback - onResult(): " + dataItemResult.getStatus());
                }

            });

        } else Log.d(TAG, "updateWearDevices: Wearable.API is not available!");

        googleApiClient.disconnect();

    }

}
