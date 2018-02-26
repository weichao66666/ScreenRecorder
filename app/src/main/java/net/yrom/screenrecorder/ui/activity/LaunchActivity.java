package net.yrom.screenrecorder.ui.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import net.yrom.screenrecorder.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class LaunchActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_STREAM = 1;
    private static String[] PERMISSIONS_STREAM = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @BindView(R.id.btn_screen_record)
    Button btnScreenRecord;
    @BindView(R.id.btn_camera_record)
    Button btnCameraRecord;

    boolean authorized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);
        ButterKnife.bind(this);
        verifyPermissions();
    }

    @OnClick({R.id.btn_screen_record, R.id.btn_camera_record})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_screen_record:
                ScreenRecordActivity.launchActivity(this);
                break;
            case R.id.btn_camera_record:
                CameraActivity.launchActivity(this);
                break;
        }
    }

    public void verifyPermissions() {
        int CAMERA_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int RECORD_AUDIO_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        int WRITE_EXTERNAL_STORAGE_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (CAMERA_permission != PackageManager.PERMISSION_GRANTED
                || RECORD_AUDIO_permission != PackageManager.PERMISSION_GRANTED
                || WRITE_EXTERNAL_STORAGE_permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_STREAM, REQUEST_CODE_STREAM);
            authorized = false;
        } else {
            authorized = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STREAM) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED
                    && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                authorized = true;
            }
        }
    }
}