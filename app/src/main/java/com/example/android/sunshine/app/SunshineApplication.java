package com.example.android.sunshine.app;

import android.app.Application;
import android.content.Context;

/**
 * Created by cristian on 15/4/16.
 */
public class SunshineApplication extends Application {

    private static Context mContext;

    public static Context getContext() {
        return mContext;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
    }

}
