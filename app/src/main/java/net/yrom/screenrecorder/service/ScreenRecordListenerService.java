package net.yrom.screenrecorder.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import net.yrom.screenrecorder.IScreenRecorderAidlInterface;
import net.yrom.screenrecorder.ui.activity.ScreenRecordActivity;
import net.yrom.screenrecorder.R;
import net.yrom.screenrecorder.model.DanmakuBean;

import java.util.List;

/**
 * author : raomengyang on 2016/12/29.
 */
@SuppressLint("LongLogTag")
public class ScreenRecordListenerService extends Service {
    private static final String TAG = "ScreenRecordListenerService";

    public static final int REQUEST_CODE_PENDING = 0x01;
    private static final int NOTIFICATION_ID = 3;

    private NotificationManager notificationManager;
    private NotificationCompat.Builder builder;
    private IScreenRecorderAidlInterface.Stub binder = new IScreenRecorderAidlInterface.Stub() {
        @Override
        public void startScreenRecord(Intent bundleData) throws RemoteException {
        }

        @Override
        public void sendDanmaku(List<DanmakuBean> danmakuBeanList) throws RemoteException {
            Log.e(TAG, "danmaku msg = " + danmakuBeanList.get(0).getMessage());
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        initNotification();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private void initNotification() {
        builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText("您正在录制视频内容哦")
                .setOngoing(true)
                .setDefaults(Notification.DEFAULT_VIBRATE);
        Intent intent = new Intent(this, ScreenRecordActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, REQUEST_CODE_PENDING, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}