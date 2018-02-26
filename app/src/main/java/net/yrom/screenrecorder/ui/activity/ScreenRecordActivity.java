package net.yrom.screenrecorder.ui.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import net.yrom.screenrecorder.R;
import net.yrom.screenrecorder.core.RESAudioClient;
import net.yrom.screenrecorder.core.RESCoreParameters;
import net.yrom.screenrecorder.rtmp.RESFlvData;
import net.yrom.screenrecorder.rtmp.RESFlvDataCollecter;
import net.yrom.screenrecorder.service.ScreenRecordListenerService;
import net.yrom.screenrecorder.task.RtmpStreamingSender;
import net.yrom.screenrecorder.task.ScreenRecorder;
import net.yrom.screenrecorder.tools.LogTools;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenRecordActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "ScreenRecordActivity";

    private static final int REQUEST_CODE = 1;

    private Button button;
    private EditText rtmpAddrET;

    private String rtmpAddr;
    private boolean isRecording;
    private MediaProjectionManager mediaProjectionManager;
    private ScreenRecorder screenRecorder;
    private RtmpStreamingSender streamingSender;
    private ExecutorService executorService;
    private RESAudioClient audioClient;
    private RESCoreParameters coreParameters;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    public static void launchActivity(Context context) {
        Intent intent = new Intent(context, ScreenRecordActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = findViewById(R.id.button);
        button.setOnClickListener(this);
        rtmpAddrET = findViewById(R.id.et_rtmp_address);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRecording) {
            stopScreenRecordService();// 在录屏状态，切换回应用，则停止录屏
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording) {
            startScreenRecordService();// 最小化应用后，开始录屏
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (screenRecorder != null) {
            stopScreenRecord();
        }
    }

    @Override
    public void onClick(View v) {
        if (screenRecorder != null) {
            stopScreenRecord();
        } else {
            startScreenCapture();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + ", " + resultCode + ")");
        MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);// 获取屏幕捕捉
        if (mediaProjection == null) {
            Log.e(TAG, "media projection is null");
            return;
        }

        rtmpAddr = rtmpAddrET.getText().toString().trim();
        if (TextUtils.isEmpty(rtmpAddr)) {
            Log.e(TAG, "rtmp address cannot be null");
            Toast.makeText(this, "rtmp address cannot be null", Toast.LENGTH_SHORT).show();
            return;
        }

        coreParameters = new RESCoreParameters();
        audioClient = new RESAudioClient(coreParameters);
        if (!audioClient.prepare()) {
            LogTools.d("!!!!!audioClient.prepare() failed");
            return;
        }

        streamingSender = new RtmpStreamingSender();
        streamingSender.sendStart(rtmpAddr);
        RESFlvDataCollecter collecter = new RESFlvDataCollecter() {
            @Override
            public void collect(RESFlvData flvData, int type) {
                streamingSender.sendFood(flvData, type);
            }
        };
        screenRecorder = new ScreenRecorder(collecter, RESFlvData.VIDEO_WIDTH, RESFlvData.VIDEO_HEIGHT, RESFlvData.VIDEO_BITRATE, 1, mediaProjection);
        screenRecorder.start();
        audioClient.start(collecter);

        executorService = Executors.newCachedThreadPool();
        executorService.execute(streamingSender);

        button.setText("Stop Recorder");
        Toast.makeText(this, "Screen recorder is running...", Toast.LENGTH_LONG).show();
        moveTaskToBack(true);// 最小化应用
    }

    private void startScreenCapture() {
        Log.d(TAG, "startScreenCapture()");
        isRecording = true;
        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();// 获取封装好的用于开始屏幕捕捉的 intent
        startActivityForResult(captureIntent, REQUEST_CODE);
    }

    private void stopScreenRecord() {
        Log.d(TAG, "stopScreenRecord()");
        screenRecorder.quit();
        screenRecorder = null;
        if (streamingSender != null) {
            streamingSender.sendStop();
            streamingSender.quit();
            streamingSender = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
        button.setText("Restart recorder");
    }

    private void startScreenRecordService() {
        Log.d(TAG, "startScreenRecordService()");
        if (screenRecorder != null && screenRecorder.getStatus()) {
            Intent intent = new Intent(this, ScreenRecordListenerService.class);
            bindService(intent, connection, BIND_AUTO_CREATE);
            startService(intent);
        }
    }

    private void stopScreenRecordService() {
        Log.d(TAG, "stopScreenRecordService()");
        Intent intent = new Intent(this, ScreenRecordListenerService.class);
        stopService(intent);
        if (screenRecorder != null && screenRecorder.getStatus()) {
            Toast.makeText(this, "现在正在进行录屏直播哦", Toast.LENGTH_SHORT).show();
        }
    }
}