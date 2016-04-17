package com.example.android.sunshine.common;

import android.graphics.Bitmap;
import android.util.DisplayMetrics;

/**
 * Created by cristian on 12/4/16.
 */
public class SunshineWearContract {

    public static final String WEATHER_UPDATE = "/SunshineWear/Update";

    public static final String WEATHER_MAXIMUM_TEMPERATURE = "maximum_temperature";
    public static final String WEATHER_MINIMUM_TEMPERATURE = "minimum_temperature";

    public static final String WEATHER_ICON_ID = "weather_icon_id";
        public static final int WEATHER_ICON_NOID = -1;
    public static final String WEATHER_ICON = "weather_icon";

    public static final String WEATHER_TIME = "weather_time";


    public static final int WEATHER_ICON_SIZE = 32; //These are dp units

    public static final String WEATHER_CAPABILITY = "sunshine_weather";


    public static final int MAX_DENSITY = DisplayMetrics.DENSITY_XXHIGH;

    public static float dpToMaxDensityPx(int dp) {
        return dp * (MAX_DENSITY / DisplayMetrics.DENSITY_DEFAULT);
    }

    public static int maxDensityPxToDensityPx(int density, int px) {
        float result = px * ((float) density / (float) MAX_DENSITY);
        return (result < 1F)? 1 : Math.round(result);
    }

    public static boolean bitmapNeedRescale(int density) {
        return (density != MAX_DENSITY);
    }

    public static Bitmap scaleDown(Bitmap realImage, float maxImageSize,
            boolean filter) {
        float ratio = Math.min(maxImageSize / realImage.getWidth(), maxImageSize / realImage.getHeight());
        Bitmap newBitmap = Bitmap.createScaledBitmap(realImage,
                Math.round(ratio * realImage.getWidth()),
                Math.round(ratio * realImage.getHeight()), filter);
        return newBitmap;
    }

}
