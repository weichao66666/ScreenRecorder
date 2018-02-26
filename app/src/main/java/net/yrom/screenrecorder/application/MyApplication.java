package net.yrom.screenrecorder.application;

import android.app.Application;
import android.content.Context;

/**
 * Created by raomengyang on 12/03/2017.
 */
public class MyApplication extends Application {
    static {
        System.loadLibrary("screenrecorderrtmp");
    }

    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
    }

    public static Context getContext() {
        return context;
    }
}