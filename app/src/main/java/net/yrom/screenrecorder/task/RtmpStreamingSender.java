package net.yrom.screenrecorder.task;

import android.text.TextUtils;

import net.yrom.screenrecorder.core.ResCoreParameters;
import net.yrom.screenrecorder.rtmp.FlvMetaData;
import net.yrom.screenrecorder.rtmp.ResFlvData;
import net.yrom.screenrecorder.rtmp.RtmpClient;
import net.yrom.screenrecorder.tools.LogTools;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by raomengyang on 12/03/2017.
 */
public class RtmpStreamingSender implements Runnable {
    private static final String TAG = "RtmpStreamingSender";

    private static class STATE {
        private static final int START = 0;
        private static final int RUNNING = 1;
        private static final int STOPPED = 2;
    }

    private volatile int state;

    private String mRtmpAddr = null;

    private final Object lock = new Object();
    private static final int MAX_QUEUE_CAPACITY = 50;
    private LinkedBlockingDeque<ResFlvData> frameQueue = new LinkedBlockingDeque<>(MAX_QUEUE_CAPACITY);
    private AtomicBoolean quit = new AtomicBoolean(false);
    private FlvMetaData flvMetaData;
    private ResCoreParameters coreParameters;

    private long jniRtmpPointer = 0;
    private int writeMsgNum = 0;

    public RtmpStreamingSender() {
        coreParameters = new ResCoreParameters();
        coreParameters.mediacodecAACBitRate = ResFlvData.AAC_BITRATE;
        coreParameters.mediacodecAACSampleRate = ResFlvData.AAC_SAMPLE_RATE;
        coreParameters.mediacodecAVCFrameRate = ResFlvData.FPS;
        coreParameters.videoWidth = ResFlvData.VIDEO_WIDTH;
        coreParameters.videoHeight = ResFlvData.VIDEO_HEIGHT;

        flvMetaData = new FlvMetaData(coreParameters);
    }

    @Override
    public void run() {
        while (!quit.get()) {
            if (frameQueue.size() > 0) {
                switch (state) {
                    case STATE.START:
                        if (TextUtils.isEmpty(mRtmpAddr)) {
                            LogTools.e("rtmp address is null!");
                            break;
                        }

                        // 建立 RTMP 链接
                        jniRtmpPointer = RtmpClient.open(mRtmpAddr, true);
                        if (jniRtmpPointer != 0) {
                            // 获取地址
                            String serverIpAddr = RtmpClient.getIpAddr(jniRtmpPointer);
                            LogTools.d("server ip address: " + serverIpAddr);

                            // 发送 onMetaData TAG
                            byte[] metaData = flvMetaData.getMetaData();
                            RtmpClient.write(jniRtmpPointer, metaData, metaData.length, ResFlvData.FLV_TAGTYPE_SCRIPT_DATA, 0);

                            state = STATE.RUNNING;
                        }
                        break;
                    case STATE.RUNNING:
                        synchronized (lock) {
                            --writeMsgNum;
                        }
                        ResFlvData flvData = frameQueue.pop();

                        // 如果发送队列中的数据过多，在条件满足的情况下，忽略这个数据
                        if (writeMsgNum >= (MAX_QUEUE_CAPACITY * 2 / 3) && flvData.flvTagType == ResFlvData.FLV_TAGTYPE_VIDEO && flvData.droppable) {
                            LogTools.d("senderQueue is crowded, abort a frame");
                            break;
                        }

                        // 发送 AVC TAG
                        int result = RtmpClient.write(jniRtmpPointer, flvData.byteBuffer, flvData.byteBuffer.length, flvData.flvTagType, flvData.dts);
                        if (result == 0) {
                            switch (flvData.flvTagType) {
                                case ResFlvData.FLV_TAGTYPE_VIDEO:
                                    LogTools.d("video frame sent: " + flvData.size);
                                    break;
                                case ResFlvData.FLV_TAGTYPE_AUDIO:
                                    LogTools.d("audio frame sent: " + flvData.size);
                                    break;
                            }
                        } else {
                            LogTools.e("write error: " + result);
                        }
                        break;
                    case STATE.STOPPED:
                        // 关闭 RTMP 链接
                        result = RtmpClient.close(jniRtmpPointer);
                        LogTools.d("close result: " + result);
                        break;
                }
            }
        }
        int result = RtmpClient.close(jniRtmpPointer);
        LogTools.e("close result: " + result);
    }

    public void sendStart(String rtmpAddr) {
        synchronized (lock) {
            writeMsgNum = 0;
        }
        mRtmpAddr = rtmpAddr;
        state = STATE.START;
    }

    public void sendStop() {
        synchronized (lock) {
            writeMsgNum = 0;
        }
        state = STATE.STOPPED;
    }

    public void sendFood(ResFlvData flvData, int type) {
        synchronized (lock) {
            if (writeMsgNum <= MAX_QUEUE_CAPACITY) {
                frameQueue.add(flvData);
                ++writeMsgNum;
            } else {
                LogTools.d("senderQueue is full, abort a frame");
            }
        }
    }

    public final void quit() {
        quit.set(true);
    }
}